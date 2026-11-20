#!/usr/bin/env python3
"""Right / wrong / silent per algorithm and coverage-vs-precision comparison from per-pair dumps (DUMP_DIR csv files:
algorithm,site,date,element,locator_class,element_class,outcome,top_score,found_in_top10).

For algorithms with a real score (Similo family, COLOR, ...) the answers are ranked by top_score and only the top k are kept, which gives a
coverage/precision curve. Algorithms with a fixed behaviour (generators) are single points. The comparison "at equal coverage" takes, for every
fixed algorithm, the scored algorithm's top-k answers with k = that algorithm's number of answers.

usage: riskcoverage.py <label> <dump dir> [<dump dir> ...]      (dirs are merged; an algorithm needs >= MIN_ROWS rows to count)
Writes <label>_summary.csv and <label>_equal_coverage.csv next to the script's out/ directory (analysis/out/)."""
import csv, glob, os, sys, collections
MIN_ROWS = int(os.environ.get('MIN_ROWS', '0'))
label, dirs = sys.argv[1], sys.argv[2:]
data = collections.defaultdict(list)           # algorithm -> [(outcome, score)]
for d in dirs:
    for f in sorted(glob.glob(d + '/*.csv')):
        algo = os.path.basename(f)[:-4]
        for r in list(csv.reader(open(f, encoding='utf8', errors='ignore')))[1:]:
            if len(r) < 8: continue
            try: s = float(r[7])
            except ValueError: s = None
            data[algo].append((r[6], s))
if MIN_ROWS == 0: MIN_ROWS = int(max(len(v) for v in data.values()) * 0.9)     # drop partial dumps
data = {a: v for a, v in data.items() if len(v) >= MIN_ROWS}
def stats(v):
    n = len(v); R = sum(1 for o, _ in v if o == 'right'); W = sum(1 for o, _ in v if o == 'wrong')
    return n, R, W, n - R - W
def scored(v): return len({s for _, s in v if s is not None}) > 20
def topk(v, k):                                 # keep the k highest-scoring answers, everything else becomes silent
    ans = sorted([(s if s is not None else 0, o) for o, s in v if o in ('right', 'wrong')], reverse=True)[:k]
    R = sum(1 for _, o in ans if o == 'right'); return R, len(ans) - R
rows = []
print('\n== %s: %d algorithms with >= %d pairs' % (label, len(data), MIN_ROWS))
print('%-30s %6s %7s %6s %7s | %9s %9s %9s' % ('algorithm', 'pairs', 'right', 'wrong', 'silent', 'accuracy', 'coverage', 'precision'))
for a in sorted(data, key=lambda a: -stats(data[a])[1] / stats(data[a])[0]):
    n, R, W, S = stats(data[a]); cov = (R + W) / n; prec = R / (R + W) if R + W else float('nan')
    rows.append([a, n, R, W, S, round(R / n, 4), round(cov, 4), round(prec, 4), 'scored' if scored(data[a]) else 'fixed'])
    print('%-30s %6d %7d %6d %7d | %8.1f%% %8.1f%% %8.1f%%  %s' % (a, n, R, W, S, 100 * R / n, 100 * cov, 100 * prec, 'scored' if scored(data[a]) else ''))
os.makedirs(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'out'), exist_ok=True)
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'out')
csv.writer(open(f'{out}/{label}_summary.csv', 'w')).writerows([['algorithm', 'pairs', 'right', 'wrong', 'silent', 'accuracy', 'coverage', 'precision', 'kind']] + rows)
SC = [a for a in data if scored(data[a])]; FX = [a for a in data if not scored(data[a])]
eq = [['reference', 'ref_answers', 'ref_precision', 'ref_accuracy', 'scored_algorithm', 'precision_at_same_answers', 'accuracy_at_same_answers']]
print('\n-- scored algorithms limited to the same number of answers as each fixed algorithm (precision / accuracy):')
print('%-28s %9s %8s | ' % ('reference (fixed)', 'answers', 'prec') + ' | '.join('%-18s' % s[:18] for s in SC))
for g in sorted(FX, key=lambda a: -stats(data[a])[1]):
    n, R, W, S = stats(data[g]); k = R + W; line = '%-28s %9d %7.1f%% | ' % (g, k, 100 * R / k if k else 0); cells = []
    for s in SC:
        r2, w2 = topk(data[s], k); ok = r2 + w2 == k
        eq.append([g, k, round(R / k, 4) if k else '', round(R / n, 4), s, round(r2 / k, 4) if k and ok else '', round(r2 / n, 4) if ok else ''])
        cells.append('%-18s' % (('%.1f%% / %.1f%%' % (100 * r2 / k, 100 * r2 / n)) if k and ok else '-'))
    print(line + ' | '.join(cells))
csv.writer(open(f'{out}/{label}_equal_coverage.csv', 'w')).writerows(eq)
