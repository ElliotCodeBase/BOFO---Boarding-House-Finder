#!/usr/bin/env python3
"""BOFO stage-1 static checks. Run from repo root: python tools/verify/check_all.py
Prints only problems + one summary line. READ-level checks (no Gradle/SDK)."""
import os, re, sys, glob, xml.etree.ElementTree as ET
ROOT = os.getcwd()
MODS = {'app': 'bofo-android/app', 'admin': 'app-admin', 'shared': 'shared'}
probs = []
def P(kind, msg): probs.append((kind, msg))
def rd(p):
    try: t = open(p, encoding='utf-8', errors='replace').read()
    except Exception: return ''
    if p.endswith('.java'): t = re.sub(r'/\*.*?\*/', lambda m: re.sub(r'[^\n]', ' ', m.group(0)), t, flags=re.S); t = re.sub(r'//[^\n]*', '', t)
    elif p.endswith('.xml'): t = re.sub(r'<!--.*?-->', lambda m: re.sub(r'[^\n]', ' ', m.group(0)), t, flags=re.S)
    return t
def files(mod, pat):
    return [f for f in glob.glob(os.path.join(MODS[mod], '**', pat), recursive=True) if '/build/' not in f]

# 1. imports vs declared dependencies --------------------------------------
DEPS = {  # package prefix -> which modules may use it
 'okhttp3': {'shared', 'admin', 'app'}, 'okio': {'shared', 'admin', 'app'},   # shared exposes okhttp as api
 'androidx.appcompat': {'admin', 'app'}, 'com.google.android.material': {'admin', 'app'},
 'androidx.credentials': {'admin', 'app'}, 'com.google.android.libraries.identity.googleid': {'admin', 'app'},
 'androidx.biometric': {'admin'}, 'androidx.swiperefreshlayout': {'admin', 'app'},
 'com.google.firebase': {'admin', 'app'},
 'androidx.constraintlayout': {'app'}, 'androidx.fragment': {'app', 'admin'},  # fragment comes with appcompat
 'androidx.navigation': {'app'}, 'androidx.lifecycle': {'app', 'admin'},       # lifecycle comes with appcompat
 'androidx.exifinterface': {'app'}, 'com.bumptech.glide': {'app'},
 'androidx.recyclerview': {'app', 'admin'},  # transitive via material
 'androidx.viewpager2': {'app'}, 'org.maplibre': {'app'}, 'com.google.android.gms.location': {'app'},
 'androidx.browser': {'app'}, 'com.google.android.gms.tasks': {'app'}, 'com.google.android.gms.common': {'app', 'admin'}, 'com.bofo.shared': {'admin'},
 'org.junit': {'shared', 'admin', 'app'}, 'org.json': {'shared', 'admin', 'app'},
}
ANDROID_OK = ('android.', 'java.', 'javax.', 'com.bofo.', 'org.json', 'org.junit', 'junit.', 'androidx.annotation', 'androidx.core', 'androidx.activity')
for m in MODS:
    for f in files(m, '*.java'):
        for imp in re.findall(r'^\s*import\s+(?:static\s+)?([\w.]+)', rd(f), re.M):
            if imp.startswith(ANDROID_OK) and not imp.startswith('com.bofo.shared') and not imp.startswith('com.bofo.finder') and not imp.startswith('com.bofo.admin'): continue
            hit = [p for p in DEPS if imp.startswith(p)]
            if imp.startswith('com.bofo.finder') and m != 'app': P('IMPORT', f'{f}: {imp} (finder class used outside app)')
            elif imp.startswith('com.bofo.admin') and m != 'admin': P('IMPORT', f'{f}: {imp}')
            elif imp.startswith('com.bofo.shared') and m == 'shared': pass
            elif imp.startswith('com.bofo.shared') and m == 'app': P('IMPORT', f'{f}: app does not depend on :shared')
            elif imp.startswith('com.bofo.'): pass
            elif not hit: P('IMPORT', f'{f}: unknown/undeclared dependency for {imp}')
            elif m not in DEPS[hit[0]]: P('IMPORT', f'{f}: {imp} not declared for module {m}')

# 2. com.bofo imports resolve to a real class --------------------------------
classes = set()
for m in MODS:
    for f in files(m, '*.java'):
        s = rd(f); pk = re.search(r'^\s*package\s+([\w.]+)', s, re.M)
        if pk: classes.add(pk.group(1) + '.' + os.path.basename(f)[:-5])
inner = lambda i: i.rsplit('.', 1)[0]
for m in MODS:
    for f in files(m, '*.java'):
        for imp in re.findall(r'^\s*import\s+(?:static\s+)?(com\.bofo\.[\w.]+)', rd(f), re.M):
            parts = imp.split('.')
            ok = any('.'.join(parts[:i]) in classes for i in range(len(parts), 2, -1)) or imp.endswith('.R') or imp.endswith('BuildConfig') or '.databinding.' in imp or imp.endswith('.*')
            if not ok: P('SYMBOL', f'{f}: import {imp} -> no such class')

