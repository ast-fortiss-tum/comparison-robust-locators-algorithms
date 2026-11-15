#!/bin/bash
# EAGL-scaled category fold models f0..f4 on the BEWT and ReproBreak sites, one fold at a time (extract its 3 models, run one container, merge the cache, delete the models).
cd "$(dirname "$0")/.."
export EAGL=/Users/stocco/repos/tools/eagl EAGL_SITES="$(cat out/apps_sites.txt | tr '\n' ' ')"
A=/Users/stocco/repos/tools/comparison-robust-locators-algorithms/similo-thesis/docs/cross_training_models.7z
M=/Users/stocco/repos/tools/oracle-work/rq4models
for k in 0 1 2 3 4; do
  rm -rf $M; mkdir -p $M
  7zz x -y -bso0 -bsp0 -o$M $A rq4/news_websites_fold$k.joblib rq4/retail_websites_fold$k.joblib rq4/web_services_websites_fold$k.joblib
  for attempt in 1 2 3; do
    scripts/eagl/run_one.sh "apps_f$k|$M/rq4|--scaled --model-map /work/scripts/eagl/rq4_apps_fold$k.json --cache out/eagl_scaled_f${k}_apps_cache.json" | tee /tmp/_apps_f$k
    grep -q "rc=137" /tmp/_apps_f$k || break
  done
  python3 - $k <<'PY'
import json,sys,shutil
k=sys.argv[1]; src=f"out/eagl/eagl_scaled_f{k}_apps_cache.json"; dst=f"cache/eagl_scaled_f{k}_cache.json"
try: new=json.load(open(src))
except Exception as e: print("MISSING",src,e); sys.exit(0)
c=json.load(open(dst)); n=len(c); c.update(new); json.dump(c,open(dst,"w")); print(f"fold {k}: cache {n} -> {len(c)} (+{len(new)})")
PY
  rm -rf $M
  echo "FOLD $k DONE $(date +%H:%M)"
done
echo "ALL FOLDS DONE"
