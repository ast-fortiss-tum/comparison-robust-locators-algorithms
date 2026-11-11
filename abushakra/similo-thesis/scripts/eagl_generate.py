"""Bridge to EAGL (Taha, Sohn, Papadakis, TOSEM 2026; https://github.com/hilalosoft/EAGL).

Generates EAGL locators with the authors' own implementation for every tracked element of every snapshot and writes
them to cache/eagl_cache.json, keyed like cache/robula_cache.json: "<element url>|<element ID-XPath>". The Scala
locator eagl.Eagl reads this cache in init() and evaluates the XPath on the new version in locate().

Run with the isolated environment (scikit-learn 1.2.1 as pinned by EAGL):
    .venv-eagl/bin/python scripts/eagl_generate.py --eagl /path/to/EAGL --model /path/to/model.joblib site [site ...]

A per-element report goes to results/eagl/generation_<model>.csv.
"""
import argparse
import csv
import json
import os
import re
import sys
import time

from bs4 import BeautifulSoup, Tag

SITES = "sites"


def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument("--eagl", required=True, help="path to a checkout of hilalosoft/EAGL")
    p.add_argument("--model", required=True, help="stability model (.joblib)")
    p.add_argument("--cache", default="cache/eagl_cache.json")
    p.add_argument("--report-dir", default="results/eagl")
    p.add_argument("sites", nargs="+")
    return p.parse_args()


def targets_by_date(site):
    """Absolute XPaths of the tracked elements per date ('-' = deleted)."""
    with open(f"{SITES}/{site}/xpaths.csv") as f:
        rows = [line.rstrip("\n").split(";") for line in f if line.strip()]
    dates = rows[0]
    return {d: [r[i] for r in rows[1:] if i < len(r) and r[i].startswith("/")] for i, d in enumerate(dates)}


STEP = re.compile(r"^([a-zA-Z0-9_-]+)(?:\[(\d+)\])?$")


def resolve_absolute(soup, xpath):
    """Resolve a recorded absolute XPath like /html/body/div[2]/a (index omitted when unique) in the soup."""
    node = soup
    for step in xpath.strip("/").split("/"):
        m = STEP.match(step)
        if not m:
            return None
        tag, idx = m.group(1).lower(), int(m.group(2) or 1)
        children = [c for c in node.children if isinstance(c, Tag) and c.name == tag]
        if len(children) < idx:
            return None
        node = children[idx - 1]
    return node


def main():
    args = parse_args()
    sys.path.insert(0, os.path.abspath(args.eagl))
    import Classes.PredictionClass as PC  # noqa: E402  (EAGL's implementation)

    # Load the model once; XpathClass keeps the predictor in a class attribute.
    PC.XpathClass.DP.load_model(args.model)

    class Generator(PC.XpathClass):
        def __init__(self, dom):  # same as XpathClass.__init__ without reloading the model per page
            self.xpath_list = []
            self.scaled = False  # EAGL_excutor.py uses the default (unscaled) features
            self.dom = dom

    cache = {}
    if os.path.exists(args.cache):
        with open(args.cache) as f:
            cache = json.load(f)

    model_name = os.path.splitext(os.path.basename(args.model))[0]
    os.makedirs(args.report_dir, exist_ok=True)
    report_path = f"{args.report_dir}/generation_{model_name}.csv"
    new_report = not os.path.exists(report_path)
    report = open(report_path, "a", newline="")
    out = csv.writer(report)
    if new_report:
        out.writerow(["site", "date", "absolute_xpath", "idxpath", "status", "eagl_xpath", "ms"])

    totals = {"ok": 0, "unresolved": 0, "error": 0}
    for site in args.sites:
        per_date = targets_by_date(site)
        for date in sorted(d for d in os.listdir(f"{SITES}/{site}") if d[:2] == "20"):
            dom_file = f"{SITES}/{site}/{date}/chromeDOM.html"
            if not os.path.exists(dom_file):
                print(f"skip  {site} {date}: no chromeDOM.html")
                continue
            with open(dom_file, encoding="utf-8") as f:
                soup = BeautifulSoup(f.read(), "html.parser")
            with open(f"{SITES}/{site}/{date}/elements.json") as f:
                recorded = {e["xpath"]["chrome"]: e for e in json.load(f)}
            gen = Generator(soup)
            counts = {"ok": 0, "unresolved": 0, "error": 0}
            for abs_xpath in per_date.get(date, []):
                element = recorded.get(abs_xpath)
                if element is None or not element.get("url"):
                    continue
                key = f'{element["url"]}|{element["xpath"]["idxpath"]}'
                target = resolve_absolute(soup, abs_xpath)
                if target is None or target.name != element["tag"].lower():
                    counts["unresolved"] += 1
                    out.writerow([site, date, abs_xpath, element["xpath"]["idxpath"], "unresolved", "", ""])
                    continue
                gen.reset_class()
                start = time.perf_counter()
                try:
                    xpath = gen.generate_locating_strategy_Xpath(target)
                    status = "ok"
                    cache[key] = xpath
                except Exception as e:  # EAGL raises/asserts on some DOM shapes; record and continue
                    xpath, status = f"{type(e).__name__}: {e}"[:200], "error"
                ms = (time.perf_counter() - start) * 1000
                counts[status] += 1
                out.writerow([site, date, abs_xpath, element["xpath"]["idxpath"], status, xpath, f"{ms:.1f}"])
            for k in totals:
                totals[k] += counts[k]
            print(f"{site} {date}: ok={counts['ok']} unresolved={counts['unresolved']} error={counts['error']}", flush=True)
            with open(args.cache, "w") as f:
                json.dump(cache, f, indent=1)
    report.close()
    print(f"TOTAL ok={totals['ok']} unresolved={totals['unresolved']} error={totals['error']} -> {args.cache}, {report_path}")


if __name__ == "__main__":
    main()
