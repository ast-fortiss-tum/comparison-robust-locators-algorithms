#!/bin/bash
# Clean single-pass rerun of all 11 algorithms on ALL imported Dolibarr pairs (v1 -> v2..v30, 5,094 sites), nothing resumed. EAGL generation cache is kept (inputs, not results).
H=/Users/stocco/repos/tools/hybrid-work; P=$H/dol; LOG=$P/out/pipeline_full.log
export STRIP_XMLNS=1; unset SITES_FILE
cd $P
echo "$(date +%m-%d\ %H:%M) CLEAN RERUN: dumps, markers and VISTA cache deleted; one pass over all sites" >> $LOG
rm -rf out/dump out/dump_v30 out/done out/algos.progress out/summary_full*.txt; rm -f out/logs/*.log cache/vista_cache.json
caffeinate -i ./run_algos.sh "absXPath SeleniumIDE RobulaPlus Montoto SIDEREAL EAGL LegacySimilo OptimizedSimilo MultiLocator VISTA MultiLocator+VISTA" 50 out >> $LOG 2>&1 || { echo "$(date +%m-%d\ %H:%M) rerun aborted (see algos.progress)" >> $LOG; exit 3; }
cd /Users/stocco/repos/tools/comparison-robust-locators-algorithms/abushakra/similo-thesis
python3 scripts/analysis/riskcoverage.py dolibarr_all_pairs $P/out/dump > $P/out/summary_all_pairs.txt 2>&1
EXCLUDE_SITES=$P/out/excluded_sites.txt python3 scripts/analysis/riskcoverage.py dolibarr_clean $P/out/dump > $P/out/summary_clean.txt 2>&1
/Users/stocco/repos/tools/addrbook-work/.venv/bin/python3.15 /Users/stocco/repos/tools/dolibarr-work/dol_finalize.py >> $LOG 2>&1
echo "$(date +%m-%d\ %H:%M) CLEAN RERUN done; summaries (all pairs / without the 178 excluded) + finalize written" >> $LOG
