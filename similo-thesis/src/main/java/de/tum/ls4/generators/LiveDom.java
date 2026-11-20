package de.tum.ls4.generators;

import de.tum.ls4.Javascript;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/** Access to the live DOM of a Selenium-controlled browser for the robust locator generators. */
public final class LiveDom {

    private static final Javascript ANCESTRY = Javascript.create()
            .addFile("generators/ancestry.js")
            .addScript("return extractAncestry(arguments[0]);");

    private static final Javascript EVALUATE = Javascript.create()
            .addFile("generators/evaluate.js")
            .addScript("return evaluateXPaths(arguments[0], arguments[1]);");

    private static final Javascript ABSOLUTE_XPATH = Javascript.create()
            .addFile("extractors/xpath.js")
            .addScript("return chromeXPathExtractor(arguments[0]);");

    private static final Javascript ID_RELATIVE_XPATH = Javascript.create()
            .addFile("extractors/xpath.js")
            .addScript("return idXPathExtractor(arguments[0]);");

    private LiveDom() {
    }

    /** Chrome-style absolute XPath ("Copy full XPath"), e.g. {@code /html/body/div[2]/a}. */
    public static String absoluteXPath(WebDriver driver, WebElement element) {
        return (String) ABSOLUTE_XPATH.executeOn((JavascriptExecutor) driver, element);
    }

    /** XPath relative to the nearest ancestor with a unique id ("Copy XPath"), e.g. {@code //*[@id='nav']/ul/li[2]/a}. */
    public static String idRelativeXPath(WebDriver driver, WebElement element) {
        return (String) ID_RELATIVE_XPATH.executeOn((JavascriptExecutor) driver, element);
    }

    /** The ancestor chain of {@code element}, read from the live DOM. */
    public static Ancestry ancestry(WebDriver driver, WebElement element) {
        return Ancestry.fromBrowser(ANCESTRY.executeOn((JavascriptExecutor) driver, element));
    }

    /** An oracle that evaluates candidate expressions with {@code document.evaluate} in the browser. */
    public static XPathOracle oracle(WebDriver driver, WebElement target) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        return xpaths -> {
            Object raw = EVALUATE.executeOn(js, xpaths, target);
            if (!(raw instanceof List<?> rows) || rows.size() != xpaths.size()) {
                throw new IllegalStateException("Unexpected result from evaluate.js: " + raw);
            }
            List<XPathOracle.Match> matches = new ArrayList<>(rows.size());
            for (Object row : rows) {
                List<?> pair = (List<?>) row;
                matches.add(new XPathOracle.Match(((Number) pair.get(0)).intValue(), (Boolean) pair.get(1)));
            }
            return matches;
        };
    }
}
