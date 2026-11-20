"""Bridge to EAGL (Taha, Sohn, Papadakis, TOSEM 2026; https://github.com/hilalosoft/EAGL).

Generates EAGL locators with the authors' own implementation for every tracked element of every snapshot and writes
them to cache/eagl_cache.json, keyed like cache/robula_cache.json: "<element url>|<element ID-XPath>". The Scala
locator eagl.Eagl reads this cache in init() and evaluates the XPath on the new version in locate().

--scaled uses the paper's per-DOM min-max features (EAGL's own scaled branch is broken, see ScaledGenerator);
--model-map gives each site its own model (e.g. a category model that was not trained on that site).

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
    p.add_argument("--model", help="stability model (.joblib) for all sites")
    p.add_argument("--model-map", help='JSON file {"site": "model.joblib", ...}: a model per site (overrides --model)')
    p.add_argument("--scaled", action="store_true",
                   help="min-max scale the features per DOM, as in training (the paper's setting); see ScaledFeatures")
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


def training_vectors(soup, SO):
    """The (position, length, depth, nr_siblings, nr_children) vectors EAGL's training extracted from one DOM
    (featureClass.compare_dom_recursive / generate_vectors_from_soup / generate_vectors_for_attr, without the change
    labels): every tag of <body> outside head/script/style subtrees, plus an attribute vector for each id."""
    body = soup.find(["body"])
    if body is None:
        raise ValueError("no <body>: EAGL's training skips such DOMs")
    rows, stack = [], [body]
    while stack:
        el = stack.pop()
        if type(el) is not Tag or el.name in ("head", "script", "style"):
            continue
        stack.extend(el.contents)
        position, _, nr_siblings = SO.get_siblings_soup(el)
        depth = SO.cal_depth(el)
        if "id" in el.attrs:
            rows.append((0, SO.cal_length(el, True), depth, len(el.attrs) - 1, 0))
        rows.append((position, SO.cal_length(el), depth, nr_siblings, len(el.contents) + len(el.attrs)))
    return rows


def main():
    args = parse_args()
    sys.path.insert(0, os.path.abspath(args.eagl))
    import math
    import Classes.PredictionClass as PC  # noqa: E402  (EAGL's implementation)
    import Classes.soup_operations as SO  # noqa: E402
    from sklearn.preprocessing import MinMaxScaler

    class Generator(PC.XpathClass):
        def __init__(self, dom):  # same as XpathClass.__init__ without reloading the model per page
            self.xpath_list = []
            self.scaled = False  # EAGL_excutor.py uses the default (unscaled) features
            self.dom = dom

    class ScaledGenerator(Generator):
        """The paper's setting (Sec. 4.4.3): features min-max scaled per DOM, as the released models were trained.

        EAGL's own scaled branch cannot run: cal_maximums() is never called (division by zero), it sets max_siblings to
        len(<3-tuple>) = 3, it divides by maxima instead of min-max scaling, and its attribute vector reuses the
        element's nr_siblings. Here the scaler is fitted on the DOM exactly as in featureClass.generate_feature_vector_dom
        and applied to the vectors of the unscaled branch (unchanged otherwise)."""

        def __init__(self, dom):
            super().__init__(dom)
            self.scaled = True
            self.scaler = MinMaxScaler().fit(training_vectors(dom, SO))

        def _predict(self, position, length, depth, nr_siblings, nr_children, isattribute):
            x = self.scaler.transform([[position, length, depth, nr_siblings, nr_children]])[0]
            return self.DP.model.predict([(*x, isattribute)])

        def element_stability_prediction(self, element, distance):  # = XpathClass's, with _predict
            length = SO.cal_length(element, True)
            depth = SO.cal_depth(element)
            nr_children = len(element.contents) + len(element.attrs)
            position, siblings_vector, nr_siblings = SO.get_siblings_soup(element)
            prediction = self._predict(position, length, depth, nr_siblings, nr_children, False)
            prediction = prediction * math.pow(PC.dfactor, distance)
            element_dict = {"soup": element, element.name: prediction}
            for attr in element.attrs:
                if attr in PC.timestamped_attributes:
                    continue
                length = SO.cal_length(element, True) + 1
                depth = 0
                nr_children = 1
                position = SO.get_siblings_attr(element, attr)
                prediction = 1 - self._predict(position, length, depth, len(element.attrs) - 1, nr_children, True)
                prediction = prediction * math.pow(PC.dfactor, distance)
                element_dict[attr] = prediction
            return element_dict

    if args.model_map:
        with open(args.model_map) as f:
            model_of = json.load(f)
    elif args.model:
        model_of = {site: args.model for site in args.sites}
    else:
        sys.exit("--model or --model-map is required")
    missing = [s for s in args.sites if s not in model_of]
    if missing:
        sys.exit(f"no model for {missing}")
    make = ScaledGenerator if args.scaled else Generator
    loaded = None

    cache = {}
    if os.path.exists(args.cache):
        with open(args.cache) as f:
            cache = json.load(f)

    model_name = (os.path.splitext(os.path.basename(args.model_map or args.model))[0]
                  + ("_scaled" if args.scaled else ""))
    os.makedirs(args.report_dir, exist_ok=True)
    report_path = f"{args.report_dir}/generation_{model_name}.csv"
    new_report = not os.path.exists(report_path)
    report = open(report_path, "a", newline="")
    out = csv.writer(report)
    if new_report:
        out.writerow(["site", "date", "absolute_xpath", "idxpath", "status", "eagl_xpath", "ms", "model"])

    totals = {"ok": 0, "unresolved": 0, "error": 0}
    for site in sorted(args.sites, key=lambda s: model_of[s]):  # load each model once
        if model_of[site] != loaded:
            print(f"loading {model_of[site]}", flush=True)
            PC.XpathClass.DP.load_model(model_of[site])  # XpathClass keeps the predictor in a class attribute
            loaded = model_of[site]
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
            try:
                gen = make(soup)
            except ValueError as e:
                print(f"skip  {site} {date}: {e}")
                continue
            counts = {"ok": 0, "unresolved": 0, "error": 0}
            for abs_xpath in per_date.get(date, []):
                element = recorded.get(abs_xpath)
                if element is None or not element.get("url"):
                    continue
                key = f'{element["url"]}|{element["xpath"]["idxpath"]}'
                target = resolve_absolute(soup, abs_xpath)
                if target is None or target.name != element["tag"].lower():
                    counts["unresolved"] += 1
                    out.writerow([site, date, abs_xpath, element["xpath"]["idxpath"], "unresolved", "", "", loaded])
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
                out.writerow([site, date, abs_xpath, element["xpath"]["idxpath"], status, xpath, f"{ms:.1f}", loaded])
            for k in totals:
                totals[k] += counts[k]
            print(f"{site} {date}: ok={counts['ok']} unresolved={counts['unresolved']} error={counts['error']}", flush=True)
            with open(args.cache, "w") as f:
                json.dump(cache, f, indent=1)
    report.close()
    print(f"TOTAL ok={totals['ok']} unresolved={totals['unresolved']} error={totals['error']} -> {args.cache}, {report_path}")


if __name__ == "__main__":
    main()
