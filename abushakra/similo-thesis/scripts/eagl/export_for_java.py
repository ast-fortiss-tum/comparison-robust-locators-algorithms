"""Export EAGL's stability model and golden fixtures for the Java port (de.tum.ls4.generators.Eagl).

Runs inside the isolated container (see export_for_java.sh): the model is a pickle, loading it executes code.

Outputs (in --out):
  eagl-forest.bin.gz   the RandomForestRegressor as plain data (big-endian, read by java.io.DataInputStream):
                         magic "EAGLRF1\n", int nFeatures, int nTrees, then per tree: int nodeCount and per node
                         byte feature (-1 = leaf), double threshold, int left, int right, double value
  golden/<site>_<date>.json.gz
                       the BeautifulSoup tree EAGL sees (html.parser), the tracked targets with the locator EAGL
                       generates for them (or "!<ExceptionType>"), and model inputs with sklearn's predictions.
Tree encoding: a Tag is [name, [attr, value, attr, value, ...], [children]] (value: str, or list for multi-valued
attributes such as class); any string node (text, comment, doctype, ...) is 1 if it equals "\\n", else 0.
Targets are numbered by pre-order position among Tags.
"""
import argparse
import gzip
import json
import os
import struct
import sys

from bs4 import BeautifulSoup, Tag

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)) + "/..")
from eagl_generate import targets_by_date, resolve_absolute  # noqa: E402

SITES = "sites"


def export_forest(model, path):
    with gzip.open(path, "wb") as f:
        f.write(b"EAGLRF1\n")
        f.write(struct.pack(">ii", model.n_features_in_, len(model.estimators_)))
        for est in model.estimators_:
            t = est.tree_
            f.write(struct.pack(">i", t.node_count))
            for i in range(t.node_count):
                leaf = t.children_left[i] == -1
                f.write(struct.pack(">bdiid", -1 if leaf else int(t.feature[i]), float(t.threshold[i]),
                                    int(t.children_left[i]), int(t.children_right[i]), float(t.value[i][0][0])))


def encode(node, index):
    if not isinstance(node, Tag):
        return 1 if node == "\n" else 0
    index[id(node)] = len(index)
    attrs = []
    for k, v in node.attrs.items():
        attrs += [k, v]
    return [node.name, attrs, [encode(c, index) for c in node.contents]]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--eagl", required=True)
    p.add_argument("--model", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--pages", nargs="*", default=None, help="site/date pairs; default: every page with a chromeDOM.html")
    p.add_argument("sites", nargs="+")
    a = p.parse_args()
    sys.path.insert(0, os.path.abspath(a.eagl))
    import Classes.PredictionClass as PC
    from Classes.soup_operations import cal_length, cal_depth, get_siblings_soup, get_siblings_attr

    PC.XpathClass.DP.load_model(a.model)
    model = PC.XpathClass.DP.model
    os.makedirs(a.out + "/golden", exist_ok=True)
    export_forest(model, a.out + "/eagl-forest.bin.gz")

    class Generator(PC.XpathClass):
        def __init__(self, dom):
            self.xpath_list, self.scaled, self.dom = [], False, dom

    import bs4.builder
    print("bs4 multi-valued attributes:", bs4.builder.HTMLTreeBuilder.DEFAULT_CDATA_LIST_ATTRIBUTES)
    for site in a.sites:
        per_date = targets_by_date(site)
        for date in sorted(d for d in os.listdir(f"{SITES}/{site}") if d[:2] == "20"):
            if a.pages and f"{site}/{date}" not in a.pages:
                continue
            dom_file = f"{SITES}/{site}/{date}/chromeDOM.html"
            if not os.path.exists(dom_file):
                continue
            with open(dom_file, encoding="utf-8") as f:
                soup = BeautifulSoup(f.read(), "html.parser")
            index = {}
            tree = [encode(c, index) for c in soup.contents]
            with open(f"{SITES}/{site}/{date}/elements.json") as f:
                recorded = {e["xpath"]["chrome"]: e for e in json.load(f)}
            gen = Generator(soup)
            targets, vectors = [], []
            for abs_xpath in per_date.get(date, []):
                element = recorded.get(abs_xpath)
                target = resolve_absolute(soup, abs_xpath)
                if element is None or target is None or target.name != element["tag"].lower():
                    continue
                gen.reset_class()
                try:
                    result = gen.generate_locating_strategy_Xpath(target)
                except Exception as e:
                    result = "!" + type(e).__name__
                targets.append([index[id(target)], result])
                # model inputs along the ancestry, as computed by EAGL
                for el in [target] + list(target.parents)[:-1]:
                    pos, _, nsib = get_siblings_soup(el)
                    vectors.append([pos, cal_length(el, True), cal_depth(el),
                                    nsib, len(el.contents) + len(el.attrs), False])
                    for attr in el.attrs:
                        vectors.append([get_siblings_attr(el, attr), cal_length(el, True) + 1, 0,
                                        len(el.attrs) - 1, 1, True])
            uniq = list({json.dumps(v): v for v in vectors}.values())
            preds = model.predict(uniq).tolist() if uniq else []
            golden = {"site": site, "date": date, "tree": tree, "targets": targets,
                      "vectors": [v + [y] for v, y in zip(uniq, preds)]}
            with gzip.open(f"{a.out}/golden/{site}_{date}.json.gz", "wt", encoding="utf-8") as f:
                json.dump(golden, f, separators=(",", ":"))
            errors = sum(1 for t in targets if t[1].startswith("!"))
            print(f"{site} {date}: tags={len(index)} targets={len(targets)} errors={errors} vectors={len(uniq)}", flush=True)


if __name__ == "__main__":
    main()
