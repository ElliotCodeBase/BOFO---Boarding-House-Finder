import re, sys
raw = open(sys.argv[1], encoding='utf-8', errors='replace').read()
blocks = re.split(r'\n(?=\S+\.java:\d+: error:)', raw)
import os, glob
OURS = {os.path.basename(x)[:-5] for x in glob.glob('shared/src/main/**/*.java', recursive=True) + glob.glob('app-admin/src/main/**/*.java', recursive=True) + glob.glob('bofo-android/app/src/main/**/*.java', recursive=True)}
PURE = set()
for x in glob.glob('shared/src/main/**/*.java', recursive=True) + glob.glob('app-admin/src/main/**/*.java', recursive=True) + glob.glob('bofo-android/app/src/main/**/*.java', recursive=True):
    t = open(x, encoding='utf-8', errors='replace').read(); n = os.path.basename(x)[:-5]
    m = re.search(r'\b(?:final\s+|abstract\s+)*(?:class|interface|enum)\s+' + n + r'\b([^{]*)\{', t)
    ext = re.findall(r'\b(?:extends|implements)\s+([\w.,<>\s]+)', m.group(1)) if m else []
    names = [w for e in ext for w in re.findall(r'[A-Z]\w*', e)]
    if all(w in OURS or w in ('Runnable','Comparable','Serializable') for w in names): PURE.add(n)
syntax, cross, count = [], [], 0
EXPECT = re.compile(r'package (android|androidx|com\.google|org\.maplibre|com\.bumptech|okhttp3|okio|org\.junit|junit|org\.json|org\.hamcrest)[\w.]* does not exist')
for b in blocks:
    m = re.match(r'(\S+\.java):(\d+): error: (.*)', b)
    if not m: continue
    count += 1
    f, ln, msg = m.groups()
    if EXPECT.search(msg): continue
    if msg.startswith('cannot find symbol'):
        sym = re.search(r'symbol:\s+(.*)', b); loc = re.search(r'location:\s+(.*)', b)
        s_ = sym.group(1).strip() if sym else ''; l = loc.group(1).strip() if loc else ''
        # real cross-class problem: a METHOD/CONSTRUCTOR missing on one of OUR classes (not an SDK class)
        lm = re.search(r'(?:class|interface) ([\w.]+)$', l) or re.search(r'variable \w+ of type ([\w.]+)$', l)
        if lm and lm.group(1).split('.')[-1] in PURE and re.match(r'(method|constructor) ', s_):
            cross.append(f'{f}:{ln}: {s_}  @ {l}')
        continue
    if re.search(r'(cannot be applied to|no suitable (method|constructor)|already defined|missing return)', msg):
        cross.append(f'{f}:{ln}: {msg}')
        continue
    if 'package' in msg and 'does not exist' in msg: continue
    if 'static import only from classes' in msg or 'import' in msg: continue
    if re.search(r'(array required|lambda expression not expected|invalid method reference|incompatible types|does not override|cannot be dereferenced|bad operand|unexpected type|not a statement$|cannot infer|illegal static)', msg): continue
    syntax.append(f'{f}:{ln}: {msg}')
print(f'javac total errors (mostly missing SDK): {count}')
print(f'SYNTAX-LIKE: {len(syntax)}');  [print('  ' + x) for x in syntax[:40]]
print(f'CROSS-CLASS (own classes): {len(cross)}'); [print('  ' + x) for x in cross[:60]]
