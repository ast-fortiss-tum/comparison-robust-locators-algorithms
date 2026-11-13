import os
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns

RESULTS_DIR = "results/survivability_results"
ALGORITHMS = {
    "OptimizedSimilo": "OptimizedSimilo",
    "LegacySimilo": "LegacySimilo",
    "COLOR": "Color",
    "VonSimilo": "VonSimilo",
    "Multi-Locator": "Multi-Locator",
    "Multi-Locator (VISTA)": "Multi-Locator (VISTA)",
    "ROBULA+": "RobulaPlus",
    "VISTA": "Vista",
    "Multi-Locator Repair": "Multi-Locator Repair",
    "Multi-Locator Repair (VISTA)": "Multi-Locator Repair (VISTA)",
    "Montoto": "Montoto",
    "ID_XPath": "IDXpath",
    "SeleniumIDE": "SeleniumIDE"
}

# Define color palette for algorithms (updated colors)
PALETTE = {
    "VISTA": "#EECA3B",
    "ROBULA+": "#54A24B",
    "Multi-Locator": "#E45756",
    "Multi-Locator Repair": "#A63636",
    "VonSimilo": "#B279A2",
    "COLOR": "#4C78A8",
    "LegacySimilo": "#F58518",
    "OptimizedSimilo": "#72B7B2"
}

# (Panel title, CSV filename, fallback column names in priority order)
PANELS = [
    ("All Elements",  "element_summary_all.csv",   ["survival_ratio", "survival_ratio_in_class"]),
    ("Minor Change",  "element_summary_minor.csv", ["survival_ratio_in_class", "survival_ratio"]),
    ("Major Change",  "element_summary_major.csv", ["survival_ratio_in_class", "survival_ratio"]),
]

def find_existing_col(df: pd.DataFrame, candidates):
    for c in candidates:
        if c in df.columns:
            return c
    return None

def load_rows(csv_file: str, col_candidates, label: str):
    rows = []
    for algo_label, dirname in ALGORITHMS.items():
        path = os.path.join(RESULTS_DIR, dirname, csv_file)
        if not os.path.isfile(path):
            print(f"[warn] missing file: {path}")
            continue
        df = pd.read_csv(path)
        col = find_existing_col(df, col_candidates)
        if col is None:
            raise ValueError(f"CSV {path} has none of columns {col_candidates} (has: {list(df.columns)})")
        for _, row in df.iterrows():
            if pd.isna(row[col]):
                continue
            entry = {
                "algorithm": algo_label,
                "survival_ratio": float(row[col]) * 100,
                "subset": label,
            }
            if "site" in df.columns:
                entry["site"] = row["site"] if pd.notna(row["site"]) else "unknown"
            else:
                entry["site"] = "unknown"
            rows.append(entry)
    return rows

