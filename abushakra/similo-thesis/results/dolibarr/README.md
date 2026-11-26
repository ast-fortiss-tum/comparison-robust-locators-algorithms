# Dolibarr multi-release locator benchmark

Ground truth by construction, as in BEWT: the study's WebDriver suite (TSE study, 76 Java page-object tests; 39 are part of the authors' ordered suite for the early versions, 55 for v30) is evolved from release to release.
Every version of the suite passes on its own release, so the same page-object statement selects the same logical element in two versions. A pair is
(old element in v1, same element in vN) with both pages, screenshots and candidate elements captured while the suites ran.

## Version mapping
The study's `dolibarr_v1`..`dolibarr_v30` are git builds in date order (hash list from the study, `dolibarr-hashes-WD.txt`): v1 = 3.0.0 ... v29 = 3.8.0; v30 = commit
3eb8d3105a2f8a6c886c8474220c4b58e77ce04c (2015-09-18, 3.9.0-beta, not a release). The order is by date, so v7 = 3.1.3 and v27 = 3.6.4 are patch releases of older branches.
v7 starts from the v5 suite, v27 from the v25 suite, v8 from v6 and v28 from v26. Releases 3.1.2 and 3.1.3 are not in the Drive archives: fetched from the public repository tags.

| Version | Release | Suite repair (changed lines) | Pairs v1→vN | Same absolute XPath | Locator changed in the suite |
|---|---|---|---|---|---|
| v1 | 3.0.0 | (original) | - | - | - |
| v2 | 3.0.1 | 0 (unchanged) | 177 | 177 | 0 |
| v3 | 3.1.0 | 20 | 177 | 148 | 9 |
| v4 | 3.1.1 | 0 (unchanged) | 177 | 148 | 9 |
| v5 | 3.1.2 | 0 (unchanged) | 177 | 148 | 9 |
| v6 | 3.2.0 | 14 | 176 | 136 | 15 |
| v7 | 3.1.3 | 0 (unchanged) | 177 | 148 | 9 |
| v8 | 3.2.1 | 0 (unchanged) | 176 | 136 | 15 |
| v9 | 3.2.2 | 0 (unchanged) | 176 | 136 | 15 |
| v10 | 3.2.3 | 0 (unchanged) | 176 | 136 | 15 |
| v11 | 3.3.0 | 18 | 176 | 120 | 23 |
| v12 | 3.3.1 | 0 (unchanged) | 176 | 118 | 23 |
| v13 | 3.3.2 | 0 (unchanged) | 176 | 118 | 23 |
| v14 | 3.3.3 | 0 (unchanged) | 176 | 118 | 23 |
| v15 | 3.4.0 | 78 | 175 | 2 | 46 |
| v16 | 3.4.1 | 0 (unchanged) | 175 | 2 | 46 |
| v17 | 3.4.2 | 0 (unchanged) | 175 | 2 | 46 |
| v18 | 3.5.0 | 32 | 176 | 0 | 48 |
| v19 | 3.5.1 | 0 (unchanged) | 176 | 0 | 48 |
| v20 | 3.5.2 | 0 (unchanged) | 176 | 0 | 48 |
| v21 | 3.5.3 | 0 (unchanged) | 176 | 0 | 48 |
| v22 | 3.5.4 | 0 (unchanged) | 176 | 0 | 48 |
| v23 | 3.6.0 | 16 | 176 | 0 | 49 |
| v24 | 3.6.2 | 0 (unchanged) | 176 | 0 | 49 |
| v25 | 3.6.3 | 0 (unchanged) | 176 | 0 | 49 |
| v26 | 3.7.0 | 6 | 176 | 0 | 52 |
| v27 | 3.6.4 | 0 (unchanged) | 176 | 0 | 49 |
| v28 | 3.7.1 | 0 (unchanged) | 176 | 0 | 52 |
| v29 | 3.8.0 | 21 | 172 | 0 | 53 |
| v30 | 3.9.0-beta (git 3eb8d31) | 0 (unchanged) | 168 | 0 | 110 |

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

## Manual review and excluded pairs
211 pairs (all changed-locator, flagged, moved and unchanged samples) were reviewed by hand in `pair-review-dolibarr.html`: 185 same, 26 different. The differences are concentrated in a few steps whose target is positional
(theme radio buttons `modifyDisplay.skin_1..4`, module activation icons `lpdap`/`margins`/`resource`, `deleteLabel`, plus `jabberid` and `selected_day` in v30). A rule derived from the reviewed pairs
(radio value, icon title, module name must match; `scripts/analysis`-style audit in `dolibarr-work/audit_pairs.py`) flagged 176 further pairs; the rule agreed with 24 of 32 reviewed flagged pairs and with all 15 reviewed unflagged pairs.
Those 176 plus the two v30 pairs (`excluded_sites.txt`, 178 in total) are left out of the headline scores: all algorithms ran on all 5,094 pairs, the exclusion is applied when scoring (`EXCLUDE_SITES` in `riskcoverage.py`).
The reviewer's verdicts are inconsistent for the theme buttons (skin_1: same in v6, v8, v25, v29 but different in v30), so those steps have no clean ground truth and are excluded when the value changes.

