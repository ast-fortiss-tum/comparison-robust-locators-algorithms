#!/usr/bin/env python3
"""Identity-oracle chains over all releases of an app, for the multi-version benchmark (Wayback-style sites).

An element is identified across releases by a stable test attribute (data-test, data-testid). Elements that carry it
exactly once on a page are the oracle; the attribute is then removed from the DOM and from every element record, so no
algorithm can use it. For each UI state one site is written: its versions are the releases in which the state exists
(consecutive releases with identical element lists are collapsed), its rows are the oracle elements first appearing in the first
version (elements that appear later form further sites, <app>_<state>_from_<release>, if at least --min-rows of them start in the same release), and a cell holds the element's absolute XPath in that release or "-" when the id is absent or not unique.

usage: chains.py <snap dir> <attr> <app> <dest dir> [--states=a,b] [--min-rows=3] [--cut=id:release,...]
writes <dest>/captures/<app>/<state>/<release>.{json,png}, <dest>/manifest.json, <dest>/flags.csv, <dest>/stats.txt
then: runMain scrape.ImportOracle <dest>/manifest.json [sites dir]"""
import glob, json, os, re, sys, csv, shutil, hashlib, collections, datetime

def vkey(v): return tuple(int(x) for x in re.findall(r"\d+", v))
def tokens(e): return set(((e.get("attributes") or {}).get("class") or "").split())
def jacc(a, b): return len(a & b) / len(a | b) if (a | b) else 1.0

args = [a for a in sys.argv[1:] if not a.startswith("--")]
snap, ATTR, app, dest = args[:4]
only = next((a.split("=", 1)[1].split(",") for a in sys.argv if a.startswith("--states=")), None)
CUT = {}
for a in sys.argv:
    if a.startswith("--cut="):   # --cut=id:release[,id:release]  an id counts as absent from that release on (a reviewed identity break)
        for x in a.split("=", 1)[1].split(","):
            i, r = x.rsplit(":", 1); CUT[i] = r
MIN_ROWS = int(next((a.split("=", 1)[1] for a in sys.argv if a.startswith("--min-rows=")), 3))
rels = sorted([os.path.basename(d) for d in glob.glob(f"{snap}/*") if os.path.isdir(d)], key=vkey)
states = sorted({os.path.basename(f)[3:-5] for f in glob.glob(f"{snap}/*/*.json")})
if only: states = [s for s in states if s in only]
dom_attr = re.compile(r' %s="[^"]*"' % re.escape(ATTR))
css_attr = re.compile(r"\[%s[^\]]*\]" % re.escape(ATTR))

def strip(e):
    e = json.loads(json.dumps(e))
    if e.get("attributes"): e["attributes"].pop(ATTR, None)
    if e.get("cssSelector"): e["cssSelector"] = css_attr.sub("", e["cssSelector"])
    return e

def find_state_file(r, s):
    c = glob.glob(f"{snap}/{r}/*-{s}.json")
    return c[0] if c else None

