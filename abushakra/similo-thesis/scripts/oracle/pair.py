#!/usr/bin/env python3
"""Identity oracle pilot: pair elements that carry the same data-testid in two versions, then hide the test id.
usage: pair.py <snap dir> <old version> <new version> <dest dir> <app>"""
import glob, json, os, re, shutil, sys, csv
snap, v1, v2, dest, app = sys.argv[1:6]
TID = "data-testid"

def strip_el(e):
    e = json.loads(json.dumps(e))
    if e.get("attributes"): e["attributes"].pop(TID, None)
    if e.get("cssSelector"): e["cssSelector"] = re.sub(r'\[data-testid[^\]]*\]', '', e["cssSelector"])
    return e

def load(v):
    return {os.path.basename(f)[3:-5]: json.load(open(f)) for f in sorted(glob.glob(f"{snap}/{v}/*.json"))}

def unique(d):
    c = {}
    for e in d["elements"]:
        t = (e.get("attributes") or {}).get(TID)
        if t: c.setdefault(t, []).append(e)
    return {t: es[0] for t, es in c.items() if len(es) == 1}

a, b = load(v1), load(v2)
plan, feats, seen, n, dropped = [], [], set(), 0, []
for state in a:
    if state not in b: continue
    ua, ub = unique(a[state]), unique(b[state])
    for t in sorted(set(ua) & set(ub)):
        ea, eb = ua[t], ub[t]
        if not (ea["dimension"][1] and eb["dimension"][1]): continue
        if abs(ea["location"][0] - eb["location"][0]) + abs(ea["location"][1] - eb["location"][1]) > 300:  # same id, far away: the id was reused for another element
            dropped.append(f"{state}/{t}: moved {ea['location']} -> {eb['location']}"); continue
        key = (t, ea["xpath"]["chrome"], eb["xpath"]["chrome"])
        if key in seen: continue
        seen.add(key); n += 1
        for side, v, st, el in (("old", v1, a[state], ea), ("new", v2, b[state], eb)):
            d = os.path.join(dest, "captures", app, side, v); os.makedirs(d, exist_ok=True)
            rec = {"url": f"http://oracle.local/{app}/{v}", "viewport": st["viewport"], "scroll": st["scroll"],
                   "dom": re.sub(r' data-testid="[^"]*"', "", st["dom"]), "elements": [strip_el(x) for x in st["elements"]],
                   "targets": [{"main": True, "visible": True, "inSvg": False, "element": strip_el(el)}], "matches": 1}
            json.dump(rec, open(os.path.join(d, f"{n}-1.json"), "w"))
            shutil.copy(f"{snap}/{v}/" + [x for x in os.listdir(f"{snap}/{v}") if x.endswith(state + ".png")][0], os.path.join(d, f"{n}-1.png"))
        same = lambda k: ea.get(k) == eb.get(k)
        sib = sum(1 for x in a[state]["elements"] if x["tag"] == ea["tag"] and x.get("attributes", {}).get("class") == ea.get("attributes", {}).get("class"))
        feats.append({"id": n, "state": state, "testid": t, "tag_old": ea["tag"], "tag_new": eb["tag"], "xpath_same": ea["xpath"]["chrome"] == eb["xpath"]["chrome"],
                      "text_same": (ea.get("innerText") or "").strip() == (eb.get("innerText") or "").strip(),
                      "class_same": (ea.get("attributes") or {}).get("class") == (eb.get("attributes") or {}).get("class"),
                      "attrs_kept": len(set((ea.get("attributes") or {}).items()) & set((eb.get("attributes") or {}).items())) - 1,
                      "lookalikes_old": sib, "candidates_old": len(a[state]["elements"]), "candidates_new": len(b[state]["elements"])})
        plan.append({"id": n, "repo": f"oracle/{app}", "commit": v2, "parent": v1, "file": f"{state}/{t}", "framework": "testid",
                     "old_locator": f"[data-testid={t}]", "new_locator": f"[data-testid={t}]"})
os.makedirs(dest, exist_ok=True)
json.dump(plan, open(os.path.join(dest, "plan.json"), "w"), indent=1)
w = csv.DictWriter(open(os.path.join(dest, "features.csv"), "w", newline=""), list(feats[0].keys())); w.writeheader(); w.writerows(feats)
open(os.path.join(dest, "dropped.txt"), "w").write("\n".join(dropped) + "\n"); print("pairs:", n, "dropped:", dropped)
