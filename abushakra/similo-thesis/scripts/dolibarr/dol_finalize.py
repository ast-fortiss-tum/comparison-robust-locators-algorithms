#!/usr/bin/env python3
"""Collects the Dolibarr benchmark into the repository working tree (results/dolibarr, uncommitted) and writes its README from the saved facts and the final summary."""
import os, glob, json, csv, shutil, re, collections
R = '/Users/stocco/repos/tools/comparison-robust-locators-algorithms/abushakra/similo-thesis'; O = R + '/results/dolibarr'; P = '/Users/stocco/repos/tools/hybrid-work/dol'; D = '/Users/stocco/repos/tools/dolibarr-work'
os.makedirs(O + '/dump', exist_ok=True)
for n in range(1, 31):   # current suites (with the final driver) and tooling
    src = f'{D}/run/wd_v{n}'
    if os.path.isdir(src):
        dst = f'{O}/suites/v{n}'; shutil.rmtree(dst, ignore_errors=True); os.makedirs(dst); shutil.copytree(src + '/src', dst + '/src'); shutil.copy(src + '/pom.xml', dst + '/pom.xml')
os.makedirs(R + '/scripts/dolibarr', exist_ok=True)
for f in ('align_dolibarr.py', 'verify_module_rows.py', 'fix_module_rows.py', 'repair_hint.py', 'scan_releases.py', 'install_v1.py', 'install_v30.py', 'new_version.sh', 'run_suite.sh', 'evolve_chain.sh', 'capture_all.sh', 'capture_retry.sh', 'mark_logout_only.sh', 'dol_modules.py', 'gen_review_dol.py', 'dol_finalize.py'):
    if os.path.exists(f'{D}/{f}'): shutil.copy(f'{D}/{f}', R + '/scripts/dolibarr/' + f)
for f in ('dol_pipeline.sh', 'dol_full.sh', 'dol_v30.sh', 'run_algos.sh'):
    shutil.copy(f'{P}/{f}', R + '/scripts/dolibarr/' + f)
shutil.copy(D + '/capture/CaptureDriver.java', R + '/scripts/dolibarr/CaptureDriver.java')
for f in glob.glob(D + '/repairs/*.diff'): shutil.copy(f, O + '/repairs/' + os.path.basename(f))
for d in ('dump', 'dump_v30'):
    for f in glob.glob(f'{P}/out/{d}/*.csv'):
        dst = f'{O}/dump/{os.path.basename(f)}'
        if d == 'dump_v30' and os.path.exists(dst):   # append the v30 rows to the main dump of the algorithm
            rows = open(f, encoding='utf8', errors='ignore').read().splitlines()[1:]; open(dst, 'a', encoding='utf8').write('\n'.join(rows) + '\n')
        else: shutil.copy(f, dst)
shutil.copy(P + '/import/plan.json', O + '/plan.json')
M = []; hdr = None
for f in sorted(glob.glob(P + '/out/manifest_*.csv')):
    rows = list(csv.reader(open(f), delimiter=';')); hdr = hdr or rows[0]; M += rows[1:]
csv.writer(open(O + '/manifest.csv', 'w', newline=''), delimiter=';').writerows([hdr] + M)
for f in ('summary_full.txt', 'summary_full_with_v30.txt', 'summary_all_pairs.txt', 'summary_clean.txt', 'excluded_sites.txt'):
    if os.path.exists(f'{P}/out/{f}'): shutil.copy(f'{P}/out/{f}', f'{O}/{f}')
if os.path.exists(D + '/review/pair-review-dolibarr.html'): shutil.copy(D + '/review/pair-review-dolibarr.html', O + '/pair-review-dolibarr.html')
if os.path.exists(D + '/chain_scan.jsonl'): shutil.copy(D + '/chain_scan.jsonl', O + '/chain_scan_unchanged_v1_suite.jsonl')
REL = '3.0.0 3.0.1 3.1.0 3.1.1 3.1.2 3.2.0 3.1.3 3.2.1 3.2.2 3.2.3 3.3.0 3.3.1 3.3.2 3.3.3 3.4.0 3.4.1 3.4.2 3.5.0 3.5.1 3.5.2 3.5.3 3.5.4 3.6.0 3.6.2 3.6.3 3.7.0 3.6.4 3.7.1 3.8.0'.split()
rep = {}
for f in glob.glob(O + '/repairs/*.diff'):
    m = re.match(r'v(\d+)_to_v(\d+)', os.path.basename(f))
    if m: rep[int(m.group(2))] = sum(1 for l in open(f) if re.match(r'^[-+][^-+]', l))
