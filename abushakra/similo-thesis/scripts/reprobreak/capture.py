"""Capture the old and the new version of every ReproBreak locator break as benchmark snapshots.

For each break (plan.json, see plan.py), the test file is run twice in ReproBreak's own Docker environment:
  - side "old": the parent commit P with the old test, capturing what the old locator selects;
  - side "new": the fixing commit C with the new test, capturing what the new locator selects.
The test file is instrumented (instrument.mjs) to take a snapshot right before the statement that uses the locator:
every visible element with the benchmark's properties (src/main/resources/javascript), the matched targets, the DOM
and a viewport screenshot. Snapshots go to <out>/<repo>/<side>/<sha>/<id>-<occurrence>[-<browser>].{json,png}.

The application code is built and run only inside Docker (ReproBreak's Dockerfile and run_tests.sh, from the
database); the test file is mounted read-only. Images are removed after use.

Usage: python capture.py --plan plan.json --db ReproBreak.db --repos <clones> --work <dir> --out <dir>
                         [--repo koenig] [--commits N] [--ids 1,2] [--force] [--keep-images]
"""
import argparse
import collections
import json
import os
import shutil
import sqlite3
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
JS_DIR = os.path.join(HERE, "..", "..", "src", "main", "resources", "javascript")


def sh(cmd, log=None, timeout=None, cwd=None):
    with open(log, "a") if log else open(os.devnull, "w") as f:
        f.write(f"\n$ {' '.join(cmd)}\n")
        f.flush()
        try:
            return subprocess.run(cmd, stdout=f, stderr=subprocess.STDOUT, timeout=timeout, cwd=cwd).returncode
        except subprocess.TimeoutExpired:
            f.write(f"\n[timeout after {timeout}s]\n")
            return 124


