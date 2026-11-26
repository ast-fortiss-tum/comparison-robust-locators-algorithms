#!/usr/bin/env python3
"""Index of the captured target element of every captured step: cap_index/<N>.json = {suite_test: {step_index: {...}}} (no element lists, small)."""
import gzip, json, os, glob, sys, re
from multiprocessing import Pool
W = '/Users/stocco/repos/tools/phpfusion-work'
GROUP = {'1': '1-InstallTestSuite', '2': '2-LoggedOut', '3': '3-LoggedIn', '4': '4-Admin'}
def one(n):
    out = {}
    for d in sorted(glob.glob(f'{W}/out_cap/{n}/*/steps.json')):
        dirn = d.split('/')[-2]; g, name = dirn.split('_', 1); test = f'{GROUP[g]}/{name}'
        L = json.load(open(d)); rec = {}
        for r in L:
            i = r['i']; sp = f'{os.path.dirname(d)}/snap_{i:02d}.json.gz'
            if not os.path.exists(sp): continue
            try: s = json.load(gzip.open(sp, 'rt', encoding='utf8'))
            except Exception: continue
            if not s.get('targets'): continue
            t = s['targets'][0]; e = t.get('element') or {}
            a = e.get('attributes') or {}
            rec[i] = dict(cmd=r['cmd'], locator=r['target'], value=r.get('value', ''), url=re.sub(r'https?://[^/]+', '', r.get('url', '')),
                          visible=bool(t.get('visible')), inSvg=bool(t.get('inSvg')), xpath=(e.get('xpath') or {}).get('chrome'), tag=e.get('tag'),
                          text=(e.get('innerText') or e.get('textContent') or '')[:120], attrs={k: a[k] for k in ('id', 'name', 'class', 'href', 'value', 'type', 'alt', 'title', 'src', 'onclick') if k in a},
                          ok=r['ok'], nels=len(s.get('elements', [])))
        out[test] = rec
    os.makedirs(f'{W}/cap_index', exist_ok=True); json.dump(out, open(f'{W}/cap_index/{n}.json', 'w'))
    return n, sum(len(v) for v in out.values())
if __name__ == '__main__':
    with Pool(6) as p:
        for n, c in p.imap_unordered(one, range(1, 42)): print(n, c, flush=True)
