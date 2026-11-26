#!/bin/bash
# scan_lane.sh LANE(1-3) N...: run the repaired v1 suite (suites_scan/vN copy) on releases N...; results out_scan/N, log scan_N.log
cd /Users/stocco/repos/tools/phpfusion-work
L=$1; shift
case $L in 1) C=pf-chrome; PORT=4448;; 2) C=pf-chrome2; PORT=4449;; 3) C=pf-chrome3; PORT=4450;; esac
for i in $(seq 1 60); do curl -s -m 3 localhost:$PORT/status | grep -q '"ready": true' && break; sleep 2; done
./prep_chrome.sh $C
for N in "$@"; do
  python3 deploy_pf.py $N >/dev/null
  mkdir -p suites_scan; rm -rf suites_scan/v$N; cp -R suites_repaired/${SCAN_BASE:-v1} suites_scan/v$N
  rm -rf out_scan/$N
  SEL_PORT=$PORT SUITES=/Users/stocco/repos/tools/phpfusion-work/suites_scan /Users/stocco/repos/tools/addrbook-work/.venv/bin/python3.15 -u replay_pf.py $N /Users/stocco/repos/tools/phpfusion-work/out_scan > scan_$N.log 2>&1
  docker restart $C >/dev/null; for i in $(seq 1 60); do curl -s -m 3 localhost:$PORT/status | grep -q '"ready": true' && break; sleep 2; done; ./prep_chrome.sh $C
  echo "v$N: $(grep -c ' pass ' scan_$N.log) pass $(grep -c ' fail ' scan_$N.log) fail" >> scan_summary.txt
done
