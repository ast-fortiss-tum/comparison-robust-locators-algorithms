"""Summarises the chromeDOM.html regeneration per site (for the dashboard): snapshots, regenerated, failed, consistency."""
import csv, os, sys, datetime
out = sys.argv[1]
sites = sorted(s for s in os.listdir("sites") if not s.startswith("rb_") and os.path.isfile(f"sites/{s}/xpaths.csv") and os.path.isfile(f"sites/{s}/classified.csv"))
last = {}
fails = {}
blocked = None
if os.path.exists("sites/regeneration_log.csv"):
    for r in csv.DictReader(open("sites/regeneration_log.csv")):
        if r["variant"] == "BLOCKED":
            blocked = r["timestamp"]; continue
        k = (r["site"], r["date"])
        if r["variant"] == "FAIL": fails[k] = fails.get(k, 0) + 1
        else: last[k] = r
status = "unknown"
if os.path.exists("out/regen/forever.log"):
    lines = [l.strip() for l in open("out/regen/forever.log") if l.strip()]
    status = lines[-1] if lines else status
now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
with open(out, "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["site", "held_out", "snapshots", "regenerated", "failed", "pending", "consistent_elements", "tracked_elements", "consistency", "updated", "crawler_status", "last_block"])
    for s in sites:
        dates = sorted(d for d in os.listdir(f"sites/{s}") if d[:2] == "20" and os.path.isdir(f"sites/{s}/{d}"))
        have = [d for d in dates if os.path.exists(f"sites/{s}/{d}/chromeDOM.html")]
        failed = [d for d in dates if d not in have and fails.get((s, d), 0) >= 2]
        agree = sum(int(last[(s, d)]["consistent"] or 0) for d in have if (s, d) in last)
        targets = sum(int(last[(s, d)]["targets"] or 0) for d in have if (s, d) in last)
        w.writerow([s.replace("zz_", ""), "yes" if s.startswith("zz_") else "no", len(dates), len(have), len(failed),
                    len(dates) - len(have) - len(failed), agree, targets, f"{100*agree/targets:.1f}" if targets else "",
                    now, status, blocked or ""])
