#!/usr/bin/env python3
"""Adds the MultiLocator repair variants that were missing (full-repair, selective-repair, VISTA+full-repair, VISTA+selective-repair) to the repo and to the dashboard data.
The hybrid-work jars still use the old names: MultiLocator+repair (= FULL repair) and MultiLocator+VISTA+repair (= VISTA+FULL repair) are written to the repo as
MultiLocator+full-repair and MultiLocator+VISTA+full-repair; selective names are unchanged. (In the repo MultiLocator+repair now means the paper variant, Algorithm 2.)
usage: add_repair_variants.py [stage ...]    stages: copy (dumps into results/), dash (dashboard results.csv rows). Default: both.
Wayback dumps come from hybrid-work/{dev,dev2}/out/svx/<lane>/dump merged and deduplicated by (site, date, element)."""
import csv, glob, os, sys, collections
S = '/Users/stocco/repos/tools/comparison-robust-locators-algorithms/abushakra/similo-thesis'; R = S + '/results'; H = '/Users/stocco/repos/tools/hybrid-work'
RENAME = {'MultiLocator+repair': 'MultiLocator+full-repair', 'MultiLocator+VISTA+repair': 'MultiLocator+VISTA+full-repair'}   # old jar name -> repo name
OLD = {'full': 'MultiLocator+repair', 'sel': 'MultiLocator+selective-repair', 'vfull': 'MultiLocator+VISTA+repair', 'vsel': 'MultiLocator+VISTA+selective-repair'}
def new(a): return RENAME.get(a, a)
def read(f):
    rows = list(csv.reader(open(f, encoding='utf8', errors='ignore')))
    return rows[0], rows[1:]
def merged(dirs, old):
    """rows of algorithm `old` from several dump dirs, deduplicated by (site, date, element), header renamed."""
    hdr, out = None, {}
    for d in dirs:
        f = f'{d}/{old}.csv'
        if not os.path.exists(f): continue
        h, rows = read(f); hdr = hdr or h
        for r in rows:
            if len(r) > 7: out[(r[1], r[2], r[3])] = [new(r[0])] + r[1:]
    return hdr, list(out.values())
def write(path, hdr, rows):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='') as fh: w = csv.writer(fh); w.writerow(hdr); w.writerows(rows)
# (benchmark label, source dirs (old-name dumps), repo dump dir, which configs, site filter)
WB = [f'{H}/dev/out/svx/{l}/dump' for l in 'ACF'] + [f'{H}/dev2/out/svx/{l}/dump' for l in 'BDEGHI']
TARGETS = [
    ('PHP-Fusion', [f'{H}/pf/out/dump'], f'{R}/phpfusion/dump', ['full', 'vfull', 'vsel'], None),
    ('Dolibarr', [f'{H}/dol/out/dump'], f'{R}/dolibarr/dump', ['full', 'vfull', 'vsel'], None),
    ('AddressBook', [f'{H}/abk/out/dump'], f'{R}/addressbook/dump', ['full', 'sel', 'vfull', 'vsel'], None),
    ('Excalidraw', [f'{H}/ob/out/dump_ex_f2a'], f'{R}/oracle_dumps/dump_ex_f2a', ['full', 'vfull', 'vsel'], None),
    ('Slickgrid', [f'{H}/ob/out/dump_asg_f2a'], f'{R}/oracle_dumps/dump_asg_f2a', ['full', 'vfull', 'vsel'], None),
    ('BEWT+ReproBreak', [f'{H}/similo-thesis/out/dump_extra_repair', f'{H}/similo-thesis/out/dump_extra_repair2'], f'{R}/bewt_reprobreak_gaps', ['full', 'sel', 'vfull', 'vsel'], None),
    ('Wayback', WB, f'{R}/wayback_first_to_all/dump_repair_variants', ['full', 'sel', 'vfull', 'vsel'], None)]
stages = sys.argv[1:] or ['copy', 'dash']
only = os.environ.get('ONLY')   # restrict to benchmarks, e.g. ONLY="PHP-Fusion,Dolibarr"
def targets(): return [t for t in TARGETS if not only or t[0] in only.split(',')]
if 'copy' in stages:
    for name, srcs, dst, cfgs, _ in targets():
        for c in cfgs:
            hdr, rows = merged(srcs, OLD[c])
            if not rows: print(f'{name}: {OLD[c]}: no rows'); continue
            write(f'{dst}/{new(OLD[c])}.csv', hdr, rows); print(f'{name}: {new(OLD[c])}: {len(rows)} pairs')
if 'dash' in stages:
    def family(a): return 'Multi-locator (voting)'
    res = f'{R}/dashboard_2026-11-20/results.csv'; old = list(csv.reader(open(res))); hdr, body = old[0], old[1:]
    excl = set(open('/Users/stocco/repos/tools/hybrid-work/dol/out/excluded_sites.txt').read().split())
    def stats(rows, flt=None):
        n = Rr = W = 0; seen = set()
        for r in rows:
            if flt and not flt(r[1]): continue
            k = (r[1], r[2], r[3])
            if k in seen: continue
            seen.add(k); n += 1; Rr += r[6] == 'right'; W += r[6] == 'wrong'
        return n, Rr, W
    def row(b, a, n, Rr, W, note=''): return [b, a, family(a), 'appendix', n, Rr, W, n - Rr - W, round(100 * Rr / n, 1), round(100 * (Rr + W) / n, 1), round(100 * Rr / (Rr + W), 1) if Rr + W else '', note]
    newrows = []
    for name, srcs, dst, cfgs, _ in targets():
        for c in cfgs:
            a = new(OLD[c]); f = f'{dst}/{a}.csv'
            if not os.path.exists(f): continue
            h, rows = read(f)
            if name == 'BEWT+ReproBreak':
                parts = [('BEWT', lambda s: s.startswith('bewt_')), ('ReproBreak', lambda s: s.startswith('rb_'))]
            elif name == 'Dolibarr': parts = [('Dolibarr', lambda s: s not in excl)]
            else: parts = [(name, None)]
            for b, flt in parts:
                n, Rr, W = stats(rows, flt)
                if n: newrows.append(row(b, a, n, Rr, W))
    keys = {(r[0], r[1]) for r in newrows}
    # keep pre-existing rows of the same (benchmark, algorithm) only if there is no new one; report disagreements
    for r in body:
        if (r[0], r[1]) in keys:
            nr = [x for x in newrows if (x[0], x[1]) == (r[0], r[1])][0]
            if r[5] != '' and (str(nr[5]) != r[5] or str(nr[4]) != r[4]): print('NOTE replaced', r[:8], '->', nr[:8])
    body = [r for r in body if (r[0], r[1]) not in keys] + newrows
    # expected pairs per benchmark for the partial note
    full = collections.defaultdict(int)
    for r in body:
        if r[4]: full[r[0]] = max(full[r[0]], int(r[4]))
    for r in body:
        if r[11] == '' and r[4] and int(r[4]) < 0.99 * full[r[0]] and (r[0], r[1]) in keys: r[11] = 'partial: %s of %d pairs' % (r[4], full[r[0]])
    csv.writer(open(res, 'w', newline='')).writerows([hdr] + body)
    print('dashboard rows added/updated:', len(newrows))
