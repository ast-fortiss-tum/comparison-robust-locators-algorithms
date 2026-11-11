import os
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns

RESULTS_DIR = "results/survivability_results"
ALGORITHMS = {
    "LegacySimilo": "LegacySimilo",
    "OptimizedSimilo": "OptimizedSimilo",
    "VonSimilo": "VonSimilo",
    "COLOR": "Color",
    "Multi-Locator": "Multi-Locator",
    "ROBULA+": "RobulaPlus",
    "VISTA": "Vista"
}
CSV_FILES = {
    "All Elements": "element_summary_all.csv",
    "Minor Change": "element_summary_minor.csv",
    "Major Change": "element_summary_major.csv",
}
PALETTE = {
    "LegacySimilo": "#4C78A8",   # blue
    "OptimizedSimilo": "#F58518",# orange
    "VISTA": "#54A24B",          # green
    "ROBULA+": "#B279A2",     # purple
    "COLOR": "#E45756",          # red
    "VonSimilo": "#72B7B2",      # teal
    "Multi-Locator": "#FF9DA6"    # pink
}

def _coerce_ratio(series: pd.Series) -> pd.Series:
    s = series.astype(str).str.strip().str.replace(",", ".", regex=False)
    out = pd.to_numeric(s, errors="coerce")
    return out[(out >= 0.0) & (out <= 1.0)]

def load_rows(csv_relpath: str, label: str):
    rows = []
    for algo_label, dirname in ALGORITHMS.items():
        path = os.path.join(RESULTS_DIR, dirname, csv_relpath)
        if not os.path.isfile(path):
            print(f"[warn] missing file: {path}")
            continue

        df = pd.read_csv(path)
        # pick the right column
        col = "survival_ratio" if "survival_ratio" in df.columns else (
            "survival_ratio_in_class" if "survival_ratio_in_class" in df.columns else None
        )
        if col is None:
            print(f"[warn] {path} has no survival_ratio column. Columns: {list(df.columns)}")
            continue

        vals = _coerce_ratio(df[col]).dropna()
        if vals.empty:
            print(f"[warn] no numeric ratios in {path}")
            continue

        for v in vals:
            rows.append({"algorithm": algo_label, "survival_ratio": float(v), "filter": label})

        # quick preview so you can see the spread
        print(f"[info] {algo_label} {label}: n={len(vals)}, min={vals.min():.3f}, "
              f"Q1={vals.quantile(0.25):.3f}, med={vals.median():.3f}, "
              f"Q3={vals.quantile(0.75):.3f}, max={vals.max():.3f}")
    return rows

def plot_panel(df: pd.DataFrame, title: str, out_name: str):
    if df.empty:
        print(f"[skip] No data to plot for {title}")
        return

    # counts per algorithm (only keep ones that actually have data)
    counts = df.groupby("algorithm")["survival_ratio"].size()
    order = [a for a in ALGORITHMS.keys() if a in counts.index]
    if not order:
        print(f"[skip] No algorithms with data for {title}")
        return

    # palette in the same (possibly reduced) order
    pal_list = [PALETTE[a] for a in order]

    plt.figure(figsize=(10, 6), dpi=150)
    ax = sns.boxplot(
        data=df[df["algorithm"].isin(order)],
        x="algorithm", y="survival_ratio",
        order=order,
        hue="algorithm", palette=PALETTE, legend=False,   # fixes seaborn future warning
        whis=(5, 95), showfliers=False, width=0.55,
        linewidth=1.6,
        medianprops=dict(color="black", linewidth=2.2),
        whiskerprops=dict(color="black", linewidth=1.6),
        capprops=dict(color="black", linewidth=1.6),
        boxprops=dict(edgecolor="black", linewidth=1.6)
    )

    ax.set_ylim(0, 1)
    ax.set_ylabel("Survival Ratio")
    ax.set_xlabel("Algorithm")
    ax.set_title(title)

    # annotate (n=…) under each tick
    tick_labels = [f"{a}\n(n={counts.get(a, 0)})" for a in order]
    ax.set_xticklabels(tick_labels)

    plt.tight_layout()
    out_path = os.path.join(RESULTS_DIR, "figures", out_name)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    plt.savefig(out_path)
    plt.close()
    print(f"[ok] Figure written to {out_path}")

def main():
    all_rows = []
    for label, csv_file in CSV_FILES.items():
        all_rows.extend(load_rows(csv_file, label))

    big = pd.DataFrame(all_rows)
    if big.empty:
        raise RuntimeError("No survival data loaded!")

    for label, _ in CSV_FILES.items():
        sub = big[big["filter"] == label]
        out = f"survival_ratios_boxplot_{label.replace(' ', '_').lower()}.png"
        plot_panel(sub, f"Survival Ratios — {label}", out)

if __name__ == "__main__":
    main()