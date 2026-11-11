package de.tum.ls4.generators;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the generators against a real browser DOM (headless Chrome). Skipped if Chrome cannot be started. */
@Tag("browser")
class LiveDomTest {

    private static final String PAGE = """
            <!DOCTYPE html>
            <html><head><title>t</title></head><body>
              <nav class="menu"><a href="/a">Home</a><a href="/b">About</a></nav>
              <form id="mce_42">
                <label>Name <input name="first" type="text"></label>
                <button class="btn primary" type="submit">  Save  </button>
                <button class="btn" type="button">Cancel</button>
              </form>
              <svg width="10" height="10"><circle r="4"></circle></svg>
              <p>It's "quoted"</p>
            </body></html>
            """;

    private static ChromeDriver driver;

    @BeforeAll
    static void start() {
        try {
            if (Files.exists(Path.of("drivers/chromedriver"))) {
                System.setProperty("webdriver.chrome.driver", "drivers/chromedriver");
            }
            driver = new ChromeDriver(new ChromeOptions().addArguments("--headless=new"));
            driver.get("data:text/html;charset=utf-8," + URLEncoder.encode(PAGE, StandardCharsets.UTF_8).replace("+", "%20"));
        } catch (Exception e) {
            Assumptions.abort("Chrome not available: " + e.getMessage());
        }
    }

    @AfterAll
    static void stop() {
        if (driver != null) driver.quit();
    }

    private static void assertUnique(String xpath, WebElement target) {
        List<WebElement> found = driver.findElements(By.xpath(xpath));
        assertEquals(1, found.size(), xpath);
        assertEquals(target, found.get(0), xpath);
    }

    @Test
    void readsTheAncestryFromTheLiveDom() {
        WebElement save = driver.findElement(By.cssSelector("button.primary"));
        Ancestry a = LiveDom.ancestry(driver, save);
        assertEquals("/html[1]/body[1]/form[1]/button[1]", a.absoluteXPath());
        assertEquals("Save", a.target().text());
        assertEquals(2, a.target().positionStar()); // after <label>
        assertEquals("btn primary", a.target().attributes().get("class"));

        Ancestry circle = LiveDom.ancestry(driver, driver.findElement(By.cssSelector("circle")));
        assertNull(circle.target().tag(), "SVG elements cannot be addressed by name in an HTML document");
        assertNull(circle.node(1).tag());
    }

    @Test
    void generatedLocatorsSelectExactlyTheTarget() {
        for (String css : List.of("button.primary", "nav a:nth-child(2)", "input[name=first]", "circle", "p", "label")) {
            WebElement target = driver.findElement(By.cssSelector(css));
            GeneratedLocator robula = RobustLocators.generateRobulaPlus(driver, target);
            GeneratedLocator sidereal = RobustLocators.generateSidereal(driver, target, FragilityModel.empty());
            assertFalse(robula.fallback(), css);
            assertFalse(sidereal.fallback(), css);
            assertUnique(robula.xpath(), target);
            assertUnique(sidereal.xpath(), target);
        }
    }

    @Test
    void robulaPlusOnTheLiveDomMatchesTheHeuristics() {
        assertEquals("//*[contains(text(),'Save')]",
                RobustLocators.generateRobulaPlus(driver, driver.findElement(By.cssSelector("button.primary"))).xpath());
        // the only <input> on the page: convertStar is tried first and is already unique
        assertEquals("//input",
                RobustLocators.generateRobulaPlus(driver, driver.findElement(By.cssSelector("input"))).xpath());
    }

    @Test
    void eaglLocatorsSelectTheTargetOnTheLiveDom() {
        List<String> css = List.of("button.primary", "nav a:nth-child(2)", "input[name=first]", "p", "label", "form");
        List<WebElement> targets = css.stream().map(c -> driver.findElement(By.cssSelector(c))).toList();
        List<Eagl.Result> results = new Eagl().generateAll(driver, targets);
        for (int i = 0; i < targets.size(); i++) {
            assertTrue(results.get(i).ok(), css.get(i) + ": " + results.get(i).failure());
            List<WebElement> found = driver.findElements(By.xpath(results.get(i).xpath()));
            assertFalse(found.isEmpty(), css.get(i) + " -> " + results.get(i).xpath());
            assertEquals(targets.get(i), found.get(0), css.get(i) + " -> " + results.get(i).xpath());
        }
        assertEquals("//form", results.get(5).xpath(), "unique tag name: EAGL's first check");
        assertFalse(RobustLocators.generateEagl(driver, targets.get(0)).fallback());
    }