def reproduce_files(db, rf_id):
    row = sqlite3.connect(db).execute("SELECT json FROM reproduce_files WHERE id = ?", (rf_id,)).fetchone()
    files = json.loads(row[0])
    return json.loads(files) if isinstance(files, str) else files


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--plan", required=True)
    ap.add_argument("--db", required=True)
    ap.add_argument("--repos", required=True)
    ap.add_argument("--work", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--repo", action="append", help="only these repositories (short name)")
    ap.add_argument("--commits", type=int, help="only the first N fixing commits per repository")
    ap.add_argument("--ids", help="only these break ids (comma-separated)")
    ap.add_argument("--keep-images", action="store_true")
    ap.add_argument("--force", action="store_true", help="capture again even if a previous run succeeded")
    ap.add_argument("--build-timeout", type=int, default=5400)
    ap.add_argument("--test-timeout", type=int, default=2400)
    args = ap.parse_args()

    plan = json.load(open(args.plan))
    if args.ids:
        keep = {int(i) for i in args.ids.split(",")}
        plan = [e for e in plan if e["id"] in keep]
    by_commit = collections.OrderedDict()
    for e in plan:
        name = e["repo"].split("/")[1]
        if args.repo and name not in args.repo:
            continue
        by_commit.setdefault((name, e["commit"]), []).append(e)
    if args.commits:
        seen = collections.Counter()
        kept = collections.OrderedDict()
        for k, v in by_commit.items():
            if seen[k[0]] < args.commits:
                kept[k] = v
                seen[k[0]] += 1
        by_commit = kept

    os.makedirs(args.work, exist_ok=True)
    status_path = os.path.join(args.out, "status.jsonl")
    os.makedirs(args.out, exist_ok=True)
    done = set()
    if os.path.exists(status_path) and not args.force:
        for line in open(status_path):
            r = json.loads(line)
            if r["status"] == "ran":  # failed checkouts/builds are retried
                done.add((r["repo"], r["side"], r["sha"], r["file"]))

    for (name, commit), breaks in by_commit.items():
        for side in ("new", "old"):
            sha = commit if side == "new" else breaks[0]["parent"]
            files = collections.OrderedDict()
            for e in breaks:
                files.setdefault(e["file"], []).append(e)
            todo = [f for f in files if (name, side, sha, f) not in done]
            if not todo:
                continue
            ctx = os.path.join(args.work, "ctx", name, sha)
            log = os.path.join(args.out, name, side, sha, "run.log")
            os.makedirs(os.path.dirname(log), exist_ok=True)
            image = f"rb-{name.lower()}:{sha[:12]}"
            t0 = time.time()
            # checkout + build context
            if not os.path.isdir(os.path.join(ctx, name)):
                os.makedirs(ctx, exist_ok=True)
                rc = sh(["git", "-C", os.path.join(args.repos, name), "worktree", "add", "--detach", "--force",
                         os.path.abspath(os.path.join(ctx, name)), sha], log)
                if rc:
                    record(status_path, name, side, sha, None, "checkout failed", log)
                    continue
            for fname, content in reproduce_files(args.db, breaks[0]["reproduce_files_id"]).items():
                if fname == "Dockerfile":
                    content = patch_dockerfile(content)
                with open(os.path.join(ctx, fname), "w") as f:
                    f.write(content)
            have = subprocess.run(["docker", "image", "inspect", image], capture_output=True).returncode == 0
            if not have:
                rc = sh(["docker", "build", "--pull", "-t", image, ctx], log, timeout=args.build_timeout)
                original = reproduce_files(args.db, breaks[0]["reproduce_files_id"]).get("Dockerfile", "")
                if rc and "-bullseye" in original:
                    # Older Playwright versions only know the Debian 11 package names: keep the original base.
                    with open(os.path.join(ctx, "Dockerfile"), "w") as fh:
                        fh.write(patch_dockerfile(original, keep_bullseye=True))
                    rc = sh(["docker", "build", "--pull", "--no-cache", "-t", image, ctx], log, timeout=args.build_timeout)
                if rc and "playwright install --with-deps" in original:
                    # Last resort: only Chromium, which the snapshots use (no Firefox build for Debian 12 on arm64).
                    with open(os.path.join(ctx, "Dockerfile"), "w") as fh:
                        fh.write(patch_dockerfile(original, keep_bullseye=True, chromium_only=True))
                    rc = sh(["docker", "build", "--pull", "--no-cache", "-t", image, ctx], log, timeout=args.build_timeout)
                if rc:
                    for f in todo:
                        record(status_path, name, side, sha, f, f"build failed ({rc})", log)
                    cleanup(args, name, ctx, image)
                    continue
            build_s = time.time() - t0
            for f in todo:
                es = files[f]
                inst_dir = os.path.join(args.work, "inst", name, sha)
                inst = os.path.join(inst_dir, f)
                os.makedirs(os.path.dirname(inst), exist_ok=True)
                shutil.copyfile(os.path.join(ctx, name, f), inst)
                spec = dict(file=inst, framework=es[0]["framework"], jsDir=JS_DIR,
                            wrapper=os.path.join(HERE, "snapshot_wrapper.js"),
                            captures=[dict(id=e["id"], line=e[f"{side}_line"], locator=e[f"{side}_locator"]) for e in es])
                spec_path = inst + ".spec.json"
                json.dump(spec, open(spec_path, "w"))
                r = subprocess.run(["node", os.path.join(HERE, "instrument.mjs"), spec_path], capture_output=True, text=True)
                if r.returncode:
                    record(status_path, name, side, sha, f, "instrumentation failed: " + r.stderr[-300:], log)
                    continue
                inst_report = json.loads(r.stdout)["report"]
                out_dir = os.path.abspath(os.path.join(args.out, name, side, sha))
                t1 = time.time()
                cname = f"rb-run-{os.getpid()}"
                rc = sh(["docker", "run", "--rm", "--name", cname, "--shm-size=2g",
                         "-v", f"{os.path.abspath(inst)}:/app/{f}:ro",
                         "-v", f"{out_dir}:/rb/out",
                         "-e", "CYPRESS_screenshotsFolder=/rb/out/shots",
                         # one run per test file into the same folder: keep the screenshots of the earlier runs
                         "-e", "CYPRESS_trashAssetsBeforeRuns=false",
                         image, "bash", "/run_tests.sh", f"/app/{f}"], log, timeout=args.test_timeout)
                if rc == 124:
                    subprocess.run(["docker", "rm", "-f", cname], capture_output=True)
                record(status_path, name, side, sha, f, "ran", log, test_rc=rc, instrumented=inst_report,
                       build_s=round(build_s), test_s=round(time.time() - t1))
            cleanup(args, name, ctx, image)


def patch_dockerfile(content, keep_bullseye=False, chromium_only=False):
    """Environment-only patches: Debian 12 instead of the end-of-life Debian 11 base, and refresh the apt index before Playwright installs its system dependencies (the pinned
    Debian images otherwise fail with 404s on superseded security updates). The application and test steps are
    unchanged."""
    out = []
    for line in content.splitlines():
        if not keep_bullseye and line.startswith("FROM ") and line.rstrip().endswith("-bullseye"):
            # Debian 11 security support ended (Aug 2026): its security mirror no longer serves the packages
            # Playwright needs. Same Node major version on Debian 12.
            line = line.rstrip()[: -len("-bullseye")] + "-bookworm"
        if line.strip() == "FROM cypress/browsers":
            # The floating tag now has no yarn; use the pin of ReproBreak's own second environment for this repo.
            line = "FROM cypress/browsers:node-20.18.0-chrome-130.0.6723.69-1-ff-131.0.3-edge-130.0.2849.52-1"
        if "playwright install --with-deps" in line and line.startswith("RUN "):
            # Refresh the apt index in the same layer: a separately cached update layer goes stale (404s).
            line = "RUN apt-get update && " + line[len("RUN "):]
            if chromium_only and "--with-deps chromium" not in line:
                line = line.replace("playwright install --with-deps", "playwright install --with-deps chromium")
        out.append(line)
    return "\n".join(out) + "\n"


def cleanup(args, name, ctx, image):
    if not args.keep_images:
        subprocess.run(["docker", "rmi", "-f", image], capture_output=True)
        subprocess.run(["docker", "builder", "prune", "-f", "--filter", "until=1h"], capture_output=True)
    subprocess.run(["git", "-C", os.path.join(args.repos, name), "worktree", "remove", "--force",
                    os.path.abspath(os.path.join(ctx, name))], capture_output=True)
    shutil.rmtree(ctx, ignore_errors=True)


def record(path, repo, side, sha, file, status, log, **kw):
    with open(path, "a") as f:
        f.write(json.dumps(dict(repo=repo, side=side, sha=sha, file=file, status=status, log=log, **kw)) + "\n")
    print(repo, side, sha[:10], file, status, kw.get("test_rc", ""), flush=True)


if __name__ == "__main__":
    sys.exit(main())
