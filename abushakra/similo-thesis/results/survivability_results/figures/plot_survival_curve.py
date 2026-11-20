import pandas as pd
import matplotlib.pyplot as plt

# Color palette mapping
PALETTE = {
    "VISTA": "#EECA3B",              # Yellow
    "ROBULA+": "#54A24B",         # Green
    "Multi-Locator": "#E45756",      # Red
    "Multi-Locator Repair": "#A63636",   # Dark Red
    "VonSimilo": "#B279A2",          # Purple
    "COLOR": "#4C78A8",              # Blue
    "LegacySimilo": "#F58518",       # Orange (Similo)
    "OptimizedSimilo": "#72B7B2"     # Teal
}

# Algorithm folder name mapping (optional, if folder names differ)
ALGO_FOLDER_MAP = {
    "LegacySimilo": "legacysimilo",
    "OptimizedSimilo": "optimizedsimilo",
    "VISTA": "vista",
    "ROBULA+": "robulaplus",
    "COLOR": "color",
    "VonSimilo": "vonsimilo",
    "Multi-Locator": "multi-locator",
    "Multi-Locator Repair": "multi-locator repair"
}

versions = 15
results = {}

for algo_name, folder in ALGO_FOLDER_MAP.items():
    try:
        df = pd.read_csv(f"results/survivability_results/{folder}/element_summary_all.csv")
        survivors = [0] * versions
        counters = [0] * versions

        for _, row in df.iterrows():
            existing = int(row['versions_existing'])
            exact = int(row['exact_matches'])

            for i in range(existing):
                counters[i] += 1
            for i in range(exact):
                survivors[i] += 1

        curve = []
        for i in range(versions):
            if counters[i] > 0:
                curve.append(survivors[i] / counters[i])
            else:
                curve.append(None)

        results[algo_name] = curve
    except Exception as e:
        print(f"Failed for {algo_name}: {e}")

# Plotting
plt.figure(figsize=(10, 6))
for algo_name, curve in results.items():
    plt.plot(range(1, versions + 1), curve, label=algo_name, color=PALETTE[algo_name], linewidth=2)

plt.xlabel("Duration of Survival (DOS) in versions")
plt.ylabel("Average Survivability Ratio")
plt.xticks(range(1, versions + 1))
plt.ylim(0, 1.05)
plt.grid(True, linestyle='--', alpha=0.6)
plt.legend()
plt.tight_layout()
plt.savefig("results/survivability_results/figures/survival_curves.png", dpi=300)
plt.show()