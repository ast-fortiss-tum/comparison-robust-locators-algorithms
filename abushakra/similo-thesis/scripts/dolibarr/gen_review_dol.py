#!/usr/bin/env python3
"""Validation page for the Dolibarr ground truth (same layout as the AddressBook pair review): pairs with the target outlined in red on both versions.
Groups: changed (the suite's locator differs), moved (same locator, other position), flagged (tag/text disagree), stable (random sample of unchanged pairs).
usage: gen_review_dol.py [max changed] [moved] [stable]       writes review/pair-review-dolibarr.html"""
import json, csv, os, re, random, sys, base64, io
from PIL import Image, ImageDraw
from lxml import html as LH
random.seed(11)
D = '/Users/stocco/repos/tools/dolibarr-work'; P = '/Users/stocco/repos/tools/hybrid-work/dol'; S = P + '/sites'
REL = dict(zip(range(1, 30), '3.0.0 3.0.1 3.1.0 3.1.1 3.1.2 3.2.0 3.1.3 3.2.1 3.2.2 3.2.3 3.3.0 3.3.1 3.3.2 3.3.3 3.4.0 3.4.1 3.4.2 3.5.0 3.5.1 3.5.2 3.5.3 3.5.4 3.6.0 3.6.2 3.6.3 3.7.0 3.6.4 3.7.1 3.8.0'.split())); REL[30] = '3.9.0-beta'
nch, nmv, nst = (int(x) for x in (sys.argv[1:4] if len(sys.argv) > 3 else (110, 30, 15)))
plan = json.load(open(P + '/import/plan.json'))
def target(sid, date, xp):
    e = json.load(open(f'{S}/{sid}/{date}/elements.json')); t = [a for a in e if a['xpath']['chrome'] == xp]
    return t[0] if t else None
def ctx(dom, xp):
    try:
        t = LH.fromstring(open(dom, encoding='utf8', errors='ignore').read()); e = t.xpath('.' + xp[len('/html[1]'):]) if xp.startswith('/html[1]') else t.xpath(xp)
        if not e: return ''
        e = e[0]; row = e
        for a in e.iterancestors():
            row = a
            if a.tag in ('tr', 'li', 'form', 'p', 'td') and ' '.join(a.itertext()).strip(): break
        return ' '.join(' '.join(row.itertext()).split())[:200]
    except Exception: return ''

def ctx2(dom, xp):
    """neighbouring rows (target marked) and the HTML of the target's row, for reviewers who need more context"""
    try:
        t = LH.fromstring(open(dom, encoding='utf8', errors='ignore').read()); e = t.xpath('.' + xp[len('/html[1]'):]) if xp.startswith('/html[1]') else t.xpath(xp)
        if not e: return [], ''
        e = e[0]; tx = lambda n: ' '.join(' '.join(n.itertext()).split())
        a = e
        for anc in [e] + list(e.iterancestors()):
            par = anc.getparent()
            if par is None: break
            sib = [s for s in par if isinstance(s.tag, str) and s.tag not in ('script', 'style') and tx(s)]
            if anc in sib and len(sib) >= 3: a = anc; break
            a = anc
        par = a.getparent(); sib = [s for s in par if isinstance(s.tag, str) and s.tag not in ('script', 'style') and (tx(s) or s is a)]
        i = sib.index(a); lo = max(0, i - 3); nb = [('▶ ' if s is a else '') + tx(s)[:110] for s in sib[lo:i + 4]]
        row = e
        for anc in e.iterancestors():
            row = anc
            if anc.tag in ('tr', 'li', 'form', 'p'): break
        h = LH.tostring(row, encoding='unicode')
        h = re.sub(r'\s+', ' ', h)[:900]
        return nb, h
    except Exception: return [], ''
