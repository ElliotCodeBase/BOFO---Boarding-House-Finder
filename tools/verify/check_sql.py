#!/usr/bin/env python3
"""Stage 2 SQL checks. Run from repo root: python3 tools/verify/check_sql.py
Parses migrations with the real Postgres parser (pglast), lints 038..045, cross-checks app RPC/table use.
Prints only failures plus a summary. Needs: pip install pglast"""
import glob, os, re, sys
from pglast import parse_sql, ast
from pglast.enums import ObjectType, FunctionParameterMode, AlterTableType, RoleSpecType

root = os.getcwd()
files = sorted(f for f in glob.glob('supabase/migrations/*.sql') if not f.endswith('_REMOVE.sql'))
idx = lambda f: int(os.path.basename(f)[:3])
LINT_FROM = 38
fails, notes = [], []
def fail(m): fails.append(m)

sigs = {}
funcs = {}       # name -> {sig: dict}
first_fn = {}    # name -> first file idx
first_tbl = {}
tables = {}      # name -> set(cols)
rls_on, sd_funcs, revoked = set(), {}, {}
created_in = {}

def nm(parts): return [p.sval for p in parts]
def fname(n): return nm(n)[-1]

for f in files:
    i = idx(f); txt = open(f).read()
    try: stmts = parse_sql(txt)
    except Exception as e: fail(f'{f}: PARSE ERROR {e}'); continue
    for rs in stmts:
        s = rs.stmt; t = type(s).__name__
        if t == 'CreateFunctionStmt':
            n = fname(s.funcname)
            params = []
            for p in (s.parameters or []):
                if p.mode in (FunctionParameterMode.FUNC_PARAM_TABLE,): continue
                params.append((p.name, p.defexpr is not None))
            sd = False; sp = False
            for o in (s.options or []):
                if o.defname == 'security': sd = bool(o.arg.boolval) if hasattr(o.arg, 'boolval') else True
                if o.defname == 'set': sp = True
            ret_trig = s.returnType is not None and 'trigger' in [x.sval for x in s.returnType.names]
            from pglast import stream as _st
            rt = _st.RawStream()(s.returnType) if s.returnType is not None else 'void'
            key = (n, len(params)); cur = (tuple(p for p, _ in params), tuple(d for _, d in params), rt.replace(' ', '').lower())
            if key in sigs and i >= LINT_FROM and sigs[key][0] != cur and s.replace:
                fail(f'{f}: CREATE OR REPLACE {n} changes parameter names/defaults/return type vs migration {sigs[key][1]:03d} (Postgres will refuse: drop it first) old={sigs[key][0]} new={cur}')
            sigs[key] = (cur, i)
            funcs.setdefault(n, {})[len(params)] = dict(params=params, file=i, sd=sd, trig=ret_trig)
            first_fn.setdefault(n, i)
            created_in[n] = i
            if i >= LINT_FROM:
                if not s.replace: fail(f'{f}: CREATE FUNCTION {n} without OR REPLACE (not idempotent)')
                if sd and not sp: fail(f'{f}: SECURITY DEFINER {n} has no SET search_path')
            if sd and not ret_trig: sd_funcs[n] = i
        elif t == 'CreateStmt':
            n = s.relation.relname; first_tbl.setdefault(n, i)
            tables[n] = {c.colname for c in s.tableElts if isinstance(c, ast.ColumnDef)}
            created_in['T:' + n] = i
            if i >= LINT_FROM and not s.if_not_exists: fail(f'{f}: CREATE TABLE {n} without IF NOT EXISTS')
        elif t == 'ViewStmt':
            first_tbl.setdefault(s.view.relname, i); tables.setdefault(s.view.relname, set())
        elif t == 'AlterTableStmt':
            n = s.relation.relname
            for c in s.cmds:
                if c.subtype == AlterTableType.AT_AddColumn:
                    tables.setdefault(n, set()).add(c.def_.colname)
                    if i >= LINT_FROM and not c.missing_ok: fail(f'{f}: ADD COLUMN {n}.{c.def_.colname} without IF NOT EXISTS')
                if c.subtype == AlterTableType.AT_EnableRowSecurity: rls_on.add(n)
        elif t == 'IndexStmt' and i >= LINT_FROM and not s.if_not_exists:
            fail(f'{f}: CREATE INDEX {s.idxname} without IF NOT EXISTS')
        elif t == 'InsertStmt' and i >= LINT_FROM and s.onConflictClause is None:
            fail(f'{f}: INSERT into {s.relation.relname} without ON CONFLICT (runs twice = duplicates or error)')
        elif t == 'DropStmt' and s.removeType == ObjectType.OBJECT_FUNCTION:
            for o in s.objects:
                n = fname(o.objname)
                if n in funcs: del funcs[n]
                sd_funcs.pop(n, None)
        elif t == "GrantStmt":
            who = []
            for g in (s.grantees or []):
                who.append('public' if g.roletype == RoleSpecType.ROLESPEC_PUBLIC else (g.rolename or ''))
            if i >= LINT_FROM and s.is_grant and any(w in ('public', 'anon') for w in who):
                fail(f'{f}: GRANT to {who} (line-level check needed)')
            if not s.is_grant and s.objtype == ObjectType.OBJECT_FUNCTION:
                for o in (s.objects or []):
                    n = fname(o.objname)
                    revoked.setdefault(n, set()).update(who)
    # text based lints
    if i >= LINT_FROM:
        head = '\n'.join(txt.splitlines()[:60]).lower()
        if 'rollback' not in head and 'undo' not in head: fail(f'{f}: no ROLLBACK/Undo note in header')
        for m in re.finditer(r'create\s+trigger\s+(\w+)', txt, re.I):
            if not re.search(r'drop\s+trigger\s+if\s+exists\s+' + m.group(1), txt, re.I): fail(f'{f}: trigger {m.group(1)} created without prior DROP TRIGGER IF EXISTS')
        for m in re.finditer(r'create\s+policy\s+"?(\w+)"?', txt, re.I):
            if not re.search(r'drop\s+policy\s+if\s+exists\s+"?' + m.group(1), txt, re.I): fail(f'{f}: policy {m.group(1)} created without prior DROP POLICY IF EXISTS')
        # use-before-create across files (function and table names defined only in a LATER migration)
        body = re.sub(r'--.*', '', txt)
        for n, fi in list(first_fn.items()): pass

