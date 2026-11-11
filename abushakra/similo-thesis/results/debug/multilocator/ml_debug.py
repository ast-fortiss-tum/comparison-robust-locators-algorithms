import pandas as pd

# Load the CSV file
df = pd.read_csv("results/debug/multilocator/repair_debug.csv")

# Normalize the 'repair_saved' and 'winner_matches_target' columns
df["repair_saved"] = df["repair_saved"].astype(str).str.lower()
df["winner_matches_target"] = df["winner_matches_target"].astype(str).str.lower()

# Print unique values for debugging
print("repair_saved values:", df["repair_saved"].unique())
print("winner_matches_target values:", df["winner_matches_target"].unique())

# Optional: Show all columns
pd.set_option("display.max_columns", None)

# Filter for rows where 'repair_saved' starts with "Healed available" and winner did NOT match the target
misrepairs = df[
    df["repair_saved"].str.startswith("Healed available") &
    (df["winner_matches_target"] == "false")
]

# Display a few key columns to inspect
print(misrepairs[[
    "timestamp", "site", "pageUrl", "orig_idxpath", "winner_abs_xpath",
    "winner_strategies", "repair_saved", "winner_matches_target", "notes"
]])

# Optional: Save to new file for further inspection
misrepairs.to_csv("misrepairs.csv", index=False)