seen, items = set(), []
for e in plan:
    sid = f"dol_dolibarr_{e['id']}"; xp = f'{S}/{sid}/xpaths.csv'
    if not os.path.exists(xp): continue
    xo, xn = list(csv.reader(open(xp), delimiter=';'))[1]
    to, tn = target(sid, '2001-01-01', xo), target(sid, '2001-01-02', xn)
    if not (to and tn): continue
    key = (e['file'].split('/')[-1], xo, xn, to['url'] if 'url' in to else '', tn['url'] if 'url' in tn else '')
    if key in seen: continue
    seen.add(key)
    fl = []
    if to['tag'] != tn['tag']: fl.append('tag %s -> %s' % (to['tag'], tn['tag']))
    if (to.get('innerText') or '').strip() != (tn.get('innerText') or '').strip() and (to.get('innerText') or '').strip() and (tn.get('innerText') or '').strip(): fl.append('text differs')
    g = 'changed' if e['old_locator'] != e['new_locator'] else ('flagged' if fl else ('moved' if xo != xn else 'stable'))
    items.append(dict(e=e, sid=sid, xo=xo, xn=xn, to=to, tn=tn, g=g, fl=fl))
G = {}
for it in items: G.setdefault(it['g'], []).append(it)
print({k: len(v) for k, v in G.items()}, 'distinct pairs', len(items))
pick = lambda L, n: random.sample(L, min(n, len(L)))
sel = pick(G.get('changed', []), nch) + G.get('flagged', [])[:40] + pick(G.get('moved', []), nmv) + pick(G.get('stable', []), nst)
RECHECK = [812, 1165, 3128, 3947, 4713]   # reviewer verdicts that disagree with the identity rule
_aud = list(csv.DictReader(open(D + '/out/audit_pairs.csv'))); _byid = {int(it['e']['id']): it for it in items}; _have = {int(it['e']['id']) for it in sel}
_rnd = random.Random(5); _extra = []
for st in sorted({r['step'] for r in _aud if r['status'] == 'SUSPECT'}):
    c = [int(r['id']) for r in _aud if r['status'] == 'SUSPECT' and r['step'] == st and int(r['id']) not in _have and int(r['id']) not in RECHECK and int(r['id']) in _byid]
    _extra += _rnd.sample(c, min(2, len(c)))
RECHECK_ALL = RECHECK + _extra
sel = [it for it in sel if int(it['e']['id']) not in RECHECK_ALL] + [dict(_byid[i], g='recheck') for i in RECHECK_ALL]
print('recheck group:', len(RECHECK_ALL), 'pairs (5 disagreements +', len(_extra), 'unreviewed rule suspects)')
def prep(sid, date, t, pid, side):
    im = Image.open(f'{S}/{sid}/{date}/screenshot.png').convert('RGB'); w, h = im.size
    x, y = t['location']; bw, bh = t['dimension']
    # adaptive window: fits the whole element plus context; small targets are cropped tight and enlarged so they are visible
    CW = int(min(w, max(640, min(1920, bw + 360)))); CH = int(min(h, max(300, min(760, bh + 240))))
    left = int(max(0, min(x + bw / 2 - CW / 2, w - CW))); top = int(max(0, min(y + bh / 2 - CH / 2, h - CH)))
    im = im.crop((left, top, left + CW, top + CH)); sc = max(1.0, 1000 / CW) if CW < 1000 else 1.0
    if sc > 1: im = im.resize((round(CW * sc), round(CH * sc)), Image.LANCZOS)
    ov = Image.new('RGBA', im.size, (0, 0, 0, 0)); d = ImageDraw.Draw(ov)
    x0, y0, x1, y1 = (x - left) * sc - 4, (y - top) * sc - 4, (x - left + bw) * sc + 4, (y - top + bh) * sc + 4
    d.rectangle([max(0, x0), max(0, y0), min(im.width - 1, x1), min(im.height - 1, y1)], fill=(255, 30, 60, 40), outline=(255, 30, 60, 255), width=3)
    im = Image.alpha_composite(im.convert('RGBA'), ov).convert('RGB')
    import os; os.makedirs(D + '/review/img', exist_ok=True); fn = f'img/{pid}-{side}.jpg'; im.save(D + '/review/' + fn, 'JPEG', quality=78, optimize=True)
    return fn
def prepW(sid, date, t, pid, side):
    im = Image.open(f'{S}/{sid}/{date}/screenshot.png').convert('RGB'); w, h = im.size; sc = min(1.0, 1920 / w)
    if sc < 1: im = im.resize((round(w * sc), round(h * sc)), Image.LANCZOS)
    x, y = t['location']; bw, bh = t['dimension']; ov = Image.new('RGBA', im.size, (0, 0, 0, 0)); d = ImageDraw.Draw(ov)
    d.rectangle([x * sc - 4, y * sc - 4, (x + bw) * sc + 4, (y + bh) * sc + 4], fill=(255, 30, 60, 40), outline=(255, 30, 60, 255), width=3)
    im = Image.alpha_composite(im.convert('RGBA'), ov).convert('RGB')
    import os; os.makedirs(D + '/review/w', exist_ok=True); im.save(D + f'/review/w/{pid}-{side}.jpg', 'JPEG', quality=68, optimize=True)
