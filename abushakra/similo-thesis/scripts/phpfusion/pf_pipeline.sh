#!/bin/bash
# PHP-Fusion first-to-all (v1 -> v2..v41): chunked import, delete captures, EAGL generation, 11 algorithms, summary.
H=/Users/stocco/repos/tools/hybrid-work; P=$H/pf; LOG=$P/out/pipeline.log
JAVA=/Users/stocco/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home/bin/java
export STRIP_XMLNS=1; export PATH="/Users/stocco/repos/tools/abushakra-comparison-robust-locators-algorithms/similo-thesis/.venv/bin:$PATH"
cd $P; mkdir -p out
python3 - <<PY
import json
p=json.load(open('$P/import/plan.json')); n=100
for i in range(0,len(p),n): json.dump(p[i:i+n],open('$P/import/plan_chunk_%03d.json'%(i//n),'w'))
print('chunks',(len(p)+n-1)//n,'pairs',len(p))
PY
echo "$(date +%m-%d\ %H:%M) import start" >> $LOG
for f in import/plan_chunk_*.json; do
  c=$(basename $f .json | sed 's/plan_chunk_//')
  $JAVA -Xmx5g -cp "$(cat $H/cp_abk.txt)" scrape.ImportReproBreak $f import/captures sites pf > out/import_$c.log 2>&1 || { echo "import chunk $c FAILED" >> $LOG; exit 1; }
  mv sites/pf_manifest.csv out/manifest_$c.csv; echo "$(date +%H:%M) chunk $c imported" >> $LOG
done
python3 - >> $LOG <<PY
import csv,glob,collections
R=[]
for f in sorted(glob.glob('$P/out/manifest_*.csv')): R+=list(csv.reader(open(f),delimiter=';'))[1:]
print('imported sites:',len(R),'status:',dict(collections.Counter(r[4] for r in R)))
PY
rm -rf import/captures
echo "$(date +%m-%d\ %H:%M) EAGL generation" >> $LOG
rm -rf scripts_eagl_tmp; cp -R $H/dev2/scripts scripts_eagl_tmp; mkdir -p out/eagl
docker run --rm --network none --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges --user "$(id -u):$(id -g)" \
  -v /Users/stocco/repos/tools/eagl:/eagl:ro -v /Users/stocco/repos/tools/eagl/database:/models:ro -v $P/scripts_eagl_tmp:/work/scripts:ro -v $P/sites:/work/sites:ro -v $P/out/eagl:/work/out \
  eagl-bridge --eagl /eagl --report-dir out --model /models/cross_training.joblib --cache out/eagl_cache.json $(ls sites | grep '^pf_phpfusion_') > out/eagl/gen.log 2>&1
echo "$(date +%m-%d\ %H:%M) EAGL rc=$?" >> $LOG
cp out/eagl/eagl_cache.json cache/eagl_cache.json; rm -rf scripts_eagl_tmp
caffeinate -i ./run_algos.sh "absXPath SeleniumIDE RobulaPlus Montoto SIDEREAL EAGL LegacySimilo OptimizedSimilo MultiLocator VISTA MultiLocator+VISTA" 50 out >> $LOG 2>&1
echo "$(date +%m-%d\ %H:%M) algorithms done" >> $LOG
