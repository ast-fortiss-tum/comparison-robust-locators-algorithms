import os
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt

RUNTIME_DIR = "results/runtime"
ROBUSTNESS_CSV = "results/robustness_results/robustness_stats.csv"

# Map names from robustness CSV -> runtime folders
NAME_MAP = {
    "Optimized Similo": "OptimizedSimilo",
    "Legacy Similo": "LegacySimilo",
    "COLOR": "Color",
    "Multi-Locator": "Multi-Locator",
    "ROBULA+": "RobulaPlus",
    "VON Similo": "VonSimilo",
    "VISTA": "Vista"
}

PALETTE = {
    "OptimizedSimilo": "#72B7B2",   # Teal
    "LegacySimilo": "#F58518",      # Orange
    "Color": "#4C78A8",             # Blue
    "Multi-Locator": "#E45756",     # Red
    "RobulaPlus": "#54A24B",        # Green
    "VonSimilo": "#B279A2",         # Purple
    "Vista": "#EECA3B"              # Yellow
}

# Mapping normalized algorithm keys to display names for plot labels
DISPLAY_NAME = {
    "RobulaPlus": "ROBULA+",
    "Vista": "VISTA",
    "Color": "COLOR",
    "LegacySimilo": "LegacySimilo",
    "OptimizedSimilo": "OptimizedSimilo",
    "VonSimilo": "VonSimilo",
    "Multi-Locator": "Multi-Locator"
}

def load_runtime_data():
    algo_means = []
    for algo in NAME_MAP.values():
        algo_dir = os.path.join(RUNTIME_DIR, algo)
        if not os.path.isdir(algo_dir):
            print(f"[warn] Missing runtime folder for {algo}")
            continue
        dfs = []
        for f in os.listdir(algo_dir):
            if f.startswith("timing_log_") and f.endswith(".csv"):
                path = os.path.join(algo_dir, f)
                df = pd.read_csv(path, header=None, names=["site", "algorithm", "xpath", "time_ms"])
                dfs.append(df)
        if dfs:
            data = pd.concat(dfs, ignore_index=True)
            avg_runtime = data["time_ms"].mean()
            algo_means.append({"Algorithm": algo, "AvgRuntimeMs": avg_runtime})
    return pd.DataFrame(algo_means)

def load_robustness_data():
    df = pd.read_csv(ROBUSTNESS_CSV)
    # Extract fitness % cleanly (strip parentheses)
    df["FitnessPercent"] = df["Fitness (%)"].astype(str).str.extract(r"\(([\d\.]+)\)").astype(float)
    df["Locator"] = df["Locator"].map(NAME_MAP)  # normalize names
    return df.rename(columns={"Locator": "Algorithm"})

def main():
    runtime_df = load_runtime_data()
    robust_df = load_robustness_data()
    combined = pd.merge(runtime_df, robust_df, on="Algorithm")

    # Add a DisplayName column for plotting and annotation
    combined["DisplayName"] = combined["Algorithm"].map(DISPLAY_NAME)

    print("[info] Combined data:")
    print(combined[["Algorithm", "AvgRuntimeMs", "FitnessPercent", "Accuracy (All)"]])

    plt.figure(figsize=(10, 6), dpi=300)
    sns.scatterplot(
        data=combined,
        x="AvgRuntimeMs", y="FitnessPercent",
        hue="DisplayName", s=120,
        palette={DISPLAY_NAME[k]: v for k, v in PALETTE.items()}
    )
    # Remove the legend title
    plt.legend(title=None)
    for _, row in combined.iterrows():
        plt.text(
            row["AvgRuntimeMs"] * 1.05,
            row["FitnessPercent"],
            row["DisplayName"],
            fontsize=8
        )

    plt.xscale("log")
    plt.xlabel("Average Runtime (ms, log scale)")
    plt.ylabel("Fitness Score (%)")
    plt.title("Efficiency Tradeoff: Runtime vs Robustness of Locator Algorithms")
    plt.grid(True, linestyle="--", alpha=0.7)
    plt.tight_layout()

    out_png = os.path.join(RUNTIME_DIR, "runtime_vs_robustness.png")
    plt.savefig(out_png, dpi=300)
    print(f"[ok] Scatterplot saved to {out_png}")
    plt.show()

if __name__ == "__main__":
    main()