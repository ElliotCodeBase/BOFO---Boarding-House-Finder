"""Static calls to our own non-Android classes (Class.method(...)): the method must exist with a matching argument count.
Closes the gap of javac_pass.sh (javac stays silent when an argument has an SDK type). Classes are resolved by package/import
(shared, admin and app have classes with the same simple name). Run from repo root."""
import os, re, glob
SRC = [f for f in glob.glob('shared/src/main/**/*.java', recursive=True) + glob.glob('app-admin/src/main/**/*.java', recursive=True) + glob.glob('bofo-android/app/src/main/**/*.java', recursive=True)]
def strip(t):
    t = re.sub(r'/\*.*?\*/', lambda m: re.sub(r'[^\n]', ' ', m.group(0)), t, flags=re.S)
    t = re.sub(r"'(?:\\.|[^'\\\n])'", "''", t)          # char literals first ('"' broke string stripping)
    t = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', t)
    return re.sub(r'//[^\n]*', '', t)
def nargs(s):
    d = 0; n = 0; cur = ''
    for ch in s:
        if ch in '([{': d += 1
        elif ch in ')]}': d -= 1
        if ch == ',' and d == 0: n += 1; cur = ''
        else: cur += ch
    return (n + 1) if (cur.strip() or n) else 0
def nparams(s):
    s = s.strip()
    if not s: return 0
    d = 0; n = 1
    for ch in s:
        if ch == '<': d += 1
        elif ch == '>': d -= 1
        elif ch == ',' and d == 0: n += 1
    return n
cls = {}
for f in SRC:
    t = strip(open(f, encoding='utf-8', errors='replace').read()); n = os.path.basename(f)[:-5]
    pk = re.search(r'^\s*package\s+([\w.]+)', t, re.M); pk = pk.group(1) if pk else ''
    m = re.search(r'\b(?:class|interface|enum)\s+' + n + r'\b([^{]*)\{', t)
    head = m.group(1) if m else ''
    ext = re.findall(r'[A-Z]\w*', ' '.join(re.findall(r'\b(?:extends|implements)\s+([\w.,<>\s]+)', head)))
    meths = {}
    rx = r'^[ \t]*(?:(?:public|private|protected|static|final|synchronized|abstract|default)\s+)*(?:<[^>\n]+>\s+)?[\w.<>\[\]?,]+(?: [\w.<>\[\]?,]+)*\s+(\w+)\s*\('
    for mm in re.finditer(rx, t, re.M):
        name = mm.group(1)
        if name in ('if', 'for', 'while', 'switch', 'catch', 'return', 'new', 'else', 'throw'): continue
        i = mm.end(); d = 1; j = i
        while j < len(t) and d and j - i < 800:
            if t[j] == '(': d += 1
            elif t[j] == ')': d -= 1
            j += 1
        if d: continue
        if not re.match(r'\s*(\{|;|throws\b)', t[j:j + 60]): continue
        p = re.sub(r'@\w+(\([^)]*\))?', '', t[i:j - 1])
        meths.setdefault(name, []).append((nparams(p), '...' in p))
    imps = {i.split('.')[-1]: i for i in re.findall(r'^\s*import\s+([\w.]+);', t, re.M)}
    cls[pk + '.' + n] = dict(methods=meths, inner=set(re.findall(r'\b(?:class|interface|enum)\s+(\w+)', t)) - {n}, path=f, ext=ext, text=t, pkg=pk, imps=imps, name=n, isenum=bool(re.search(r'\benum\s+' + n + r'\b', t)))
SIMPLE = {}
for k, v in cls.items(): SIMPLE.setdefault(v['name'], []).append(k)
def pure(k): return all(any(e == cls[x]['name'] for x in cls) for e in cls[k]['ext'])
bad = 0; calls = 0; used = 0
for k, v in cls.items():
    t = v['text']
    for m in re.finditer(r'(?<![\w.])([A-Z]\w*)\.([a-z]\w*)\s*\(', t):
        c, name = m.group(1), m.group(2)
        if c in v['inner'] or c == v['name'] and False: continue
        tgt = v['imps'].get(c) if v['imps'].get(c) in cls else (v['pkg'] + '.' + c if v['pkg'] + '.' + c in cls else None)
        if not tgt or not pure(tgt): continue
        if cls[tgt]['isenum'] and name in ('valueOf', 'values'): continue
        i = m.end(); d = 1; j = i
        while j < len(t) and d:
            if t[j] in '([{': d += 1
            elif t[j] in ')]}': d -= 1
            j += 1
        na = nargs(t[i:j - 1]); calls += 1
        opts = cls[tgt]['methods'].get(name); line = t.count('\n', 0, m.start()) + 1
        if not opts: print(f'[CALL] {v["path"]}:{line}: {c}.{name}(...) -> no such method in {cls[tgt]["path"]}'); bad += 1
        elif not any(n == na or (va and na >= n - 1) for n, va in opts): print(f'[CALL] {v["path"]}:{line}: {c}.{name}() called with {na} arg(s); declared: {sorted(set(n for n, _ in opts))}'); bad += 1
print(f'call check: {calls} static calls on own classes, {bad} problem(s)')
