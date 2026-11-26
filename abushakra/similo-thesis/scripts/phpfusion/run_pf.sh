#!/bin/bash
# run_pf.sh N [ONLY=a,b] [NORESET=1] : restart chrome grid slot, replay repaired suite vN into out_r/N
cd /Users/stocco/repos/tools/phpfusion-work
pkill -f 'replay_pf.py .*out_r'; curl -s -m 3 localhost:4448/status | grep -q '"ready": true' || docker restart pf-chrome >/dev/null; for i in $(seq 1 40); do curl -s -m 3 localhost:4448/status | grep -q '"ready": true' && break; sleep 2; done; ./prep_chrome.sh
N=$1; shift
env SUITES=/Users/stocco/repos/tools/phpfusion-work/suites_repaired "$@" /Users/stocco/repos/tools/addrbook-work/.venv/bin/python3.15 -u replay_pf.py $N /Users/stocco/repos/tools/phpfusion-work/out_r
