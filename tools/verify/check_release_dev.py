#!/usr/bin/env python3
"""Stage 4 (S-04): a release build must not carry test logins. Run from repo root. Prints only failures plus a summary."""
import re, sys, glob
f = []
g = open('bofo-android/app/build.gradle').read()
m = re.search(r'\n\s*release\s*\{(.*?)\n\s{8}\}', g, re.S)
dm = re.search(r'\n\s*debug\s*\{(.*?)\n\s{8}\}', g, re.S)
rel = m.group(1) if m else ''
if not m: f.append('build.gradle: release block not found')
if 'DEV_' in rel or "'dev." in rel: f.append('build.gradle: release block sets DEV_* / dev.* values')
dc = re.search(r'defaultConfig\s*\{(.*?)\n    \}', g, re.S).group(1)
if not re.search(r'DEV_\$\{r\}_EMAIL.*\'""\'', dc) or not re.search(r'DEV_\$\{r\}_PASSWORD.*\'""\'', dc): f.append('build.gradle: defaultConfig DEV_* fields are not empty strings')
if not dm or 'dev.' not in dm.group(1): f.append('build.gradle: debug block no longer fills DEV_* (login buttons would vanish)')
c = open('bofo-android/app/src/main/java/com/bofo/finder/config/AppConfig.java').read()
if c.count('BuildConfig.DEBUG') < 2: f.append('AppConfig: devEmail/devPassword are not gated by BuildConfig.DEBUG')
a = open('bofo-android/app/src/main/java/com/bofo/finder/service/AuthService.java').read()
if not re.search(r'devSignIn[^{]*\{\s*if \(!com\.bofo\.finder\.BuildConfig\.DEBUG\)', a): f.append('AuthService.devSignIn is not gated by BuildConfig.DEBUG')
for p in glob.glob('app-admin/**/*.java', recursive=True) + glob.glob('app-admin/build.gradle'):
    if re.search(r'DEV_(ADMIN|OWNER|STUDENT)|grant_type=password', open(p).read()): f.append(f'{p}: admin app has a password / dev login')
print('\n'.join('FAIL ' + x for x in f)); print(f'SUMMARY: release dev-login check, {len(f)} failures'); sys.exit(1 if f else 0)
