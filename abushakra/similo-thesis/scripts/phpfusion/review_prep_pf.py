#!/usr/bin/env python3
"""Validation set for the PHP-Fusion ground truth: every flagged pair of pairs_pf_dedup.json (identity-risk rules) + a random sample of unflagged pairs.
Writes review_pf/pairs.json (records for the review page) and review_pf/img/<id>.jpg (old | new screenshot side by side, target outlined in red)."""
import json, os, random, collections, re
from PIL import Image, ImageDraw
W = '/Users/stocco/repos/tools/phpfusion-work'; O = W + '/review_pf'; os.makedirs(O + '/img', exist_ok=True)
rel = {int(v[1:]): r for v, r in (l.split() for l in open(W + '/rels.txt'))}
P = json.load(open(W + '/pairs_pf_dedup.json'))
GROUP = {'1': '1-InstallTestSuite', '2': '2-LoggedOut', '3': '3-LoggedIn', '4': '4-Admin'}; REV = {v: k for k, v in GROUP.items()}
def dirname(test): g, n = test.split('/'); return f'{REV[g]}_{n}'
def span(ns):
    ns = sorted(set(ns)); out = []; s = p = ns[0]
    for n in ns[1:] + [None]:
        if n is not None and n == p + 1: p = n; continue
        out.append(f'v{s}' if s == p else f'v{s}–v{p}');
        if n is not None: s = p = n
    return ', '.join(out)
def group(p):
    r = p['risk']
    if 'tag' in r: return 'tag'
    if 'text' in r: return 'text'
    if any(x.startswith('attr') for x in r): return 'attr'
    if 'locator' in r: return 'locator'
    return 'sample'
random.seed(11)
for p in P: p['g'] = group(p)
flagged = [p for p in P if p['g'] != 'sample']; stable = [p for p in P if p['g'] == 'sample']
by = collections.defaultdict(list)
for p in stable: by[p['n']].append(p)
samp = []
for n in sorted(by): samp += random.sample(by[n], min(len(by[n]), max(2, round(90 * len(by[n]) / len(stable)))))
random.shuffle(samp); samp = samp[:90]
sel = flagged + samp; sel.sort(key=lambda p: (p['n'], p['test'], p['step'])); print('flagged', len(flagged), 'sample', len(samp), 'total', len(sel))
def shot(n, test, i):
    f = f"{W}/out_cap/{n}/{dirname(test)}/shot_{i:02d}.png"
    return Image.open(f).convert('RGB') if os.path.exists(f) else None
recs = []
for k, p in enumerate(sel, 1):
    a, b = shot(1, p['test'], p['step_old']), shot(p['n'], p['test'], p['step_new'])
    if a is None or b is None: continue
    w = 640; a = a.resize((w, round(a.height * w / a.width))); b = b.resize((w, round(b.height * w / b.width)))
    h = max(a.height, b.height) + 22; c = Image.new('RGB', (2 * w + 6, h), (240, 240, 240)); d = ImageDraw.Draw(c)
    c.paste(a, (0, 22)); c.paste(b, (w + 6, 22)); d.text((6, 5), f"OLD  v1 ({rel[1]})", fill=(0, 0, 0)); d.text((w + 12, 5), f"NEW  v{p['n']} ({rel[p['n']]})", fill=(0, 0, 0))
    c.save(f'{O}/img/{k}.jpg', quality=68)
    recs.append(dict(id=k, img=f'img/{k}.jpg', g=p['g'], a=f"v1 ({rel[1]})", b=f"v{p['n']} ({rel[p['n']]})", n=p['n'], t=p['test'], i=p['step_old'], j=p['step_new'], cmd=p['cmd'],
                     lo=p['loc_old'], ln=p['loc_new'], xo=p['xp_old'], xn=p['xp_new'], ao=p['attrs_old'], an=p['attrs_new'], to=p['text_old'], tn=p['text_new'],
                     tgo=p['tag_old'], tgn=p['tag_new'], fl=p['risk'], rel=span([p['n']] + p['dups']), nrel=1 + len(p['dups'])))
json.dump(recs, open(O + '/pairs.json', 'w'))
print('records', len(recs), collections.Counter(r['g'] for r in recs)); os.system(f'du -sh {O}/img')
