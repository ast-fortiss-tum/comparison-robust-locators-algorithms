package de.tum.ls4.locators;

import de.tum.ls4.Javascript;
import de.tum.ls4.artifacts.Element;
import de.tum.ls4.artifacts.SimpleSimiloStore;
import de.tum.ls4.locators.comparisons.StringComparisons;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * COLOR (Kirinuki, Tanno, Natsukawa, "COLOR: Correct Locator Recommender for Broken Test Scripts using Various Clues
 * in Web Application", SANER 2019): re-identifies an element by a weighted comparison of its properties, using the
 * property weights of the paper (as in the thesis' LegacyColor): binary equality for tag and size, normalized
 * Levenshtein similarity for string properties, and 1 - |a - b| / max for position and size.
 * <p>
 * Same workflow and store as {@link BySimpleSimilo}. Not yet included: COLOR's image property (perceptual hash of the
 * element's rendering, weight 0.12 of 10.27), which needs element images in the artifact store.
 */
public class ByColor extends BySimilo<Element> {

    private static final String SCRAPE_ELEMENT_FROM_WEB_ELEMENT = Javascript.create()
            .addFile("extractors/attributes.js")
            .addFile("extractors/css_styles.js")
            .addFile("extractors/xpath.js")
            .addFile("extractors/css_selector.js")
            .addFile("extractors/direct_text.js")
            .addFile("extractors/element.js")
            .addScript("return extract_element_from_web_element(arguments[0]);")
            .getScript();

    private static final double MAX_X = 1920, MAX_Y = 1080, MAX_W = 1920, MAX_H = 1080;

    private static Function<Element, String> attribute(String name) {
        return e -> e.attributes() == null ? null : e.attributes().get(name);
    }

    private static double numeric(int a, int b, double max) {
        return Math.max(0.0, Math.min(1.0, 1.0 - Math.abs(a - b) / max));
    }

    private static final List<PropertyComparison<?>> PROPERTY_COMPARISONS = List.of(
            // binary (exact equality)
            new PropertyComparison<>(Element::tag, StringComparisons::equality, 0.21),
            new PropertyComparison<>(attribute("size"), StringComparisons::equality, 0.26),
            // strings (normalized Levenshtein)
            new PropertyComparison<>(Element::id, StringComparisons::levenshtein, 0.86),
            new PropertyComparison<>(Element::className, StringComparisons::levenshtein, 0.35),
            new PropertyComparison<>(Element::name, StringComparisons::levenshtein, 0.80),
            new PropertyComparison<>(Element::value, StringComparisons::levenshtein, 0.61),
            new PropertyComparison<>(Element::typeName, StringComparisons::levenshtein, 0.44),
            new PropertyComparison<>(Element::alt, StringComparisons::levenshtein, 0.57),
            new PropertyComparison<>(attribute("src"), StringComparisons::levenshtein, 0.62),
            new PropertyComparison<>(Element::href, StringComparisons::levenshtein, 0.62),
            new PropertyComparison<>(attribute("onclick"), StringComparisons::levenshtein, 0.66),
            new PropertyComparison<>(Element::xpathChrome, StringComparisons::levenshtein, 0.72),
            new PropertyComparison<>(Element::visibleText, StringComparisons::levenshtein, 0.76),
            new PropertyComparison<>(Element::neighborText, StringComparisons::levenshtein, 0.31),
            // numeric (1 - |a - b| / max)
            new PropertyComparison<>(e -> e.x, (a, b) -> numeric(a, b, MAX_X), 0.72),
            new PropertyComparison<>(e -> e.y, (a, b) -> numeric(a, b, MAX_Y), 0.65),
            new PropertyComparison<>(e -> e.width, (a, b) -> numeric(a, b, MAX_W), 0.66),
            new PropertyComparison<>(e -> e.height, (a, b) -> numeric(a, b, MAX_H), 0.33)
    );

    /** Normalized over all COLOR weights, including the not yet supported image property (0.12). */
    private static final double TOTAL_WEIGHT = PROPERTY_COMPARISONS.stream()
            .mapToDouble(PropertyComparison::weight).sum() + 0.12;

    public ByColor(By by) {
        super(by, new SimpleSimiloStore());
    }

    @Override
    protected double normalizedScore(Element artifact, Element element) {
        double score = 0;
        for (PropertyComparison<?> comparison : PROPERTY_COMPARISONS) {
            score += comparison.score(artifact, element);
        }
        return score / TOTAL_WEIGHT;
    }

    @Override
    protected Object scrapeArtifact(WebElement webElement, JavascriptExecutor jsExecutor) {
        return jsExecutor.executeScript(SCRAPE_ELEMENT_FROM_WEB_ELEMENT, webElement);
    }

    @Override
    protected Element constructArtifact(Map<String, Object> map) {
        return new Element(map);
    }

    /** COLOR's score of a candidate against a stored element, in [0, 1] (exposed for tests and analyses). */
    public static double score(Element stored, Element candidate) {
        double score = 0;
        for (PropertyComparison<?> comparison : PROPERTY_COMPARISONS) {
            score += comparison.score(stored, candidate);
        }
        return score / TOTAL_WEIGHT;
    }
}