# second pass: references to objects defined only in later files
allfirst = dict(first_fn)
for f in files:
    i = idx(f)
    if i < LINT_FROM: continue
    body = re.sub(r'--.*', '', open(f).read())
    for n, fi in allfirst.items():
        if fi > i and re.search(r'\bpublic\.' + n + r'\s*\(', body): fail(f'{f}: calls {n}() which is first created in migration {fi:03d} (later)')
    for n, fi in first_tbl.items():
        if fi > i and re.search(r'\bpublic\.' + n + r'\b', body): fail(f'{f}: uses table {n} first created in migration {fi:03d} (later)')

# RLS on every table created in 038..045
for k, i in created_in.items():
    if k.startswith('T:') and i >= LINT_FROM and k[2:] not in rls_on: fail(f'table {k[2:]} (migration {i:03d}) has no ENABLE ROW LEVEL SECURITY')
# every callable SECURITY DEFINER function created in 038..045 must revoke from public AND anon
for n, i in sd_funcs.items():
    if i >= LINT_FROM:
        r = revoked.get(n, set())
        if not ('public' in r and 'anon' in r): fail(f'function {n} (migration {i:03d}) is SECURITY DEFINER but not revoked from {"public " if "public" not in r else ""}{"anon" if "anon" not in r else ""}'.strip())

# 045 guard before destructive statements
t45 = open('supabase/migrations/045_drop_legacy_admin.sql').read(); l45 = re.sub(r'--.*', '', t45).lower()
g = l45.find('raise exception')
d = min([x for x in (l45.find('drop function'), l45.find('create or replace function public.has_permission'), l45.find('delete from')) if x >= 0] or [10**9])
if g < 0 or g > d: fail('045: no RAISE EXCEPTION guard before the first destructive statement')
for need in ('admin_members', 'owner', 'min_app_version'):
    if need not in l45[:d]: fail(f'045: guard does not mention {need}')
for need in ('break_glass_keys', 'admin_break_glass'):
    if need not in open('supabase/migrations/044_admin_stage4_safety.sql').read(): fail(f'044 lacks {need} that 045 expects')

# app -> SQL cross-check
def call_text(src, start):
    d = 0
    for j in range(start, len(src)):
        if src[j] == '(': d += 1
        elif src[j] == ')':
            d -= 1
            if d == 0: return src[start:j + 1]
    return src[start:start + 300]
