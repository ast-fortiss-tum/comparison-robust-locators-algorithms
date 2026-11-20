package de.tum.ls4.locators;

import de.tum.ls4.Javascript;
import de.tum.ls4.artifacts.Element;
import de.tum.ls4.artifacts.SimpleSimiloStore;
import de.tum.ls4.locators.comparisons.StringComparisons;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static de.tum.ls4.utils.Utils.checkedCastToList;
import static de.tum.ls4.utils.Utils.checkedCastToMap;

/**
 * COLOR (Kirinuki, Tanno, Natsukawa, "COLOR: Correct Locator Recommender for Broken Test Scripts using Various Clues
 * in Web Application", SANER 2019): re-identifies an element by a weighted comparison of its properties, using the
 * property weights of the paper (as in the thesis' LegacyColor): binary equality for tag and size, normalized
 * Levenshtein similarity for string properties, and 1 - |a - b| / max for position and size.
 * <p>
 * Same workflow and store as {@link BySimpleSimilo}. COLOR's image property (weight 0.12) is the perceptual hash of the
 * element's rendering ({@link ElementImages}), stored with the element as the pseudo-attribute
 * {@value #IMAGE_HASH_ATTRIBUTE} (hexadecimal); it scores 0 when either hash is missing, as in the thesis.
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

    /** Attribute under which the element's image hash is stored (not a valid HTML attribute name of real pages). */
    public static final String IMAGE_HASH_ATTRIBUTE = "color:ahash";

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
            // image (perceptual hash; 0 if missing)
            new PropertyComparison<>(ByColor::imageHash, ElementImages::similarity, 0.12),
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

    private static final double TOTAL_WEIGHT = PROPERTY_COMPARISONS.stream()
            .mapToDouble(PropertyComparison::weight).sum();

    private static Long imageHash(Element e) {
        String hex = e.attributes() == null ? null : e.attributes().get(IMAGE_HASH_ATTRIBUTE);
        return hex == null ? null : Long.parseUnsignedLong(hex, 16);
    }

    /** The scraped element map with the image hash added to its attributes (if there is one). */
    static Map<String, Object> withImageHash(Map<String, Object> scraped, Long hash) {
        Map<String, Object> copy = new HashMap<>(scraped);
        Object attributes = scraped.get("attributes");
        Map<String, Object> attrs = attributes instanceof Map<?, ?> ? new HashMap<>(checkedCastToMap(attributes)) : new HashMap<>();
        if (hash != null) attrs.put(IMAGE_HASH_ATTRIBUTE, Long.toHexString(hash));
        copy.put("attributes", attrs);
        return copy;
    }

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
        Map<String, Object> scraped = checkedCastToMap(jsExecutor.executeScript(SCRAPE_ELEMENT_FROM_WEB_ELEMENT, webElement));
        return withImageHash(scraped, ElementImages.hash(webDriver, webElement));
    }

    /** As in {@link BySimilo}, but the candidates' images are hashed in one scan of the page. */
    @Override
    protected List<WebElement> findElementWithSimilo() {
        List<Object> allElements = checkedCastToList(jsExecutor.executeScript(GET_ALL_ELEMENTS));
        List<WebElement> candidates = new ArrayList<>(allElements.size());
        for (Object row : allElements) candidates.add((WebElement) ((List<?>) row).get(1));
        Map<WebElement, Long> hashes = ElementImages.hashAll(webDriver, candidates);

        double bestScore = 0;
        WebElement bestElement = null;
        for (int i = 0; i < allElements.size(); i++) {
            Map<String, Object> scraped = checkedCastToMap(((List<?>) allElements.get(i)).get(0));
            Element candidate = constructArtifact(withImageHash(scraped, hashes.get(candidates.get(i))));
            double score = normalizedScore(this.artifactData.artifact(), candidate);
            if (score > bestScore) {
                bestScore = score;
                bestElement = candidates.get(i);
            }
        }
        if (bestElement == null) {
            throw new NoSuchElementException("No element found with " + by + " in " + url
                    + " with COLOR! Could not repair broken locator.");
        }
        return List.of(bestElement);
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
