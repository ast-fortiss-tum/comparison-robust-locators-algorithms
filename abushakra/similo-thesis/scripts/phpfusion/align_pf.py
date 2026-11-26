#!/usr/bin/env python3
"""First-to-all alignment for PHP-Fusion: v1 -> vN (N=2..41). Pair = same lineage step (lineage_pf.py) captured in both releases with a visible target.
Writes pairs_pf.json (list of dicts) and prints the statistics."""
import json, collections, sys
from lineage_pf import build, CHAIN
W = '/Users/stocco/repos/tools/phpfusion-work'
def base(n): return 'v1' if n <= 6 else 'v7' if n <= 17 else 'v18' if n <= 25 else 'v26' if n <= 31 else 'v32' if n == 32 else 'v33' if n == 33 else 'v34' if n <= 40 else 'v41'
lin, _ = build()
idx = {n: json.load(open(f'{W}/cap_index/{n}.json')) for n in range(1, 42)}
def by_id(n):
    b = base(n); out = {}
    for t, rec in idx[n].items():
        ids = lin[b].get(t, [])
        for i, r in rec.items():
            i = int(i)
            if i < len(ids): out[(t, ids[i])] = (i, r)
    return out
old = by_id(1); pairs = []; st = collections.Counter()
usable = lambda r: r['visible'] and not r['inSvg'] and r['xpath']
for n in range(2, 42):
    new = by_id(n)
    for key in sorted(set(old) & set(new)):
        (io, a), (jn, b) = old[key], new[key]; st['aligned'] += 1
        if not (usable(a) and usable(b)): st['unusable'] += 1; continue
        pairs.append(dict(old='v1', new=f'v{n}', n=n, test=key[0], step=key[1], step_old=io, step_new=jn, loc_old=a['locator'], loc_new=b['locator'], xp_old=a['xpath'], xp_new=b['xpath'],
                          tag_old=a['tag'], tag_new=b['tag'], text_old=a['text'], text_new=b['text'], attrs_old=a['attrs'], attrs_new=b['attrs'], url_old=a['url'], url_new=b['url'], cmd=a['cmd']))
json.dump(pairs, open(f'{W}/pairs_pf.json', 'w'))
c = collections.Counter()
for p in pairs:
    c['pairs'] += 1; c['locator_changed'] += p['loc_old'] != p['loc_new']; c['xpath_changed'] += p['xp_old'] != p['xp_new']; c['tag_changed'] += p['tag_old'] != p['tag_new']
    c['text_changed'] += p['text_old'] != p['text_new']; c['attrs_changed'] += p['attrs_old'] != p['attrs_new']; c['url_changed'] += p['url_old'] != p['url_new']
print(dict(st), dict(c))
per = collections.Counter(); chg = collections.Counter()
for p in pairs: per[p['n']] += 1; chg[p['n']] += (p['loc_old'] != p['loc_new'] or p['xp_old'] != p['xp_new'])
print('per release (pairs, locator-or-xpath changed):', ' '.join(f"v{n}:{per[n]}/{chg[n]}" for n in sorted(per)))
