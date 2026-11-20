#!/usr/bin/env python3
"""Removal-test pairs from multi-version sites (Wayback, Excalidraw, Slickgrid) for the removal test, stage 2.

For a random sample of (element, version i -> i+1) pairs per site, two two-version mini-sites are written:
  inw_<site>_<k>  intact: version i and version i+1 unchanged;
  rmw_<site>_<k>  removed: the same, but the target is deleted from version i+1 (element list, DOM with its children, screenshot box
                  painted over; the paths of the remaining elements follow the modified DOM).
Source files are never modified (files that stay unchanged are symlinked).

usage: removal_pairs.py <source sites dir> <dest sites dir> <pairs per site> <seed> <site> [<site> ...]"""
import sys, os, json, shutil, random
import lxml.html, cv2, numpy as np
sys.setrecursionlimit(20000)
src, dest, N, seed = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
sites = sys.argv[5:]
OLD, NEW = "2001-01-01", "2001-01-02"

def all_paths(doc):
    """node -> (chrome, selenium, id-relative) XPath for every element node, in one pass"""
    out = {}
    def rec(node, chrome, selenium, idx):
        out[node] = (chrome, selenium, idx)
        kids = [c for c in node if isinstance(c.tag, str)]
        cnt = {}
        for c in kids: cnt[c.tag] = cnt.get(c.tag, 0) + 1
        seen = {}
        for c in kids:
            seen[c.tag] = seen.get(c.tag, 0) + 1
            i, n, t = seen[c.tag], cnt[c.tag], c.tag.lower()
            step = t if n == 1 else f"{t}[{i}]"
            ch = f"{chrome}/{step}"; se = f"{selenium}/{t}[{i}]"
            if c.get("id"): ix = f"//*[@id='{c.get('id')}']"
            elif idx.startswith("//*[@id="): ix = f"{idx}/{step}"
            else: ix = ch
            rec(c, ch, se, ix)
    r = doc
    rec(r, "/" + r.tag.lower(), "/" + r.tag.lower() + "[1]", f"//*[@id='{r.get('id')}']" if r.get("id") else "/" + r.tag.lower())
    return out

def scale_of(shot_dir, im):
    f = f"{shot_dir}/screenshot_scale.txt"
    if os.path.exists(f):
        try: return float(open(f).read().strip().replace(",", "."))
        except Exception: pass
    raw = im.shape[1] / 1920.0
    return min([0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0], key=lambda d: abs(d - raw))

def link(a, b):
    if os.path.lexists(b): os.remove(b)
    os.symlink(a, b)

