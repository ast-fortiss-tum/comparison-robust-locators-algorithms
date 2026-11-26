#!/bin/bash
# Runs algorithms on the imported AddressBook sites (sites/dol_dolibarr_*), one JVM per (algorithm, batch of sites), resumable (marker files).
# usage: run_algos.sh [algorithms: space-separated list or 'all'] [batch size, default 50] [out dir, default out]
# VISTA and the MultiLocator variants that use it run sequentially in this one lane because VISTA writes its cache.
H=/Users/stocco/repos/tools/hybrid-work; P=$H/dol; cd $P
ALL="absXPath IDXPath SeleniumIDE RobulaPlus Montoto SIDEREAL SIDEREAL-uniform LegacySimilo OptimizedSimilo OptimizedMinimal VonSimilo OptimizedVonSimilo LegacyCombined OptimizedCombined COLOR MultiLocator MultiLocator+repair MultiLocator+selective-repair VISTA MultiLocator+VISTA MultiLocator+VISTA+repair"
LIST=${1:-all}; [ "$LIST" = all ] && LIST=$ALL; BATCH=${2:-50}; OUT=${3:-out}
JAVA=/Users/stocco/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home/bin/java
export PATH="/Users/stocco/repos/tools/abushakra-comparison-robust-locators-algorithms/similo-thesis/.venv/bin:$PATH"
mkdir -p $OUT/dump $OUT/logs $OUT/done; PROG=$OUT/algos.progress
SITES=(); while IFS= read -r s; do SITES+=("$s"); done < <(if [ -n "$SITES_FILE" ]; then cat "$SITES_FILE"; else ls sites | grep "^dol_dolibarr_"; fi)   # macOS bash 3.2 has no mapfile
echo "$(date +%m-%d\ %H:%M) start: ${#SITES[@]} sites, batch $BATCH, algorithms: $LIST" >> $PROG
for A in $LIST; do
  for ((i=0; i<${#SITES[@]}; i+=BATCH)); do
    mark="$OUT/done/$A.$i"; [ -f "$mark" ] && continue
    free=$(df -g /Users | tail -1 | awk '{print $4}'); [ "$free" -lt 15 ] && { echo "$(date +%H:%M) STOP: disk free ${free}G" >> $PROG; exit 3; }
    t0=$(date +%s)
    ALGOS="$A" DUMP_DIR=$P/$OUT/dump perl -e 'alarm shift; exec @ARGV' 3600 $JAVA -Xmx6g -cp "$(cat $H/cp_abk.txt)" Main "${SITES[@]:i:BATCH}" >> $OUT/logs/$A.log 2>&1; rc=$?
    [ $rc -eq 0 ] && touch "$mark"
    echo "$(date +%m-%d\ %H:%M) $A batch@$i rc=$rc $(( $(date +%s)-t0 ))s" >> $PROG
  done
  echo "$(date +%m-%d\ %H:%M) $A complete" >> $PROG
done
echo "$(date +%m-%d\ %H:%M) all done" >> $PROG
