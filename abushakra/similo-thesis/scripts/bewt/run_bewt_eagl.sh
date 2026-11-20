#!/bin/bash
# EAGL (plain model, and scaled released "adobe" model) on the BEWT sites, then merge into the caches Eagl reads.
S=/Users/stocco/repos/tools/abushakra-comparison-robust-locators-algorithms/similo-thesis; cd $S
export TAG SITE_RE; export EAGL=/Users/stocco/repos/tools/eagl EAGL_SITES="$(grep -E "${SITE_RE:-.}" out/bewt_sites.txt | tr "\n" " ")"
scripts/eagl/run_one.sh "bewt_plain|$EAGL/database|--model /models/cross_training.joblib --cache out/eagl_bewt_plain_${TAG:-a}_cache.json"
scripts/eagl/run_one.sh "bewt_adobe|$EAGL/database|--scaled --model /models/cross_training.joblib --cache out/eagl_bewt_adobe_${TAG:-a}_cache.json"
python3 - <<'PY'
import json,shutil,os
T=os.environ.get("TAG","a")
for src,dst in [("out/eagl/eagl_bewt_plain_%s_cache.json"%T,"cache/eagl_cache.json"),("out/eagl/eagl_bewt_adobe_%s_cache.json"%T,"cache/eagl_scaled_adobe_cache.json")]:
    try: new=json.load(open(src))
    except Exception as e: print("missing",src,e); continue
    shutil.copy(dst,dst+".before_bewt")
    c=json.load(open(dst)); c.update(new); json.dump(c,open(dst,"w")); print(dst,"+",len(new))
PY
echo "EAGL BEWT DONE"
