#!/usr/bin/env python3
"""Step lineage across the repaired suites. Every step of the final v1 suite gets an id; the repair logs (repairs_vN.jsonl: ins/rep/del by step index, applied in
log order) are replayed to follow each step into the suites of v7, v18, v26, v32, v33, v34, v41: 'rep' keeps the id (same logical step, new locator), 'ins' creates a
new id, 'del' drops it. Result: lin[base][test] = list of ids aligned with the row index of that test in suites_repaired/<base>.
Consistency check: the length of each list equals the number of steps of the suite file."""
import json, os, re, html, glob, itertools
W = '/Users/stocco/repos/tools/phpfusion-work'
ROW = re.compile(r"<tr>\s*<td[^>]*>(.*?)</td>\s*<td[^>]*>(.*?)</td>\s*<td[^>]*>(.*?)</td>\s*</tr>", re.S)
CLEAN = lambda x: html.unescape(re.sub(r'<[^>]+>', '', x)).strip()
CHAIN = [('v1', None), ('v7', 'v1'), ('v18', 'v7'), ('v26', 'v18'), ('v32', 'v26'), ('v33', 'v32'), ('v34', 'v33'), ('v41', 'v34')]
def suite_rows(base):
    root = glob.glob(f'{W}/suites_repaired/{base}/*')[0]; out = {}
    for f in sorted(glob.glob(root + '/*/*.html')):
        t = '/'.join(f.split('/')[-2:])[:-5]
        out[t] = [[CLEAN(x) for x in m.groups()] for m in ROW.finditer(open(f, encoding='utf8', errors='ignore').read())]
    return out
def build():
    counter = itertools.count(1); lin = {}
    rows1 = suite_rows('v1'); lin['v1'] = {t: [f's{next(counter)}' for _ in r] for t, r in rows1.items()}
    for base, parent in CHAIN[1:]:
        cur = {t: list(v) for t, v in lin[parent].items()}
        for line in open(f'{W}/suites_repaired/repairs_{base}.jsonl'):
            r = json.loads(line); t = r['test']; i = r['i']
            if r['op'] == 'rep': pass
            elif r['op'] == 'ins': cur[t].insert(i, f's{next(counter)}')
            elif r['op'] == 'del': del cur[t][i]
        lin[base] = cur
    for base, _ in CHAIN:
        rows = suite_rows(base)
        for t in rows:
            assert len(rows[t]) == len(lin[base][t]), (base, t, len(rows[t]), len(lin[base][t]))
    return lin, {b: suite_rows(b) for b, _ in CHAIN}
if __name__ == '__main__':
    lin, rows = build(); print('lineage consistent for', len(lin), 'suites;', sum(len(v) for v in lin['v1'].values()), 'steps in v1;', sum(len(v) for v in lin['v41'].values()), 'in v41')
    common = set(sum(lin['v1'].values(), [])) & set(sum(lin['v41'].values(), [])); print('v1 steps still present in v41:', len(common))
