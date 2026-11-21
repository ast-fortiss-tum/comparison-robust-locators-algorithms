#!/bin/bash
# Adds v1 -> v30 (the 3.9.0-beta commit; 50 of 55 tests pass, the failing ones are skipped) after dol_full has finished: align, import, EAGL for the new sites, algorithms into out/dump_v30, summary of both dumps merged.
H=/Users/stocco/repos/tools/hybrid-work; P=$H/dol; D=/Users/stocco/repos/tools/dolibarr-work; LOG=$P/out/pipeline_full.log
JAVA=/Users/stocco/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home/bin/java; PY=/Users/stocco/repos/tools/addrbook-work/.venv/bin/python3.15
export STRIP_XMLNS=1; export PATH="/Users/stocco/repos/tools/abushakra-comparison-robust-locators-algorithms/similo-thesis/.venv/bin:$PATH"
cd $P; until grep -q "done; summary in out/summary_full.txt" $LOG 2>/dev/null; do sleep 60; done
echo "$(date +%m-%d\ %H:%M) [v30] align + import" >> $LOG
ls sites | grep '^dol_dolibarr_' | sort > out/sites_before_v30.txt
$PY $D/align_dolibarr.py $P/import 1 30 >> $LOG 2>&1 || { echo "v30 align FAILED" >> $LOG; exit 1; }
python3 - <<PY
import json
p=[e for e in json.load(open('$P/import/plan.json')) if e['commit']=='v30']; n=100
for i in range(0,len(p),n): json.dump(p[i:i+n],open('$P/import/plan_v30_%03d.json'%(i//n),'w'))
print('v30 pairs',len(p))
PY
for f in import/plan_v30_*.json; do c=$(basename $f .json | sed 's/plan_//'); $JAVA -Xmx5g -cp "$(cat $H/cp_abk.txt)" scrape.ImportReproBreak $f import/captures sites dol > out/import_$c.log 2>&1 || echo "import $c FAILED" >> $LOG; mv sites/dol_manifest.csv out/manifest_$c.csv; done
rm -rf import/captures
ls sites | grep '^dol_dolibarr_' | sort | comm -13 out/sites_before_v30.txt - > out/sites_v30.txt; echo "$(date +%m-%d\ %H:%M) [v30] $(wc -l < out/sites_v30.txt) new sites; EAGL + algorithms" >> $LOG
rm -rf scripts_eagl_tmp; cp -R $H/dev2/scripts scripts_eagl_tmp
docker run --rm --network none --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges --user "$(id -u):$(id -g)" \
  -v /Users/stocco/repos/tools/eagl:/eagl:ro -v /Users/stocco/repos/tools/eagl/database:/models:ro -v $P/scripts_eagl_tmp:/work/scripts:ro -v $P/sites:/work/sites:ro -v $P/out/eagl:/work/out \
  eagl-bridge --eagl /eagl --report-dir out --model /models/cross_training.joblib --cache out/eagl_cache.json $(cat out/sites_v30.txt) > out/eagl/gen_v30.log 2>&1
cp out/eagl/eagl_cache.json cache/eagl_cache.json; rm -rf scripts_eagl_tmp
mkdir -p out/dump_v30; SITES=($(cat out/sites_v30.txt))
for A in absXPath SeleniumIDE RobulaPlus Montoto SIDEREAL EAGL LegacySimilo OptimizedSimilo MultiLocator VISTA MultiLocator+VISTA; do
  for ((i=0; i<${#SITES[@]}; i+=50)); do ALGOS="$A" DUMP_DIR=$P/out/dump_v30 perl -e 'alarm shift; exec @ARGV' 3600 $JAVA -Xmx6g -cp "$(cat $H/cp_abk.txt)" Main "${SITES[@]:i:50}" >> out/logs/v30_$A.log 2>&1; done
  echo "$(date +%m-%d\ %H:%M) [v30] $A complete" >> $LOG
done
cd /Users/stocco/repos/tools/comparison-robust-locators-algorithms/abushakra/similo-thesis && python3 scripts/analysis/riskcoverage.py dolibarr_f2a_all $P/out/dump $P/out/dump_v30 > $P/out/summary_full_with_v30.txt 2>&1
echo "$(date +%m-%d\ %H:%M) [v30] done; summary in out/summary_full_with_v30.txt" >> $LOG