total = 0
for site in sites:
    try:
        rows = [l.rstrip("\n").split(";") for l in open(f"{src}/{site}/xpaths.csv") if l.strip()]
        dates = rows[0]; cells = rows[1:]
        cand = [(r, i) for r, row in enumerate(cells) for i in range(len(dates) - 1)
                if i + 1 < len(row) and row[i] not in ("", "-") and row[i + 1] not in ("", "-")]
        random.Random(f"{seed}{site}").shuffle(cand)
    except Exception as ex:
        print(f"skip {site}: {ex}"); continue
    made = 0; cache = {}
    for r, i in cand:
        if made >= N: break
        old_x, tgt_x = cells[r][i], cells[r][i + 1]
        d_old, d_new = dates[i], dates[i + 1]
        try:
            if d_new not in cache:
                els = json.load(open(f"{src}/{site}/{d_new}/elements.json"))
                doc = lxml.html.document_fromstring(open(f"{src}/{site}/{d_new}/chromeDOM.html", encoding="utf-8", errors="replace").read())
                cache = {d_new: (els, doc, all_paths(doc))}
            els, doc, ap = cache[d_new]
            tgt = next((e for e in els if e["xpath"]["chrome"] == tgt_x), None)
            if tgt is None: continue
            by_chrome = {}
            for nd, p in ap.items(): by_chrome.setdefault(p[0], nd)
            tnode = by_chrome.get(tgt_x)
            if tnode is None: continue
            name = f"{site}_{made}"; urlb = lambda kind, v: f"http://reprobreak.local/{kind}_{name}/{v}"
            old_els = json.load(open(f"{src}/{site}/{d_old}/elements.json"))
            if not any(e["xpath"]["chrome"] == old_x for e in old_els): continue
            # ---- both mini-sites share the old version (re-pointed elements, linked page files)
            for kind in ("inw", "rmw"):
                dd = f"{dest}/{kind}_{name}"
                for v, dsrc in ((OLD, d_old), (NEW, d_new)):
                    os.makedirs(f"{dd}/{v}", exist_ok=True)
                oe = json.loads(json.dumps(old_els))
                for e in oe: e["url"] = urlb(kind, OLD)
                json.dump(oe, open(f"{dd}/{OLD}/elements.json", "w"))
                for f in ("chromeDOM.html", "screenshot.png", "screenshot_scale.txt"):
                    if os.path.exists(f"{src}/{site}/{d_old}/{f}"): link(f"{src}/{site}/{d_old}/{f}", f"{dd}/{OLD}/{f}")
                open(f"{dd}/urls.csv", "w").write(f"date;url\n{OLD};{urlb(kind, OLD)}\n{NEW};{urlb(kind, NEW)}\n")
            # ---- intact new version
            ne = json.loads(json.dumps(els))
            for e in ne: e["url"] = urlb("inw", NEW)
            dd = f"{dest}/inw_{name}"
            json.dump(ne, open(f"{dd}/{NEW}/elements.json", "w"))
            for f in ("chromeDOM.html", "screenshot.png", "screenshot_scale.txt"):
                if os.path.exists(f"{src}/{site}/{d_new}/{f}"): link(f"{src}/{site}/{d_new}/{f}", f"{dd}/{NEW}/{f}")
            open(f"{dd}/xpaths.csv", "w").write(f"{OLD};{NEW}\n{old_x};{tgt_x}\n")
            open(f"{dd}/classified.csv", "w").write(f"{OLD};{NEW}\ninitial|initial|{old_x};same   |same   |{tgt_x}")
            # ---- removed new version: re-parse (the cached tree is kept intact for the next pair)
            doc2 = lxml.html.document_fromstring(open(f"{src}/{site}/{d_new}/chromeDOM.html", encoding="utf-8", errors="replace").read())
            ap2 = all_paths(doc2); by2 = {}
            for nd, p in ap2.items(): by2.setdefault(p[0], nd)
            node_of = {}
            keep = [e for e in json.loads(json.dumps(els)) if not (e["xpath"]["chrome"] == tgt_x or e["xpath"]["chrome"].startswith(tgt_x + "/"))]
            for e in keep:
                nd = by2.get(e["xpath"]["chrome"])
                if nd is not None: node_of[id(e)] = nd
            by2[tgt_x].drop_tree()
            ap3 = all_paths(doc2)
            for e in keep:
                nd = node_of.get(id(e))
                if nd is not None and nd in ap3:
                    c, se, ix = ap3[nd]; e["xpath"] = dict(e["xpath"], chrome=c, selenium=se, idxpath=ix)
                e["url"] = urlb("rmw", NEW)
            dd = f"{dest}/rmw_{name}"
            json.dump(keep, open(f"{dd}/{NEW}/elements.json", "w"))
            open(f"{dd}/{NEW}/chromeDOM.html", "w", encoding="utf-8").write(lxml.html.tostring(doc2, encoding="unicode", doctype="<!DOCTYPE html>"))
            shotf = f"{src}/{site}/{d_new}/screenshot.png"
            if os.path.exists(shotf):
                im = cv2.imread(shotf); H, W = im.shape[:2]; sc = scale_of(f"{src}/{site}/{d_new}", im)
                x, y = tgt["location"]; w, h = tgt["dimension"]
                x0, y0, x1, y1 = max(0, int(x * sc)), max(0, int(y * sc)), min(W, int((x + w) * sc)), min(H, int((y + h) * sc))
                if x1 > x0 and y1 > y0:
                    parts = [im[max(0, y0 - 3):max(1, y0), x0:x1], im[y1:min(H, y1 + 3), x0:x1], im[y0:y1, max(0, x0 - 3):max(1, x0)], im[y0:y1, x1:min(W, x1 + 3)]]
                    ring = np.concatenate([p.reshape(-1, 3) for p in parts if p.size])
                    im[y0:y1, x0:x1] = np.median(ring, axis=0) if len(ring) else 255
                cv2.imwrite(f"{dd}/{NEW}/screenshot.png", im)
                sf = f"{src}/{site}/{d_new}/screenshot_scale.txt"
                if os.path.exists(sf): shutil.copy(sf, f"{dd}/{NEW}/screenshot_scale.txt")
            open(f"{dd}/xpaths.csv", "w").write(f"{OLD};{NEW}\n{old_x};-\n")
            open(f"{dd}/classified.csv", "w").write(f"{OLD};{NEW}\ninitial|initial|{old_x};deleted|deleted|-")
            made += 1; total += 1
        except Exception as ex:
            print(f"skip pair {site} r{r} i{i}: {ex}")
    print(f"{site}: {made} pairs")
print(f"{total} pairs written to {dest}")
