#!/bin/sh
# Syntax + cross-class check without Android SDK. Errors about android/androidx/BuildConfig/R/Binding classes are expected and filtered.
# Usage (repo root): sh tools/verify/javac_pass.sh
OUT=${TMPDIR:-/tmp}/bofo_javac; rm -rf $OUT; mkdir -p $OUT
find shared/src/main app-admin/src/main bofo-android/app/src/main -name '*.java' > $OUT/all.txt
java -m jdk.compiler/com.sun.tools.javac.Main -proc:none -Xmaxerrs 100000 -Xmaxwarns 0 -d $OUT/classes @$OUT/all.txt > $OUT/raw.txt 2>&1
python3 tools/verify/javac_filter.py $OUT/raw.txt
