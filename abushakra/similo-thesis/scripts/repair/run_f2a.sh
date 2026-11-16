#!/bin/bash
# First-to-all pairing (fixed anchor from version 1): plain MultiLocator against selective repair, so that repaired locators are reused.
cd "$(dirname "$0")"
export PATH="/Users/stocco/repos/tools/abushakra-comparison-robust-locators-algorithms/similo-thesis/.venv/bin:$PATH"
export JAVA_HOME=/Users/stocco/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home
export PAIRING=firstToAll DUMP_DIR=out/dump_f2a
TUNING="accuweather adobe amazon apple bestbuy bing discord ebay espn etsy facebook fidelity google indeed linkedin netflix office paypal reddit roblox t-mobile tiktok twitter usps walmart weather wikipedia yahoo zillow zoom"
HELD="zz_aol zz_cnn zz_fandom zz_foxnews zz_imdb zz_instagram"
mkdir -p out/f2a
if [ "$1" = X ]; then S="$TUNING"; P=tuning; else S="$HELD"; P=heldout; fi
L=out/f2a/$P.log
ALGOS="MultiLocator,MultiLocator+selective-repair" sbt -batch -J-Xmx12g "runMain Main $S" > "$L" 2>&1
echo "$(date +%m-%d\ %H:%M) first-to-all $P rc=$? $(grep '@ALL' $L | cut -d, -f1-4 | tr '\n' ' ')" >> out/f2a/progress.log
echo "LANE $1 DONE" >> out/f2a/progress.log
