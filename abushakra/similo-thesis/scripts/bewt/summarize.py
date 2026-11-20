#!/usr/bin/env python3
"""Per-site results and subset summary of the algorithms on the BEWT pairs.

usage: summarize.py <Main log(s)...> --plan imp/plan.json --manifest sites/bewt_manifest.csv --out results/bewt

Main prints one CSV row per algorithm and site ("<algo>@<site>,AccuracyAll%,AccAll_Num,AccAll_Denom,..."); a pair counts as
found when the algorithm's top candidate is the ground-truth element (AccAll_Num == 1).
Subsets: ALL; same position (the target keeps its absolute XPath) vs moved; locator changed (the page object's locator
text differs between v1 and v2, i.e. the developers had to edit it) vs unchanged; and per application.
"""
import argparse, csv, json, os, re, sys
from collections import defaultdict

ap = argparse.ArgumentParser()
ap.add_argument("logs", nargs="+")
ap.add_argument("--plan", required=True)
ap.add_argument("--manifest", required=True)
ap.add_argument("--out", required=True)
a = ap.parse_args()

plan = {p["id"]: p for p in json.load(open(a.plan))}
man = {}
for r in csv.DictReader(open(a.manifest), delimiter=";"):
    man[r["site"]] = r

found = {}  # (algo, site) -> (num, denom)
for log in a.logs:
    for line in open(log, errors="replace"):
        m = re.match(r"^([^@,]+)@(bewt_[a-z]+_\d+),([\d.]+),(\d+),(\d+),", line)
        if m:
            found[(m.group(1), m.group(2))] = (int(m.group(4)), int(m.group(5)))

sites = sorted({s for _, s in found})
algos = []
for al, _ in found:
    if al not in algos:
        algos.append(al)


def meta(site):
    _, app, i = site.split("_")
    p = plan.get(int(i)) if app is not None else None
    # ids are per app in align.py's output; the plan is keyed by (repo, id)
    return app, int(i)


# plan.json holds ids that restart per app? align.py numbers per app, so key by (app, id)
plank = {}
for p in json.load(open(a.plan)):
    plank[(p["repo"].split("/")[-1], p["id"])] = p


def subsets(site):
    app, i = meta(site)
    p = plank.get((app, i), {})
    m = man.get(site, {})
    s = ["ALL", "app: " + app]
    if m:
        s.append("same position" if m["old_xpath"] == m["new_xpath"] else "moved")
    if p:
        s.append("locator unchanged" if p["old_locator"] == p["new_locator"] else "locator changed")
    return s


os.makedirs(a.out, exist_ok=True)
with open(os.path.join(a.out, "eval_per_site.csv"), "w", newline="") as f:
    w = csv.writer(f); w.writerow(["algorithm", "site", "found", "elements"])
    for (al, s), (n, d) in sorted(found.items()):
        w.writerow([al, s, n, d])

agg = defaultdict(lambda: [0, 0])
for (al, s), (n, d) in found.items():
    for sub in subsets(s):
        agg[(al, sub)][0] += n; agg[(al, sub)][1] += d
with open(os.path.join(a.out, "summary.csv"), "w", newline="") as f:
    w = csv.writer(f); w.writerow(["algorithm", "subset", "found", "pairs", "percent"])
    for al in algos:
        for sub in sorted({k[1] for k in agg}, key=lambda x: (x != "ALL", x)):
            n, d = agg.get((al, sub), (0, 0))
            if d:
                w.writerow([al, sub, n, d, "%.1f" % (100.0 * n / d)])
print("algorithms:", len(algos), "sites:", len(sites))
for al in algos:
    n, d = agg[(al, "ALL")]
    print("%-24s %4d/%d  %.1f%%" % (al, n, d, 100.0 * n / max(d, 1)))
