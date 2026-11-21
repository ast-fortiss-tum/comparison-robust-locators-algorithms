#!/bin/bash
# Reruns the versions whose capture run was not 39/39 (flaky logout redirect), up to 3 rounds, then marks the capture complete.
cd /Users/stocco/repos/tools/dolibarr-work
until grep -q "capture_all done" capture_all.log 2>/dev/null; do sleep 30; done
for r in 1 2 3; do
  missing=$(for n in $(seq 1 29); do [ -f capture-out/v$n/.done ] || echo $n; done | tr '\n' ' ')
  [ -z "$missing" ] && break
  echo "$(date +%H:%M) retry round $r for: $missing" >> capture_all.log
  sed -i '' '/capture_all done/d' capture_all.log; ./capture_all.sh
done
echo "$(date +%H:%M) capture_final (missing: $(for n in $(seq 1 29); do [ -f capture-out/v$n/.done ] || echo -n "$n "; done))" >> capture_all.log
