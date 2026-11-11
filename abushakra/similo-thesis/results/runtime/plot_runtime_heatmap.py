import os
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt

# Algorithms (must match your folder names under results/runtime/)
ALGORITHMS = [
    "Montoto",
    "SeleniumIDE",
    "LegacySimilo",
    "Color",
    "Multi-Locator",
    "OptimizedSimilo",
    "RobulaPlus",
    "Vista",
    "VonSimilo"
]

RUNTIME_DIR = "results/runtime"

def load_algorithm_data(algorithm):
    algo_dir = os.path.join(RUNTIME_DIR, algorithm)
    if not os.path.isdir(algo_dir):
        print(f"[warn] No folder for {algorithm}")
        return pd.DataFrame()

    dfs = []
    for f in os.listdir(algo_dir):
        if f.startswith("timing_log_") and f.endswith(".csv"):
            path = os.path.join(algo_dir, f)
            df = pd.read_csv(path, header=None, names=["site", "algorithm", "xpath", "time_ms"])
            dfs.append(df)

    if not dfs:
        return pd.DataFrame()

    data = pd.concat(dfs, ignore_index=True)

    # Average runtime per (site, xpath) across attempts
    elem_avg = data.groupby(["site", "xpath"], as_index=False)["time_ms"].mean()

    # Average per site
    site_avg = elem_avg.groupby("site")["time_ms"].mean().reset_index()
    site_avg["algorithm"] = algorithm
    return site_avg

def main():
    all_data = []
    for algo in ALGORITHMS:
        df = load_algorithm_data(algo)
        if not df.empty:
            all_data.append(df)

    combined = pd.concat(all_data, ignore_index=True)

    # Pivot to matrix: algorithms × sites
    pivot = combined.pivot(index="algorithm", columns="site", values="time_ms")

    # Add overall mean across all sites
    pivot["OVERALL"] = pivot.mean(axis=1)

    # Sort algorithms by OVERALL runtime (ascending = fastest first)
    pivot = pivot.sort_values("OVERALL", ascending=True)

    # Save CSV
    out_csv = os.path.join(RUNTIME_DIR, "avg_runtime_per_site.csv")
    pivot.to_csv(out_csv)
    print(f"[ok] CSV saved to {out_csv}")

    # Plot heatmap
    plt.figure(figsize=(15, 6))
    sns.heatmap(
        pivot, annot=True, fmt=".2f", cmap="YlOrRd", cbar_kws={'label': 'Avg runtime (ms)'}
    )
    plt.title("Average Runtime per Site and Algorithm (Sorted by OVERALL)")
    plt.xlabel("Site")
    plt.ylabel("Algorithm")
    plt.xticks(rotation=90)
    plt.tight_layout()

    out_png = os.path.join(RUNTIME_DIR, "avg_runtime_heatmap.png")
    plt.savefig(out_png, dpi=300)
    print(f"[ok] Heatmap saved to {out_png}")
    plt.show()

if __name__ == "__main__":
    main()
