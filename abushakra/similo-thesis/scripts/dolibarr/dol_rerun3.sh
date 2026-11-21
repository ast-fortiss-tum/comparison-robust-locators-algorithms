#!/bin/bash
# Re-prioritised remainder of the clean rerun: waits for OptimizedSimilo to finish in dol_rerun.sh, replaces the rest of the queue (and dol_rerun2.sh) with
# MultiLocator and all its variants first, then VISTA, then the other appendix algorithms; summaries and finalize at the end.
H=/Users/stocco/repos/tools/hybrid-work; P=$H/dol; LOG=$P/out/pipeline_full.log
export STRIP_XMLNS=1; unset SITES_FILE
cd $P
until grep -q " OptimizedSimilo complete$" out/algos.progress; do sleep 1; done
pkill -f "dol_rerun2.sh"; pkill -f "dol_rerun.sh"; pkill -f "caffeinate -i ./run_algos"; pkill -f "./run_algos.sh"; sleep 1; pkill -f "Main dol_dolibarr"; sleep 3
rm -f out/dump/MultiLocator.csv out/done/MultiLocator.* out/logs/MultiLocator.log   # partial start of the next algorithm in the old order
echo "$(date +%m-%d\ %H:%M) RERUN3: OptimizedSimilo done; MultiLocator family first, then VISTA, then the other appendix algorithms" >> $LOG
caffeinate -i ./run_algos.sh "MultiLocator MultiLocator+VISTA MultiLocator+paper-repair MultiLocator+selective-repair MultiLocator+VISTA+paper-repair VISTA IDXPath SIDEREAL-uniform OptimizedMinimal VonSimilo OptimizedVonSimilo LegacyCombined OptimizedCombined COLOR" 50 out >> $LOG 2>&1 || { echo "$(date +%m-%d\ %H:%M) rerun3 aborted (see algos.progress)" >> $LOG; exit 3; }
cd /Users/stocco/repos/tools/comparison-robust-locators-algorithms/abushakra/similo-thesis
python3 scripts/analysis/riskcoverage.py dolibarr_all_pairs $P/out/dump > $P/out/summary_all_pairs.txt 2>&1
EXCLUDE_SITES=$P/out/excluded_sites.txt python3 scripts/analysis/riskcoverage.py dolibarr_clean $P/out/dump > $P/out/summary_clean.txt 2>&1
/Users/stocco/repos/tools/addrbook-work/.venv/bin/python3.15 /Users/stocco/repos/tools/dolibarr-work/dol_finalize.py >> $LOG 2>&1
echo "$(date +%m-%d\ %H:%M) CLEAN RERUN done; all 22 algorithms, summaries (all pairs / without the 178 excluded) + finalize written" >> $LOG
