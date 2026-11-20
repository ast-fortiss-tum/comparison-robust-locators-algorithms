"""Fix rebuilt pages (chromeDOM.html) whose labelled absolute XPaths are shifted by elements injected at the top of
<body> when the Wayback snapshot is replayed today: ad slots, app-install banners, consent layers.

For each snapshot whose labels do not all resolve, try removing one or two top-level <body> children and keep the
removal that resolves the most labels (only if it resolves strictly more). The elements are cut out of the HTML text,
so the rest of the file stays byte-identical; the result is re-parsed to check that exactly those children are gone
and that the label count matches. The original is kept as chromeDOM.unfixed.html, and a row with variant "fixed" is
appended to sites/regeneration_log.csv (read by regen_progress.py).

Labels (xpaths.csv) are never changed. Snapshots short of a few labels because dynamic content did not render are
left alone: removing elements cannot add them.

usage: .venv/bin/python scripts/fix_regen_offsets.py [--dry-run] [site ...]   (default: every rebuilt site, no zz_/rb_)
"""
import csv, datetime, itertools, os, re, shutil, sys
from lxml import html

VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}
RAW = {"script", "style", "textarea", "title"}
TAG = re.compile(r"<!--.*?-->|<(/?)([a-zA-Z][a-zA-Z0-9:-]*)((?:\"[^\"]*\"|'[^']*'|[^'\">])*)>", re.S)


def top_level_spans(src):
    """(start, end, tag) of every element directly under <body>, in document order, from the HTML text."""
    # find the real <body> tag, skipping script/style contents (which may contain the string "<body")
    pos, body_end = 0, None
    while True:
        t = TAG.search(src, pos)
        if not t:
            return []
        pos = t.end()
        if t.group(0).startswith("<!--") or t.group(1):
            continue
        tag = t.group(2).lower()
        if tag == "body":
            body_end = t.end(); break
        if tag in RAW:
            end = re.compile(rf"</{tag}\s*>", re.I).search(src, pos)
            pos = end.end() if end else len(src)
    pos, depth, spans, start, tag0 = body_end, 0, [], None, None
    while True:
        t = TAG.search(src, pos)
        if not t:
            break
        pos = t.end()
        if t.group(0).startswith("<!--"):
            continue
        close, tag, attrs = t.group(1), t.group(2).lower(), t.group(3)
        if close:
            if tag == "body" and depth == 0:
                break
            depth -= 1
            if depth == 0 and start is not None:
                spans.append((start, t.end(), tag0)); start = None
            continue
        selfclosing = attrs.rstrip().endswith("/")
        if depth == 0:
            start, tag0 = t.start(), tag
        if tag in RAW:
            end = re.compile(rf"</{tag}\s*>", re.I).search(src, pos)
            pos = end.end() if end else len(src)
            if depth == 0:
                spans.append((start, pos, tag)); start = None
            continue
        if tag in VOID or selfclosing:
            if depth == 0:
                spans.append((start, t.end(), tag)); start = None
            continue
        depth += 1
    return spans


def resolved(tree, xs):
    return sum(1 for x in xs if tree.xpath(x))


def describe(el):
    return el.tag + ("#" + el.get("id") if el.get("id") else "") + ("." + (el.get("class") or "").split(" ")[0] if el.get("class") else "")


def fix(site, date, xs, dry):
    path = f"sites/{site}/{date}/chromeDOM.html"
    src = open(path, encoding="utf-8").read()
    tree = html.fromstring(src.encode())
    base = resolved(tree, xs)
    if base == len(xs):
        return None
    kids = [c for c in tree.xpath("/html/body/*")]
    candidates = [i for i, c in enumerate(kids[:15]) if c.tag not in ("script", "noscript", "style", "link", "meta")]
    best = (base, None)
    for k in (1, 2):
        for combo in itertools.combinations(candidates, k):
            t2 = html.fromstring(src.encode()); k2 = t2.xpath("/html/body/*")
            for i in combo:
                k2[i].getparent().remove(k2[i])
            n = resolved(t2, xs)
            if n > best[0]:
                best = (n, combo)
        if best[0] == len(xs):
            break
    if best[1] is None:
        return (base, base, len(xs), "no removal helps")
    removed = [describe(kids[i]) for i in best[1]]
    # cut the same elements out of the text: match lxml's children to the text spans by tag sequence
    spans = top_level_spans(src)
    tags_lxml = [c.tag for c in kids]
    tags_text = [s[2] for s in spans]
    if tags_text[:len(tags_lxml)] != tags_lxml[:len(tags_text)] or len(tags_text) < max(best[1]) + 1:
        return (base, best[0], len(xs), "text and parse disagree; skipped: " + ", ".join(removed))
    out = src
    for i in sorted(best[1], reverse=True):
        s, e, _ = spans[i]
        out = out[:s] + out[e:]
    check = html.fromstring(out.encode())
    n = resolved(check, xs)
    if n != best[0] or len(check.xpath("/html/body/*")) != len(kids) - len(best[1]):
        return (base, best[0], len(xs), "verification failed; skipped: " + ", ".join(removed))
    if not dry:
        backup = f"sites/{site}/{date}/chromeDOM.unfixed.html"
        if not os.path.exists(backup):
            shutil.copyfile(path, backup)
        open(path, "w", encoding="utf-8").write(out)
        with open("sites/regeneration_log.csv", "a", newline="") as f:
            f.write(",".join([datetime.datetime.now().isoformat(), site, date, "fixed", str(len(xs)), str(n), str(n), "",
                              "removed " + " + ".join(removed).replace(",", " ")]) + "\n")
    return (base, n, len(xs), "removed " + " + ".join(removed))


def main():
    args = [a for a in sys.argv[1:] if a != "--dry-run"]
    dry = "--dry-run" in sys.argv
    sites = args or sorted(s for s in os.listdir("sites") if not s.startswith(("zz_", "rb_", "templates"))
                           and os.path.isfile(f"sites/{s}/xpaths.csv"))
    for site in sites:
        rows = list(csv.reader(open(f"sites/{site}/xpaths.csv"), delimiter=";"))
        hdr = rows[0]
        for d in hdr:
            if not os.path.exists(f"sites/{site}/{d}/chromeDOM.html"):
                continue
            i = hdr.index(d)
            xs = [r[i] for r in rows[1:] if len(r) > i and r[i].startswith("/")]
            r = fix(site, d, xs, dry)
            if r:
                print(f"{site} {d}: {r[0]}/{r[2]} -> {r[1]}/{r[2]}  {r[3]}", flush=True)


if __name__ == "__main__":
    main()