os.makedirs(dest, exist_ok=True)
manifest, flags, stats = [], [], []
for s in states:
    kept, last_sig = [], None
    for r in rels:
        f = find_state_file(r, s)
        if not f: continue
        d = json.load(open(f))
        ids = collections.defaultdict(list)
        for e in d["elements"]:
            t = (e.get("attributes") or {}).get(ATTR)
            if t: ids[t].append(e)
        els = [strip(e) for e in d["elements"]]
        sig = hashlib.md5(json.dumps([(e["tag"], e["xpath"]["chrome"], e.get("innerText"), (e.get("attributes") or {}).get("class"), e["location"]) for e in els]).encode()).hexdigest()
        if sig == last_sig: continue            # identical to the previous kept release: no new information
        last_sig = sig
        unique = {t: v[0] for t, v in ids.items() if len(v) == 1 and not (t in CUT and vkey(r) >= vkey(CUT[t]))}
        dup = [t for t, v in ids.items() if len(v) > 1]
        cdir = f"{dest}/captures/{app}/{s}"; os.makedirs(cdir, exist_ok=True)
        json.dump({"elements": els, "dom": dom_attr.sub("", d["dom"]), "scroll": d.get("scroll", [0, 0]), "viewport": d.get("viewport", [1920, 1080])}, open(f"{cdir}/{r}.json", "w"))
        png = f[:-5] + ".png"
        if os.path.exists(png): shutil.copy(png, f"{cdir}/{r}.png")
        kept.append(dict(release=r, unique={t: dict(xpath=e["xpath"]["chrome"], tag=e["tag"], text=(e.get("innerText") or "").strip(), cls=tokens(e), loc=e["location"]) for t, e in unique.items()}, dup=dup))
    if len(kept) < 2: stats.append(f"{s}: {len(kept)} usable release(s), skipped"); continue
    # One site per cohort of elements that first appear in the same release (an element is followed from the release where
    # it first appears, so every element belongs to exactly one site); small cohorts are dropped.
    first_idx = {}
    for i, k in enumerate(kept):
        for t in k["unique"]: first_idx.setdefault(t, i)
    cohorts = collections.defaultdict(list)
    for t, i in first_idx.items(): cohorts[i].append(t)
    for i0 in sorted(cohorts):
        ids = sorted(cohorts[i0])
        ks = kept[i0:]
        rows = []
        for t in ids:
            cells = [k["unique"][t]["xpath"] if t in k["unique"] else "-" for k in ks]
            if sum(c != "-" for c in cells) < 2: continue
            rows.append(dict(id=t, xpaths=cells))
            prev = None
            for k in ks:
                cur = k["unique"].get(t)
                if cur and prev:
                    moved = abs(cur["loc"][0] - prev[1]["loc"][0]) + abs(cur["loc"][1] - prev[1]["loc"][1])
                    why = []
                    if cur["tag"] != prev[1]["tag"]: why.append("tag changed")
                    if moved > 300: why.append(f"moved {moved}px")
                    if cur["text"] != prev[1]["text"] and jacc(cur["cls"], prev[1]["cls"]) < 0.3: why.append("text and classes changed")
                    if why: flags.append([s, t, prev[0], k["release"], "; ".join(why)])
                if cur: prev = (k["release"], cur)
        if len(rows) < MIN_ROWS or len(ks) < 2:
            if rows: stats.append(f"{s} from {kept[i0]['release']}: {len(rows)} element(s), below the minimum, dropped")
            continue
        name = f"{app}_{s}" + ("" if i0 == 0 else f"_from_{kept[i0]['release']}")
        dates = [(datetime.date(2001, 1, 1) + datetime.timedelta(days=j)).isoformat() for j in range(len(ks))]
        manifest.append(dict(site=name.replace("-", "_").replace(".", "_"), state=s, releases=[dict(release=k["release"], date=dates[j], json=f"{dest}/captures/{app}/{s}/{k['release']}.json", png=f"{dest}/captures/{app}/{s}/{k['release']}.png") for j, k in enumerate(ks)], rows=rows))
        full = sum(all(c != "-" for c in r["xpaths"]) for r in rows)
        stats.append(f"{name}: {len(ks)} versions ({ks[0]['release']}..{ks[-1]['release']}), {len(rows)} elements, {full} present in every version")
json.dump(manifest, open(f"{dest}/manifest.json", "w"), indent=1)
with open(f"{dest}/flags.csv", "w", newline="") as fh:
    w = csv.writer(fh); w.writerow(["state", "id", "from_release", "to_release", "reason"]); w.writerows(flags)
open(f"{dest}/stats.txt", "w").write("\n".join(stats) + "\n")
print("\n".join(stats)); print(f"\nsites {len(manifest)}, rows {sum(len(m['rows']) for m in manifest)}, flagged transitions {len(flags)}")