    @Test
    void seleniumIdeFollowsItsBuilderPriority() {
        WebElement form = driver.findElement(By.id("mce_42"));
        assertEquals(new SeleniumIde.Candidate("id=mce_42", "id"), SeleniumIde.build(driver, form));

        WebElement about = driver.findElement(By.cssSelector("nav a:nth-child(2)"));
        List<SeleniumIde.Candidate> all = SeleniumIde.buildAll(driver, about);
        assertEquals("linkText=About", all.get(0).locator());
        assertEquals(List.of("linkText", "css:finder", "xpath:link", "xpath:href", "xpath:position", "xpath:innerText"),
                all.stream().map(SeleniumIde.Candidate::builder).toList());
        for (SeleniumIde.Candidate c : all) {
            assertEquals(about, driver.findElements(c.toBy()).get(0), c.locator());
        }

        WebElement input = driver.findElement(By.cssSelector("input"));
        assertEquals("name=first", SeleniumIde.build(driver, input).locator());
        assertUnique(SeleniumIde.buildXPath(driver, input).toXPath(), input);
    }

    private static de.tum.ls4.artifacts.Element scrape(WebElement e) {
        Object raw = de.tum.ls4.Javascript.create()
                .addFile("extractors/attributes.js").addFile("extractors/css_styles.js").addFile("extractors/xpath.js")
                .addFile("extractors/css_selector.js").addFile("extractors/direct_text.js").addFile("extractors/element.js")
                .addScript("return extract_element_from_web_element(arguments[0]);")
                .executeOn((org.openqa.selenium.JavascriptExecutor) driver, e);
        return new de.tum.ls4.artifacts.Element(de.tum.ls4.utils.Utils.checkedCastToMap(raw));
    }

    @Test
    void colorRanksTheSameElementFirstAfterAChange() {
        WebElement save = driver.findElement(By.cssSelector("button.primary"));
        var stored = scrape(save);
        double self = de.tum.ls4.locators.ByColor.score(stored, stored);
        // Absent properties (no id, name, src, href, ... on this button) score 0, as in Similo and the thesis' COLOR
        assertTrue(self > 0.4 && self <= 1.0, "self score " + self);
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "arguments[0].className = 'btn btn-save'; arguments[0].textContent = 'Save changes';", save);
        try {
            var changed = scrape(save);
            var cancel = scrape(driver.findElement(By.xpath("//button[@type='button']")));
            double toChanged = de.tum.ls4.locators.ByColor.score(stored, changed);
            double toCancel = de.tum.ls4.locators.ByColor.score(stored, cancel);
            assertTrue(self >= toChanged && toChanged > toCancel, self + " >= " + toChanged + " > " + toCancel);
        } finally {
            driver.navigate().refresh();
        }
    }

    @Test
    void vistaRelocatesAnElementVisually() {
        WebElement save = driver.findElement(By.cssSelector("button.primary"));
        Vista.Snapshot snapshot = Vista.capture(driver, save);
        assertTrue(snapshot.template().length > 0);
        assertEquals(save, new Vista().relocate(driver, snapshot).element(), "unchanged page");

        // Evolve: push the form down and change the button's attributes (but not its rendering)
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "const p = document.createElement('div'); p.style.height = '700px'; p.textContent = 'banner';"
                        + "document.body.insertBefore(p, document.body.firstChild);"
                        + "arguments[0].removeAttribute('type'); arguments[0].id = 'renamed';", save);
        try {
            Vista.Match match = new Vista().relocate(driver, snapshot);
            assertEquals(save, match.element(), "found after moving: score " + match.score());
        } finally {
            driver.navigate().refresh();
        }
    }

    @Test
    void multiLocatorVotesAndRepairs() {
        WebElement save = driver.findElement(By.cssSelector("button.primary"));
        MultiLocator multi = MultiLocator.generate(driver, save);
        assertEquals(List.of("ABSOLUTE_XPATH", "RELATIVE_XPATH", "SELENIUM_IDE", "MONTOTO", "ROBULA+"),
                multi.voters().stream().map(MultiLocator.Voter::strategy).toList());
        assertEquals(save, driver.findElement(multi));
        assertEquals(5, multi.lastVotes().get(0).voters().size(), "all locators agree on the unchanged page");

        // Evolve the page: a new first button shifts positions and the target's class changes
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "const f = document.getElementById('mce_42');"
                        + "const b = document.createElement('button'); b.textContent = 'Reset'; f.insertBefore(b, f.querySelector('button'));"
                        + "arguments[0].className = 'btn btn-primary';", save);
        try {
            assertEquals(save, driver.findElement(multi), "the vote still finds the target");
            MultiLocator.Vote winner = multi.lastVotes().get(0);
            assertFalse(winner.voters().size() == 5, "some locators broke: " + multi.lastVotes());

            MultiLocator repaired = multi.repaired(driver);
            assertEquals(save, driver.findElement(repaired));
            assertEquals(5, repaired.lastVotes().get(0).voters().size(), "repair re-aligns the broken locators");
        } finally {
            driver.navigate().refresh();
        }
    }
}
