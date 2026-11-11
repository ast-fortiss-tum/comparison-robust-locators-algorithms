package de.tum.ls4.generators;

import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-locator (Leotta, Stocco, Ricca, Tonella, "Using multi-locators to increase the robustness of web test
 * cases", ICST 2015): several locators for the same element, produced by different generation algorithms, vote for
 * the element each of them finds; the element with the highest combined vote {@code 1 - prod(1 - weight)} wins.
 * Weights are the paper's (absolute XPath 0.32, relative XPath 0.50, Selenium IDE 0.81, Montoto 0.64, ROBULA+ 0.87).
 * <p>
 * The four variants of the benchmark (multilocator.MultiLocator(withRepair, withVista)):
 * <ul>
 *   <li>{@link #generate(WebDriver, WebElement)}: voting only;</li>
 *   <li>{@link #generateWithVista(WebDriver, WebElement)}: when no locator finds anything, VISTA relocates the element
 *       visually from a snapshot taken at generation time (the element of the target's tag at the best template
 *       match);</li>
 *   <li>{@link #repaired(SearchContext)} on either: the locators that disagreed with the winning vote are regenerated
 *       on the current page ("with repair"). As in the benchmark, a multi-locator is not repaired when the element was
 *       only found by the VISTA fallback.</li>
 * </ul>
 */
public final class MultiLocator extends By {

    public static final double ABSOLUTE_XPATH = 0.32;
    public static final double RELATIVE_XPATH = 0.50;
    public static final double SELENIUM_IDE = 0.81;
    public static final double MONTOTO = 0.64;
    public static final double ROBULA_PLUS = 0.87;

    public record Voter(String strategy, By by, double weight) {
    }

    /** The outcome of the last vote: candidate elements with their combined vote and voters. */
    public record Vote(WebElement element, double score, List<String> voters) {
    }

    private final WebDriver driver;
    private final List<Voter> voters;
    private final Vista.Snapshot snapshot; // null: no VISTA fallback
    private final String tag;
    private final Vista vista;
    private List<Vote> lastVotes = List.of();
    private boolean lastFoundByVista;

    public MultiLocator(WebDriver driver, List<Voter> voters) {
        this(driver, voters, null, null, null);
    }

    /**
     * @param snapshot VISTA snapshot of the element, used when no locator finds anything (null: no fallback)
     * @param tag      the element's tag name; the fallback only returns an element with this tag (null: any)
     * @param vista    the VISTA matcher (null: default threshold)
     */
    public MultiLocator(WebDriver driver, List<Voter> voters, Vista.Snapshot snapshot, String tag, Vista vista) {
        this.driver = driver;
        this.voters = List.copyOf(voters);
        this.snapshot = snapshot;
        this.tag = tag;
        this.vista = snapshot == null ? null : (vista == null ? new Vista() : vista);
    }

    /** Generates the five locators of the paper for {@code element} on the current page. */
    public static MultiLocator generate(WebDriver driver, WebElement element) {
        return new MultiLocator(driver, voters(driver, element));
    }

    /** As {@link #generate}, plus a VISTA snapshot of the element as fallback when every locator fails. */
    public static MultiLocator generateWithVista(WebDriver driver, WebElement element) {
        return generateWithVista(driver, element, new Vista());
    }

    public static MultiLocator generateWithVista(WebDriver driver, WebElement element, Vista vista) {
        List<Voter> voters = voters(driver, element);
        Vista.Snapshot snapshot = Vista.capture(driver, element);
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
        return new MultiLocator(driver, voters, snapshot, element.getTagName(), vista);
    }

    /** Whether this multi-locator falls back to VISTA. */
    public boolean usesVista() {
        return snapshot != null;
    }

    /** Whether the last {@link #findElements} found the element only through the VISTA fallback. */
    public boolean lastFoundByVista() {
        return lastFoundByVista;
    }

    private static List<Voter> voters(WebDriver driver, WebElement element) {
        Ancestry ancestry = LiveDom.ancestry(driver, element);
        XPathOracle oracle = LiveDom.oracle(driver, element);
        List<Voter> out = new ArrayList<>();
        out.add(new Voter("ABSOLUTE_XPATH", By.xpath(LiveDom.absoluteXPath(driver, element)), ABSOLUTE_XPATH));
        out.add(new Voter("RELATIVE_XPATH", By.xpath(LiveDom.idRelativeXPath(driver, element)), RELATIVE_XPATH));
        SeleniumIde.Candidate side = SeleniumIde.build(driver, element);
        if (side != null) {
            out.add(new Voter("SELENIUM_IDE", side.toBy(), SELENIUM_IDE));
        }
        out.add(new Voter("MONTOTO", By.xpath(new Montoto().generate(ancestry, oracle).xpath()), MONTOTO));
        out.add(new Voter("ROBULA+", By.xpath(new RobulaPlus().generate(ancestry, oracle).xpath()), ROBULA_PLUS));
        return out;
    }

    public List<Voter> voters() {
        return voters;
    }

    public List<Vote> lastVotes() {
        return lastVotes;
    }

    @Override
    public List<WebElement> findElements(SearchContext context) {
        Map<WebElement, List<Voter>> byElement = new LinkedHashMap<>();
        for (Voter v : voters) {
            try {
                List<WebElement> found = context.findElements(v.by());
                if (!found.isEmpty()) {
                    byElement.computeIfAbsent(found.get(0), k -> new ArrayList<>()).add(v);
                }
            } catch (RuntimeException e) {
                // a broken locator simply does not vote
            }
        }
        List<Vote> votes = new ArrayList<>();
        byElement.forEach((element, vs) -> {
            double product = 1.0;
            for (Voter v : vs) product *= 1.0 - v.weight();
            votes.add(new Vote(element, 1.0 - product, vs.stream().map(Voter::strategy).toList()));
        });
        votes.sort((a, b) -> Double.compare(b.score(), a.score()));
        lastVotes = List.copyOf(votes);
        lastFoundByVista = false;
        if (!votes.isEmpty()) return List.of(votes.get(0).element());
        if (snapshot != null) {
            Vista.Match match = vista.relocate(driver, snapshot, tag);
            if (match != null) {
                lastFoundByVista = true;
                return List.of(match.element());
            }
        }
        return List.of();
    }

    /**
     * Locates the element by voting and regenerates the locators that did not vote for the winner, so that they
     * point to it again on the current page. Returns this multi-locator unchanged if nothing is found.
     */
    public MultiLocator repaired(SearchContext context) {
        List<WebElement> winner = findElements(context);
        if (winner.isEmpty() || lastFoundByVista) {
            return this;
        }
        List<String> agreeing = lastVotes.get(0).voters();
        Map<String, Voter> fresh = new LinkedHashMap<>();
        for (Voter v : voters(driver, winner.get(0))) fresh.put(v.strategy(), v);
        List<Voter> repaired = new ArrayList<>();
        for (Voter v : voters) {
            repaired.add(agreeing.contains(v.strategy()) ? v : fresh.getOrDefault(v.strategy(), v));
        }
        fresh.values().stream().filter(v -> repaired.stream().noneMatch(r -> r.strategy().equals(v.strategy())))
                .forEach(repaired::add);
        return new MultiLocator(driver, repaired, snapshot, tag, vista);
    }

    @Override
    public String toString() {
        return "MultiLocator" + (snapshot != null ? "+VISTA" : "") + voters;
    }
}
