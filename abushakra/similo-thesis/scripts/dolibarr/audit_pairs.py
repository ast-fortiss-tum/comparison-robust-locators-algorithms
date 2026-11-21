#!/usr/bin/env python3
"""Identity audit of all imported Dolibarr pairs after the manual review: rules derived from the 13 'different' verdicts.
skin_*: radio value must match; deleteLabel: icon title must match; module activation icons: module name (row text) must match.
usage: audit_pairs.py   -> out/audit_pairs.csv (suspects) and a per-step summary; also checks the rules against the reviewed pairs."""
import json, csv, re, sys, collections, os
from lxml import html as LH
P = '/Users/stocco/repos/tools/hybrid-work/dol'; S = P + '/sites'; D = '/Users/stocco/repos/tools/dolibarr-work'
plan = json.load(open(P + '/import/plan.json'))
def target(sid, date, xp):
    e = json.load(open(f'{S}/{sid}/{date}/elements.json')); t = [a for a in e if a['xpath']['chrome'] == xp]; return t[0] if t else None
def rowtext(dom, xp):
    try:
        t = LH.fromstring(open(dom, encoding='utf8', errors='ignore').read()); e = t.xpath('.' + xp[len('/html[1]'):]) if xp.startswith('/html[1]') else t.xpath(xp)
        if not e: return ''
        row = e[0]
        for a in e[0].iterancestors():
            row = a
            if a.tag in ('tr', 'li') and ' '.join(a.itertext()).strip(): break
        return ' '.join(' '.join(row.itertext()).split())
    except Exception: return ''
norm = lambda s: re.sub(r'[^a-z0-9]', '', (s or '').lower())
res, seen = [], set()
for e in plan:
    cmd = e['file'].split('/')[-1]; sid = f"dol_dolibarr_{e['id']}"
    kind = 'skin' if '.modifyDisplay.skin_' in cmd else 'delete' if 'deleteLabel' in cmd else 'module' if re.search(r'activate\w*Modules', cmd) else None
    if not kind: continue
    try: xo, xn = list(csv.reader(open(f'{S}/{sid}/xpaths.csv'), delimiter=';'))[1]
    except Exception: continue
    to, tn = target(sid, '2001-01-01', xo), target(sid, '2001-01-02', xn)
    if not (to and tn): continue
    ao, an = to.get('attributes', {}), tn.get('attributes', {}); bad = False; why = ''
    if kind == 'skin': bad = (ao.get('value') != an.get('value')); why = f"{ao.get('value')} -> {an.get('value')}"
    elif kind == 'delete': bad = norm(ao.get('title')) != norm(an.get('title')) and norm(ao.get('alt')) != norm(an.get('alt')); why = f"{ao.get('title')} -> {an.get('title')}"
    else:
        ro, rn = rowtext(f'{S}/{sid}/2001-01-01/chromeDOM.html', xo), rowtext(f'{S}/{sid}/2001-01-02/chromeDOM.html', xn)
        fo, fn = norm(ro.split(' ')[0]), norm(rn.split(' ')[0]); bad = not (fo and fn and (fo in norm(rn) or fn in norm(ro))); why = f"{ro[:40]} -> {rn[:40]}"
    res.append((e['id'], e['commit'], cmd, kind, 'SUSPECT' if bad else 'ok', why))
os.makedirs(D + '/out', exist_ok=True)
w = csv.writer(open(D + '/out/audit_pairs.csv', 'w')); w.writerow(['id', 'version', 'step', 'rule', 'status', 'detail']); w.writerows(res)
c = collections.Counter((r[3], r[4]) for r in res); print(dict(c), 'pairs checked', len(res))
byc = collections.defaultdict(lambda: [0, 0])
for r in res: byc[r[2]][r[4] == 'SUSPECT'] += 1
for k, (ok, bad) in sorted(byc.items(), key=lambda x: -x[1][1])[:25]: print(f'{k:60s} ok={ok:4d} suspect={bad:4d}')
V = {int(k): v['verdict'] for k, v in json.load(open('/tmp/dol_verdicts.json')).items()}
chk = collections.Counter((V[int(r[0])], r[4]) for r in res if int(r[0]) in V); print('vs your verdicts (verdict, rule):', dict(chk))
for r in res:
    i = int(r[0])
    if i in V and ((V[i] == 'diff') != (r[4] == 'SUSPECT')): print('DISAGREE', i, V[i], r[1:])
