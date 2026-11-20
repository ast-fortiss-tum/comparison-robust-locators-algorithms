#!/bin/bash
# First-to-all (fixed anchor from version 1) on all 36 Wayback sites, one JVM per algorithm and period (a single JVM over
# all 36 sites ran out of heap at 11 GB), per-element dumps for survivability.py.
# Usage (from a project copy that has sites/ and cache/): run_survivability.sh LANE "Algo1,Algo2,..." CLASSPATH_FILE
cd "$(dirname "$0")"
export PATH="/Users/stocco/repos/tools/abushakra-comparison-robust-locators-algorithms/similo-thesis/.venv/bin:$PATH"
JAVA=/Users/stocco/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home/bin/java
LANE=$1; LIST=$2; CP=$(cat $3)
O=out/sv/$LANE; mkdir -p $O/logs
export PAIRING=firstToAll DUMP_DIR=$O/dump
TUNING="accuweather adobe amazon apple bestbuy bing discord ebay espn etsy facebook fidelity google indeed linkedin netflix office paypal reddit roblox t-mobile tiktok twitter usps walmart weather wikipedia yahoo zillow zoom"
HELD="zz_aol zz_cnn zz_fandom zz_foxnews zz_imdb zz_instagram"
for A in $(echo "$LIST" | tr ',' ' '); do
  for P in tuning heldout; do
    if [ $P = tuning ]; then S="$TUNING"; else S="$HELD"; fi
    ALGOS="$A" $JAVA -Xmx12g -cp "$CP" Main $S > "$O/logs/$A.$P.log" 2>&1
    echo "$(date +%m-%d\ %H:%M) $A $P rc=$? $(grep '@ALL' $O/logs/$A.$P.log | cut -d, -f1-4 | tr '\n' ' ')" >> $O/progress.log
  done
done
echo "$(date +%m-%d\ %H:%M) LANE $LANE DONE" >> $O/progress.log
