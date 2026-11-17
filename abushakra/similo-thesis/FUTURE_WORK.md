# Future work

Planned extensions that are not started. Keep entries short; move one to `scripts/<name>/` once it has code.

## 1. LLM-based locator generation with volatile-stripped observations

**Source.** Angelov and Lazarova, "Cost-Efficient Observation for LLM-Based UI Grounding", *Applied Sciences* 16, 11180 (2026), DOI 10.3390/app162211180. Code and data per the paper: Zenodo DOI 10.5281/zenodo.23822894, mirrored at github.com/angelovstanton/observation-sufficiency (MIT code, CC BY 4.0 data). Not yet inspected.

**What it adds to our comparison.** A model-based generator family next to the XPath generators (Robula+, Selenium IDE, Montoto, MultiLocator, EAGL): an LLM receives a compact text observation of the original page and returns one locator for the target element. Closest published relative that we do not run: VON Similo LLM (Nass et al. 2024).

**Components, in the order I would build them**
1. **Volatile-token filter (no LLM).** Drop build-generated class tokens and ids (hashed CSS-module suffixes, framework-generated ids) from the element's attributes. Per the paper it raised grounding success by 9.5 points (16 points with more complete filtering) by removing decoys, not by adding information. Try it first as preprocessing for the existing generators and for Similo's class and id comparisons.
2. **Compact serialisation.** One line per element, `tag attr=value {text}`, attribute names kept. Per the paper this saves 29% of tokens against full-key JSON with no loss. Abbreviated keys lose 12 points and save 0.2%, so do not abbreviate.
3. **LLM generator.** Fixed prompt that returns one locator, run on the volatile-stripped, compact observation.
4. **Role- or ARIA-based locators** (accessible role plus name) as a further generator family. The paper lists them as untested.

**Metrics to add**
- **P5-style predicate for any generator** on the original page: unique, correct, non-volatile, non-positional, and a stable signal exists. Extend `multilocator/LocatorQuality.scala` (it already counts positional indices) with a volatile-token check.
- **Does P5 predict survival?** The paper found a 9-point P5 gain but no survival advantage on real pages. We have real outcomes (`out/dump/*.csv`), so we can test whether non-volatile, non-positional locators survive better.
- **Lacked versus grabbed per element.** "Lacked" means the target has no stable attribute at all, so no attribute-based method can succeed. Use it to stratify benchmarks by true difficulty and to sample hard elements for the harder benchmark.
- **Cost.** Tokens and money per element next to accuracy, reported as an accuracy-versus-cost Pareto frontier.

