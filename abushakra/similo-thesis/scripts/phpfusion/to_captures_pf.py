#!/usr/bin/env python3
"""PHP-Fusion pairs -> layout read by scrape.ImportReproBreak (like addrbook-work/to_captures.py).
Pairs: pairs_pf_dedup.json (id = index+1) minus the pairs judged 'diff' in review_pf/verdicts.json. nrel (weight) = 1 + len(dups) -> <dest>/weights.json.
usage: to_captures_pf.py <dest>"""
import gzip, json, os, shutil, sys
W = '/Users/stocco/repos/tools/phpfusion-work'; dest = sys.argv[1]
GROUP = {'1': '1-InstallTestSuite', '2': '2-LoggedOut', '3': '3-LoggedIn', '4': '4-Admin'}
pairs = json.load(open(f'{W}/pairs_pf_dedup.json')); rev = json.load(open(f'{W}/review_pf/pairs.json')); ver = json.load(open(f'{W}/review_pf/verdicts.json'))
key = lambda p: (p['n'], p['test'], p['step_old'], p['step_new'])
idx = {key(p): k + 1 for k, p in enumerate(pairs)}
drop = set()
for r in rev:
    if ver.get(str(r['id'])) == 'diff':
        drop.add(idx[(r['n'], r['t'], r['i'], r['j'])])
print('pairs', len(pairs), 'dropped (diff)', len(drop))
def tdir(n, test):
    g, name = test.split('/'); g = [k for k, v in GROUP.items() if v == g][0]; return f'{W}/out_cap/{n}/{g}_{name}'
def emit(n, test, i, side, pid):
    d = tdir(n, test); snap = json.load(gzip.open(f'{d}/snap_{i:02d}.json.gz', 'rt', encoding='utf8'))
    if not snap.get('targets'): return False
    tg = dict(snap['targets'][0]); tg['main'] = True
    rec = dict(url=f'http://pf.local/v{n}/{test}/{i}', viewport=snap['viewport'], scroll=[0, 0], dom=open(f'{d}/pre_{i:02d}.html', encoding='utf8', errors='replace').read(), elements=snap['elements'], targets=[tg], matches=1)
    base = f'{dest}/captures/phpfusion/{side}/v{n}'; os.makedirs(base, exist_ok=True)
    json.dump(rec, open(f'{base}/{pid}-1.json', 'w')); shutil.copy(f'{d}/clean_{i:02d}.png', f'{base}/{pid}-1.png'); return True
plan, weights, skipped = [], {}, 0
for k, p in enumerate(pairs):
    pid = k + 1
    if pid in drop: continue
    if emit(1, p['test'], p['step_old'], 'old', pid) and emit(p['n'], p['test'], p['step_new'], 'new', pid):
        plan.append(dict(id=pid, repo='pf/phpfusion', commit=p['new'], parent='v1', file=f"{p['test']}/{p['step']}", framework='selenium', old_locator=p['loc_old'], new_locator=p['loc_new']))
        weights[pid] = 1 + len(p['dups'])
    else: skipped += 1
os.makedirs(dest, exist_ok=True); json.dump(plan, open(f'{dest}/plan.json', 'w'), indent=1); json.dump(weights, open(f'{dest}/weights.json', 'w'))
print('plan entries', len(plan), 'skipped (no target snapshot)', skipped, 'weight total', sum(weights.values()))
