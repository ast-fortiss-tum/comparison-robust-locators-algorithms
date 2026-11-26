#!/usr/bin/env python3
"""Builds the data files of the "Locator Benchmark Results" dashboard from the saved results (per-pair dumps, BEWT and ReproBreak summaries, removal test data).
Writes results/dashboard_2026-11-20/{results,benchmarks,abstention,removal}.csv"""
import csv, glob, os, collections, shutil
S = '/Users/stocco/repos/tools/comparison-robust-locators-algorithms/abushakra/similo-thesis'; R = S + '/results'; H = '/Users/stocco/repos/tools/hybrid-work'
OUT = R + '/dashboard_2026-11-20'; os.makedirs(OUT, exist_ok=True)
MAIN = {'absXPath', 'SeleniumIDE', 'RobulaPlus', 'Montoto', 'SIDEREAL', 'EAGL', 'MultiLocator', 'MultiLocator+repair', 'MultiLocator+VISTA', 'MultiLocator+VISTA+repair', 'LegacySimilo', 'OptimizedSimilo', 'VISTA'}
def family(a):
    if a.startswith('MultiLocator'): return 'Multi-locator (voting)'
    if a in ('VISTA',): return 'Visual'
    if a.startswith(('SIDEREAL', 'EAGL')): return 'Locator generator (learned)'
    if a in ('absXPath', 'IDXPath', 'SeleniumIDE', 'RobulaPlus', 'Montoto'): return 'Locator generator'
    return 'Similarity (relocator)'
def dumps(*dirs):
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
BENCH = [('Wayback', dumps(H + '/dev/out/sv/A/dump', H + '/dev2/out/sv/B/dump')), ('Slickgrid', dumps(H + '/ob/out/dump_asg_f2a')),
         ('Excalidraw', dumps(H + '/ob/out/dump_ex_f2a')), ('AddressBook', dumps(H + '/abk/out/dump'))]
rows = []
for name, D in BENCH:
    full = max(len(v) for v in D.values())
    for a, v in sorted(D.items()):
        n = len(v); Rr = sum(o == 'right' for o, _ in v.values()); W = sum(o == 'wrong' for o, _ in v.values()); Sx = n - Rr - W
        note = '' if n >= 0.99 * full else 'partial: %d of %d pairs' % (n, full)
        rows.append([name, a, family(a), 'main' if a in MAIN else 'appendix', n, Rr, W, Sx, round(100 * Rr / n, 1), round(100 * (Rr + W) / n, 1), round(100 * Rr / (Rr + W), 1) if Rr + W else '', note])
for name, f in (('BEWT', R + '/bewt/summary.csv'), ('ReproBreak', R + '/reprobreak/summary.csv')):
    for r in list(csv.reader(open(f)))[1:]:
        if r[1] != 'ALL': continue
        a = r[0]; rows.append([name, a, family(a), 'main' if a in MAIN else 'appendix', int(r[3]), int(r[2]), '', '', float(r[4]), '', '', 'accuracy only (no per-pair dump)'])
with open(OUT + '/results.csv', 'w', newline='') as fh:
    w = csv.writer(fh); w.writerow(['benchmark', 'algorithm', 'family', 'tier', 'pairs', 'right', 'wrong', 'silent', 'accuracy', 'coverage', 'precision', 'note']); w.writerows(rows)
print('results.csv rows:', len(rows))
bm = [['Wayback', 'Wayback Machine snapshots of 36 popular sites, each snapshot paired with the first one (first-to-all); 30 tuning sites and 6 held-out sites', 'Manual labels from the Similo study', 36, 12266, 'complete'],
      ['BEWT', 'Four web apps (bludit, joomla, mantisbt, prestashop) in two versions each; the same page-object method at the same step of the same test identifies the element', 'Test structure (page-object method)', 4, 337, 'complete'],
      ['ReproBreak', 'Real Cypress and Playwright locator breaks captured before and after the developers fix; only 11 moved pairs are confirmed same-element relocations', 'Developer fixes (partly re-targeted)', 4, 350, 'validity caveat'],
      ['Excalidraw', 'Excalidraw releases with the hidden test id as identity oracle, first-to-all', 'Hidden test id (oracle)', 15, 529, 'complete'],
      ['Slickgrid', 'Angular-Slickgrid releases with the hidden test id as identity oracle, first-to-all', 'Hidden test id (oracle)', 23, 8276, 'complete'],
      ['AddressBook', 'PHP AddressBook, 75 releases with the study suites; 1,958 of 14,028 validated pairs given to the algorithms (all changed or human-validated pairs plus a stable sample)', 'Developers suites pass on each release; 24 pairs removed after human review', 74, 1958, 'complete; XPath-based algorithms rerun after a namespace fix']]
with open(OUT + '/benchmarks.csv', 'w', newline='') as fh:
    w = csv.writer(fh); w.writerow(['benchmark', 'description', 'labels_from', 'units', 'pairs', 'status']); w.writerows(bm)
# abstention: threshold chosen on Wayback tuning sites, applied unchanged elsewhere
wb = BENCH[0][1]
SETS = [('Wayback tuning', lambda k: not k[0].startswith('zz_'), wb), ('Wayback held-out', lambda k: k[0].startswith('zz_'), wb), ('AddressBook', lambda k: True, BENCH[3][1]),
        ('Slickgrid', lambda k: True, BENCH[1][1]), ('Excalidraw', lambda k: True, BENCH[2][1])]
