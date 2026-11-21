#!/usr/bin/env python3
"""Release mapping by test outcome: for the first release of each minor series (or the releases given), installs it, enables the modules and runs a fixed signature subset of the
v1 and v30 WebDriver suites (run/wd_v1, run/wd_v30; browser = dol-chrome, app = dolibarr_scan). Writes suite_scan.jsonl (one line per release and suite).
usage: scan_suites.py [release ...]"""
import sys, os, re, json, subprocess, time
W = '/Users/stocco/repos/tools/dolibarr-work'; PY = '/Users/stocco/repos/tools/addrbook-work/.venv/bin/python3.15'
JH = '/Users/stocco/Library/Java/JavaVirtualMachines/jdk-17.0.21+9/Contents/Home'
SIG = 'TestLoginOk,CreateThirdPartyOk,SearchThirdPartyOk,AddContactOk,CreateProductOk,SearchProduct,CreateProposalOk,CreateInvoiceOk,CreateLabelOk,CreateFinancialOk,CreateLeaveRequest,ModifyMain,ModifyMenu,ModifyDisplay'
def key(r): return tuple(int(x) for x in r.split('.'))
if len(sys.argv) > 1: rels = sys.argv[1:]
else:
    allr = sorted({json.loads(l)['release'] for l in open(f'{W}/scan_results.jsonl')}, key=key); seen = set(); rels = []
    for r in allr:
        m = key(r)[:2]
        if m not in seen and m >= (2, 4): seen.add(m); rels.append(r)
done = set()
if os.path.exists(f'{W}/suite_scan.jsonl'): done = {(j['release'], j['suite']) for j in map(json.loads, open(f'{W}/suite_scan.jsonl'))}
for rel in rels:
    if all((rel, s) in done for s in ('wd_v1', 'wd_v30')): continue
    r = subprocess.run([PY, f'{W}/scan_dbg.py', rel], capture_output=True, text=True, cwd='/Users/stocco/repos/tools/addrbook-work')
    inst = (r.stdout.strip().splitlines() or [''])[-1]
    if '"install": "ok' not in inst: print(rel, 'install failed', flush=True); continue
    subprocess.run([PY, f'{W}/dol_modules.py'], capture_output=True, text=True)
    for suite in ('wd_v1', 'wd_v30'):
        if (rel, suite) in done: continue
        # a clean state for each suite: reinstall before the second one
        if suite == 'wd_v30':
            subprocess.run([PY, f'{W}/scan_dbg.py', rel], capture_output=True, text=True, cwd='/Users/stocco/repos/tools/addrbook-work'); subprocess.run([PY, f'{W}/dol_modules.py'], capture_output=True, text=True)
        t0 = time.time()
        p = subprocess.run(['perl', '-e', 'alarm shift; exec @ARGV', '900', 'mvn', 'test', f'-Dtest={SIG}'], cwd=f'{W}/run/{suite}', capture_output=True, text=True, env=dict(os.environ, JAVA_HOME=JH))
        out = p.stdout; fails = sorted(set(re.findall(r'<<< (?:FAILURE|ERROR)!\s*- in tests\.(\w+)', out))); ran = sorted(set(re.findall(r'in tests\.(\w+)', out)))
        passed = [t for t in ran if t not in fails]
        line = dict(release=rel, suite=suite, passed=passed, failed=fails, npass=len(passed), nfail=len(fails), secs=round(time.time() - t0))
        print(json.dumps(line), flush=True); open(f'{W}/suite_scan.jsonl', 'a').write(json.dumps(line) + '\n')