rpc_seen, unchecked = {}, []
for jf in glob.glob('**/*.java', recursive=True):
    if '/test/' in jf or jf.startswith('BOFO_STAGE_1'): continue
    src = open(jf).read()
    for m in re.finditer(r'\.(rpc|rpcObject)\(', src):
        txt = call_text(src, m.end() - 1)
        names = re.findall(r'"([a-z_0-9]+)"', txt.split(',')[0] if 'new JSONObject' in txt else txt)
        first_arg = txt[1:].split(', new JSONObject')[0] if ', new JSONObject' in txt else txt[1:]
        fns = [n for n in re.findall(r'"([a-z_0-9]+)"', first_arg) if n in funcs or n.startswith(('admin_', 'my_'))]
        keys = re.findall(r'\.put\("([a-z_0-9]+)"', txt[len(first_arg):]) if ', new JSONObject' in txt else None
        if not fns: unchecked.append(f'{jf}: rpc with non-literal name'); continue
        for n in fns:
            rpc_seen.setdefault(n, []).append((jf, keys))

def split_top(t):
    out, d, cur, q = [], 0, '', False
    for ch in t:
        if ch == '"': q = not q
        if not q:
            if ch in '([{': d += 1
            elif ch in ')]}': d -= 1
            elif ch == ',' and d == 0: out.append(cur.strip()); cur = ''; continue
        cur += ch
    out.append(cur.strip()); return out
# generic pass: any  "known_fn", Ui.json(...)  or  "known_fn", new JSONObject()...put(..)  in app code (covers helper wrappers like TeamScreen.call)
for jf in glob.glob('**/*.java', recursive=True):
    if '/test/' in jf or jf.startswith('BOFO_STAGE_1'): continue
    src = open(jf).read()
    for m in re.finditer(r'"([a-z_0-9]+)",\s*(Ui\.json\(|new JSONObject\(\))', src):
        n = m.group(1)
        if n not in funcs: continue
        if m.group(2).startswith('Ui.json'):
            inner = call_text(src, m.end() - 1)[1:-1]
            parts = split_top(inner); keys = [re.sub(r'"', '', x) for x in parts[0::2] if x.startswith('"')]
        else:
            d = 0; j = m.end(); tail = src[j:j+800]; end = len(tail)
            for k, ch in enumerate(tail):
                if ch == '(': d += 1
                elif ch == ')':
                    d -= 1
                    if d < 0: end = k; break
                elif ch == ';' and d == 0: end = k; break
            keys = re.findall(r'\.put\("([a-z_0-9]+)"', tail[:end])
        rpc_seen.setdefault(n, []).append((jf, keys))
for n, uses in sorted(rpc_seen.items()):
    if n not in funcs: fail(f'APP calls rpc {n}() but no migration defines it ({uses[0][0]})'); continue
    for jf, keys in uses:
        if keys is None: continue
        ok = False
        for sig in funcs[n].values():
            pn = {p for p, _ in sig['params']}; req = {p for p, d in sig['params'] if not d}
            if set(keys) <= pn and req <= set(keys): ok = True
        if not ok: fail(f'{jf}: rpc {n} args {sorted(keys)} do not match any signature {[ [p for p,_ in s["params"]] for s in funcs[n].values()]}')
for dead in ('admin_takedown', 'send_test_push', 'push_test_result'):
    r = os.popen(f"grep -rlE '\\b{dead}\\b' --include=*.java app-admin bofo-android shared 2>/dev/null").read().split()
    if r: fail(f'APP still references {dead}() which 045 drops: {r}')

# tables / columns used by select()
tab_seen = {}
for jf in glob.glob('**/*.java', recursive=True):
    if '/test/' in jf or jf.startswith('BOFO_STAGE_1'): continue
    for m in re.finditer(r'select\("([a-z_0-9]+)",\s*"([^"]*)"', open(jf).read()):
        tab_seen.setdefault(m.group(1), set())
        sel = re.search(r'select=([a-z_0-9,*]+)', m.group(2))
        if sel: tab_seen[m.group(1)] |= set(sel.group(1).split(','))
for tname, cols in tab_seen.items():
    if tname not in first_tbl: fail(f'APP selects from {tname} but no migration creates it (may be created outside migrations)'); continue
    miss = sorted(c for c in cols if c != '*' and tables.get(tname) and c not in tables[tname])
    if miss: notes.append(f'{tname}: columns not seen in CREATE/ADD COLUMN: {miss} (may come from 001 via ALTER or a view)')

for u in unchecked: notes.append(u)
print('\n'.join('FAIL ' + x for x in fails))
print('\n'.join('NOTE ' + x for x in notes))
print(f'SUMMARY: {len(files)} migrations parsed, {len(funcs)} functions, {len(rpc_seen)} app RPCs checked, {len(tab_seen)} app tables checked, {len(fails)} failures, {len(notes)} notes')
sys.exit(1 if fails else 0)
