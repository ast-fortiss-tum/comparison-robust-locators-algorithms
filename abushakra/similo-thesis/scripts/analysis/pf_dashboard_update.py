#!/usr/bin/env python3
"""Adds/refreshes the PHP-Fusion rows in results/dashboard_2026-11-20/results.csv (+ benchmarks.csv) from the clean-rerun dumps.
Only algorithms with a ' complete' line in algos.progress are used (the running one has a partial dump); no pairs excluded (20 pairs judged different in review were dropped before import)."""
import csv, os, re, sys, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
S = '/Users/stocco/repos/tools/comparison-robust-locators-algorithms/abushakra/similo-thesis'; OUT = S + '/results/dashboard_2026-11-20'
P = '/Users/stocco/repos/tools/hybrid-work/pf/out'
MAIN = {'absXPath', 'SeleniumIDE', 'RobulaPlus', 'Montoto', 'SIDEREAL', 'EAGL', 'MultiLocator', 'MultiLocator+paper-repair', 'MultiLocator+VISTA', 'MultiLocator+VISTA+paper-repair', 'LegacySimilo', 'OptimizedSimilo', 'VISTA'}
def family(a):
    if a.startswith('MultiLocator'): return 'Multi-locator (voting)'
    if a == 'VISTA': return 'Visual'
    if a.startswith(('SIDEREAL', 'EAGL')): return 'Locator generator (learned)'
    if a in ('absXPath', 'IDXPath', 'SeleniumIDE', 'RobulaPlus', 'Montoto'): return 'Locator generator'
    return 'Similarity (relocator)'
done = [m.group(1) for l in open(P + '/algos.progress') for m in [re.search(r' (\S+) complete$', l.strip())] if m]
excl = set()
rows = []
for a in done:
    f = f'{P}/dump/{a}.csv'
    if not os.path.exists(f): continue
    n = Rr = W = 0; seen = set()
    for r in list(csv.reader(open(f, encoding='utf8', errors='ignore')))[1:]:
        if len(r) < 8 or r[1] in excl or (r[1], r[2], r[3]) in seen: continue
        seen.add((r[1], r[2], r[3])); n += 1; Rr += r[6] == 'right'; W += r[6] == 'wrong'
    if n: rows.append(['PHP-Fusion', a, family(a), 'main' if a in MAIN else 'appendix', n, Rr, W, n - Rr - W, round(100 * Rr / n, 1), round(100 * (Rr + W) / n, 1), round(100 * Rr / (Rr + W), 1) if Rr + W else '', ''])
old = list(csv.reader(open(OUT + '/results.csv'))); hdr, body = old[0], [r for r in old[1:] if r[0] != 'PHP-Fusion']
csv.writer(open(OUT + '/results.csv', 'w', newline='')).writerows([hdr] + body + rows)
bm = list(csv.reader(open(OUT + '/benchmarks.csv'))); bh, bb = bm[0], [r for r in bm[1:] if r[0] != 'PHP-Fusion']
npairs = rows[0][4] if rows else 0
bb.append(['PHP-Fusion', 'PHP-Fusion CMS, releases 6.00.207 to 7.02.00 (41 releases), first-to-all v1 to v2..v41; the study per-version Selenese suites were repaired so that every release passes (28 tests); pairs deduplicated by identical new state', 'Test structure (same suite step, traced through the repair logs, suite passes on each release); 340 pairs reviewed by hand (20 judged different and removed)', 40, npairs, 'running' if len(done) < 11 else 'complete'])
csv.writer(open(OUT + '/benchmarks.csv', 'w', newline='')).writerows([bh] + bb)
print('Dolibarr rows:', len(rows), 'algorithms:', ' '.join(done))
for r in rows: print('  %-30s %5d  right %5d wrong %4d silent %5d  acc %5.1f%% cov %5.1f%% prec %s%%' % (r[1], r[4], r[5], r[6], r[7], r[8], r[9], r[10]))
