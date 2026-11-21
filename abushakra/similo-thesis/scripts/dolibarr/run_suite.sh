#!/bin/bash
# usage: run_suite.sh <N> <release>   fresh install of <release> as dolibarr_<N>, then the ordered suite in run/wd_v<N> (log native_wd_v<N>.log)
cd /Users/stocco/repos/tools/dolibarr-work; N=$1; REL=$2; export JAVA_HOME=/Users/stocco/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home; PY=/Users/stocco/repos/tools/addrbook-work/.venv/bin/python3.15
(cd ../addrbook-work && $PY ../dolibarr-work/install_v$N.py $REL | tail -1)
[ $N -ge 11 ] && $PY fix_module_rows.py $N | grep -v 'left as is'   # identity-faithful module-row locators for this release
if [ -n "$CAPTURE" ]; then export CAPTURE_OUT=/Users/stocco/repos/tools/dolibarr-work/capture-out/v$N CAPTURE_HUB=http://localhost:4455 CAPTURE_JS=/Users/stocco/repos/tools/comparison-robust-locators-algorithms/abushakra/similo-thesis/src/main/resources/javascript; rm -rf $CAPTURE_OUT; fi
(cd run/wd_v$N && FAIL_DUMP=/Users/stocco/repos/tools/dolibarr-work/fail_dump_v$N perl -e 'alarm shift; exec @ARGV' 2400 mvn test -Dtest=OrderedSuite > ../../native_wd_v$N.log 2>&1)
grep -E '^Tests run:.*Fail' native_wd_v$N.log | tail -1
grep -E "^\s+\w+\.test" native_wd_v$N.log | sed -E 's/^\s+//' | cut -c1-120
