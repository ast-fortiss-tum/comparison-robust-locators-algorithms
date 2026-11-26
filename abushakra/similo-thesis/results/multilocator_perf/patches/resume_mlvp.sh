#!/bin/bash
# MultiLocator+VISTA+repair with a wall-clock limit per site (it stalls in Robula+ regeneration on some sites).
# usage: resume_mlvp.sh wayback | asg_f2a | asg_consec        limit: LIMIT seconds per site (default 300)
# A site that hits the limit is written to <out>/mlvp.timeouts and skipped on later runs (counts as "no result", report it).
A="MultiLocator+VISTA+repair"; LIMIT=${LIMIT:-300}; H=/Users/stocco/repos/tools/hybrid-work
export PATH="/Users/stocco/repos/tools/abushakra-comparison-robust-locators-algorithms/similo-thesis/.venv/bin:$PATH"
JAVA=/Users/stocco/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home/bin/java
CP="$(cat $H/cp_ob.txt)"   # ob classes contain the paper-repair entry; the dev classes are older
guard() { # stop if disk is low; wait while free memory is below MINFREE % (default 20) or swap is above 12 GB (runaway)
  local free mem sw; free=$(df -g /Users | tail -1 | awk '{print $4}'); [ "$free" -lt 15 ] && { echo "$(date +%H:%M) STOP: disk free ${free}G" >> $PROG; exit 3; }
  while true; do
    mem=$(memory_pressure -Q 2>/dev/null | awk '/free percentage/ {gsub("%","",$NF); print int($NF)}')
    sw=$(sysctl -n vm.swapusage | awk '{gsub("M","",$6); print int($6)}')
    [ "${mem:-0}" -ge "${MINFREE:-20}" ] && [ "${sw:-0}" -le 12000 ] && break
    echo "$(date +%H:%M) waiting: free memory ${mem:-?}% (min ${MINFREE:-20}%), swap ${sw}M" >> $PROG; sleep 120
  done
}
lim() { perl -e 'alarm shift; exec @ARGV' "$LIMIT" "$@"; }   # SIGALRM ends the JVM -> rc 142
done_site() { [ -f "$1" ] && cut -d, -f2 "$1" | grep -qx "$2"; }
one() { # site heap
  ALGOS="$A" lim $JAVA -Xmx$2 -cp "$CP" Main $1 2>&1 | grep -E "@|Exception|OutOfMemory|Error" | grep -v "@ALL"; return ${PIPESTATUS[0]}; }
case "$1" in
 wayback)
  cd $H/dev2; O=out/sv/B; mkdir -p $O/dump $O/logs; PROG=$O/mlvp.progress; export PAIRING=firstToAll DUMP_DIR=$O/dump
  ALL="accuweather adobe amazon apple bestbuy bing discord ebay espn etsy facebook fidelity google indeed linkedin netflix office paypal reddit roblox t-mobile tiktok twitter usps walmart weather wikipedia yahoo zillow zoom zz_aol zz_cnn zz_fandom zz_foxnews zz_imdb zz_instagram"
  for S in $ALL; do
    done_site "$O/dump/$A.csv" $S && continue; grep -qx "$S" $O/mlvp.timeouts 2>/dev/null && continue; guard
    t0=$(date +%s); out=$(one $S 9g); rc=$?; echo "$out" >> $O/logs/mlvp.log
    if [ $rc -eq 142 ]; then echo "$S" >> $O/mlvp.timeouts; echo "$(date +%m-%d\ %H:%M) TIMEOUT $S" >> $PROG
    else echo "$(date +%m-%d\ %H:%M) $S rc=$rc $(( $(date +%s)-t0 ))s" >> $PROG; fi
  done; echo "$(date +%m-%d\ %H:%M) wayback done" >> $PROG ;;
 asg_f2a|asg_consec)
  P=${1#asg_}; cd $H/ob; O=out; mkdir -p $O/asg_logs $O/dump_asg_$P; PROG=$O/mlvp.progress; DUMPF=$O/dump_asg_$P/$A.csv
  [ $P = f2a ] && export PAIRING=firstToAll || unset PAIRING; export DUMP_DIR=$O/dump_asg_$P
  for S in $(ls sites | grep '^asg_'); do
    done_site "$DUMPF" $S && continue; grep -qx "$S" $O/mlvp.$P.timeouts 2>/dev/null && continue; guard
    t0=$(date +%s); out=$(one $S 7g); rc=$?
    if echo "$out" | grep -q OutOfMemory; then out=$(one $S 11g); rc=$?; fi
    echo "$out" >> $O/asg_logs/$A.$P.log
    if [ $rc -eq 142 ]; then echo "$S" >> $O/mlvp.$P.timeouts; echo "$(date +%m-%d\ %H:%M) TIMEOUT $P $S" >> $PROG
    else echo "$(date +%m-%d\ %H:%M) $P $S rc=$rc $(( $(date +%s)-t0 ))s" >> $PROG; fi
  done; echo "$(date +%m-%d\ %H:%M) $1 done" >> $PROG ;;
 *) echo "usage: $0 wayback|asg_f2a|asg_consec"; exit 2;;
esac
