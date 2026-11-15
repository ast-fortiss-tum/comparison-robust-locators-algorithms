# BEWT status report (overnight run, 2026-11-14 evening to 2026-11-15)

## Done
- Docker was down after the reboot; restarted. All 8 BEWT suites re-run with an extended capture driver (CaptureDriver now also dumps every visible element with the benchmark's extractor, so algorithms get candidate sets, plus full-page screenshot and DOM).
- Fixed: Joomla 3 (suite started before installation finished), Joomla 4 (stale apache pid in image), Prestashop 1.7 (onboarding popup; module disabled in SQL; suite now rc=0).
- Pairing: same page-object method, k-th call, same test method in v1 and v2 => same element by construction (no relabelling needed). 211 unique pairs: bludit 57, joomla 27, mantisbt 124, prestashop 3. Imported as sites/bewt_<app>_<n>.
- All algorithms run on the 211 pairs (results/bewt/summary.csv, eval_per_site.csv, eval_final.log).

## Results (found / 211)
LegacySimilo 84.8%, OptimizedMinimal 83.9%, OptimizedSimilo/Combined 83.4%, LegacyCombined 82.9%, VonSimilo 81.0%, COLOR 81.0%, OptimizedVonSimilo 79.1%,
MultiLocator 69.2% (noVISTA 64.9%), RobulaPlus 68.2%, SIDEREAL 62.6%, SeleniumIDE 55.5%, EAGL 43.6% (scaled-adobe identical), VISTA 28.0%, IDXPath 19.9%, Montoto 17.5%, absXPath 12.3%.
190 of 211 pairs have the target at a different absolute XPath; Similo-family still finds ~82% of them (ReproBreak moved: ~2-22%).

## Caveats
- Prestashop contributes only 3 pairs: 1.6.1.23 runs under amd64 emulation; its installer test times out and the suite hits request timeouts (two re-runs, 4-11 test dirs). Its captures should be considered unusable; needs native image or longer timeouts.
- Joomla 3 (36 failing tests) and Joomla 4 (12, infra timeouts) are partial.
- BEWT pages have fewer candidates than ReproBreak (median 107 vs 350 elements), and many pairs are login/admin pages, so the benchmark is easier than ReproBreak. Dedupe is per (po method, locators, xpaths, url path).
- EAGL-scaled-f0..f4 rows in eval_final.log are meaningless (category fold models need Wayback categories; no caches); ignore them. Only EAGL plain and scaled-adobe were generated.
- VISTA's OpenCV fallback bug fixed (venv python symlink); VISTA 28% is real now.
- Not done: ReproBreak EAGL fold variants, Wayback (MultiLocator+repair) items from the plan; nothing committed.

## Files
Work dir /Users/stocco/repos/tools/bewt-work (LOG.md, out/, imp/); repo changes uncommitted in comparison-robust-locators-algorithms: results/bewt/, scripts/bewt/, ImportReproBreak.scala (prefix arg), crop_element.py/visual_match.py.

## Update (2026-11-15 morning): Joomla re-run
- Joomla 3: failures were a race (the "Site Administrator" link opens a new tab and the page object looks elements up before it loads; the suite has no implicit wait). A 10 s implicit wait in the instrumented BaseTest makes all 35 tests pass. Joomla 4 re-run alone: all 35 pass (earlier failures were timeouts under load).
- Pairs: 221 (bludit 57, joomla 37, mantisbt 124, prestashop 3). EAGL regenerated for all.
- Found / 221: LegacySimilo 85.5%, OptimizedMinimal 84.6%, OptimizedSimilo/Combined 84.2%, LegacyCombined 83.7%, VonSimilo/COLOR 81.9%, OptimizedVonSimilo 80.1%, MultiLocator(+repair) 70.6% (noVISTA 66.5%), RobulaPlus 68.3%, SIDEREAL 63.3%, SeleniumIDE 56.1%, EAGL 45.2%, VISTA 31.2%, IDXPath 23.5%, Montoto 19.9%, absXPath 14.5%.
(The numbers in the first sections above are from the earlier 211-pair run.)

## Update 2 (2026-11-15 midday): Prestashop 1.6 captured, 337 pairs
- Prestashop 1.6 root causes: (1) emulated MySQL 5.7 replaced by native MariaDB 10.5 (arm64); (2) the installer guessed Italy and failed to download the Italian language pack, stalling at installDefaultData; the test now sets country=US; (3) installer wait raised so the script no longer deletes /install mid-installation. Suite: 35 tests, 30 pass.
- Pairs: 337 (bludit 57, joomla 37, mantisbt 124, prestashop 119); 310 moved, 27 same position, 219 with a changed locator.
- Found / 337: OptimizedSimilo & OptimizedCombined 79.2%, OptimizedMinimal 78.9%, COLOR 77.7%, LegacySimilo 76.3%, LegacyCombined 73.3%, OptimizedVonSimilo 72.7%, VonSimilo 71.5%, MultiLocator(+repair) 54.9% (noVISTA 50.7%), RobulaPlus 51.9%, SIDEREAL 49.0%, SeleniumIDE 42.7%, EAGL 37.1%, VISTA 24.9%, IDXPath 21.4%, Montoto 19.6%, absXPath 9.5%. Prestashop is harder (OptimizedSimilo 84/119).
- Sections above describe earlier, smaller runs.
