# results/survivability_heatmaps/plot_heatmaps.py
import sys, os, glob
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from matplotlib.colors import ListedColormap, BoundaryNorm
from matplotlib.patches import Patch
from io import StringIO
from typing import Optional, List

# ---- fixed timeline (force x-axis to start at 2018-09-01) ----
EXPECTED_VERSIONS = [
    "2018-09-01",
    "2019-01-01","2019-05-01","2019-09-01",
    "2020-01-01","2020-05-01","2020-09-01",
    "2021-01-01","2021-05-01","2021-09-01",
    "2022-01-01","2022-05-01","2022-09-01",
    "2023-01-01","2023-05-01","2023-09-01",
]

# Codes → colors (sync with Scala Cell)
# -1 = N/A, 0 = no match, 1 = overlap, 2 = exact, 3 = deleted
CODE_TO_COLOR = {
    -1: '#d3d3d3',  # light gray
    0:  '#d62728',  # red
    1:  '#f0ad00',  # yellow
    2:  '#2ca02c',  # green
    3:  '#9aa0a6',  # dark gray
}
LABELS = {
    -1: "N/A",
    0: "No match",
    1: "In overlap",
    2: "Exact match",
    3: "Deleted",
}

CODE_ORDER = [-1, 0, 1, 2, 3]
COLORS = [CODE_TO_COLOR[c] for c in CODE_ORDER]
cmap = ListedColormap(COLORS)
norm = BoundaryNorm([-1.5, -0.5, 0.5, 1.5, 2.5, 3.5], cmap.N)

def read_csv_auto(path: str):
    """Return (df, titleFromMeta). Handles optional '# XPATH=...' first line."""
    title = None
    with open(path, 'r', encoding='utf-8') as f:
        first = f.readline()
        if first.startswith("# XPATH="):
            title = first[len("# XPATH="):].strip()
            header = f.readline()
            rest = f.read()
            sep = ';' if (';' in header and ',' not in header) else ','
            df = pd.read_csv(StringIO(header + rest), sep=sep)
        else:
            sep = ';' if (';' in first and ',' not in first) else ','
            df = pd.read_csv(path, sep=sep)

    df.columns = [c.strip() for c in df.columns]
    if df.columns[0].lower() != 'algorithm':
        df.rename(columns={df.columns[0]: 'algorithm'}, inplace=True)
    df['algorithm'] = df['algorithm'].astype(str).str.strip()
    # force ints; missing -> -1
    for c in df.columns[1:]:
        df[c] = pd.to_numeric(df[c], errors='coerce').fillna(-1).astype(int)
    return df, title

def reindex_to_expected_versions(df: pd.DataFrame) -> pd.DataFrame:
    """Ensure all EXPECTED_VERSIONS exist as columns (fill -1), and ordered."""
    out = df.copy()
    for v in EXPECTED_VERSIONS:
        if v not in out.columns:
            out[v] = -1
    cols = ['algorithm'] + EXPECTED_VERSIONS
    out = out.reindex(columns=cols)
    return out

def maybe_load_classes(matrix_csv_path: str, versions: List[str]) -> Optional[pd.DataFrame]:
    """
    Try to load a companion classification CSV:
      - <base>.classes.csv
      - <base>.cls.csv
    Return a DataFrame with same shape (algorithms x versions) of strings,
    or None if not found/parsable.
    """
    base, _ = os.path.splitext(matrix_csv_path)
    candidates = [base + ".classes.csv", base + ".cls.csv"]
    for p in candidates:
        if os.path.isfile(p):
            with open(p, 'r', encoding='utf-8') as f:
                first = f.readline()
                if first.startswith("# XPATH="):
                    header = f.readline()
                    rest = f.read()
                    sep = ';' if (';' in header and ',' not in header) else ','
                    dfc = pd.read_csv(StringIO(header + rest), sep=sep, dtype=str)
                else:
                    sep = ';' if (';' in first and ',' not in first) else ','
                    dfc = pd.read_csv(p, sep=sep, dtype=str)
            dfc.columns = [c.strip() for c in dfc.columns]
            if dfc.columns[0].lower() != 'algorithm':
                dfc.rename(columns={dfc.columns[0]: 'algorithm'}, inplace=True)
            dfc['algorithm'] = dfc['algorithm'].astype(str).str.strip()
            for v in versions:
                if v not in dfc.columns:
                    dfc[v] = ''
            dfc = dfc[['algorithm'] + versions]
            for c in dfc.columns[1:]:
                dfc[c] = dfc[c].fillna('').astype(str).str.upper().replace({
                    'SAME':'SAME','MINOR':'MINOR','MAJOR':'MAJOR'
                })
            return dfc
    return None

