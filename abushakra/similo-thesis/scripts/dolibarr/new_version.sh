#!/bin/bash
# usage: new_version.sh <N> <release>   creates run/wd_v<N> as a copy of run/wd_v<N-1> (the previous suite, to be repaired) and install_v<N>.py, then runs the suite once
cd /Users/stocco/repos/tools/dolibarr-work; N=$1; REL=$2; P=${3:-$((N-1))}   # optional 3rd argument: the suite to start from (default: the previous version)
rm -rf run/wd_v$N; cp -R run/wd_v$P run/wd_v$N; rm -rf run/wd_v$N/target
sed -i '' -E "s#version = \"[0-9]+\"#version = \"$N\"#" run/wd_v$N/src/test/java/utils/Version.java
python3 - <<P
s=open('install_v1.py').read().replace('dolibarr_1','dolibarr_$N').replace('dol_1','dol_$N'); open('install_v$N.py','w').write(s)
P
rm -rf fail_dump_v$N; ./run_suite.sh $N $REL