## Results
Scored on 4,916 pairs (5,094 minus the 178 excluded). Single-lookup pairs: the repair variants of MultiLocator cannot change a first lookup, so they equal plain MultiLocator.
```
== dolibarr_clean: 22 algorithms with >= 4424 pairs
algorithm                       pairs   right  wrong  silent |  accuracy  coverage precision
OptimizedCombined                4916    4601    315       0 |     93.6%    100.0%     93.6%  scored
OptimizedSimilo                  4916    4601    315       0 |     93.6%    100.0%     93.6%  scored
OptimizedMinimal                 4916    4595    321       0 |     93.5%    100.0%     93.5%  scored
COLOR                            4916    4531    385       0 |     92.2%    100.0%     92.2%  scored
LegacyCombined                   4916    4502    414       0 |     91.6%    100.0%     91.6%  scored
LegacySimilo                     4916    4502    414       0 |     91.6%    100.0%     91.6%  scored
VonSimilo                        4916    4295    621       0 |     87.4%    100.0%     87.4%  scored
OptimizedVonSimilo               4916    4290    626       0 |     87.3%    100.0%     87.3%  scored
MultiLocator+VISTA+repair   4916    4220    248     448 |     85.8%     90.9%     94.4%  
MultiLocator+VISTA               4916    4220    248     448 |     85.8%     90.9%     94.4%  
MultiLocator+repair        4916    4214    231     471 |     85.7%     90.4%     94.8%  
MultiLocator+selective-repair    4916    4214    231     471 |     85.7%     90.4%     94.8%  
MultiLocator                     4916    4214    231     471 |     85.7%     90.4%     94.8%  
RobulaPlus                       4916    4127    318     471 |     84.0%     90.4%     92.8%  
EAGL                             4916    3918    223     775 |     79.7%     84.2%     94.6%  
SeleniumIDE                      4916    3434     96    1386 |     69.9%     71.8%     97.3%  
SIDEREAL-uniform                 4916    3396     80    1440 |     69.1%     70.7%     97.7%  
SIDEREAL                         4916    3396     80    1440 |     69.1%     70.7%     97.7%  
Montoto                          4916    2739     49    2128 |     55.7%     56.7%     98.2%  
VISTA                            4916    2571   1049    1296 |     52.3%     73.6%     71.0%  scored
IDXPath                          4916    2154    104    2658 |     43.8%     45.9%     95.4%  
absXPath                         4916    1811    128    2977 |     36.8%     39.4%     93.4%  

-- scored algorithms limited to the same number of answers as each fixed algorithm (precision / accuracy):
reference (fixed)              answers     prec | COLOR              | LegacyCombined     | LegacySimilo       | OptimizedCombined  | OptimizedMinimal   | OptimizedSimilo    | OptimizedVonSimilo | VISTA              | VonSimilo         
MultiLocator+VISTA+repair      4468    94.4% | 96.2% / 87.4%      | 97.0% / 88.2%      | 97.0% / 88.2%      | 97.4% / 88.5%      | 97.9% / 89.0%      | 97.4% / 88.5%      | 91.0% / 82.7%      | -                  | 92.5% / 84.1%     
MultiLocator+VISTA                4468    94.4% | 96.2% / 87.4%      | 97.0% / 88.2%      | 97.0% / 88.2%      | 97.4% / 88.5%      | 97.9% / 89.0%      | 97.4% / 88.5%      | 91.0% / 82.7%      | -                  | 92.5% / 84.1%     
MultiLocator+repair         4445    94.8% | 96.2% / 87.0%      | 97.1% / 87.8%      | 97.1% / 87.8%      | 97.4% / 88.1%      | 98.0% / 88.6%      | 97.4% / 88.1%      | 91.0% / 82.3%      | -                  | 92.4% / 83.6%     
MultiLocator+selective-repair      4445    94.8% | 96.2% / 87.0%      | 97.1% / 87.8%      | 97.1% / 87.8%      | 97.4% / 88.1%      | 98.0% / 88.6%      | 97.4% / 88.1%      | 91.0% / 82.3%      | -                  | 92.4% / 83.6%     
MultiLocator                      4445    94.8% | 96.2% / 87.0%      | 97.1% / 87.8%      | 97.1% / 87.8%      | 97.4% / 88.1%      | 98.0% / 88.6%      | 97.4% / 88.1%      | 91.0% / 82.3%      | -                  | 92.4% / 83.6%     
RobulaPlus                        4445    92.8% | 96.2% / 87.0%      | 97.1% / 87.8%      | 97.1% / 87.8%      | 97.4% / 88.1%      | 98.0% / 88.6%      | 97.4% / 88.1%      | 91.0% / 82.3%      | -                  | 92.4% / 83.6%     
EAGL                              4141    94.6% | 96.4% / 81.2%      | 97.6% / 82.2%      | 97.6% / 82.2%      | 97.6% / 82.2%      | 98.4% / 82.9%      | 97.6% / 82.2%      | 92.8% / 78.2%      | -                  | 93.0% / 78.3%     
SeleniumIDE                       3530    97.3% | 97.4% / 69.9%      | 98.6% / 70.8%      | 98.6% / 70.8%      | 98.4% / 70.6%      | 98.9% / 71.0%      | 98.4% / 70.6%      | 93.4% / 67.1%      | 72.7% / 52.2%      | 93.3% / 67.0%     
SIDEREAL-uniform                  3476    97.7% | 97.4% / 68.9%      | 98.8% / 69.8%      | 98.8% / 69.8%      | 98.4% / 69.6%      | 98.9% / 69.9%      | 98.4% / 69.6%      | 93.4% / 66.0%      | 73.8% / 52.2%      | 93.4% / 66.0%     
SIDEREAL                          3476    97.7% | 97.4% / 68.9%      | 98.8% / 69.8%      | 98.8% / 69.8%      | 98.4% / 69.6%      | 98.9% / 69.9%      | 98.4% / 69.6%      | 93.4% / 66.0%      | 73.8% / 52.2%      | 93.4% / 66.0%     
Montoto                           2788    98.2% | 96.8% / 54.9%      | 98.9% / 56.1%      | 98.9% / 56.1%      | 99.2% / 56.2%      | 99.6% / 56.5%      | 99.2% / 56.2%      | 93.4% / 53.0%      | 69.3% / 39.3%      | 93.8% / 53.2%     
IDXPath                           2258    95.4% | 98.4% / 45.2%      | 98.8% / 45.4%      | 98.8% / 45.4%      | 99.4% / 45.6%      | 99.8% / 45.9%      | 99.4% / 45.6%      | 92.6% / 42.5%      | 62.0% / 28.5%      | 93.7% / 43.0%     
absXPath                          1939    93.4% | 98.7% / 38.9%      | 98.7% / 38.9%      | 98.7% / 38.9%      | 100.0% / 39.4%     | 100.0% / 39.4%     | 100.0% / 39.4%     | 92.0% / 36.3%      | 55.8% / 22.0%      | 93.1% / 36.7%     
```

