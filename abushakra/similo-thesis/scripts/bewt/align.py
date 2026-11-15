#!/usr/bin/env python3
"""Aligns the captures of the two versions of each BEWT app and writes them in the layout ImportReproBreak reads.

Ground truth: the same page-object method called at the same place of the same test (k-th call of that method within
the test) selects the same logical element in v1 and v2 by construction (both suites implement the same Gherkin step).

usage: align.py <out dir with one capture dir per version> <dest dir> [app ...]
Writes <dest>/plan.json and <dest>/captures/<app>/{old,new}/<version>/<id>-1.json (+ .png).
"""
import glob, json, os, re, shutil, sys

APPS = {"bludit": ("bludit-2.3.4", "bludit-3.13.1"), "joomla": ("joomla-3.10.11", "joomla-4.2.0"),
        "mantisbt": ("mantisbt-1.2.0", "mantisbt-2.25.4"), "prestashop": ("prestashop-1.6.1.23", "prestashop-1.7.8.5")}


def tkey(test):
    """Test method name, case-insensitive without a Test suffix (the two versions name some classes differently)."""
    m = test.split(".")[-1].lower().replace("_", "")
    return m[:-4] if m.endswith("test") and len(m) > 4 else m


def mkey(po):
    p = po.split(".")
    return ".".join(p[-2:])


def load(version_dir):
    """test key -> ordered list of captures (dicts with the json, its directory)."""
    tests = {}
    for f in glob.glob(os.path.join(version_dir, "s*", "[0-9]*.json")):
        d = os.path.dirname(f)
        j = json.load(open(f))
        j["_dir"] = d
        if j["test"] == "unknown":
            continue
        tests.setdefault(tkey(j["test"]), []).append(j)
    for k in tests:
        tests[k].sort(key=lambda j: (j["time"], j["seq"]))
    return tests


def usable(j):
    els = j.get("page", {}).get("elements", [])
    t = j.get("targets")
    if not (isinstance(t, list) and t and isinstance(t[0], dict) and isinstance(t[0].get("element"), dict)):
        return False
    return bool(j.get("po_method")) and len(els) == 1 and t and len(t) == 1 and t[0]["visible"] and not t[0]["inSvg"] and j.get("shot") \
        and os.path.exists(os.path.join(j["_dir"], j["dom"]))


def calls(caps):
    """k-th usable call per page-object method; consecutive repeats of the same element on the same DOM are polling, not new calls."""
    out, last = {}, None
    for j in caps:
        if not j.get("po_method"):
            continue
        t = j.get("targets")
        x = t[0]["element"]["xpath"]["chrome"] if isinstance(t, list) and t and isinstance(t[0], dict) and isinstance(t[0].get("element"), dict) else None
        sig = (j["po_method"], x, j["dom"], j["_dir"])
        if sig == last:
            continue
        last = sig
        out.setdefault(mkey(j["po_method"]), []).append(j)
    return out


def snap_of(j):
    n = re.search(r"dom_(\d+)", j["dom"]).group(1)
    return json.load(open(os.path.join(j["_dir"], "snap_%s.json" % n)))


def emit(j, dest_json, dest_png):
    s = snap_of(j)
    if not s["elements"]:
        return None
    tg = dict(j["targets"][0]); tg["main"] = True
    dom = open(os.path.join(j["_dir"], j["dom"]), encoding="utf-8", errors="replace").read()
    rec = {"url": j["page"]["url"], "viewport": s["viewport"], "scroll": s["scroll"], "dom": dom, "elements": s["elements"],
           "targets": [tg], "matches": 1}
    json.dump(rec, open(dest_json, "w"))
    shutil.copy(os.path.join(j["_dir"], j["shot"]), dest_png)
    return tg["element"]["xpath"]["chrome"]


def main():
    out, dest = sys.argv[1], sys.argv[2]
    apps = sys.argv[3:] or list(APPS)
    plan, report = [], []
    for app in apps:
        v1, v2 = APPS[app]
        d1, d2 = os.path.join(out, v1), os.path.join(out, v2)
        if not (os.path.isdir(d1) and os.path.isdir(d2)):
            report.append("%s: missing capture dir" % app); continue
        t1, t2 = load(d1), load(d2)
        seen, n, st = set(), 0, dict(tests_common=0, aligned=0, unusable=0, dup=0, kept=0)
        for tk in sorted(set(t1) & set(t2)):
            st["tests_common"] += 1
            c1, c2 = calls(t1[tk]), calls(t2[tk])
            for mk in sorted(set(c1) & set(c2)):
                for a, b in zip(c1[mk], c2[mk]):
                    st["aligned"] += 1
                    if not (usable(a) and usable(b)):
                        st["unusable"] += 1; continue
                    xa, xb = a["targets"][0]["element"]["xpath"]["chrome"], b["targets"][0]["element"]["xpath"]["chrome"]
                    ua, ub = re.sub(r"https?://[^/]+", "", a["page"]["url"]).split("?")[0], re.sub(r"https?://[^/]+", "", b["page"]["url"]).split("?")[0]
                    key = (mk, a["by"], b["by"], xa, xb, ua, ub)
                    if key in seen:
                        st["dup"] += 1; continue
                    seen.add(key)
                    n += 1
                    base_old = os.path.join(dest, "captures", app, "old", v1)
                    base_new = os.path.join(dest, "captures", app, "new", v2)
                    os.makedirs(base_old, exist_ok=True); os.makedirs(base_new, exist_ok=True)
                    if emit(a, os.path.join(base_old, "%d-1.json" % n), os.path.join(base_old, "%d-1.png" % n)) is None or \
                       emit(b, os.path.join(base_new, "%d-1.json" % n), os.path.join(base_new, "%d-1.png" % n)) is None:
                        n -= 1; st["unusable"] += 1; seen.discard(key); continue
                    st["kept"] += 1
                    plan.append({"id": n, "repo": "bewt/" + app, "commit": v2, "parent": v1, "file": tk + "/" + mk, "framework": "selenium",
                                 "old_locator": a["by"], "new_locator": b["by"]})
        report.append("%s: %s" % (app, st))
    os.makedirs(dest, exist_ok=True)
    json.dump(plan, open(os.path.join(dest, "plan.json"), "w"), indent=1)
    print("\n".join(report)); print("pairs:", len(plan))


main()
