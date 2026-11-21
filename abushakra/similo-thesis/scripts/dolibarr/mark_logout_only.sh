#!/bin/bash
# In capture mode TestLogout does not complete on 3.5.0+ (logout link click has no effect). Versions whose only failure is TestLogout count as captured.
cd /Users/stocco/repos/tools/dolibarr-work
while true; do
  for n in $(seq 1 29); do
    [ -d capture-out/v$n ] && [ ! -f capture-out/v$n/.done ] || continue
    grep -qE "^Tests run: 39, Failures: 0, Errors: 1" native_wd_v$n.log 2>/dev/null || continue
    [ "$(grep -E '^\s+\w+\.test' native_wd_v$n.log | grep -vc 'TestLogout.test')" = "0" ] && grep -q "capture_all.log" /dev/null; 
    if [ "$(grep -E '^\s+\w+\.test' native_wd_v$n.log | grep -vc 'TestLogout.test')" = "0" ] && grep -E "v$n " capture_all.log | grep -q "Errors: 1"; then touch capture-out/v$n/.done; echo "$(date +%H:%M) v$n accepted (only TestLogout fails in capture mode)" >> capture_all.log; fi
  done
  grep -q "capture_final" capture_all.log && break; sleep 20
done