def best_tau(D, a, sel, c):
    rr = sorted(((s, o) for k, (o, s) in D[a].items() if sel(k) and o in ('right', 'wrong')), reverse=True); best = (0.0, -1e9); run = 0.0
    for i, (s, o) in enumerate(rr):
        run += 1 if o == 'right' else -c
        if run > best[1] and (i + 1 == len(rr) or rr[i + 1][0] < s): best = (s, run)
    return best[0]
ab = []
for c in (1, 3, 10):
    taus = {a: best_tau(wb, a, SETS[0][1], c) for a in ('OptimizedSimilo', 'LegacySimilo')}
    cand = [('OptimizedSimilo', 'OptimizedSimilo', None), ('LegacySimilo', 'LegacySimilo', None), ('OptimizedSimilo + abstention', 'OptimizedSimilo', taus['OptimizedSimilo']), ('LegacySimilo + abstention', 'LegacySimilo', taus['LegacySimilo'])] + [(a, a, None) for a in ('RobulaPlus', 'SIDEREAL', 'SeleniumIDE', 'Montoto', 'absXPath', 'MultiLocator', 'MultiLocator+VISTA', 'VISTA')]
    for label, a, tau in cand:
        for sname, sel, D in SETS:
            if a not in D: continue
            n = Rr = W = 0
            for k, (o, s) in D[a].items():
                if not sel(k): continue
                n += 1
                if tau is not None and s < tau: continue
                Rr += o == 'right'; W += o == 'wrong'
            ab.append([c, sname, label, round((Rr - c * W) / n, 3), round(100 * Rr / n, 1), W, n])
with open(OUT + '/abstention.csv', 'w', newline='') as fh:
    w = csv.writer(fh); w.writerow(['cost', 'benchmark', 'algorithm', 'score', 'accuracy', 'wrong', 'pairs']); w.writerows(ab)
print('abstention.csv rows:', len(ab))
shutil.copy('/private/tmp/claude-2062308220/-Users-stocco-repos-tools-comparison-robust-locators-algorithms/d100bc27-a1a7-4803-9b69-31c838afc5b5/scratchpad/up6/removal.csv', OUT + '/removal.csv')
print('files:', sorted(os.listdir(OUT)))

# Post-processing of the accuracy-only rows (BEWT, ReproBreak): fill the right/wrong/silent split where it is known.
#  - Similo variants always answer, so wrong = pairs - right and silent = 0.
#  - BEWT: the removal test ran per pair for some algorithms (removal.csv); use its split where its right count equals the summary's.
rem = {r['algorithm']: r for r in csv.DictReader(open(OUT + '/removal.csv')) if r['benchmark'] == 'BEWT'}
res = list(csv.DictReader(open(OUT + '/results.csv'))); filled = 0
for r in res:
    if r['benchmark'] not in ('BEWT', 'ReproBreak') or r['wrong'] != '': continue
    P, Rr = int(r['pairs']), int(r['right'])
    if r['algorithm'] in ('LegacySimilo', 'OptimizedSimilo', 'VonSimilo', 'OptimizedVonSimilo'):
        r.update(wrong=P - Rr, silent=0, coverage='100.0', precision=r['accuracy'], note='Similo always answers: wrong = pairs minus right'); filled += 1
    elif r['benchmark'] == 'BEWT' and r['algorithm'] in rem and rem[r['algorithm']]['right'] == r['right'] and rem[r['algorithm']]['pairs'] == r['pairs']:
        m = rem[r['algorithm']]; W = int(m['wrong'])
        r.update(wrong=W, silent=m['silent'], coverage=round(100 * (Rr + W) / P, 1), precision=m['precision'], note=''); filled += 1
with open(OUT + '/results.csv', 'w', newline='') as fh:
    w = csv.DictWriter(fh, fieldnames=list(res[0].keys())); w.writeheader(); w.writerows(res)
print('accuracy-only rows given a split:', filled)

# BEWT and ReproBreak per-pair dumps of the configurations added on 2026-11-20 (EAGL on ReproBreak, paper-repair variants on both); they add rows or replace accuracy-only ones.
G = R + '/bewt_reprobreak_gaps'; res = list(csv.DictReader(open(OUT + '/results.csv'))); idx = {(r['benchmark'], r['algorithm']): r for r in res}
for f in sorted(glob.glob(G + '/*.csv')):
    a = os.path.basename(f)[:-4]; cnt = {'BEWT': collections.Counter(), 'ReproBreak': collections.Counter()}
    for r in list(csv.reader(open(f, encoding='utf8', errors='ignore')))[1:]:
        if len(r) > 7: cnt['BEWT' if r[1].startswith('bewt') else 'ReproBreak'][r[6]] += 1
    for b, c in cnt.items():
        n = sum(c.values())
        if not n: continue
        Rr, W = c['right'], c['wrong']; row = dict(benchmark=b, algorithm=a, family=family(a), tier='main' if a in MAIN else 'appendix', pairs=n, right=Rr, wrong=W, silent=n - Rr - W,
            accuracy=round(100 * Rr / n, 1), coverage=round(100 * (Rr + W) / n, 1), precision=round(100 * Rr / (Rr + W), 1) if Rr + W else '', note='')
        if (b, a) in idx: idx[(b, a)].update(row)
        else: res.append(row); idx[(b, a)] = row
with open(OUT + '/results.csv', 'w', newline='') as fh:
    w = csv.DictWriter(fh, fieldnames=list(res[0].keys())); w.writeheader(); w.writerows(res)
print('results.csv rows after BEWT/ReproBreak additions:', len(res))