# 3. leftovers of deleted admin files ----------------------------------------
dl = [l.strip().replace('\r', '') for l in rd('DELETED_FILES.txt').splitlines() if l.strip()]
dnames = set()
for p in dl:
    b = os.path.basename(p); n, e = os.path.splitext(b)
    if e == '.java': dnames.add(('class', n))
    if '/layout/' in p and e == '.xml': dnames.add(('layout', n))
src = [f for m in MODS for f in files(m, '*.java')] + [f for m in MODS for f in files(m, '*.xml')] + glob.glob('bofo-android/app/proguard-rules.pro') + glob.glob('app-admin/proguard-rules.pro')
srctxt = {f: rd(f) for f in src}
live_classes = {os.path.basename(f)[:-5] for f in src if f.endswith('.java')}
for kind, n in sorted(dnames):
    if kind == 'class' and n in live_classes: continue   # name re-used by a live class (e.g. shared copy)
    pat = re.compile(r'\b' + re.escape(n) + r'\b') if kind == 'class' else re.compile(r'(R\.layout\.|@layout/)' + re.escape(n) + r'\b')
    for f, t in srctxt.items():
        if pat.search(t): P('DELETED-REF', f'{f}: still mentions {kind} {n}')
        # binding class name
    if kind == 'layout':
        bn = ''.join(w.capitalize() for w in n.split('_')) + 'Binding'
        for f, t in srctxt.items():
            if re.search(r'\b' + bn + r'\b', t): P('DELETED-REF', f'{f}: still uses {bn}')

# 4. XML well-formed ----------------------------------------------------------
for m in MODS:
    for f in files(m, '*.xml'):
        try: ET.parse(f)
        except Exception as e: P('XML', f'{f}: {e}')

# 5. resources: R.x.y in Java / @x/y in XML must exist (all modules) ----------
defined = {}
def add(t, n): defined.setdefault(t, set()).add(n)
for m in MODS:
    res = os.path.join(MODS[m], 'src/main/res')
    if not os.path.isdir(res): continue
    for d in os.listdir(res):
        base = d.split('-')[0]
        for f in glob.glob(os.path.join(res, d, '*')):
            n = os.path.splitext(os.path.basename(f))[0]
            if base in ('layout', 'drawable', 'mipmap', 'menu', 'xml', 'anim', 'color', 'font', 'raw', 'navigation', 'animator', 'interpolator'): add(base, n.split('.')[0])
            if base == 'values':
                try:
                    t = ET.parse(f).getroot()
                except Exception: continue
                for e in t:
                    nm = e.get('name')
                    if not nm: continue
                    tag = e.tag
                    if tag == 'item' and e.get('type'): tag = e.get('type')
                    if tag == 'id': add('id', nm)
                    if tag in ('string', 'color', 'dimen', 'style', 'integer', 'bool', 'array', 'string-array', 'plurals', 'attr', 'declare-styleable', 'integer-array'):
                        add({'string-array': 'array', 'integer-array': 'array', 'declare-styleable': 'styleable'}.get(tag, tag), nm.replace('.', '_'))
                        if tag == 'style': add('style', nm.replace('.', '_'))
                    if tag == 'attr': add('attr', nm)
            # ids
            if base in ('layout', 'menu', 'navigation') and f.endswith('.xml'):
                for i in re.findall(r'@\+id/([\w.]+)', rd(f)): add('id', i)
                for i in re.findall(r'android:id="@\+?id/([\w.]+)"', rd(f)): add('id', i)
for t in ('drawable', 'mipmap', 'color', 'string', 'style', 'dimen', 'layout', 'menu', 'xml', 'anim', 'navigation', 'array', 'plurals', 'integer', 'bool', 'font', 'raw', 'id', 'attr'):
    defined.setdefault(t, set())
