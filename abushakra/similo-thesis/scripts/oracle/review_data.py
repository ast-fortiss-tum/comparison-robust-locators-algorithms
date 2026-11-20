#!/usr/bin/env python3
"""Review data for the identity-oracle mapping, one item per CHANGE.

For every pair of consecutive releases in which an oracle element exists, compare the element before and after (tag, text,
position, and a pixel difference of the element's own box). Transitions that look identical are accepted automatically;
the others become review items showing the element before and after side by side, with one question: is the right one
the same control as the left one? The same change in several UI states is one item (group key: id, from, to), and a few
automatically accepted transitions are added as a spot check.

usage: review_data.py <ob dir (manifest.json, captures)> <out dir> [--vis=10] [--moved=150] [--spot=12] [--repo=<git clone with release tags>] [--attr=data-testid] [--app=ex]
writes <out>/img/<key>.jpg and <out>/chunks/chunk_<n>.json ({"items": [...]}, 25 records each), <out>/summary.txt"""
import json, os, sys, re, random, collections, subprocess
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ob, out = [a for a in sys.argv[1:] if not a.startswith("--")][:2]
opt = lambda n, d: float(next((a.split("=", 1)[1] for a in sys.argv if a.startswith(f"--{n}=")), d))
VIS, MOVED, SPOT = opt("vis", 10), opt("moved", 150), int(opt("spot", 12))
TILE_H, PAD, CAP = 200, 90, 20
REPO = next((a.split("=", 1)[1] for a in sys.argv if a.startswith("--repo=")), None)
APP = next((a.split("=", 1)[1] for a in sys.argv if a.startswith("--app=")), "")
ATTR = next((a.split("=", 1)[1] for a in sys.argv if a.startswith("--attr=")), "data-testid")
os.makedirs(f"{out}/img", exist_ok=True); os.makedirs(f"{out}/chunks", exist_ok=True)
manifest = json.load(open(f"{ob}/manifest.json"))
try: font = ImageFont.truetype("/System/Library/Fonts/Menlo.ttc", 13)
except Exception: font = ImageFont.load_default()

def tile(img, e, label):
    x, y = e["location"]; w, h = e["dimension"]
    x0, y0 = max(0, x - PAD), max(0, y - PAD); x1, y1 = min(img.width, x + w + PAD), min(img.height, y + h + PAD)
    if x1 - x0 < 300: x1 = min(img.width, x0 + 300)
    if y1 - y0 < 140: y1 = min(img.height, y0 + 140)
    crop = img.crop((x0, y0, x1, y1)); ImageDraw.Draw(crop).rectangle([x - x0, y - y0, x - x0 + max(w, 2), y - y0 + max(h, 2)], outline=(220, 38, 38), width=3)
    sc = TILE_H / crop.height; crop = crop.resize((max(80, min(int(crop.width * sc), 560)), TILE_H))
    t = Image.new("RGB", (crop.width, TILE_H + CAP), (246, 247, 249)); t.paste(crop, (0, CAP))
    ImageDraw.Draw(t).text((5, 3), label, fill=(40, 48, 64), font=font); return t

def info(e, els=None):
    a = e.get("attributes") or {}
    d = dict(tag=e["tag"], text=(e.get("innerText") or "").strip().replace("\n", " ")[:60], cls=(a.get("class") or "")[:80], xpath=e["xpath"]["chrome"], box=[*e["location"], *e["dimension"]],
             attrs={k: str(v)[:70] for k, v in a.items() if k not in ("class", ATTR)})
    if els is not None:
        par = els.get(e["xpath"]["chrome"].rsplit("/", 1)[0])
        if par: d["parent"] = dict(tag=par["tag"], cls=((par.get("attributes") or {}).get("class") or "")[:60], text=(par.get("innerText") or "").strip().replace("\n", " ")[:60])
    return d

def source_hits(release, ident):
    """lines of the application source (not tests) that define the test id at that release"""
    if not REPO: return []
    pats = [f'{ATTR}="{ident}"', f"{ATTR}='{ident}'", f'{ATTR}={{"{ident}"}}', f'"{ident}"']
    hits = []
    for pat in pats[:3]:
        r = subprocess.run(["git", "-C", REPO, "grep", "-n", "-F", "-e", pat, release, "--"], capture_output=True, text=True)
        for line in r.stdout.splitlines():
            f = line.split(":", 2)
            if len(f) < 3 or re.search(r"(test|spec|snap|\.md|\.json|docs/|e2e|node_modules)", f[1], re.I): continue
            hits.append(f"{f[1]}:{f[2].strip()[:120] if f[2].strip().isdigit() is False else ''}".replace("::", ":"))
        if hits: break
    return hits[:4]

