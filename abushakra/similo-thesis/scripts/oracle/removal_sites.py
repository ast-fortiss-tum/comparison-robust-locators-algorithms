#!/usr/bin/env python3
"""Removal-test sites: for each source site (two versions), a copy in which the target element is gone from the new version.

The old version is kept (elements are re-pointed to the new site's URLs); in the new version the target is removed, with its
descendants, from elements.json and from chromeDOM.html, and its box in the screenshot is painted over with the colour around
it. xpaths.csv and classified.csv mark the target as deleted ("-"), so the standard evaluation sees a target that does not
exist; the algorithms still run and their answers are what the test counts.

usage: removal_sites.py <source sites dir> <dest sites dir> [<site> ...]   (default: every site named bewt_*)
Nothing in the source directory is modified."""
import sys, os, json, shutil, csv, statistics
import lxml.html
import cv2, numpy as np


def _steps(node):
    """list of (tag, index among same-tag siblings, number of same-tag siblings) from the root down to node"""
    out = []
    while node is not None and isinstance(node.tag, str):
        par = node.getparent()
        same = [c for c in (par if par is not None else [node]) if isinstance(c.tag, str) and c.tag == node.tag]
        out.append((node.tag.lower(), same.index(node) + 1 if node in same else 1, len(same)))
        node = par
    return out[::-1]

def paths(node):
    """chrome, selenium and id-relative XPath of a node, in the formats of the benchmark's extractor"""
    st = _steps(node)
    chrome = "/" + "/".join(t if n == 1 else f"{t}[{i}]" for t, i, n in st)
    selenium = "/" + "/".join(f"{t}[{i}]" for t, i, n in st)
    idx = chrome
    cur = node; depth = 0
    while cur is not None and isinstance(cur.tag, str):
        if cur.get("id"):
            rest = _steps(node)[len(_steps(cur)):]
            idx = f"//*[@id='{cur.get('id')}']" + "".join("/" + (t if n == 1 else f"{t}[{i}]") for t, i, n in rest)
            break
        cur = cur.getparent()
    return chrome, selenium, idx

src, dest = sys.argv[1:3]
names = sys.argv[3:] or sorted(n for n in os.listdir(src) if n.startswith("bewt_"))
os.makedirs(dest, exist_ok=True)
OLD, NEW = "2001-01-01", "2001-01-02"
ok = skipped = 0
for name in names:
    try:
        s = f"{src}/{name}"; rm = f"rm_{name}"; d = f"{dest}/{rm}"
        rows = [l.rstrip("\n").split(";") for l in open(f"{s}/xpaths.csv") if l.strip()]
        old_x, tgt_x = rows[1][0], rows[1][1]
        new_els = json.load(open(f"{s}/{NEW}/elements.json"))
        tgt = next((e for e in new_els if e["xpath"]["chrome"] == tgt_x), None)
        if tgt is None: raise ValueError("target not among the new elements")
        keep = [e for e in new_els if not (e["xpath"]["chrome"] == tgt_x or e["xpath"]["chrome"].startswith(tgt_x + "/"))]
        os.makedirs(f"{d}/{OLD}", exist_ok=True); os.makedirs(f"{d}/{NEW}", exist_ok=True)
        url = lambda v: f"http://reprobreak.local/{rm}/{v}"
        # old version: same files, elements re-pointed to the new site's URL
        old_els = json.load(open(f"{s}/{OLD}/elements.json"))
        for e in old_els: e["url"] = url(OLD)
        json.dump(old_els, open(f"{d}/{OLD}/elements.json", "w"))
        for f in ("chromeDOM.html", "screenshot.png", "screenshot_scale.txt"):
            if os.path.exists(f"{s}/{OLD}/{f}"): shutil.copy(f"{s}/{OLD}/{f}", f"{d}/{OLD}/{f}")
        # new version without the target
        for e in keep: e["url"] = url(NEW)
        doc = lxml.html.document_fromstring(open(f"{s}/{NEW}/chromeDOM.html", encoding="utf-8", errors="replace").read())
        nodes = doc.xpath(tgt_x)
        if len(nodes) != 1: raise ValueError(f"target xpath matches {len(nodes)} nodes in the DOM")
        node_of = {}
        for e in keep:
            m = doc.xpath(e["xpath"]["chrome"])
            if len(m) == 1: node_of[id(e)] = m[0]
        nodes[0].drop_tree()
        for e in keep:   # the siblings of the deleted node move up: paths follow the modified DOM
            nd = node_of.get(id(e))
            if nd is not None:
                c, se, ix = paths(nd)
                e["xpath"] = dict(e["xpath"], chrome=c, selenium=se, idxpath=ix)
        json.dump(keep, open(f"{d}/{NEW}/elements.json", "w"))
        open(f"{d}/{NEW}/chromeDOM.html", "w", encoding="utf-8").write(lxml.html.tostring(doc, encoding="unicode", doctype="<!DOCTYPE html>"))
        scale = float(open(f"{s}/{NEW}/screenshot_scale.txt").read().strip() or 1) if os.path.exists(f"{s}/{NEW}/screenshot_scale.txt") else 1.0
        shutil.copy(f"{s}/{NEW}/screenshot_scale.txt", f"{d}/{NEW}/screenshot_scale.txt") if os.path.exists(f"{s}/{NEW}/screenshot_scale.txt") else None
        im = cv2.imread(f"{s}/{NEW}/screenshot.png")
        H, W = im.shape[:2]
        x, y = tgt["location"]; w, h = tgt["dimension"]
        x0, y0, x1, y1 = max(0, int(x * scale)), max(0, int(y * scale)), min(W, int((x + w) * scale)), min(H, int((y + h) * scale))
        if x1 > x0 and y1 > y0:
            ring = np.concatenate([im[max(0, y0 - 3):y0 or 1, x0:x1].reshape(-1, 3), im[y1:min(H, y1 + 3), x0:x1].reshape(-1, 3),
                                   im[y0:y1, max(0, x0 - 3):x0 or 1].reshape(-1, 3), im[y0:y1, x1:min(W, x1 + 3)].reshape(-1, 3)])
            col = np.median(ring, axis=0) if len(ring) else np.array([255, 255, 255])
            im[y0:y1, x0:x1] = col
        cv2.imwrite(f"{d}/{NEW}/screenshot.png", im)
        open(f"{d}/urls.csv", "w").write(f"date;url\n{OLD};{url(OLD)}\n{NEW};{url(NEW)}\n")
        open(f"{d}/xpaths.csv", "w").write(f"{OLD};{NEW}\n{old_x};-\n")
        open(f"{d}/classified.csv", "w").write(f"{OLD};{NEW}\ninitial|initial|{old_x};deleted|deleted|-")
        ok += 1
    except Exception as ex:
        skipped += 1; print(f"skip {name}: {ex}")
print(f"{ok} removal sites written to {dest}, {skipped} skipped")
