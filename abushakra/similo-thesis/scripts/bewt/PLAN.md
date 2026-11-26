# BEWT evaluation plan (agreed 2026-11-14)
Order: (1) finish Wayback (MultiLocator+VISTA+full-repair) -> (2) finish all algorithms on ReproBreak (EAGL + EAGL-scaled adobe/f0..f4 missing) -> (3) BEWT, all four apps at once.
Apps (v1 -> v2): bludit 2.3.4->3.13.1, joomla 3.10.11->4.2.0, mantisbt 1.2.0->2.25.4, prestashop 1.6.1.23->1.7.8.5.
Repo: /Users/stocco/repos/tools/BenchmarkE2EWebTesting/<app>/baseline/<app-ver> (page objects in src/main/java/po, tests in src/test/java/{tests|base}).
Capture: wrap Selenium WebDriver (Java) so each findElement dumps DOM, full-page screenshot, element xpath/attributes/bbox, plus calling po method + ordinal for v1/v2 alignment.
Static first look: bludit 24 common tests; po locators via By.* and probably @FindBy (not yet counted); test packages differ (tests vs base).
Constraints: Docker memory capped, one heavy app at a time unless free memory allows, keep guard3.sh on.
