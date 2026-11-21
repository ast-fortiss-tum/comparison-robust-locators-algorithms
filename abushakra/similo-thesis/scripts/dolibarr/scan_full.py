#!/usr/bin/env python3
"""Full ordered suites: for each (suite, release) installs the release, enables the modules and runs run/<suite>'s OrderedSuite (the classes of the authors' Dolibarr_TestSuite
in file order, minus the ones marked not applicable). Appends to full_scan.jsonl.   usage: scan_full.py suite:release [suite:release ...]"""
import sys, os, re, json, subprocess, time
W = '/Users/stocco/repos/tools/dolibarr-work'; PY = '/Users/stocco/repos/tools/addrbook-work/.venv/bin/python3.15'; JH = '/Users/stocco/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home'
for spec in sys.argv[1:]:
    suite, rel = spec.split(':')
    r = subprocess.run([PY, f'{W}/scan_dbg.py', rel], capture_output=True, text=True, cwd='/Users/stocco/repos/tools/addrbook-work')
    if '"install": "ok' not in r.stdout: print(spec, 'install failed', flush=True); continue
    subprocess.run([PY, f'{W}/dol_modules.py'], capture_output=True, text=True)
    t0 = time.time()
    p = subprocess.run(['perl', '-e', 'alarm shift; exec @ARGV', '2400', 'mvn', 'test', '-Dtest=OrderedSuite'], cwd=f'{W}/run/{suite}', capture_output=True, text=True, env=dict(os.environ, JAVA_HOME=JH))
    out = p.stdout; m = re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+)', out); tot, fl, er = map(int, m[-1]) if m else (0, 0, 0)
    failed = sorted(set(re.findall(r'^\s+(\w+)\.test[:\w]*\s+»', out, re.M)) | set(re.findall(r'^\s+(\w+)\.test:\d+', out, re.M)))
    open(f'{W}/full_scan_{suite}_{rel}.log', 'w').write(out)
    line = dict(suite=suite, release=rel, total=tot, failures=fl, errors=er, passed=tot - fl - er, failed=failed, secs=round(time.time() - t0)); print(json.dumps(line), flush=True)
    open(f'{W}/full_scan.jsonl', 'a').write(json.dumps(line) + '\n')
