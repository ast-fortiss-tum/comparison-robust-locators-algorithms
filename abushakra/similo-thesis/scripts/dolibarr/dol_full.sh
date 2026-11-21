#!/bin/bash
# Full Dolibarr benchmark (first-to-all: v1 -> v2..v29): waits for capture_all, then per version: align -> chunked import -> delete captures; EAGL generation; all algorithms; summary.
H=/Users/stocco/repos/tools/hybrid-work; P=$H/dol; D=/Users/stocco/repos/tools/dolibarr-work; LOG=$P/out/pipeline_full.log
JAVA=/Users/stocco/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home/bin/java; PY=/Users/stocco/repos/tools/addrbook-work/.venv/bin/python3.15
export STRIP_XMLNS=1; export PATH="/Users/stocco/repos/tools/abushakra-comparison-robust-locators-algorithms/similo-thesis/.venv/bin:$PATH"
mkdir -p $P/out; cd $P
until grep -q "capture_final" $D/capture_all.log 2>/dev/null; do sleep 60; done
echo "$(date +%m-%d\ %H:%M) capture finished; building the full benchmark" >> $LOG
rm -rf import out/dump out/done out/logs out/algos.progress out/manifest_*.csv; find sites -maxdepth 1 -name 'dol_*' -exec rm -rf {} + ; rm -rf sites/templates; mkdir -p sites/templates cache; rm -f cache/vista_cache.json
for N in $(seq 2 29); do
  [ -f $D/capture-out/v$N/.done ] || { echo "$(date +%H:%M) v$N capture missing, skipped" >> $LOG; continue; }
  $PY $D/align_dolibarr.py $P/import 1 $N >> $LOG 2>&1 || { echo "align v$N FAILED" >> $LOG; continue; }
  python3 - <<PY
import json
p=[e for e in json.load(open('$P/import/plan.json')) if e['commit']=='v$N']; n=100
for i in range(0,len(p),n): json.dump(p[i:i+n],open('$P/import/plan_v${N}_%03d.json'%(i//n),'w'))
PY
  for f in import/plan_v${N}_*.json; do
    c=$(basename $f .json | sed 's/plan_//')
    $JAVA -Xmx5g -cp "$(cat $H/cp_abk.txt)" scrape.ImportReproBreak $f import/captures sites dol > out/import_$c.log 2>&1 || { echo "import $c FAILED" >> $LOG; continue; }
    mv sites/dol_manifest.csv out/manifest_$c.csv
  done
  rm -rf import/captures
done
python3 - >> $LOG <<PY
import csv,glob,collections
R=[]
for f in sorted(glob.glob('$P/out/manifest_*.csv')): R+=list(csv.reader(open(f),delimiter=';'))[1:]
print('imported sites:',len(R),'status:',dict(collections.Counter(r[4] for r in R)))
PY
echo "$(date +%m-%d\ %H:%M) EAGL generation" >> $LOG
rm -rf scripts_eagl_tmp; cp -R $H/dev2/scripts scripts_eagl_tmp; mkdir -p out/eagl
docker run --rm --network none --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges --user "$(id -u):$(id -g)" \
  -v /Users/stocco/repos/tools/eagl:/eagl:ro -v /Users/stocco/repos/tools/eagl/database:/models:ro -v $P/scripts_eagl_tmp:/work/scripts:ro -v $P/sites:/work/sites:ro -v $P/out/eagl:/work/out \
  eagl-bridge --eagl /eagl --report-dir out --model /models/cross_training.joblib --cache out/eagl_cache.json $(ls sites | grep '^dol_dolibarr_') > out/eagl/gen.log 2>&1
cp out/eagl/eagl_cache.json cache/eagl_cache.json; rm -rf scripts_eagl_tmp
echo "$(date +%m-%d\ %H:%M) EAGL generation rc=$?; running algorithms" >> $LOG
caffeinate -i ./run_algos.sh "absXPath SeleniumIDE RobulaPlus Montoto SIDEREAL EAGL LegacySimilo OptimizedSimilo MultiLocator VISTA MultiLocator+VISTA" 50 out >> $LOG 2>&1
echo "$(date +%m-%d\ %H:%M) algorithms done; summarising" >> $LOG
cd /Users/stocco/repos/tools/comparison-robust-locators-algorithms/abushakra/similo-thesis && python3 scripts/analysis/riskcoverage.py dolibarr_f2a $P/out/dump > $P/out/summary_full.txt 2>&1
echo "$(date +%m-%d\ %H:%M) done; summary in out/summary_full.txt" >> $LOG
