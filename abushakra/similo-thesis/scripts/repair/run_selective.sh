#!/bin/bash
# Selective, verified, in-memory repair on every benchmark. Two lanes, each in its own project copy (own sbt server):
#   X (dev):  MultiLocator+selective-repair on BEWT, ReproBreak, Wayback tuning; then the +VISTA variant on the same
#   Y (dev2): Wayback held-out, MultiLocator+selective-repair then the +VISTA variant
cd "$(dirname "$0")"
export PATH="/Users/stocco/repos/tools/abushakra-comparison-robust-locators-algorithms/similo-thesis/.venv/bin:$PATH"
export JAVA_HOME=/Users/stocco/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home
export DUMP_DIR=out/dump_selective
TUNING="accuweather adobe amazon apple bestbuy bing discord ebay espn etsy facebook fidelity google indeed linkedin netflix office paypal reddit roblox t-mobile tiktok twitter usps walmart weather wikipedia yahoo zillow zoom"
HELD="zz_aol zz_cnn zz_fandom zz_foxnews zz_imdb zz_instagram"
BEWT=$(ls -d sites/bewt_*/ | xargs -n1 basename | tr '\n' ' ')
RB=$(ls -d sites/rb_*/ | xargs -n1 basename | tr '\n' ' ')
mkdir -p out/selective
run() { # part algo sites
  L=out/selective/$2.$1.log
  [ "$(grep -c '@ALL' "$L" 2>/dev/null)" -ge 1 ] 2>/dev/null && return
  ALGOS="$2" sbt -batch -J-Xmx12g "runMain Main $3" > "$L" 2>&1
  echo "$(date +%m-%d\ %H:%M) $2 $1 rc=$? $(grep '@ALL' $L | cut -d, -f1-4) | $(grep '^#' $L | head -1 | cut -c1-30)" >> out/selective/progress.log
}
if [ "$1" = X ]; then
  for A in MultiLocator+selective-repair MultiLocator+VISTA+selective-repair; do run bewt $A "$BEWT"; run reprobreak $A "$RB"; run tuning $A "$TUNING"; done
else
  for A in MultiLocator+selective-repair MultiLocator+VISTA+selective-repair; do run heldout $A "$HELD"; done
fi
echo "LANE $1 DONE" >> out/selective/progress.log
