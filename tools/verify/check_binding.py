import os, re, glob
camel = lambda i: re.sub(r'_(\w)', lambda m: m.group(1).upper(), i)
L = 'bofo-android/app/src/main/res/layout'
bad = 0; checked = 0
def ids_of(name, seen=()):
    f = f'{L}/{name}.xml'
    if not os.path.exists(f) or name in seen: return set()
    t = open(f, encoding='utf-8', errors='replace').read()
    ids = {camel(i) for i in re.findall(r'android:id="@\+id/(\w+)"', t)}
    for inc in re.findall(r'<include[^>]*layout="@layout/(\w+)"', t):
        ids |= ids_of(inc, seen + (name,))   # included layouts: only ids WITHOUT an own id on <include> are merged
    return ids
for f in glob.glob('bofo-android/app/src/main/java/**/*.java', recursive=True):
    t = re.sub(r'//[^\n]*', '', open(f, encoding='utf-8', errors='replace').read())
    bs = {b for b in re.findall(r'\b([A-Z]\w*Binding)\b', t) if b not in ('ViewBinding',)}
    if len(bs) != 1: continue
    b = bs.pop(); lay = re.sub(r'(?<!^)(?=[A-Z])', '_', b[:-7]).lower()
    if not os.path.exists(f'{L}/{lay}.xml'): continue
    have = ids_of(lay); checked += 1
    used = set(re.findall(r'\b(?:binding|b|vb)\.(\w+)\b', t))
    skip = {'getRoot', 'inflate', 'bind'}
    for u in used - have - skip:
        if re.search(r'\b(?:binding|b|vb)\.' + u + r'\s*\(', t) and not re.search(r'\b(?:binding|b|vb)\.' + u + r'\s*\.', t): continue
        print(f'[BINDING-FIELD] {f}: {b}.{u} not an id in {lay}.xml (or in its includes)'); bad += 1
print(f'binding field check: {checked} files, {bad} problem(s)')