def main():
    plt.rcParams.update({
        "font.size": 12,
        "axes.labelsize": 13,
        "xtick.labelsize": 11,
        "ytick.labelsize": 11,
        "legend.fontsize": 11,
        "font.family": "serif",
        # "text.usetex": True,
        "axes.titleweight": "normal",
        "axes.labelweight": "normal",
        "axes.linewidth": 0.8,
        "xtick.direction": "in",
        "ytick.direction": "in",
        "xtick.top": True,
        "ytick.right": True,
    })

    # Prepare one figure with three subplots (one per panel)
    fig, axes = plt.subplots(nrows=3, ncols=1, figsize=(11, 13), dpi=300, sharex=True)
    if not isinstance(axes, (list, np.ndarray)):
        axes = [axes]
    out_dir = os.path.join(RESULTS_DIR, "figures")
    os.makedirs(out_dir, exist_ok=True)
    panel_dfs = []
    # Define original algorithms only for plotting (exclude new ones)
    original_algorithms = [
        "OptimizedSimilo",
        "LegacySimilo",
        "COLOR",
        "VonSimilo",
        "Multi-Locator",
        "ROBULA+",
        "VISTA"
    ]
    for idx, (panel_title, csv_name, ratio_cols) in enumerate(PANELS):
        rows = load_rows(csv_name, ratio_cols, panel_title)
        df = pd.DataFrame(rows)
        panel_dfs.append(df)
        print(f"[info] Panel '{panel_title}': loaded {len(df)} points")
        if df.empty:
            print(f"[warn] No data for panel '{panel_title}'")

        # Debug: print boxplot-related statistics per algorithm
        if not df.empty:
            for algo in df['algorithm'].unique():
                subset = df[df['algorithm'] == algo]['survival_ratio']
                count = len(subset)
                mean_val = subset.mean()
                median_val = subset.median()
                std_val = subset.std()
                min_val = subset.min()
                q1 = subset.quantile(0.25)
                q3 = subset.quantile(0.75)
                max_val = subset.max()
                count_ones = (subset == 100.0).sum()
                percent_ones = 100.0 * count_ones / count if count > 0 else 0.0
                print(f"[debug] {panel_title} - {algo}:")
                print(f"    count = {count}")
                print(f"    mean  = {mean_val:.2f}")
                print(f"    median= {median_val:.2f}")
                print(f"    std   = {std_val:.2f}")
                print(f"    min   = {min_val:.2f}")
                print(f"    Q1    = {q1:.2f}")
                print(f"    Q3    = {q3:.2f}")
                print(f"    max   = {max_val:.2f}")
                print(f"    count_1.0 = {count_ones}")
                print(f"    percent_1.0 = {percent_ones:.2f}%")

        ax = axes[idx]
        # Filter df to only original algorithms for plotting
        plot_df = df[df['algorithm'].isin(original_algorithms)]

        # Violinplot first (drawn behind boxplot), with transparency and no inner markings
        sns.violinplot(
            x="algorithm", y="survival_ratio", data=plot_df, ax=ax,
            palette=PALETTE,
            alpha=0.9,
            width=0.99,
            inner=None
        )
        # Boxplot with academic style: white boxes, black edges, thicker lines, mean marker smaller and red
        sns.boxplot(
            x="algorithm", y="survival_ratio", data=plot_df, ax=ax,
            whis=[5, 95], width=0.15, showmeans=True,
            meanprops={"marker": "o", "markerfacecolor": "red", "markeredgecolor": "red", "markersize": 4},
            boxprops={"facecolor": (1, 1, 1, 0.4), "edgecolor": "black", "linewidth": 1},
            medianprops={"color": "black", "linewidth": 1.2},
            whiskerprops={"color": "black", "linewidth": 1},
            capprops={"color": "black", "linewidth": 1},
            palette=PALETTE,
            dodge=False
        )

        # Add faint dashed horizontal lines for each algorithm's mean survival ratio
        # (Removed as per instructions)

        ax.yaxis.grid(True, linestyle="--", linewidth=0.7, alpha=0.7)
        ax.set_ylim(0, 100)
        ax.set_xlabel("")
        ax.set_ylabel(r"Survival Ratio (%)")
        # Title each subplot with the panel title
        ax.set_title(panel_title, fontsize=14, fontweight="normal", pad=10)
        # Set x-axis tick labels horizontal and larger for clarity in LaTeX
        ax.tick_params(axis='x', rotation=0, labelsize=11)
        ax.tick_params(axis='y', labelsize=11, labelrotation=0, labelcolor='black')
        # Set y-axis tick labels to use monospace font for alignment
        for label in ax.get_yticklabels():
            label.set_fontfamily('monospace')
        # Adjust axis spine colors to transparent grey (#AAAAAA with alpha)
        spine_color = (170/255, 170/255, 170/255, 0.5)
        for spine in ['left', 'bottom', 'top', 'right']:
            ax.spines[spine].set_color(spine_color)

        # Annotate each algorithm name below its respective box/violin
        xticks = ax.get_xticks()
        for i, algo_name in enumerate(original_algorithms):
            if i < len(xticks):
                ax.text(xticks[i], -5, algo_name, ha='center', va='top', fontsize=11, color='black', rotation=0)

    # Remove the generic x-axis label for the bottom subplot
    # Set x-axis tick labels explicitly to algorithm names in ALGORITHMS.keys()
    # axes[-1].set_xticklabels(list(ALGORITHMS.keys()), fontsize=12, fontweight='bold', color='black', rotation=0)
    # Remove x-tick labels for upper subplots
    for ax in axes[:-1]:
        ax.set_xticklabels([])
        ax.set_xlabel("")
    fig.tight_layout(rect=[0, 0, 1, 1], h_pad=2)
    out_path = os.path.join(out_dir, "survival_ratios_boxplot_combined.png")
    fig.savefig(out_path, bbox_inches="tight")
    plt.close(fig)
    print(f"[ok] Combined figure written to: {out_path}")

    # After processing all panels, compute and print mean survival ratios grouped by algorithm and subset
    if panel_dfs:
        combined_df = pd.concat(panel_dfs, ignore_index=True)
        grouped = combined_df.groupby(['algorithm', 'subset'])['survival_ratio'].mean().reset_index()
        print("\n[info] Mean survival ratios grouped by algorithm and subset:")
        for subset in grouped['subset'].unique():
            print(f"\nSubset: {subset}")
            subset_df = grouped[grouped['subset'] == subset]
            for _, row in subset_df.iterrows():
                print(f"  Algorithm: {row['algorithm']:15} Mean Survival Ratio: {row['survival_ratio']:.2f}")

        # Compute and print mean survival ratios grouped by site and subset
        if 'site' in combined_df.columns:
            grouped_site = combined_df.groupby(['site', 'subset'])['survival_ratio'].mean().reset_index()
            print("\n[info] Mean survival ratios grouped by site and subset:")
            for subset in grouped_site['subset'].unique():
                print(f"\nSubset: {subset}")
                subset_df = grouped_site[grouped_site['subset'] == subset]
                for _, row in subset_df.iterrows():
                    print(f"  Site: {row['site']:20} Mean Survival Ratio: {row['survival_ratio']:.2f}")

import numpy as np
if __name__ == "__main__":
    main()