log = open(P + '/out/pipeline_full.log').read()
st = {int(m.group(1)): m.groups() for m in re.finditer(r'v1->v(\d+): \{.*?\'kept\': (\d+), \'same_xpath\': (\d+), \'changed_locator\': (\d+)', log)}
lines = ['| Version | Release | Suite repair (changed lines) | Pairs v1→vN | Same absolute XPath | Locator changed in the suite |', '|---|---|---|---|---|---|']
for n in range(1, 31):
    rel = REL[n - 1] if n <= 29 else '3.9.0-beta (git 3eb8d31)'; s = st.get(n)
    lines.append(f'| v{n} | {rel} | {rep.get(n, "0 (unchanged)" if n > 1 else "(original)")} | ' + (f'{s[1]} | {s[2]} | {s[3]} |' if s else '- | - | - |'))
summ = ''
for f in ('summary_clean.txt', 'summary_full_with_v30.txt', 'summary_full.txt'):
    if os.path.exists(f'{O}/{f}'): summ = open(f'{O}/{f}').read(); break
open(O + '/mapping_table.md', 'w').write('\n'.join(lines) + '\n')
readme = f'''# Dolibarr multi-release locator benchmark

Ground truth by construction, as in BEWT: the study's WebDriver suite (TSE study, 76 Java page-object tests; 39 are part of the authors' ordered suite for the early versions, 55 for v30) is evolved from release to release.
Every version of the suite passes on its own release, so the same page-object statement selects the same logical element in two versions. A pair is
(old element in v1, same element in vN) with both pages, screenshots and candidate elements captured while the suites ran.

## Version mapping
The study's `dolibarr_v1`..`dolibarr_v30` are git builds in date order (hash list from the study, `dolibarr-hashes-WD.txt`): v1 = 3.0.0 ... v29 = 3.8.0; v30 = commit
3eb8d3105a2f8a6c886c8474220c4b58e77ce04c (2015-09-18, 3.9.0-beta, not a release). The order is by date, so v7 = 3.1.3 and v27 = 3.6.4 are patch releases of older branches.
v7 starts from the v5 suite, v27 from the v25 suite, v8 from v6 and v28 from v26. Releases 3.1.2 and 3.1.3 are not in the Drive archives: fetched from the public repository tags.

{chr(10).join(lines)}

## Method and files
* `suites/vN`: the suite of each version (sources and pom). `repairs/*.diff`: what each repair changed. `../../scripts/dolibarr`: tooling (install, scan, repair helpers, capture, alignment).
* Install: fresh wizard install of the release, no pre-configuration (the suites' own tests create the company and activate the modules; pre-enabling modules makes their icon clicks switch them off).
* `SettleDriver` (in every suite): waits for the page after clicks (modern Chrome returns earlier than the 2016 driver), falls back to the DOM for interactions Chrome refuses (hidden select2 options), retries lookups for a few seconds, and dumps the page when a lookup fails.
* Identity safeguard: a passing test does not prove a locator still points at the same element. Module-activation icons silently drifted to neighbouring rows when rows were inserted (3.3.0, 3.4.0, 3.5.0);
  `verify_module_rows.py` checks every such locator against the module name and `fix_module_rows.py` repairs it. Other positional locators may drift the same way without breaking a test; that is what the review page is for.
* Capture: `CaptureDriver` records, for every lookup, the page, a full-page screenshot, the located element and the candidate elements. Alignment (`align_dolibarr.py`) matches the k-th touch of the same field
  (read from the source line of the page-object statement in that version) of the same test class.
* Pairs are first-to-all (v1 → vN). `plan.json` lists them with the suite's old and new locator; `manifest.csv` the import status; `dump/` the per-pair outcome of every algorithm.

## Caveats
* v30: 50 of 55 tests pass; five (AddContactEmpty, CreateInvoiceOk, CreateLeaveRequest, DeleteInvoice, DeleteLeaveRequest) are skipped for pair building. The suite never selects a company country, so the setting is preset to Italy; its counters
  (8, 13, 18 active modules) need Products and Proposals activated in ModifyMain (two added clicks, counter assertion 6 → 8).
* `TestLogout` does not complete in capture mode on 3.5.0 and later (the logout click has no effect); its later lookups are missing from those versions.
* Other repairs that are not locator changes: country label ("IT - Italy" → "Italy (IT)"), message texts ("Lastname / Label" → "Last name / Label"), steps removed (the Modify link of the company page from 3.4.0).
* Silent mis-targeting beyond the module icons has not been ruled out: validate with `pair-review-dolibarr.html` (changed, flagged, moved and unchanged samples).

## Results (all algorithms, first-to-all pairs)
```
{summ}
```
'''
_keep = os.path.exists(O + '/README.md') and 'Manual review and excluded pairs' in open(O + '/README.md').read()   # README was completed by hand after the review
if not _keep: open(O + '/README.md', 'w').write(readme)
print('written', O, '(README kept)' if _keep else '')
