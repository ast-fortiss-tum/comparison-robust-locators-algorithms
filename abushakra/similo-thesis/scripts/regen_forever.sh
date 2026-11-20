#!/bin/bash
# Regenerates sites/<site>/<date>/chromeDOM.html for every benchmark site from the Wayback Machine, slowly, until done.
# - RegenerateDoms paces requests (60 s between page loads, <= 3 variants) and stops on archive.org's block page.
# - After a block this waits 6 h, doubling up to 48 h, and resumes (-Dregen.resume=true skips finished snapshots).
# - Runs from a frozen copy of the compiled classes so benchmark rebuilds cannot change it mid-run.
# Usage (detached): nohup caffeinate -i scripts/regen_forever.sh > out/regen/forever.log 2>&1 &
set -u
cd "$(dirname "$0")/.."
LOG=out/regen/forever.log
mkdir -p out/regen
say() { echo "$(date '+%F %T') $*"; }

# 1. classes: reuse a frozen copy if prepared (out/regen/classpath.txt), else wait for sbt, compile and freeze
J=$(/usr/libexec/java_home -v 17)
if [ -s out/regen/classpath.txt ]; then
  CP=$(cat out/regen/classpath.txt)
else
  while pgrep -f "runMain (Main|scripts|scrape)" >/dev/null; do say "waiting for running sbt jobs"; sleep 600; done
  sbt -batch -java-home "$J" compile > out/regen/compile.log 2>&1 || { say "compile failed"; exit 1; }
  rm -rf out/regen/classes && cp -R target/scala-3.3.0/classes out/regen/classes
  CP="$PWD/out/regen/classes:$(tail -1 target/streams/runtime/fullClasspathAsJars/_global/streams/export | tr ':' '\n' | grep -v similo-thesis | paste -sd: -)"
  echo "$CP" > out/regen/classpath.txt
fi
echo "$CP" | tr ':' '\n' | grep -q "selenium-chrome-driver" || { say "classpath looks wrong"; exit 1; }
say "classpath ready ($(echo "$CP" | tr ':' '\n' | wc -l) entries)"

backoff=21600
while true; do
  PASS=out/regen/pass-$(date +%Y%m%d-%H%M%S).log
  say "pass starts -> $PASS"
  "$J/bin/java" -Xmx10g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=out/regen -Dregen.resume=true -cp "$CP" scrape.RegenerateDoms fetch ${REGEN_SITES:-all} > "$PASS" 2>&1
  if grep -q "^STOPPED:" "$PASS"; then
    say "blocked by archive.org; sleeping $backoff s"; sleep "$backoff"
    backoff=$(( backoff * 2 > 172800 ? 172800 : backoff * 2 ))
    continue
  fi
  backoff=21600
  rem=$(grep -o "^REMAINING [0-9]*" "$PASS" | awk '{print $2}')
  if [ "${rem:-x}" = "0" ]; then say "all snapshots done"; break; fi
  if [ -z "${rem:-}" ]; then say "pass ended without a summary (crash?); retrying in 1 h"; sleep 3600; continue; fi
  say "$rem snapshots still pending (transient failures); next pass in 1 h"; sleep 3600
done
