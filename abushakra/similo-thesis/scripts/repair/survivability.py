#!/usr/bin/env python3
"""Survivability (first-to-all) from per-element dumps written by Main with PAIRING=firstToAll DUMP_DIR=...

For every element (site, element xpath at the anchor version) the dump has one row per later version in which the element
still exists, ordered by date. Per element:
  accuracy_ratio   = right versions / existing versions                (counts every version, as the thesis' survival ratio)
  survival_ratio   = leading run of right versions / existing versions (stops counting at the first failure)
Reported per algorithm: mean of both ratios over elements with >= 2 versions, element-level and pair-level accuracy, and
survival at version k (share of the elements that have at least k later versions and are still found in all of the first k).
Usage: survivability.py out.csv dumpdir [dumpdir ...]   (algorithm csv files are merged across dirs)
"""
import csv, sys, os, collections, glob

def load(dirs):
    rows = collections.defaultdict(list)
    for d in dirs:
        for f in glob.glob(os.path.join(d, '*.csv')):
            with open(f, newline='') as fh:
                for r in csv.DictReader(fh):
                    if r['site'].startswith(('bewt_', 'rb_')): continue   # Wayback sites only
                    rows[r['algorithm']].append(r)
    return rows

def summarize(rs):
    by = collections.defaultdict(list)
    seen = set()
    for r in rs:
        key = (r['site'], r['element'], r['date'])
        if key in seen: continue   # a duplicated run of the same site
        seen.add(key)
        by[(r['site'], r['element'])].append(r)
    acc, surv, curve, longer = [], [], collections.Counter(), collections.Counter()
    pairs = right = 0
    elems = 0
    maxv = 0
    for tl in by.values():
        tl.sort(key=lambda r: r['date'])
        n = len(tl)
        pairs += n; right += sum(r['outcome'] == 'right' for r in tl)
        if n < 2: continue
        run = 0
        for r in tl:
            if r['outcome'] == 'right': run += 1
            else: break
        acc.append(sum(r['outcome'] == 'right' for r in tl) / n)
        surv.append(run / n)
        elems += 1
        for k in range(1, run + 1): curve[k] += 1
        for k in range(1, n + 1): longer[k] += 1   # elements that have at least k later versions
        maxv = max(maxv, n)
    m = lambda v: 100 * sum(v) / len(v) if v else 0.0
    return dict(elements=elems, pairs=pairs, right=right, pair_accuracy=100 * right / pairs if pairs else 0.0,
                mean_accuracy_ratio=m(acc), mean_survival_ratio=m(surv),
                survive_5=100 * curve[5] / longer[5] if longer[5] else 0, survive_10=100 * curve[10] / longer[10] if longer[10] else 0,
                survive_15=100 * curve[15] / longer[15] if longer[15] else 0,
                n5=longer[5], n10=longer[10], n15=longer[15])

if __name__ == '__main__':
    out, dirs = sys.argv[1], sys.argv[2:]
    data = load(dirs)
    cols = ['algorithm', 'elements', 'pairs', 'right', 'pair_accuracy', 'mean_accuracy_ratio', 'mean_survival_ratio', 'survive_5', 'survive_10', 'survive_15']
    with open(out, 'w', newline='') as fh:
        w = csv.writer(fh); w.writerow(cols)
        for a in sorted(data):
            s = summarize(data[a])
            w.writerow([a] + [f'{s[c]:.2f}' if isinstance(s[c], float) else s[c] for c in cols[1:]])
    print(open(out).read())
