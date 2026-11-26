#!/bin/bash
# t.sh <test> <snapshot-name> [ctx]   (env V=version, default 1): restore snapshot, run only <test>, diagnose
cd /Users/stocco/repos/tools/phpfusion-work
V=${V:-1}
rm -rf out_r/$V/$1
./run_pf.sh $V ONLY=$1 RESTORE=/Users/stocco/repos/tools/phpfusion-work/snap/$V/$2 SNAP=1 2>&1 | cut -c1-200
python3 diag.py out_r/$V $1 ${3:-4}