# library resources we do not own (material/appcompat/androidx): accept
LIB = re.compile(r'^(Theme|Widget|TextAppearance|ThemeOverlay|ShapeAppearance|Base|Platform|MaterialAlertDialog|Animation|Preference)[._]|^(appbar_|abc_|mtrl_|m3_|material_|design_|appcompat_|notification_|navigation_|common_|fcm_|default_|gms_)')
for m in MODS:
    for f in files(m, '*.java'):
        s = rd(f)
        for t, n in re.findall(r'(?<![\w.])R\.(\w+)\.(\w+)', s):
            if t == 'styleable' or t == 'attr' and n in defined['attr']: continue
            if n.replace('.', '_') not in defined.get(t, set()) and not LIB.search(n) and not n.startswith('mtrl') and not f.startswith('shared'):
                P('RES', f'{f}: R.{t}.{n} not found in module resources (library resource? verify)')
    for f in files(m, '*.xml'):
        s = rd(f)
        for t, n in re.findall(r'@(?!android:)(?:\+)?(drawable|mipmap|color|string|style|dimen|layout|menu|xml|anim|array|plurals|integer|bool|font|raw|navigation|id)/([\w.]+)', s):
            if t == 'id': continue
            if n.replace('.', '_') not in defined.get(t, set()) and not LIB.search(n):
                P('RES', f'{f}: @{t}/{n} not defined')
        for n in re.findall(r'\?attr/(\w+)', s): pass

# 6. ViewBinding: Binding classes used must have layout + ids exist ----------
for f in files('app', '*.java'):
    s = rd(f)
    for b in set(re.findall(r'\b([A-Z]\w*Binding)\b', s)):
        if b in ('ViewBinding',): continue
        lay = re.sub(r'(?<!^)(?=[A-Z])', '_', b[:-7]).lower()
        if not os.path.exists(f'bofo-android/app/src/main/res/layout/{lay}.xml'):
            P('BINDING', f'{f}: {b} -> layout {lay}.xml missing')
            continue
        lt = rd(f'bofo-android/app/src/main/res/layout/{lay}.xml')
        ids = set(re.findall(r'@\+id/(\w+)', lt))
        camel = lambda i: re.sub(r'_(\w)', lambda m: m.group(1).upper(), i)
        have = {camel(i) for i in ids}
        for fld in set(re.findall(r'\b(?:binding|b|vb)\.(\w+)\b', s)):
            pass
for f in files('admin', '*.java'):
    if re.search(r'\b\w+Binding\b', rd(f)) and 'ViewBinding' not in rd(f).split('class')[0][:0]:
        for b in set(re.findall(r'\b(\w+Binding)\b', rd(f))): P('BINDING', f'{f}: {b} used but admin has viewBinding off')

# 7. Manifests: declared components exist; notification channels -------------
for m, mf in (('app', 'bofo-android/app/src/main/AndroidManifest.xml'), ('admin', 'app-admin/src/main/AndroidManifest.xml'), ('shared', 'shared/src/main/AndroidManifest.xml')):
    t = rd(mf)
    if not t: P('MANIFEST', f'{mf} missing'); continue
    pk = {'app': 'com.bofo.finder', 'admin': 'com.bofo.admin', 'shared': 'com.bofo.shared'}[m]
    for n in re.findall(r'<(?:activity|service|receiver|provider)[^>]*android:name="([^"]+)"', t):
        full = n if n.startswith('com.') else (pk + n if n.startswith('.') else pk + '.' + n)
        if full not in classes and not n.startswith(('androidx.', 'com.google.')): P('MANIFEST', f'{mf}: component {n} -> class missing')
    for a in ('allowBackup="false"', 'usesCleartextTraffic="false"') if m != 'shared' else ():
        if a not in t: P('MANIFEST', f'{mf}: missing {a}')
    for r in re.findall(r'(?:android:icon|android:roundIcon|android:theme|android:dataExtractionRules|android:fullBackupContent|android:label)="@(\w+)/([\w.]+)"', t):
        if r[0] in defined and r[1].replace('.', '_') not in defined[r[0]] and not LIB.search(r[1]): P('MANIFEST', f'{mf}: @{r[0]}/{r[1]} not defined')

# 8. Gradle: module wiring ----------------------------------------------------
sg = rd('bofo-android/settings.gradle')
for mod in ('shared', 'app-admin'):
    if f"include ':{mod}'" not in sg: P('GRADLE', f'settings.gradle misses :{mod}')
for pth in ('shared/build.gradle', 'app-admin/build.gradle', 'bofo-android/app/build.gradle', 'app-admin/proguard-rules.pro', 'bofo-android/app/proguard-rules.pro', 'bofo-android/gradlew', 'bofo-android/gradle/wrapper/gradle-wrapper.properties'):
    if not os.path.exists(pth): P('GRADLE', f'{pth} missing')
va = re.findall(r"versionCode (\d+)\s*\n\s*versionName '([\d.]+)'", rd('app-admin/build.gradle') + rd('bofo-android/app/build.gradle'))
if len(set(va)) != 1: P('GRADLE', f'versions differ between apps: {va}')
if not os.path.exists('app-admin/src/main/res'): P('GRADLE', 'app-admin has no res/ (themes, launcher icon, strings) -> check manifest theme/icon')

print(f'{len(probs)} problem(s)')
for k, m in sorted(probs): print(f'[{k}] {m}')
sys.exit(1 if probs else 0)
