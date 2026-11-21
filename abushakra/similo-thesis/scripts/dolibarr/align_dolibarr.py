#!/usr/bin/env python3
"""Ground truth for the Dolibarr chain: aligns the captures of two versions of the study's suites and writes them in the layout scrape.ImportReproBreak reads.

Every suite version passes the same 39 tests on its own release, so the same page-object statement (class.method, the field it touches, k-th touch of that field
in the test) selects the same logical element in both versions by construction. The field is read from the source line of the capture's po_frame in that
version's page objects (run/wd_vN), so steps added or removed by a repair do not shift the alignment.

usage: align_dolibarr.py <dest dir> <old version> <new version> [<new version> ...]     (versions as numbers: 1 29 ...; pairs are old -> each new)
Writes <dest>/plan.json (appending ids across calls through plan.json if it exists) and <dest>/captures/dolibarr/{old,new}/vN/<id>-1.{json,png}.
"""
import glob, json, os, re, shutil, sys
W = '/Users/stocco/repos/tools/dolibarr-work'
_src = {}
def src_line(ver, frame):
    f, _, ln = frame.rpartition(':')
    key = (ver, f)
    if key not in _src:
        p = f'{W}/run/wd_v{ver}/src/main/java/pageobjects/{f}'
        _src[key] = open(p, encoding='utf8', errors='ignore').read().splitlines() if os.path.exists(p) else []
    lines = _src[key]; i = int(ln) - 1
    return lines[i] if 0 <= i < len(lines) else ''
def var_of(line):
    for rx in (r'new\s+Select\((\w+)\)', r'(\w+)\.(?:click|sendKeys|clear|getText|submit|isDisplayed|isSelected|isEnabled|getAttribute|selectByVisibleText)\b', r'(\w+)\s*;'):
        m = re.search(rx, line)
        if m: return m.group(1)
    m = re.search(r'\b([A-Za-z_]\w*)\b', line); return m.group(1) if m else ''
def tkey(test):
    m = test.split('.')[-2].lower() if test.count('.') >= 2 else test.lower()
    return m
def mkey(po): return '.'.join(po.split('.')[-2:])
def load(ver):
    tests = {}
    for f in glob.glob(f'{W}/capture-out/v{ver}/s*/[0-9]*.json'):
        d = os.path.dirname(f); j = json.load(open(f)); j['_dir'] = d
        if j['test'] == 'unknown' or not j.get('po_method'): continue
        tests.setdefault(tkey(j['test']), []).append(j)
    for k in tests: tests[k].sort(key=lambda j: (j['time'], j['seq']))
    skip = f'{W}/capture-out/v{ver}/skip_tests.txt'      # tests of this version that did not pass (v30): their lookups are not used
    if os.path.exists(skip):
        for t in open(skip).read().split(): tests.pop(t.strip().lower(), None)
    return tests
def usable(j):
    els = j.get('page', {}).get('elements', []); t = j.get('targets')
    if not (isinstance(t, list) and t and isinstance(t[0], dict) and isinstance(t[0].get('element'), dict)): return False
    return len(els) == 1 and len(t) == 1 and t[0]['visible'] and not t[0]['inSvg'] and j.get('shot') and os.path.exists(os.path.join(j['_dir'], j['dom']))
def calls(caps, ver):
    """(class.method, field, k) -> capture; consecutive repeats of the same element on the same DOM are polling, not new touches."""
    out, last, cnt = {}, None, {}
    for j in caps:
        t = j.get('targets'); x = t[0]['element']['xpath']['chrome'] if isinstance(t, list) and t and isinstance(t[0], dict) and isinstance(t[0].get('element'), dict) else None
        v = var_of(src_line(ver, j['po_frame']))
        sig = (mkey(j['po_method']), v, x, j['dom'], j['_dir'])
        if sig == last: continue
        last = sig; base = (mkey(j['po_method']), v); k = cnt.get(base, 0); cnt[base] = k + 1
        out[base + (k,)] = j
    return out
def snap_of(j):
    n = re.search(r'dom_(\d+)', j['dom']).group(1); return json.load(open(os.path.join(j['_dir'], 'snap_%s.json' % n)))
def emit(j, dj, dp):
    s = snap_of(j)
    if not s['elements']: return None
    tg = dict(j['targets'][0]); tg['main'] = True
    dom = open(os.path.join(j['_dir'], j['dom']), encoding='utf-8', errors='replace').read()
    json.dump({'url': j['page']['url'], 'viewport': s['viewport'], 'scroll': s['scroll'], 'dom': dom, 'elements': s['elements'], 'targets': [tg], 'matches': 1}, open(dj, 'w'))
    shutil.copy(os.path.join(j['_dir'], j['shot']), dp); return tg['element']['xpath']['chrome']
def main():
    dest = sys.argv[1]; old = int(sys.argv[2]); news = [int(x) for x in sys.argv[3:]]
    pp = os.path.join(dest, 'plan.json'); plan = json.load(open(pp)) if os.path.exists(pp) else []; n = max([e['id'] for e in plan], default=0)
    t_old = load(old); co = {tk: calls(c, old) for tk, c in t_old.items()}; report = []
    for new in news:
        t_new = load(new); st = dict(aligned=0, unusable=0, dup=0, kept=0, same_xpath=0, changed_locator=0); seen = set()
        for tk in sorted(set(t_old) & set(t_new)):
            cn = calls(t_new[tk], new)
            for key in sorted(set(co[tk]) & set(cn)):
                a, b = co[tk][key], cn[key]; st['aligned'] += 1
                if not (usable(a) and usable(b)): st['unusable'] += 1; continue
                xa, xb = a['targets'][0]['element']['xpath']['chrome'], b['targets'][0]['element']['xpath']['chrome']
                ua, ub = re.sub(r'https?://[^/]+', '', a['page']['url']).split('?')[0], re.sub(r'https?://[^/]+', '', b['page']['url']).split('?')[0]
                k2 = (key[0], key[1], a['by'], b['by'], xa, xb, ua, ub)
                if k2 in seen: st['dup'] += 1; continue
                seen.add(k2); n += 1
                bo = os.path.join(dest, 'captures', 'dolibarr', 'old', f'v{old}'); bn = os.path.join(dest, 'captures', 'dolibarr', 'new', f'v{new}')
                os.makedirs(bo, exist_ok=True); os.makedirs(bn, exist_ok=True)
                if emit(a, os.path.join(bo, f'{n}-1.json'), os.path.join(bo, f'{n}-1.png')) is None or emit(b, os.path.join(bn, f'{n}-1.json'), os.path.join(bn, f'{n}-1.png')) is None:
                    n -= 1; st['unusable'] += 1; seen.discard(k2); continue
                st['kept'] += 1; st['same_xpath'] += (xa == xb); st['changed_locator'] += (a['by'] != b['by'])
                plan.append({'id': n, 'repo': 'dolibarr/dolibarr', 'commit': f'v{new}', 'parent': f'v{old}', 'file': tk + '/' + key[0] + '.' + key[1], 'framework': 'selenium', 'old_locator': a['by'], 'new_locator': b['by']})
        report.append(f'v{old}->v{new}: {st}')
    os.makedirs(dest, exist_ok=True); json.dump(plan, open(pp, 'w'), indent=1); print('\n'.join(report)); print('pairs in plan:', len(plan))
main()
