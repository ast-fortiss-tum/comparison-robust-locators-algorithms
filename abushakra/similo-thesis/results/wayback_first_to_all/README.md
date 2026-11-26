# Wayback, first-to-all pairing (each snapshot paired with the first one), 36 sites, 12,266 pairs
`dump_laneA/`: 14 algorithms (Similo family, COLOR, generators, SIDEREAL; OptimizedVonSimilo has 11,572 pairs, one site missing). `dump_laneB/`: EAGL, VISTA, MultiLocator+VISTA and `MultiLocator+VISTA+repair`. The last one covers 34 of 36 sites (11,524 pairs; accuracy 74.6%, precision 86.5%, 1,587 silent): adobe (a runaway Robula+ call) and zz_cnn hit the 10-minute limit per site, so the row is partial. `mlvp_progress.log` and `mlvp.timeouts*` record the 5-minute pass, the 10-minute retry (before the caching fix) and the rerun after the caching fix. Analysis: `../riskcoverage/`.

## Repair variants on all benchmarks (2026-11-26)
The MultiLocator repair variants that were still missing were run on every benchmark (`scripts/analysis/add_repair_variants.py` copies the dumps and adds the dashboard rows).
Names: `MultiLocator+repair` = Algorithm 2 of the paper (formerly `+paper-repair`), `MultiLocator+selective-repair` = repair only the locators that did not vote for the winner,
`MultiLocator+full-repair` = repair every locator after each answer (formerly `MultiLocator+repair`), and the same three with `+VISTA`.
The Wayback dumps of the new runs are in `dump_repair_variants/`. `MultiLocator+VISTA+selective-repair` is partial there: adobe (the Robula+ regeneration of the repair step does not finish, see FUTURE_WORK, known gap)
and `zz_cnn` (90 minute limit per site) are missing, 11,524 of 12,266 pairs. Its accuracy is therefore not comparable with the other rows.
On the ReproBreak benchmark the old accuracy-only row of `MultiLocator+VISTA+full-repair` (178 of 350) was replaced by the per-pair result (181 of 350).
