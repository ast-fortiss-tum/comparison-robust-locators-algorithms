#!/bin/bash
# The four Similo/MultiLocator hybrids (+ reference algorithms for the per-element comparison), per-element outcomes dumped to out/dump/.
cd "$(dirname "$0")"
export PATH="/Users/stocco/repos/tools/abushakra-comparison-robust-locators-algorithms/similo-thesis/.venv/bin:$PATH"
export JAVA_HOME=/Users/stocco/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home
export DUMP_DIR=out/dump
H="MultiLocator+Similo-fallback,MultiLocator+Similo-voter,MultiLocator+Similo-gated,MultiLocator+Similo-repair"
CORE="OptimizedSimilo,COLOR,MultiLocator,MultiLocator+VISTA,RobulaPlus,SeleniumIDE,SIDEREAL,EAGL,VISTA"
TUNING="accuweather adobe amazon apple bestbuy bing discord ebay espn etsy facebook fidelity google indeed linkedin netflix office paypal reddit roblox t-mobile tiktok twitter usps walmart weather wikipedia yahoo zillow zoom"
HELD="zz_aol zz_cnn zz_fandom zz_foxnews zz_imdb zz_instagram"
BEWT=$(ls -d sites/bewt_*/ | xargs -n1 basename | tr '\n' ' ')
RB=$(ls -d sites/rb_*/ | xargs -n1 basename | tr '\n' ' ')
mkdir -p out/logs
run() { # part algos sites
  L=out/logs/$1.log
  grep -q "@ALL" "$L" 2>/dev/null && return
  rm -f out/dump/*.part_$1 2>/dev/null
  ALGOS="$2" sbt -batch -J-Xmx12g "runMain Main $3" > "$L" 2>&1
  echo "$(date +%m-%d\ %H:%M) $1 rc=$? $(grep -c '@ALL' $L) algorithms done" >> out/progress.log
}
run bewt "$H,$CORE" "$BEWT"
run reprobreak "$H,$CORE" "$RB"
run wayback_tuning "$H,OptimizedSimilo,MultiLocator" "$TUNING"
run wayback_heldout "$H,OptimizedSimilo,MultiLocator" "$HELD"
echo "HYBRIDS DONE" >> out/progress.log
