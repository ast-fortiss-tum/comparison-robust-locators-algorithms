#!/bin/bash
# Dolibarr pilot: align v1 -> each given version, import in chunks (the importer runs out of memory on many pairs at once), run algorithms, summarise.
# usage: dol_pipeline.sh "<new versions, e.g. 2 3 4 5>" "<algorithms or 'all'>"       (env LABEL names the run, default pilot)
H=/Users/stocco/repos/tools/hybrid-work; P=$H/dol; D=/Users/stocco/repos/tools/dolibarr-work; LABEL=${LABEL:-pilot}; LOG=$P/out/pipeline_$LABEL.log
JAVA=/Users/stocco/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home/bin/java; PY=/Users/stocco/repos/tools/addrbook-work/.venv/bin/python3.15
VERS=$1; ALGS=${2:-all}; mkdir -p $P/out; cd $P
echo "$(date +%m-%d\ %H:%M) [$LABEL] aligning v1 -> $VERS" >> $LOG
rm -rf import; find sites -maxdepth 1 -name 'dol_*' -exec rm -rf {} + ; rm -rf out/dump_$LABEL out/done out/logs out/algos.progress
$PY $D/align_dolibarr.py $P/import 1 $VERS >> $LOG 2>&1 || { echo "align FAILED" >> $LOG; exit 1; }
python3 - <<PY
import json
p=json.load(open('$P/import/plan.json')); n=100
for i in range(0,len(p),n): json.dump(p[i:i+n],open('$P/import/plan_chunk_%03d.json'%(i//n),'w'))
print('chunks',(len(p)+n-1)//n,'pairs',len(p))
PY
for f in import/plan_chunk_*.json; do
  c=$(basename $f .json | sed 's/plan_chunk_//')
  $JAVA -Xmx5g -cp "$(cat $H/cp_abk.txt)" scrape.ImportReproBreak $f import/captures sites dol > out/import_$c.log 2>&1 || { echo "import chunk $c FAILED" >> $LOG; exit 1; }
  mv sites/dol_manifest.csv out/manifest_$c.csv
done
python3 - >> $LOG <<PY
import csv,glob,collections
R=[]
for f in sorted(glob.glob('$P/out/manifest_*.csv')): R+=list(csv.reader(open(f),delimiter=';'))[1:]
print('imported sites:',len(R),'status:',dict(collections.Counter(r[4] for r in R)))
PY
rm -rf import/captures; echo "$(date +%m-%d\ %H:%M) captures removed; running algorithms: $ALGS" >> $LOG
OUT=out ./run_algos.sh "$ALGS" 50 out >> $LOG 2>&1
echo "$(date +%m-%d\ %H:%M) algorithms done; summarising" >> $LOG
cd /Users/stocco/repos/tools/comparison-robust-locators-algorithms/abushakra/similo-thesis && python3 scripts/analysis/riskcoverage.py dolibarr_$LABEL $P/out/dump > $P/out/summary_$LABEL.txt 2>&1
echo "$(date +%m-%d\ %H:%M) done; summary in out/summary_$LABEL.txt" >> $LOG