groups = collections.OrderedDict()   # (id, from, to) -> dict
total = 0
for site in manifest:
    rel, cache = site["releases"], {}
    def load(i):
        if i not in cache:
            c = json.load(open(rel[i]["json"]))
            cache[i] = ({e["xpath"]["chrome"]: e for e in c["elements"]}, Image.open(rel[i]["png"]).convert("RGB") if os.path.exists(rel[i]["png"]) else None)
        return cache[i]
    for row in site["rows"]:
        prev = None
        for i, x in enumerate(row["xpaths"]):
            if x == "-": continue
            els, img = load(i); e = els.get(x)
            if e is None or img is None: continue
            gray = np.asarray(img.convert("L").crop((e["location"][0], e["location"][1], e["location"][0] + max(e["dimension"][0], 2), e["location"][1] + max(e["dimension"][1], 2))).resize((24, 24)), dtype=float)
            if prev is not None:
                total += 1
                pe, pi = prev["e"], prev["i"]
                moved = abs(e["location"][0] - pe["location"][0]) + abs(e["location"][1] - pe["location"][1])
                vis = float(np.abs(gray - prev["gray"]).mean())
                reasons = []
                if e["tag"] != pe["tag"]: reasons.append(f"tag {pe['tag'].lower()} to {e['tag'].lower()}")
                if info(e)["text"] != info(pe)["text"]: reasons.append("text changed")
                if moved > MOVED: reasons.append(f"moved {moved} px")
                if vis > VIS: reasons.append(f"looks different ({vis:.0f})")
                g = groups.setdefault((row["id"], rel[pi]["release"], rel[i]["release"]), dict(id=row["id"], frm=rel[pi]["release"], to=rel[i]["release"], states=[], reasons=reasons, example=None, vis=vis))
                g["states"].append(site["state"])
                if reasons and not g["reasons"]: g["reasons"] = reasons
                if g["example"] is None or (reasons and not g.get("ex_reasons")):
                    g["example"] = (prev["img"], img, pe, e, prev["els"], els); g["ex_reasons"] = bool(reasons)
            prev = dict(e=e, i=i, gray=gray, img=img, els=els)

review = [g for g in groups.values() if g["reasons"]]
auto = [g for g in groups.values() if not g["reasons"]]
random.Random(1).shuffle(auto)
items = []
for kind, gs in (("review", review), ("spot", auto[:SPOT])):
    for g in gs:
        a_img, b_img, ae, be, aels, bels = g["example"]
        key = re.sub(r"[^A-Za-z0-9_.~:@+-]", "_", f"{g['id']}__{g['frm']}__{g['to']}")
        ta, tb = tile(a_img, ae, g["frm"]), tile(b_img, be, g["to"])
        film = Image.new("RGB", (ta.width + tb.width + 14, TILE_H + CAP), (200, 205, 214)); film.paste(ta, (0, 0)); film.paste(tb, (ta.width + 14, 0))
        film.save(f"{out}/img/{key}.jpg", quality=84)
        def page(img, e):
            x, y = e["location"]; w, h = e["dimension"]; top = max(0, y - 380); bot = min(img.height, y + h + 380)
            c = img.crop((0, top, img.width, bot)); ImageDraw.Draw(c).rectangle([x, y - top, x + max(w, 2), y - top + max(h, 2)], outline=(220, 38, 38), width=5)
            sc = 620 / c.width; return c.resize((620, max(60, int(c.height * sc))))
        pa, pb = page(a_img, ae), page(b_img, be); H = max(pa.height, pb.height) + CAP
        full = Image.new("RGB", (pa.width + pb.width + 14, H), (200, 205, 214)); full.paste(pa, (0, CAP)); full.paste(pb, (pa.width + 14, CAP))
        d = ImageDraw.Draw(full); d.text((5, 3), g["frm"], fill=(20, 24, 36), font=font); d.text((pa.width + 19, 3), g["to"], fill=(20, 24, 36), font=font)
        full.save(f"{out}/img/full_{key}.jpg", quality=72)
        items.append(dict(key=key, app=APP, kind=kind, id=g["id"], frm=g["frm"], to=g["to"], states=sorted(set(g["states"])), reasons=g["reasons"], before=info(ae, aels), after=info(be, bels), img=f"img/{key}.jpg", full=f"img/full_{key}.jpg", src_before=source_hits(g["frm"], g["id"]), src_after=source_hits(g["to"], g["id"])))
sev = lambda r: (r["kind"] != "review", not any(x.startswith("tag") for x in r["reasons"]), -len(r["reasons"]), r["id"])
items.sort(key=sev)
for c in range(0, len(items), 25): json.dump({"items": items[c:c + 25]}, open(f"{out}/chunks/chunk_{c // 25}.json", "w"))
msg = (f"{total} transitions, {len(groups)} distinct changes; {len(review)} need review ({sum(len(g['states']) for g in review)} state-level transitions), "
       f"{len(auto)} accepted automatically; {SPOT} of those added as a spot check; {len(items)} items in {(len(items) + 24) // 25} chunks")
open(f"{out}/summary.txt", "w").write(msg + "\n"); print(msg)
