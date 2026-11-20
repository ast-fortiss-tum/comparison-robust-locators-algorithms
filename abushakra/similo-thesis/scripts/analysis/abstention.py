#!/usr/bin/env python3
"""Similo with an abstention threshold, compared with fixed algorithms under a cost where a wrong answer costs c and a silent one costs 0.
The threshold is chosen on the Wayback TUNING sites only (maximising right - c*wrong) and then applied unchanged to Wayback held-out, AddressBook, Slickgrid, Excalidraw.
usage: abstention.py   (reads the per-pair dumps under hybrid-work; writes results/riskcoverage/abstention_summary.txt)"""
import csv, glob, os, collections
H = '/Users/stocco/repos/tools/hybrid-work'
def load(*dirs):
    D = collections.defaultdict(dict)
    for d in dirs:
        for f in glob.glob(d + '/*.csv'):
            a = os.path.basename(f)[:-4]
            for r in list(csv.reader(open(f, encoding='utf8', errors='ignore')))[1:]:
                if len(r) > 7:
                    try: s = float(r[7])
                    except ValueError: s = 0.0
                    D[a][(r[1], r[2], r[3])] = (r[6], s)
    return D
wb = load(H + '/dev/out/sv/A/dump', H + '/dev2/out/sv/B/dump')
BEN = {'Wayback tuning (30 sites)': (wb, lambda k: not k[0].startswith('zz_')), 'Wayback held-out (6 sites)': (wb, lambda k: k[0].startswith('zz_')),
       'AddressBook (1,958 pairs)': (load(H + '/abk/out/dump'), lambda k: True), 'Slickgrid (23 sites)': (load(H + '/ob/out/dump_asg_f2a'), lambda k: True),
       'Excalidraw (15 sites)': (load(H + '/ob/out/dump_ex_f2a'), lambda k: True)}
FIXED = ['RobulaPlus', 'SIDEREAL', 'SeleniumIDE', 'Montoto', 'absXPath', 'MultiLocator', 'MultiLocator+VISTA', 'VISTA']
SIM = ['OptimizedSimilo', 'LegacySimilo']
def stats(D, a, sel, tau=None):
    n = R = W = 0
    for k, (o, s) in D[a].items():
        if not sel(k): continue
        n += 1
        if tau is not None and s < tau: continue
        R += o == 'right'; W += o == 'wrong'
    return n, R, W
def best_tau(D, a, sel, c):
    rows = sorted(((s, o) for k, (o, s) in D[a].items() if sel(k) and o in ('right', 'wrong')), reverse=True)
    best = (0.0, -1e9, 0); run = 0.0
    for i, (s, o) in enumerate(rows):
        run += 1 if o == 'right' else -c
        if run > best[1] and (i + 1 == len(rows) or rows[i + 1][0] < s): best = (s, run, i + 1)
    return best[0]
out = []
def p(*a): s = ' '.join(str(x) for x in a); print(s); out.append(s)
for c in (1, 3, 10):
    p('\n==== cost of a wrong answer = %d silent answers (score = (right - %d x wrong) / pairs; higher is better) ====' % (c, c))
    taus = {a: best_tau(BEN['Wayback tuning (30 sites)'][0], a, BEN['Wayback tuning (30 sites)'][1], c) for a in SIM}
    p('threshold chosen on Wayback tuning: ' + ', '.join('%s: top_score >= %.3f' % (a, t) for a, t in taus.items()))
    p('%-34s' % 'algorithm' + ''.join('%-27s' % b[:26] for b in BEN))
    rows = [(a + ' (no abstention)', a, None) for a in SIM] + [(a + ' + threshold', a, taus[a]) for a in SIM] + [(a, a, None) for a in FIXED]
    for label, a, tau in rows:
        line = '%-34s' % label
        for b, (D, sel) in BEN.items():
            if a not in D: line += '%-27s' % '-'; continue
            n, R, W = stats(D, a, sel, tau)
            line += '%-27s' % ('%+.3f  (%.0f%%/%dw)' % ((R - c * W) / n, 100 * R / n, W))
        p(line)
    p('(cell: score, then accuracy %/number of wrong answers)')
p('\n==== Similo risk-coverage: top_score threshold -> coverage, precision, wrong answers ====')
for a in SIM[:1]:
    for b, (D, sel) in BEN.items():
        rows = sorted(((s, o) for k, (o, s) in D[a].items() if sel(k)), reverse=True); n = len(rows); line = '%-28s' % b[:27]
        for cov in (1.0, 0.99, 0.97, 0.95, 0.90, 0.80):
            m = max(1, int(round(cov * n))); top = rows[:m]; R = sum(o == 'right' for _, o in top); line += '  %3.0f%%: %.1f%%/%dw' % (cov * 100, 100 * R / m, m - R)
        p(line)
d = '/Users/stocco/repos/tools/comparison-robust-locators-algorithms/abushakra/similo-thesis/results/riskcoverage'
open(d + '/abstention_summary.txt', 'w').write('\n'.join(out) + '\n')
