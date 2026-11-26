#!/bin/bash
# capture_lane.sh LANE(1-3) N... : clean replay of the final suite of release N with element snapshots + clean screenshots (CAPTURE_JS) -> out_cap/N, cap_N.log, cap_summary.txt
cd /Users/stocco/repos/tools/phpfusion-work
export CAPTURE_JS=/Users/stocco/repos/tools/comparison-robust-locators-algorithms/abushakra/similo-thesis/src/main/resources/javascript
L=$1; shift
case $L in 1) C=pf-chrome; PORT=4448;; 2) C=pf-chrome2; PORT=4449;; 3) C=pf-chrome3; PORT=4450;; esac
base() { n=$1; if [ $n -le 6 ]; then echo v1; elif [ $n -le 17 ]; then echo v7; elif [ $n -le 25 ]; then echo v18; elif [ $n -le 31 ]; then echo v26; elif [ $n -eq 32 ]; then echo v32; elif [ $n -eq 33 ]; then echo v33; elif [ $n -le 40 ]; then echo v34; else echo v41; fi; }
for i in $(seq 1 60); do curl -s -m 3 localhost:$PORT/status | grep -q '"ready": true' && break; sleep 2; done
./prep_chrome.sh $C
for N in "$@"; do
  B=$(base $N)
  python3 deploy_pf.py $N >/dev/null
  mkdir -p suites_final; rm -rf suites_final/v$N; cp -R suites_repaired/$B suites_final/v$N
  rm -rf out_cap/$N
  SEL_PORT=$PORT SUITES=/Users/stocco/repos/tools/phpfusion-work/suites_final /Users/stocco/repos/tools/addrbook-work/.venv/bin/python3.15 -u replay_pf.py $N /Users/stocco/repos/tools/phpfusion-work/out_cap > cap_$N.log 2>&1
  docker restart $C >/dev/null; for i in $(seq 1 60); do curl -s -m 3 localhost:$PORT/status | grep -q '"ready": true' && break; sleep 2; done; ./prep_chrome.sh $C
  echo "v$N: base $B, $(grep -c ' pass ' cap_$N.log) pass $(grep -c ' fail ' cap_$N.log) fail" >> cap_summary.txt
done
