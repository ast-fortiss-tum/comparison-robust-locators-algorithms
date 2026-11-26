#!/bin/bash
# evolve_pf.sh N : deploy vN, start suite = repaired v(N-1) (if suites_repaired/vN missing), run it, print summary
cd /Users/stocco/repos/tools/phpfusion-work
N=$1; P=${2:-$((N-1))}
python3 deploy_pf.py $N
[ -d suites_repaired/v$N ] || cp -R suites_repaired/v$P suites_repaired/v$N
rm -rf out_r/$N
./run_pf.sh $N SNAP=1 > replay_r$N.log 2>&1
echo "v$N: $(grep -c ' pass ' replay_r$N.log) pass, $(grep -c ' fail ' replay_r$N.log) fail"; grep ' fail ' replay_r$N.log | cut -c1-170
