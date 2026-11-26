# PHP-Fusion multi-release locator benchmark

Same construction as the Dolibarr benchmark. Source: the TSE study's PHP-Fusion artifact, 41 releases (6.00.207 to 7.02.00, `releases.txt` maps v1..v41 to the release) with one Selenese IDE suite per version.
The original suites do not pass on their own releases and the authors' `test.php` content seeder is lost, so the suites were repaired release by release and a replacement seeder written
(`scripts/phpfusion/seed/test.php`). After repair every release passes 28/28 tests from a clean fresh install (PHP 5.6 + MySQL 5.6 in Docker).

- Suite per release (`suites/`, edits in `repairs/repairs_vN.jsonl`, about 390 step edits): v1 for v1-v6, v7 for v7-v17, v18 for v18-v25, v26 for v26-v31, v32, v33, v34 for v34-v40, v41.
- Step identity = lineage through the repair logs (`scripts/phpfusion/lineage_pf.py`); a pair is the same step captured in v1 and in vN with a visible target (22,125 pairs, first-to-all v1 -> v2..v41).
- Deduplicated by identical new state (earliest release kept, `dups` lists the later releases with the same new state): 1,936 pairs (`pairs_pf_dedup.json`).
- Manual review of 340 pairs (all 250 risk-flagged plus a 90-pair unflagged sample; `review_pairs.json`, `verdicts.json`, page source `scripts/phpfusion/pair-review-phpfusion.html`): 320 same element, 20 different.
  The 20 different pairs (and the later releases they stand for) were removed, leaving 1,916 pairs (`plan.json`, imported with prefix pf: `manifest.csv`). Unreviewed pairs count as the same element.
- Weight of a pair = 1 + number of later releases with the identical new state (`weights.json`, total 21,919 pair-releases). The dashboard rows are unweighted.
- 22 algorithms, per-pair dumps in `dump/` (run with `scripts/phpfusion/pf_pipeline.sh` for the first 11, then `run_algos.sh` for the rest).

## Results (1,916 pairs, unweighted)
| Algorithm | right % | wrong % | silent % |
|---|---|---|---|
| OptimizedSimilo / OptimizedMinimal / OptimizedCombined | 94.6 | 5.4 | 0 |
| COLOR | 94.1 | 5.9 | 0 |
| OptimizedVonSimilo | 94.0 | 6.0 | 0 |
| LegacySimilo / LegacyCombined | 92.6 | 7.4 | 0 |
| VonSimilo | 92.4 | 7.6 | 0 |
| MultiLocator+VISTA (and +repair) | 89.8 | 4.6 | 5.6 |
| MultiLocator (and both repair variants) | 88.7 | 1.8 | 9.6 |
| RobulaPlus | 87.8 | 2.6 | 9.6 |
| EAGL | 79.1 | 0.6 | 20.3 |
| SIDEREAL / SIDEREAL-uniform | 76.9 | 0.5 | 22.7 |
| SeleniumIDE | 73.2 | 1.5 | 25.3 |
| Montoto | 65.7 | 0 | 34.3 |
| VISTA | 60.4 | 15.9 | 23.7 |
| IDXPath | 27.8 | 0.5 | 71.7 |
| absXPath | 27.2 | 0.5 | 72.2 |

Only 135 of the 1,916 pairs have a changed locator; the absolute XPath changes for most pairs at the v7 and v26 layout changes.

## Not included
Captures (6.3 GB), DB snapshots, the downloaded releases and the SIR artifact stay in `phpfusion-work/` and `hybrid-work/pf/`. The helper scripts under `scripts/phpfusion/` use absolute paths of the working machine.
