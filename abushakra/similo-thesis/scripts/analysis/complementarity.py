#!/usr/bin/env python3
"""Complementarity analysis: on the pairs where the reference algorithm (default OptimizedSimilo) is not right, how often is another algorithm right?
usage: complementarity.py <benchmark> [reference]   (benchmark = name in BENCH below)
Per benchmark: reference accuracy, failures (weighted by nrel where a weights file exists), per-algorithm rescue rate, oracle ceiling (any other algorithm right),
greedy set cover (which algorithms to add, in order), unique rescues, pairs nobody solves, and the reverse (reference right, algorithm wrong)."""
import csv, json, os, sys, collections
R = '/Users/stocco/repos/tools/comparison-robust-locators-algorithms/abushakra/similo-thesis/results'
BENCH = {  # name -> (dump dirs, weights file or None, site-id extractor)
    'PHP-Fusion': ([f'{R}/phpfusion/dump'], f'{R}/phpfusion/weights.json'),
    'Dolibarr': ([f'{R}/dolibarr/dump'], f'{R}/dolibarr/weights.json'),
    'AddressBook': ([f'{R}/addressbook/dump'], None),
    'Excalidraw': ([f'{R}/oracle_dumps/dump_ex_f2a'], None),
    'Slickgrid': ([f'{R}/oracle_dumps/dump_asg_f2a'], None),
    'Wayback': ([f'{R}/wayback_first_to_all/dump_laneA', f'{R}/wayback_first_to_all/dump_laneB', f'{R}/wayback_first_to_all/dump_repair_variants'], None),
    'Perturbation': (['/Users/stocco/repos/tools/hybrid-work/lb/out/dump'], None),
}
REN = {'MultiLocator+repair': 'MultiLocator+full-repair', 'MultiLocator+VISTA+repair': 'MultiLocator+VISTA+full-repair',
       'MultiLocator+paper-repair': 'MultiLocator+repair', 'MultiLocator+VISTA+paper-repair': 'MultiLocator+VISTA+repair'}
SKIP = {'MultiLocator+full-repair', 'MultiLocator+selective-repair', 'MultiLocator+repair', 'MultiLocator+VISTA+full-repair', 'MultiLocator+VISTA+repair',
        'MultiLocator+VISTA+selective-repair', 'SIDEREAL-uniform', 'LegacyCombined', 'OptimizedCombined', 'OptimizedMinimal'}   # near-duplicates of listed ones
name = sys.argv[1]; ref = sys.argv[2] if len(sys.argv) > 2 else 'OptimizedSimilo'
dirs, wf = BENCH[name]
W = json.load(open(wf)) if wf and os.path.exists(wf) else None
out = collections.defaultdict(dict); weight = {}
for d in dirs:
    for f in sorted(os.listdir(d)):
        if not f.endswith('.csv'): continue
        for r in csv.DictReader(open(f'{d}/{f}', encoding='utf8')):
            a = r['algorithm']
            if a in SKIP: continue
            k = (r['site'], r['date'], r['element']); out[a][k] = r['outcome']
            if k not in weight:
                weight[k] = W.get(r['site'].split('_')[-1], 1) if W else 1
if ref not in out: sys.exit(f'{ref} missing')
keys = [k for k in out[ref]]
# only pairs every algorithm reports (an algorithm with a partial dump is compared on its own pairs, noted)
tot = sum(weight[k] for k in keys); fail = [k for k in keys if out[ref][k] != 'right']
wf_ = sum(weight[k] for k in fail)
print('backers exclude every *Similo* algorithm')
print(f'== {name}: reference {ref}: {len(keys)} pairs (weight {tot}), right {100*(tot-wf_)/tot:.1f}%, failing {len(fail)} pairs (weight {wf_})')
algos = [a for a in out if a != ref and 'Similo' not in a]   # backers must be independent of the Similo family: every *Similo* algorithm (COLOR is the paper version, a weighted average, not a Similo instance)
rows = []
for a in algos:
    cov = [k for k in fail if k in out[a]]
    resc = [k for k in cov if out[a][k] == 'right']
    rev = [k for k in keys if k in out[a] and out[ref][k] == 'right' and out[a][k] != 'right']
    rows.append((sum(weight[k] for k in resc) / wf_ * 100, a, len(cov), sum(weight[k] for k in resc), sum(weight[k] for k in rev)))
print(f'{"algorithm":34s} {"covered":>8s} {"rescued":>8s} {"%of fails":>9s} {"breaks (ref right, algo not)":>30s}')
for p, a, c, rs, rv in sorted(rows, reverse=True): print(f'{a:34s} {c:8d} {rs:8d} {p:9.1f} {rv:30d}')
# oracle: any other algorithm right
anyr = [k for k in fail if any(out[a].get(k) == 'right' for a in algos)]
w_any = sum(weight[k] for k in anyr)
print(f'oracle: some other algorithm is right on {100*w_any/wf_:.1f}% of the failures -> ceiling {100*(tot-wf_+w_any)/tot:.1f}%; nobody right on {len(fail)-len(anyr)} pairs (weight {wf_-w_any})')
# greedy cover
left = set(anyr); order = []
while left:
    best = max(algos, key=lambda a: sum(weight[k] for k in left if out[a].get(k) == 'right'))
    gain = [k for k in left if out[best].get(k) == 'right']
    if not gain: break
    order.append((best, sum(weight[k] for k in gain))); left -= set(gain)
print('greedy cover of the failures:', '; '.join(f'{a} +{g}' for a, g in order[:6]))
# unique rescues
uniq = collections.Counter()
for k in anyr:
    who = [a for a in algos if out[a].get(k) == 'right']
    if len(who) == 1: uniq[who[0]] += weight[k]
print('rescued by exactly one algorithm:', dict(uniq.most_common(8)))
# failure types of the reference
ty = collections.Counter()
import itertools
fr = {}
for r in csv.DictReader(open(f'{dirs[0]}/{ref}.csv', encoding='utf8')): fr[(r['site'], r['date'], r['element'])] = (r.get('locator_class'), r.get('element_class'), r.get('found_in_top10'))
for k in fail: ty[(fr[k][0], fr[k][1], 'in-top10' if fr[k][2] == 'true' else 'not-in-top10')] += weight[k]
print('reference failures by (locator change, element change, in top10):', dict(ty.most_common(8)))
