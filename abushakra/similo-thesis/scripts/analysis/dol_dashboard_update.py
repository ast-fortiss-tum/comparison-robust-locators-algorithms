#!/usr/bin/env python3
"""Adds/refreshes the Dolibarr rows in results/dashboard_2026-11-20/results.csv (+ benchmarks.csv) from the clean-rerun dumps.
Only algorithms with a ' complete' line in algos.progress are used (the running one has a partial dump); the 178 excluded pairs are left out."""
import csv, os, re, sys, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
S = '/Users/stocco/repos/tools/comparison-robust-locators-algorithms/abushakra/similo-thesis'; OUT = S + '/results/dashboard_2026-11-20'
P = '/Users/stocco/repos/tools/hybrid-work/dol/out'
MAIN = {'absXPath', 'SeleniumIDE', 'RobulaPlus', 'Montoto', 'SIDEREAL', 'EAGL', 'MultiLocator', 'MultiLocator+repair', 'MultiLocator+VISTA', 'MultiLocator+VISTA+repair', 'LegacySimilo', 'OptimizedSimilo', 'VISTA'}
def family(a):
    if a.startswith('MultiLocator'): return 'Multi-locator (voting)'
    if a == 'VISTA': return 'Visual'
    if a.startswith(('SIDEREAL', 'EAGL')): return 'Locator generator (learned)'
    if a in ('absXPath', 'IDXPath', 'SeleniumIDE', 'RobulaPlus', 'Montoto'): return 'Locator generator'
    return 'Similarity (relocator)'
done = [m.group(1) for l in open(P + '/algos.progress') for m in [re.search(r' (\S+) complete$', l.strip())] if m]
excl = set(open(P + '/excluded_sites.txt').read().split())
rows = []
for a in done:
    f = f'{P}/dump/{a}.csv'
    if not os.path.exists(f): continue
    n = Rr = W = 0; seen = set()
    for r in list(csv.reader(open(f, encoding='utf8', errors='ignore')))[1:]:
        if len(r) < 8 or r[1] in excl or (r[1], r[2], r[3]) in seen: continue
        seen.add((r[1], r[2], r[3])); n += 1; Rr += r[6] == 'right'; W += r[6] == 'wrong'
    if n: rows.append(['Dolibarr', a, family(a), 'main' if a in MAIN else 'appendix', n, Rr, W, n - Rr - W, round(100 * Rr / n, 1), round(100 * (Rr + W) / n, 1), round(100 * Rr / (Rr + W), 1) if Rr + W else '', ''])
old = list(csv.reader(open(OUT + '/results.csv'))); hdr, body = old[0], [r for r in old[1:] if r[0] != 'Dolibarr']
csv.writer(open(OUT + '/results.csv', 'w', newline='')).writerows([hdr] + body + rows)
bm = list(csv.reader(open(OUT + '/benchmarks.csv'))); bh, bb = bm[0], [r for r in bm[1:] if r[0] != 'Dolibarr']
npairs = rows[0][4] if rows else 0
bb.append(['Dolibarr', 'Dolibarr ERP/CRM, releases 3.0.0 to 3.9.0-beta (30 builds); the study WebDriver suite was evolved release by release so that every version passes; 178 pairs removed after manual review', 'Test structure (same page-object statement at the same step, suite passes on each release); 211 pairs reviewed by hand', 29, npairs, 'running' if len(done) < 11 else 'complete'])
csv.writer(open(OUT + '/benchmarks.csv', 'w', newline='')).writerows([bh] + bb)
print('Dolibarr rows:', len(rows), 'algorithms:', ' '.join(done))
for r in rows: print('  %-30s %5d  right %5d wrong %4d silent %5d  acc %5.1f%% cov %5.1f%% prec %s%%' % (r[1], r[4], r[5], r[6], r[7], r[8], r[9], r[10]))
