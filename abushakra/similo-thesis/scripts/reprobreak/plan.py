"""Plan the capture of ReproBreak's locator breaks (de Moura et al., ASE 2026; https://github.com/rub-sq/ReproBreak).

For every confirmed break: the commit C that changed the locator, its parent P, the test file, the line of the new
locator in C and the line of the old locator in P (found through the diff of the test file). Writes plan.json.

Usage: python plan.py <ReproBreak.db> <dir with clones of the 4 repositories> <out plan.json>
"""
import difflib, json, sqlite3, subprocess, sys

db, repos, out = sys.argv[1:4]
con = sqlite3.connect(db)
rows = con.execute("""
    SELECT lc.id, lc.repository_name, lc.commit_sha, gc.previous_sha, lc.test_file_path, lc.line_no,
           lc.old_locator, lc.new_locator, lc.framework, lb.reproduce_files_id
    FROM locator_break lb JOIN locator_change lc ON lc.id = lb.locator_change_id
    JOIN git_commit gc ON gc.sha = lc.commit_sha AND gc.repository_name = lc.repository_name
    ORDER BY lc.repository_name, lc.commit_sha, lc.id""").fetchall()


def show(repo, sha, path):
    r = subprocess.run(["git", "-C", f"{repos}/{repo}", "show", f"{sha}:{path}"], capture_output=True, text=True)
    return r.stdout.splitlines() if r.returncode == 0 else None


def norm(s):
    return "".join(s.split())


plan, problems = [], {}
cache = {}
for (lid, repo_full, c, p, path, line, old, new, fw, rf) in rows:
    repo = repo_full.split("/")[1]
    key = (repo, c, p, path)
    if key not in cache:
        new_lines, old_lines = show(repo, c, path), show(repo, p, path)
        mapping = {}
        if new_lines is not None and old_lines is not None:
            sm = difflib.SequenceMatcher(None, old_lines, new_lines, autojunk=False)
            for tag, i1, i2, j1, j2 in sm.get_opcodes():
                for k in range(j2 - j1):  # new line (1-based) -> candidate old lines (1-based)
                    mapping[j1 + k + 1] = (i1 + 1, max(i2, i1 + 1)) if tag != "equal" else (i1 + k + 1, i1 + k + 1)
        cache[key] = (new_lines, old_lines, mapping)
    new_lines, old_lines, mapping = cache[key]
    entry = dict(id=lid, repo=repo_full, commit=c, parent=p, file=path, framework=fw, reproduce_files_id=rf,
                 new_line=line, new_locator=new, old_locator=old, old_line=None)
    if new_lines is None or old_lines is None:
        problems.setdefault("file missing in C or P", []).append(lid)
    else:
        if not (1 <= line <= len(new_lines)) or norm(new.rstrip(")"))[:20] not in norm(new_lines[line - 1]):
            problems.setdefault("new locator not on line_no", []).append(lid)
        lo, hi = mapping.get(line, (1, len(old_lines)))
        target = norm(old.rstrip(")"))[:40]
        cands = [i for i in range(max(1, lo - 3), min(len(old_lines), hi + 3) + 1) if target in norm(old_lines[i - 1])]
        if not cands:  # fall back to the whole file, nearest to the mapped position
            cands = sorted((i for i in range(1, len(old_lines) + 1) if target in norm(old_lines[i - 1])), key=lambda i: abs(i - lo))[:1]
        if cands:
            entry["old_line"] = min(cands, key=lambda i: abs(i - lo))
        else:
            problems.setdefault("old locator not found in P", []).append(lid)
    plan.append(entry)

json.dump(plan, open(out, "w"), indent=1)
print(len(plan), "breaks;", sum(e["old_line"] is not None for e in plan), "with an old line")
for k, v in problems.items():
    print(f"{k}: {len(v)} e.g. {v[:8]}")
