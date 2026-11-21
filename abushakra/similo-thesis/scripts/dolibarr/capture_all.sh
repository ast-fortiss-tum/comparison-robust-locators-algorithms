#!/bin/bash
# Runs every repaired suite on its own release with the capture driver (also re-validates that each is 39/39 with the final driver). Results: capture-out/vN, capture_all.log
cd /Users/stocco/repos/tools/dolibarr-work
MAP="1:3.0.0 2:3.0.1 3:3.1.0 4:3.1.1 5:3.1.2 6:3.2.0 7:3.1.3 8:3.2.1 9:3.2.2 10:3.2.3 11:3.3.0 12:3.3.1 13:3.3.2 14:3.3.3 15:3.4.0 16:3.4.1 17:3.4.2 18:3.5.0 19:3.5.1 20:3.5.2 21:3.5.3 22:3.5.4 23:3.6.0 24:3.6.2 25:3.6.3 26:3.7.0 27:3.6.4 28:3.7.1 29:3.8.0"
for c in $MAP; do N=${c%%:*}; REL=${c#*:}
  [ -f capture-out/v$N/.done ] && continue
  free=$(df -g /Users | tail -1 | awk '{print $4}'); [ "$free" -lt 15 ] && { echo "$(date +%H:%M) STOP: disk free ${free}G" >> capture_all.log; exit 3; }
  line=$(CAPTURE=1 ./run_suite.sh $N $REL 2>&1 | grep -E '^Tests run:.*Fail' | tail -1)
  echo "$(date +%H:%M) v$N $REL: $line  size=$(du -sm capture-out/v$N 2>/dev/null | cut -f1)MB" >> capture_all.log
  echo "$line" | grep -q "Tests run: 39, Failures: 0, Errors: 0" && touch capture-out/v$N/.done
done
echo "$(date +%H:%M) capture_all done" >> capture_all.log