**Open points**
- Sending page content to an external API needs the user's explicit go-ahead; choose the model and the budget first.
- Repeated generations: the paper saw 92.9–98.8% identical outcomes across three runs at temperature 0, so run at least three and report agreement.
- The paper outputs CSS selectors. Our ground truth is an element identity, so map the returned locator to an element on the new page and score it like the other algorithms.
- The paper's id rule was not supported by its own Common Crawl check (6.1% of flagged ids disappeared against 24.4% of the rest). The class rule was (46.4% against 13.7%). Use the class rule, and measure the id rule before trusting it.
- Check whether our element extractor reaches into shadow roots (the paper's corpus has 32% of targets inside them).

## 2. Common Crawl–based benchmark

**Design to copy** (paper, section 3.9). Take the first 100 domains of the Tranco list that return HTTP 200 and `text/html` in two Common Crawl snapshots (the paper used CC-MAIN-2026-21 and CC-MAIN-2026-39, four months apart). Four inclusion conditions left 33 sites. Targets were chosen automatically from clickable and fillable elements in the light DOM (326 in total), and tasks came from fixed Click and Fill templates. Archived HTML is loaded offline with all other network requests blocked.

**Why it is attractive for us.** Cheap, repeatable, many sites, no live crawling of web.archive.org (which is rate-limited, see the Wayback notes). Two snapshots per site give version pairs at scale.

**Hard parts**
- **Identity is not known.** The paper's survival check could not be assessed for 61 of 326 targets (missing element, or not uniquely identifiable). We need a way to pair elements: stable attributes hidden afterwards (as in the Excalidraw pilot, `scripts/oracle/`), or a dedicated tie-break. This is the part to design first.
- **Server HTML, not the rendered DOM.** JavaScript-generated content is missing, and there are no screenshots, so the visual algorithms (VISTA, COLOR's visual parts) cannot run. Rendering the archived HTML in a browser, as the paper does, recovers a DOM but not the original page's behaviour.
- **Sampling.** Automatic targets from templates are mostly easy. Sample on the "lacked/grabbed" difficulty signals above.
- **Terms and redistribution.** Check the Common Crawl terms. The paper does not redistribute the archived pages; plan to publish identifiers and scripts, not pages.

**Related idea: perturbation pairs from the paper's synthetic corpus.** The paper's 24 pages (240 targets) carry hidden oracle ids and labelled volatile class and id tokens. Re-hashing the labelled tokens simulates a rebuild and gives version pairs with identity known by construction and difficulty set by the semantic-density factor (HIGH or LOW). Pages come in three shadow-DOM depths, so it also tests shadow roots. Check the repository's files and license first.

**Next steps**
1. Inspect the Zenodo and GitHub material (corpus format, `truth.json`, the volatility rule).
2. Prototype the volatile-token filter as preprocessing and re-run BEWT, ReproBreak and Wayback tuning on the generators.
3. Decide the identity-pairing method for Common Crawl pairs; pilot on 10 sites.

## 3. Validation plan: LLMs that generate locators directly

Written after reading D. R. Kılıç, MSc thesis, TUM, 26.01.2026 ("LLM-based generation of robust web element locators"). The thesis mostly tests LLM *relocators* (the LLM scores candidates in the new DOM, once per element and version). Its one generative variant, LLMLocator5 (XPath from the target plus 5 neighbours before and after), reached about 30%, was not tabulated, and was dropped. Its matching compared the generated XPath as a string to each candidate's absolute Chrome XPath (exact, then path similarity above 0.7). That penalises exactly the short relative XPaths a robust generator should write, so the 30% does not refute the idea. Not verified against the thesis code.

**Hypotheses**
- **H1, validity at authoring time.** With a generate-execute-repair loop on the original page (possible only for generators), at least 95% of LLM locators resolve uniquely to the target on version A.
- **H2, robustness.** Executed on the new page, they are Pareto-better than the classical generators on (right, wrong), with abstentions reported separately.
- **H3, cost.** One call per element at authoring time, none per version, so cost per element is amortised over all later versions (the relocator pays per lookup).
- **H4, observation content matters.** Pruned context (ancestor chain, siblings, volatile tokens removed, compact format) beats the thesis-style 11-element neighbour window.
- **H5, ensembles.** K samples voted like MultiLocator, or an LLM locator with Similo as fallback or verifier, improve precision.

**Stages, each with a stop rule**
0. **Harness, no LLM.** Execute any locator string on the new page via JDOM and score it right, wrong or abstain (reuse `DUMP_DIR`); add the volatile-token check to `LocatorQuality`; compute trivial baselines (tag plus text, id, `data-testid`) next to Robula+, MultiLocator and Similo, on the same pairs.
1. **Pilot on BEWT (337 pairs).** One mid-size model, three variants: thesis-style context, pruned context, pruned plus repair loop. Stop if H1 fails after repair, or if no variant beats MultiLocator (50.7% right, 7 wrong, 159 abstain) with a paired interval that excludes zero.
2. **BEWT and ReproBreak (687 pairs).** Models by context by repair loop by K; paired tests against the baselines; ReproBreak split by identity labels.
3. **Wayback, first-to-all.** Generate once on version 1 and evaluate on every later version; survival curves per site; cost accounting.
4. **Controls.** Contamination: compare pages before and after each model's cutoff, since 2018–2023 snapshots of popular sites are probably in training data. Run-to-run variation: three repeats per cell with agreement. Then the new multi-version sets once the pairing design is agreed.

**Reporting rules.** Intervals clustered by site or app. Equal pair sets for every compared method. Report right, wrong and abstain, not only accuracy. Include the trivial baselines in every table.

**Thesis points to keep in mind when reusing its numbers**
- Its headline 96.38% for gpt-oss is the mean of four rows: linkedin 87.5, netflix 100 (listed twice), wikipedia 98. gpt-oss ran on 3 sites only; the other models on 10. Netflix is the easiest site for every model.
- Its tag-plus-text baseline scores 78.2% accuracy, above mistral, gemma2 and phi3:medium (69.6–73.5%).
- The overlap column equals accuracy in every row; LLM locators return 3 candidates, so top-10 is top-3.
- The text says 120 targets and 1,800 pairs, Table 5.2 lists 214 targets and 2,244 pairs, and the firstAndLast table has about 50 pairs (all values are multiples of 2%).
- Its COLOR (92.56%, consecutive pairs) differs from our COLOR on first-to-all (98.93% on 36 sites). Reconcile before using it as a baseline.

## 4. Understand the hybrids and the repair variants (parked 2026-11-16)

Implemented and run, but not understood well enough to report. Code: `multilocator/Hybrid.scala`, `Vista.scala` (`withRepair`), `MultiLocator.scala` (`withRepair`, helpers), runners in `scripts/repair/` and `scripts/hybrid/`. Per-element outcomes (right, wrong, abstain) for BEWT, ReproBreak and Wayback held-out are in the hybrid work directory's `out/dump/` (not in the repo).

**Results so far** (found / pairs)

| Algorithm | BEWT | ReproBreak | Wayback tuning | Wayback held-out |
|---|---|---|---|---|
| Voting, Similo if it fails | 267/337 | 182/350 | 10,010/10,376 | 1,800/1,889 |
| Voting + Similo as extra voter | 263/337 | 179/350 | 10,100/10,376 | 1,847/1,889 |
| Voting, checked by Similo | 267/337 | 179/350 | 10,071/10,376 | 1,831/1,889 |
| Voting, Similo if it fails, then regenerate | 267/337 | 182/350 | stopped | stopped |
| OptimizedSimilo alone (reference) | 267/337 | 179/350 | 10,350/10,376 | 1,866/1,889 |
| VISTA+repair | 84/337 | 50/350 | 8,222/10,376 | 1,632/1,889 |
| MultiLocator+repair | 171/337 | 181/350 | 9,583/10,376 | not finished |
| MultiLocator+VISTA+repair | 185/337 | 181/350 | 9,720/10,376 | 1,709/1,889 |

**Open questions**
1. **Repair has no effect on any benchmark** (identical to the no-repair twin to the element) except `VISTA+repair` on ReproBreak (50/350 against 104/350 for plain VISTA). Check whether the repaired state is ever used on a later version: the on-disk store of MultiLocator (`healed-locators.json` under `sites/`) and the in-memory template store in `Vista.scala`. The store written by earlier runs also contaminated older repair rows; the runners now delete it first.
2. **Hybrids never beat Similo alone** (best: 97.3% against 99.75% on Wayback tuning). On BEWT, voting is precise (171 right of 178 answered, 7 wrong) but covers only half the elements, and it is right where Similo is wrong on only 6 of 337. Per-element dumps show where each hybrid gains and loses. Ideas not tried: Similo abstaining on a low score margin (the user declined it for now), weights for the extra-voter hybrid (0.9 was a guess, not tuned), `k` of the checked-by-Similo variant (3).
3. **Regenerate hybrid is too slow on Wayback.** It regenerates five locators including Robula+ for every element and version (1 of 30 tuning sites in over 5 hours). Needs caching or a cheaper regeneration step before it can be compared.
4. **Not run:** `MultiLocator+repair` on Wayback held-out (1 of 6 sites when stopped); the Excalidraw pilot (log lost); repair and hybrids on the new multi-version sets.
5. **Consider** metrics that separate wrong from no-answer for every algorithm (dumps exist), and a clean A/B of repair on a benchmark with several versions per element, where it can matter at all.
6. **Make repair selective (user idea, 2026-11-16).** Today `MultiLocator.locate` regenerates all five locators from the winning element after every successful lookup, including a fresh Robula+ run, and rewrites the healed-locators file. That is why the repair variants are slow on large pages. Repair only the locators that did not vote for the winner (or only when the vote is not unanimous), so the regeneration cost applies to the small subset where the original locators broke. This should also make the results comparable to the no-repair twin only where locators actually broke, which is where repair can matter.

7. **Selective repair implemented (2026-11-16)** as `MultiLocator+selective-repair` (`MultiLocator(true, false, selective = true)`): only locators that did not vote for the winner are regenerated, a regenerated locator is kept only if it resolves uniquely to the winner on its own page, and the state is in memory. Smoke test on wikipedia, zillow and amazon (1,137 pairs): 1,092 right, identical to plain MultiLocator; 508.6 s against 465.1 s (+9%). Counters: 1,149 lookups with an answer, repair triggered on 844 (73%, because Selenium IDE and Montoto often do not resolve uniquely), 1,572 locators regenerated (Robula+ only 55), 1,065 accepted, 507 rejected (Montoto 394, Selenium IDE 113). Open: the outcome never changed, so measure how often a healed locator voted for the winner where the original did not; try a stricter trigger (vote below three voters) before running on BEWT, ReproBreak and Wayback.
8. **Why repair never changed anything: the Wayback evaluation pairs consecutive versions (2026-11-16).** `Main` calls `EvaluationComputing.computeAll` with the default mapper, so version i is paired with i+1 and the old element is the ground-truth element at version i. Its locators are regenerated from that element at every step, and a repaired locator is keyed to the old element, so it is almost never reused (selective repair on tuning: used in 6 of 9,910 lookups, 9,583/10,376, equal to plain MultiLocator, 5,384 s). Every earlier repair run is uninformative for this reason. `PAIRING=firstToAll` now pairs every later version with the first one (a fixed anchor), where repaired locators are reused. Wikipedia smoke test (both 295/295): repaired locators ran in 228 lookups, 144 rescues, 38 lost votes, 42 pointing elsewhere, answer changed 0. The first-to-all comparison of MultiLocator against selective repair on Wayback tuning and held-out (`scripts/repair/run_f2a.sh`) is running; its counters decide whether repair matters. Also open: the dashboard's Wayback numbers are consecutive-pair numbers (10,376 tuning pairs); label them so, and decide whether first-to-all (harder, closer to a locator that must survive many versions) should be the headline protocol.
9. **First-to-all, Wayback held-out, selective repair against plain MultiLocator (2026-11-16, 1,889 pairs).** Plain 963 right, 70 wrong, 856 no answer; selective repair 974 right, 120 wrong, 795 no answer. Per element: no answer to right 7, no answer to wrong 57, wrong to right 6, right to no answer 2, wrong to no answer 1. Repair turned 64 abstentions into answers, but only 7 of them are correct: it mostly converts "no answer" into silent wrong answers (+11 right, +50 wrong). Counters: 1,289 lookups with an answer, repair triggered on 988, 1,913 locators regenerated, 991 accepted, 922 rejected (Montoto 457, Selenium IDE 465); repaired locators ran in 854 lookups (no answer in 863 more), 1,287 rescues, 74 lost votes, 80 pointing elsewhere, answer changed in 148. Wayback tuning first-to-all: plain MultiLocator 7,084/10,377 (68.26%), selective repair running. Next: confidence gate (option 2) or a Similo agreement gate before accepting a repair, since healing from a wrong winner is what produces the wrong answers; compare gain per added wrong answer, not accuracy alone.
10. **First-to-all, Wayback tuning, selective repair against plain MultiLocator (2026-11-16, 10,377 pairs).** Per-element outcomes (dump): plain 7,063 right, 358 wrong, 2,956 no answer; selective repair 7,423 right, 426 wrong, 2,528 no answer (headline counts 7,084 and 7,441: 68.26% and 71.70%, +3.4 pp). Changes: no answer to right 356, no answer to wrong 82, wrong to right 39, right to wrong 30, right to no answer 5, wrong to no answer 5. Of the 438 abstentions turned into answers, 81% are right, against 7 of 64 on held-out. Counters: 9,065 lookups with an answer, repair triggered on 6,354, 11,651 locators regenerated (Robula+ 470), 7,221 accepted, 4,430 rejected (Montoto 2,349, Selenium IDE 2,027), repaired locators ran in 7,116 lookups (no answer 2,978 more), 10,904 rescues, 280 lost votes, 507 pointing elsewhere, answer changed in 969. All 36 Wayback sites, first-to-all: plain 8,047/12,266 (65.6%), selective repair 8,415 (68.6%). The gain is concentrated on the tuning sites; on the large held-out news sites repair mostly adds wrong answers. Open: what separates the sites where repair helps from those where it hurts, and a confidence or Similo agreement gate.
11. **Paper-style repair (Algorithm 2), first-to-all, all 36 Wayback sites (2026-11-17).** `MultiLocator+paper-repair` repairs only locators of the original set L that no longer select the winner. Tuning: 7,406/10,377 (71.36%) against plain 7,084 (68.26%) and the earlier selective repair 7,441 (71.70%); per element right 7,388, wrong 426, no answer 2,563 (plain 7,063, 358, 2,956). Changes against plain: no answer to right 325, no answer to wrong 78, wrong to right 34, right to wrong 29, right to no answer 5, wrong to no answer 5. Held-out: 974/1,889, identical to the earlier selective repair (0 elements differ; tuning 41 differ). Repair triggered on 4,508 of 9,008 answered lookups on tuning (50%, was 70%), 6,884 of 7,606 regenerated locators accepted. Paper categories on tuning, per locator of L: C1 27,866; B1 5,091; B2 1,547; B3 459; B4 419; C2 22; B3x 140. Correct to wrong repairs 11:1 on tuning (5,091 to 459), 3.6:1 on held-out (571 to 158); the paper reports 848 to 59. All sites combined: plain 8,047/12,266 (65.6%), paper repair 8,380 (68.3%). The repair still turns many abstentions into wrong answers on the large held-out pages.
12. **Raise the ceiling of repair (2026-11-17).** After repair on Wayback tuning (first-to-all) 2,563 pairs still get no answer, and 2,234 of them (87%) belong to an element that was already lost in an earlier version: repair needs a winner in the previous version to heal from, so a broken chain never recovers (344 of 933 elements lose the answer at some point). Two separate ideas, to be used together:
    - **Re-anchoring.** When voting gives no answer, ask a relocator (Similo) for the element and heal the locators from it. Adds coverage (turns no answer into an answer, right or wrong). No evidence yet in first-to-all; in consecutive pairs the Similo fallback hybrid gave 96.5% against 99.75% for Similo alone on Wayback tuning.
    - **Confidence gate.** Repair only when the winner is trustworthy (clear vote margin, several locators agreeing, or Similo agreeing). Removes bad repairs (459 wrongly repaired locators on tuning, 158 on held-out, and 78 and 57 new wrong answers). Adds no coverage and may lose a few correct repairs. Apply it to the re-anchoring step as well, otherwise re-anchoring adds wrong answers.
    Report gain per added wrong answer, with the paper's repair categories (C1, C2, B1 to B4).
13. **Naming (2026-11-17).** The Wayback benchmark stays on the dashboard with consecutive pairs (version i against i+1). The first-to-all runs (items 8 to 11) are a side analysis. In the code, first-to-all is the survivability setting (`firstToAllMapper`, the exports to `results/survivability_results/` in the commented block of `Main.scala`, `averageLocatorSurvivabilityNormalized`), while the consecutive table is called robustness (`printRobustnessTable`, `results/robustness_heatmaps`). Kılıç's thesis calls consecutive pairs "robustness (adjacent comparison)" and first against last "evolution". Confirmed in the Abu Shakra thesis (Figure 4.1, sections 4.3.1 and 4.3.2): robustness compares consecutive versions, survivability compares the initial version against all future versions. Use "survivability" for first-to-all.
14. **Abu Shakra thesis cross-checks (2025-10-01, read 2026-11-17).**
    - Survivability (Table 5.2, 30 sites): Multi-Locator survival ratio 69.09, with repair 71.47, with VISTA 72.25, VISTA plus repair 74.16; survival curves at version 15: 51.2% plain, 56.3% with repair. Our first-to-all tuning numbers are close: plain MultiLocator 68.27%, paper repair 71.37%, selective repair 71.70%. The definitions differ (the thesis stops counting a locator at its first failure), so the agreement is encouraging, not an exact replication.
    - Robustness (Table 5.1): Multi-Locator 93.90% accuracy, 69.38% locator break success, fitness 83.73; with VISTA 95.34%, 76.70%. Our consecutive tuning numbers are about 1.5 points lower (MultiLocator 92.35%, MultiLocator+VISTA 93.67%) after the DOM regeneration of 2026-11-12; the thesis itself reports a 0.27% effect of its own XPath repairs for ChromeDriver 139. Reconcile before publishing both side by side. Optimized Similo agrees (99.47% there, 99.75% on our tuning sites).
    - The thesis pseudocode of the repair step (Pseudocode 2, line 18) is `if repair is enabled and confidenceHigh(best)`. The code we run only requires a non-empty vote list (the debug print compares top and second score but does not gate), and `RepairStore.MinConfirmations` is 1. So a confidence gate was intended but is not enforced; check the thesis code before claiming otherwise.
    - Runtime per element for locator generation (Table 5.3, 300 elements): Montoto 0.03 ms, Selenium IDE 0.48, Legacy Similo 1.81, Optimized Similo 4.57, VON Similo 11.34, ROBULA+ 16.17, COLOR 20.71, Multi-Locator 22.08, VISTA 2,282.79. Reuse for the cost metric instead of measuring again.
    - Its future work suggests SIDEREAL as a sixth voter in Multi-Locator (VISTA). We have SIDEREAL results; a MultiLocator with SIDEREAL as an extra voter is a cheap hybrid to add.

