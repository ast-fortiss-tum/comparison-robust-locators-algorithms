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