All 5,094 pairs (nothing excluded):
```
== dolibarr_all_pairs: 22 algorithms with >= 4584 pairs
algorithm                       pairs   right  wrong  silent |  accuracy  coverage precision
OptimizedSimilo                  5094    4673    421       0 |     91.7%    100.0%     91.7%  scored
OptimizedCombined                5094    4671    423       0 |     91.7%    100.0%     91.7%  scored
OptimizedMinimal                 5094    4659    435       0 |     91.5%    100.0%     91.5%  scored
COLOR                            5094    4594    500       0 |     90.2%    100.0%     90.2%  scored
LegacyCombined                   5094    4525    569       0 |     88.8%    100.0%     88.8%  scored
LegacySimilo                     5094    4525    569       0 |     88.8%    100.0%     88.8%  scored
OptimizedVonSimilo               5094    4331    763       0 |     85.0%    100.0%     85.0%  scored
VonSimilo                        5094    4318    776       0 |     84.8%    100.0%     84.8%  scored
MultiLocator+VISTA+repair   5094    4259    325     510 |     83.6%     90.0%     92.9%  
MultiLocator+VISTA               5094    4259    325     510 |     83.6%     90.0%     92.9%  
MultiLocator+repair        5094    4252    305     537 |     83.5%     89.5%     93.3%  
MultiLocator+selective-repair    5094    4252    305     537 |     83.5%     89.5%     93.3%  
MultiLocator                     5094    4252    305     537 |     83.5%     89.5%     93.3%  
RobulaPlus                       5094    4161    414     519 |     81.7%     89.8%     91.0%  
EAGL                             5094    3971    276     847 |     78.0%     83.4%     93.5%  
SeleniumIDE                      5094    3435    108    1551 |     67.4%     69.6%     97.0%  
SIDEREAL-uniform                 5094    3397    137    1560 |     66.7%     69.4%     96.1%  
SIDEREAL                         5094    3397    137    1560 |     66.7%     69.4%     96.1%  
Montoto                          5094    2740    106    2248 |     53.8%     55.9%     96.3%  
VISTA                            5094    2576   1174    1344 |     50.6%     73.6%     68.7%  scored
IDXPath                          5094    2170    104    2820 |     42.6%     44.6%     95.4%  
absXPath                         5094    1827    128    3139 |     35.9%     38.4%     93.5%  

-- scored algorithms limited to the same number of answers as each fixed algorithm (precision / accuracy):
reference (fixed)              answers     prec | COLOR              | LegacyCombined     | LegacySimilo       | OptimizedCombined  | OptimizedMinimal   | OptimizedSimilo    | OptimizedVonSimilo | VISTA              | VonSimilo         
MultiLocator+VISTA+repair      4584    92.9% | 94.3% / 84.8%      | 94.7% / 85.2%      | 94.7% / 85.2%      | 95.4% / 85.8%      | 95.9% / 86.3%      | 95.4% / 85.8%      | 89.1% / 80.2%      | -                  | 90.2% / 81.2%     
MultiLocator+VISTA                4584    92.9% | 94.3% / 84.8%      | 94.7% / 85.2%      | 94.7% / 85.2%      | 95.4% / 85.8%      | 95.9% / 86.3%      | 95.4% / 85.8%      | 89.1% / 80.2%      | -                  | 90.2% / 81.2%     
MultiLocator+repair         4557    93.3% | 94.3% / 84.3%      | 94.7% / 84.7%      | 94.7% / 84.7%      | 95.4% / 85.3%      | 95.9% / 85.8%      | 95.4% / 85.3%      | 89.2% / 79.8%      | -                  | 90.2% / 80.7%     
MultiLocator+selective-repair      4557    93.3% | 94.3% / 84.3%      | 94.7% / 84.7%      | 94.7% / 84.7%      | 95.4% / 85.3%      | 95.9% / 85.8%      | 95.4% / 85.3%      | 89.2% / 79.8%      | -                  | 90.2% / 80.7%     
MultiLocator                      4557    93.3% | 94.3% / 84.3%      | 94.7% / 84.7%      | 94.7% / 84.7%      | 95.4% / 85.3%      | 95.9% / 85.8%      | 95.4% / 85.3%      | 89.2% / 79.8%      | -                  | 90.2% / 80.7%     
RobulaPlus                        4575    91.0% | 94.3% / 84.7%      | 94.7% / 85.1%      | 94.7% / 85.1%      | 95.4% / 85.7%      | 95.9% / 86.1%      | 95.4% / 85.7%      | 89.2% / 80.1%      | -                  | 90.2% / 81.0%     
EAGL                              4247    93.5% | 94.3% / 78.6%      | 95.3% / 79.5%      | 95.3% / 79.5%      | 95.5% / 79.6%      | 96.1% / 80.1%      | 95.5% / 79.6%      | 90.9% / 75.8%      | -                  | 90.9% / 75.8%     
SeleniumIDE                       3543    97.0% | 94.9% / 66.0%      | 96.3% / 67.0%      | 96.3% / 67.0%      | 96.2% / 66.9%      | 96.3% / 67.0%      | 96.2% / 66.9%      | 91.1% / 63.3%      | 72.4% / 50.4%      | 91.0% / 63.3%     
SIDEREAL-uniform                  3534    96.1% | 94.9% / 65.8%      | 96.3% / 66.8%      | 96.3% / 66.8%      | 96.2% / 66.7%      | 96.3% / 66.8%      | 96.2% / 66.7%      | 91.0% / 63.2%      | 72.6% / 50.4%      | 91.2% / 63.3%     
SIDEREAL                          3534    96.1% | 94.9% / 65.8%      | 96.3% / 66.8%      | 96.3% / 66.8%      | 96.2% / 66.7%      | 96.3% / 66.8%      | 96.2% / 66.7%      | 91.0% / 63.2%      | 72.6% / 50.4%      | 91.2% / 63.3%     
Montoto                           2846    96.3% | 94.4% / 52.7%      | 96.7% / 54.0%      | 96.7% / 54.0%      | 96.2% / 53.7%      | 96.5% / 53.9%      | 96.2% / 53.7%      | 91.5% / 51.1%      | 66.3% / 37.0%      | 91.7% / 51.2%     
IDXPath                           2274    95.4% | 96.5% / 43.1%      | 96.4% / 43.0%      | 96.4% / 43.0%      | 96.2% / 42.9%      | 96.1% / 42.9%      | 96.2% / 42.9%      | 90.5% / 40.4%      | 57.8% / 25.8%      | 91.6% / 40.9%     
absXPath                          1955    93.5% | 96.3% / 36.9%      | 95.9% / 36.8%      | 95.9% / 36.8%      | 95.9% / 36.8%      | 96.1% / 36.9%      | 95.9% / 36.8%      | 89.6% / 34.4%      | 50.9% / 19.6%      | 90.5% / 34.7%     
```
