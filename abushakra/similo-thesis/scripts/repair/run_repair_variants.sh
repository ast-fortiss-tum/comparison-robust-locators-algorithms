#!/bin/bash
# Repair variants, clean state. Lane A (disk-based repair store, so strictly sequential and cleaned before every run):
#   run_repair_variants.sh A  ;  lane B (in-memory VISTA templates): run_repair_variants.sh B
cd "$(dirname "$0")/.."
export PATH="$PWD/.venv/bin:$PATH"  # VISTA needs OpenCV (cv2) in the venv
export JAVA_HOME=/Users/stocco/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home
TUNING="accuweather adobe amazon apple bestbuy bing discord ebay espn etsy facebook fidelity google indeed linkedin netflix office paypal reddit roblox t-mobile tiktok twitter usps walmart weather wikipedia yahoo zillow zoom"
HELD="zz_aol zz_cnn zz_fandom zz_foxnews zz_imdb zz_instagram"
BEWT=$(ls -d sites/bewt_*/ | xargs -n1 basename | tr '\n' ' ')
RB=$(ls -d sites/rb_*/ | xargs -n1 basename | tr '\n' ' ')
run() { # algo part sites
  L=out/repair_variants/$1.$2.log
  grep -q "@ALL" "$L" 2>/dev/null && return
  [ "$LANE" = A ] && find sites -name healed-locators.json -delete
  ALGOS=$1 sbt -batch -J-Xmx12g "runMain Main $3" > "$L" 2>&1
  echo "$(date +%m-%d\ %H:%M) $1 $2 rc=$? $(grep '@ALL' $L | cut -d, -f1-4)" >> out/repair_variants/progress.log
}
LANE=$1
if [ $LANE = A ]; then
  for A in MultiLocator+VISTA+repair MultiLocator+repair; do run $A bewt "$BEWT"; run $A reprobreak "$RB"; done
  for A in MultiLocator+repair MultiLocator+VISTA+repair; do run $A tuning "$TUNING"; run $A heldout "$HELD"; done
else
  run VISTA+repair bewt "$BEWT"; run VISTA+repair reprobreak "$RB"; run VISTA+repair tuning "$TUNING"; run VISTA+repair heldout "$HELD"
fi
echo "LANE $LANE DONE" >> out/repair_variants/progress.log
