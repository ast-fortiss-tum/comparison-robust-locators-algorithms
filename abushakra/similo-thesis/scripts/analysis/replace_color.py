#!/usr/bin/env python3
"""Replaces the old COLOR results (the paper's attributes inside Similo's scoring) by the paper-faithful COLOR (weighted average over the common properties, Kirinuki et al. 2019):
dump files COLOR-paper.csv become COLOR.csv (algorithm column renamed) in every results dump dir, the operable-candidates variant is dropped, and the COLOR rows of the dashboard results.csv are recomputed."""
import csv, os
S = '/Users/stocco/repos/tools/comparison-robust-locators-algorithms/abushakra/similo-thesis'; R = S + '/results'
DIRS = [f'{R}/phpfusion/dump', f'{R}/dolibarr/dump', f'{R}/addressbook/dump', f'{R}/oracle_dumps/dump_ex_f2a', f'{R}/oracle_dumps/dump_asg_f2a', f'{R}/bewt_reprobreak_gaps',
        (f'{R}/wayback_first_to_all/dump_laneA', f'{R}/wayback_first_to_all/dump_laneP')]
for d in DIRS:
    dst, src = (d if isinstance(d, str) else d[0]), (d if isinstance(d, str) else d[1])
    rows = list(csv.reader(open(f'{src}/COLOR-paper.csv', encoding='utf8')))
    for r in rows[1:]: r[0] = 'COLOR'
    with open(f'{dst}/COLOR.csv', 'w', newline='', encoding='utf8') as fh: csv.writer(fh).writerows(rows)
    print(dst, len(rows) - 1, 'pairs')
    for f in (f'{src}/COLOR-paper.csv', f'{dst}/COLOR-paper-operable.csv', f'{src}/COLOR-paper-operable.csv'):
        if os.path.exists(f): os.remove(f)
for dd in (f'{R}/wayback_first_to_all/dump_laneP', f'{R}/wayback_first_to_all/dump_laneQ'):
    for f in os.listdir(dd): os.remove(f'{dd}/{f}')
    os.rmdir(dd)
# dashboard rows
excl = set(open('/Users/stocco/repos/tools/hybrid-work/dol/out/excluded_sites.txt').read().split())
BM = {'Wayback': (f'{R}/wayback_first_to_all/dump_laneA', None, set()), 'Slickgrid': (f'{R}/oracle_dumps/dump_asg_f2a', None, set()), 'Excalidraw': (f'{R}/oracle_dumps/dump_ex_f2a', None, set()),
      'AddressBook': (f'{R}/addressbook/dump', None, set()), 'BEWT': (f'{R}/bewt_reprobreak_gaps', 'bewt_', set()), 'ReproBreak': (f'{R}/bewt_reprobreak_gaps', 'rb_', set()),
      'Dolibarr': (f'{R}/dolibarr/dump', None, excl), 'PHP-Fusion': (f'{R}/phpfusion/dump', None, set())}
NOTE = 'Kirinuki et al. 2019: weighted average over the properties both elements have (the earlier row used the same attributes inside Similo scoring; replaced 2026-11-27)'
res = f'{R}/dashboard_2026-11-20/results.csv'; old = list(csv.reader(open(res))); hdr = old[0]; out = [hdr]
for r in old[1:]:
    if r[1] != 'COLOR' or r[0] not in BM: out.append(r); continue
    d, pre, ex = BM[r[0]]; n = Rr = W = 0; seen = set()
    for x in csv.reader(open(f'{d}/COLOR.csv', encoding='utf8')):
        if x[0] != 'COLOR' or len(x) < 8 or x[1] in ex or (pre and not x[1].startswith(pre)) or (x[1], x[2], x[3]) in seen: continue
        seen.add((x[1], x[2], x[3])); n += 1; Rr += x[6] == 'right'; W += x[6] == 'wrong'
    new = [r[0], 'COLOR', r[2], r[3], n, Rr, W, n - Rr - W, round(100 * Rr / n, 1), round(100 * (Rr + W) / n, 1), round(100 * Rr / (Rr + W), 1) if Rr + W else '', NOTE]
    print(f'{r[0]:12s} old: {r[4]} pairs acc {r[8]}  ->  new: {n} pairs acc {new[8]} (right {Rr}, wrong {W}, silent {n-Rr-W})'); out.append(new)
csv.writer(open(res, 'w', newline='')).writerows(out)