def plot_single_csv(csv_path: str, out_png: str):
    df, meta_title = read_csv_auto(csv_path)
    df = reindex_to_expected_versions(df)

    algos = df['algorithm'].tolist()
    versions = df.columns[1:]
    matrix = df.iloc[:, 1:].to_numpy(dtype=int)

    # Ensure an initial column (2018-09-01) exists and is shown as N/A with text "INITIAL"
    if "2018-09-01" in versions:
        j0 = list(versions).index("2018-09-01")
        # force neutral color for the initial column
        if matrix.shape[1] > j0:
            matrix[:, j0] = -1

    # optional classification labels
    cls_df = maybe_load_classes(csv_path, list(versions))
    cls_matrix = None
    if cls_df is not None:
        cls_df = cls_df.set_index('algorithm').reindex(algos).reset_index()
        cls_matrix = cls_df.iloc[:, 1:].to_numpy(dtype=str)

    fig, ax = plt.subplots(figsize=(max(12, len(versions)*0.9), 3.2), dpi=180)
    im = ax.imshow(matrix, cmap=cmap, norm=norm, aspect='auto')

    ax.set_xticks(np.arange(len(versions)))
    ax.set_yticks(np.arange(len(algos)))
    ax.set_xticklabels(versions, rotation=45, ha='right')
    ax.set_yticklabels(algos)

    # cell borders
    ax.set_xticks(np.arange(-0.5, len(versions), 1), minor=True)
    ax.set_yticks(np.arange(-0.5, len(algos), 1), minor=True)
    ax.grid(which='minor', color='white', linewidth=0.8)
    ax.tick_params(which='minor', bottom=False, left=False)

    # overlay change-type text if provided
    if cls_matrix is not None:
        for i in range(len(algos)):
            for j in range(len(versions)):
                label = cls_matrix[i, j]
                if isinstance(label, str) and label:
                    bg_code = matrix[i, j]
                    txt_color = 'black' if bg_code in (1, 2) else 'white'
                    ax.text(j, i, label, ha='center', va='center', fontsize=6, color=txt_color)

    # Always label the initial column
    if "2018-09-01" in versions:
        j0 = list(versions).index("2018-09-01")
        for i in range(len(algos)):
            # choose readable text color based on background code
            bg_code = matrix[i, j0] if matrix.shape[1] > j0 else -1
            txt_color = 'black' if bg_code in (1, 2) else 'white'
            ax.text(j0, i, "INITIAL", ha='center', va='center', fontsize=6, color=txt_color)

    # Title from meta (XPath) if present, else file name
    title = meta_title if meta_title else os.path.basename(csv_path)
    ax.set_title(title, fontsize=12, pad=10)

    # Legend
    handles = [
        Patch(facecolor=CODE_TO_COLOR[2], edgecolor='black', label=LABELS[2]),
        Patch(facecolor=CODE_TO_COLOR[1], edgecolor='black', label=LABELS[1]),
        Patch(facecolor=CODE_TO_COLOR[0], edgecolor='black', label=LABELS[0]),
        Patch(facecolor=CODE_TO_COLOR[3], edgecolor='black', label=LABELS[3]),
        Patch(facecolor=CODE_TO_COLOR[-1], edgecolor='black', label=LABELS[-1]),
    ]
    ax.legend(handles=handles, loc='center left', bbox_to_anchor=(1.02, 0.5), frameon=True)

    fig.tight_layout()
    out_dir = os.path.dirname(out_png) or "."
    os.makedirs(out_dir, exist_ok=True)
    fig.savefig(out_png, bbox_inches='tight')
    plt.close(fig)
    print(f"[saved] {out_png}")

def main():
    if len(sys.argv) != 2:
        print("Usage: python3 plot_heatmaps.py <root_dir_of_csvs>")
        sys.exit(1)

    root = sys.argv[1]
    if not os.path.isdir(root):
        print(f"Not a directory: {root}")
        sys.exit(1)

    csvs = glob.glob(os.path.join(root, "**", "*.csv"), recursive=True)
    if not csvs:
        print(f"No CSV files found in {root}")
        sys.exit(0)

    for csv in sorted([p for p in csvs if not p.endswith(('.classes.csv','.cls.csv'))]):
        png = csv[:-4] + ".png"
        try:
            plot_single_csv(csv, png)
        except Exception as e:
            print(f"[skip] {csv}: {e}")

if __name__ == "__main__":
    main()