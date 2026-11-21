#!/bin/bash
# Walks the chain from v6 on: each step copies the previous (repaired) suite, runs it on the next release and stops at the first release where it does not pass 39/39.
cd /Users/stocco/repos/tools/dolibarr-work
CHAIN="6:3.2.0 7:3.1.3 8:3.2.1 9:3.2.2 10:3.2.3 11:3.3.0 12:3.3.1 13:3.3.2 14:3.3.3 15:3.4.0 16:3.4.1 17:3.4.2 18:3.5.0 19:3.5.1 20:3.5.2 21:3.5.3 22:3.5.4 23:3.6.0 24:3.6.2 25:3.6.3 26:3.7.0 27:3.6.4 28:3.7.1 29:3.8.0"
for c in $CHAIN; do
  N=${c%%:*}; REL=${c#*:}
  [ -f chain_pass_$N ] && continue
  case $N in 7) BASE=5;; 8) BASE=6;; 27) BASE=25;; 28) BASE=26;; *) BASE=$((N-1));; esac   # 3.1.3 (v7) is a 3.1 branch release: start from the 3.1.2 suite; 3.2.1 (v8) continues from 3.2.0
  out=$(./new_version.sh $N $REL $BASE 2>&1); line=$(echo "$out" | grep -E '^Tests run:.*Fail' | tail -1)
  echo "$(date +%H:%M) v$N $REL: $line" >> evolve_progress.log
  if echo "$line" | grep -q "Tests run: 39, Failures: 0, Errors: 0"; then touch chain_pass_$N; else echo "STOP at v$N ($REL)" >> evolve_progress.log; break; fi
done