out = []
for it in sel:
    e, sid = it['e'], it['sid']; n = int(re.sub(r'\D', '', e['commit'])); to, tn = it['to'], it['tn']
    fmt = lambda t: dict(x=t['location'][0], y=t['location'][1], width=t['dimension'][0], height=t['dimension'][1])
    r = lambda t: (round(t['location'][0]), round(t['location'][1]), round(t['dimension'][0]), round(t['dimension'][1]))
    prepW(sid, '2001-01-01', to, e['id'], 'a'); prepW(sid, '2001-01-02', tn, e['id'], 'b')
    out.append(dict(id=e['id'], ia=prep(sid, '2001-01-01', to, e['id'], 'a'), ib=prep(sid, '2001-01-02', tn, e['id'], 'b'), g=it['g'], a='v1 (3.0.0)', b=f'v{n} ({REL[n]})', t=e['file'].split('/')[0], i=0, j=0, cmd=e['file'].split('/')[-1],
        lo=e['old_locator'], ln=e['new_locator'], xo=it['xo'], xn=it['xn'], eo=json.dumps(to.get('attributes', {}))[:600], en=json.dumps(tn.get('attributes', {}))[:600],
        to=(to.get('innerText') or '')[:120], tn=(tn.get('innerText') or '')[:120], ro=r(to), rn=r(tn), fl=it['fl'],
        co=ctx(f'{S}/{sid}/2001-01-01/chromeDOM.html', it['xo']), cn=ctx(f'{S}/{sid}/2001-01-02/chromeDOM.html', it['xn']),
        nbo=ctx2(f'{S}/{sid}/2001-01-01/chromeDOM.html', it['xo'])[0], nbn=ctx2(f'{S}/{sid}/2001-01-02/chromeDOM.html', it['xn'])[0], hto=ctx2(f'{S}/{sid}/2001-01-01/chromeDOM.html', it['xo'])[1], htn=ctx2(f'{S}/{sid}/2001-01-02/chromeDOM.html', it['xn'])[1]))
data = json.dumps(out, ensure_ascii=False).replace('<', '\\u003c').replace('\u2028', '\\u2028').replace('\u2029', '\\u2029').replace('\ufffd', '\\ufffd')
tpl = open('/private/tmp/claude-2062308220/-Users-stocco-repos-tools-comparison-robust-locators-algorithms/5f7cb56c-457a-4031-a55e-825754ed669a/scratchpad/review_tpl.html').read()
tpl = tpl.replace('<title>AddressBook Pair Review</title>', '<title>Dolibarr Pair Review</title>').replace('<h1>AddressBook pair review</h1>', '<h1>Dolibarr pair review</h1>').replace('abk-pair-review', 'dol-pair-review')
tpl = re.sub(r"const GROUPS=\[.*?\]\];", "const GROUPS=[['recheck','Please recheck','5 verdicts that disagree with the identity rule, plus unreviewed pairs the rule flags (to test the rule)'],['changed','Changed locator','The suite had to change the locator between versions'],['flagged','Flagged','Same locator, but tag or visible text disagree'],['moved','Moved','Same locator, element at another position'],['stable','Sample','Random unchanged pairs, to measure the error rate']];", tpl, flags=re.S)
tpl = tpl.replace("Each pair claims that one step selects the same element in release N and N+1.", "Each pair claims that one page-object statement selects the same element in v1 (3.0.0) and the later version.")
tpl = tpl.replace("grp='changed'", "grp='recheck'"); tpl = re.sub(r'const UNS=new Set\(\[.*?\]\)', 'const UNS=new Set(' + json.dumps([int(i) for i in RECHECK_ALL]) + ')', tpl)
open(D + '/review/pair-review-dolibarr.html', 'w').write(tpl.replace('/*DATA*/[]', data))
print(len(out), 'pairs;', len(data) // 1024, 'KB json')
