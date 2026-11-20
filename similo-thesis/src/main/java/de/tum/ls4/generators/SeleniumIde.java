package de.tum.ls4.generators;

import de.tum.ls4.Javascript;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Selenium IDE's locator generation: the locator builders of SeleniumHQ/selenium-ide (css:data-test-id, id, linkText,
 * name, css:data-attr, css:finder, xpath:link, xpath:img, xpath:attributes, xpath:idRelative, xpath:href,
 * xpath:position, xpath:innerText) run in the browser in priority order; every locator whose first match is the
 * element is kept, and the first one is Selenium IDE's choice. See resources/javascript/generators/seleniumide.js.
 */
public final class SeleniumIde {

    /** A Selenium IDE locator such as {@code id=login} or {@code xpath=//a[contains(text(),'Help')]}. */
    public record Candidate(String locator, String builder) {
        public By toBy() {
            return SeleniumIde.toBy(locator);
        }

        /** An XPath equivalent, or null for CSS locators (used where only XPath can be evaluated). */
        public String toXPath() {
            int i = locator.indexOf('=');
            String type = locator.substring(0, i), value = locator.substring(i + 1);
            return switch (type) {
                case "xpath" -> value;
                case "id" -> "//*[@id=" + XPathLiteral.quote(value) + "]";
                case "name" -> "//*[@name=" + XPathLiteral.quote(value) + "]";
                case "linkText" -> "//a[normalize-space(.)=" + XPathLiteral.quote(value) + "]";
                default -> null;
            };
        }
    }

    private static final Javascript BUILD_ALL = Javascript.create()
            .addFile("generators/vendor/finder.js")
            .addFile("generators/seleniumide.js")
            .addScript("return sideBuildAll(arguments[0]);");

    private SeleniumIde() {
    }

    /** All valid locators for the element, in Selenium IDE's priority order. */
    public static List<Candidate> buildAll(WebDriver driver, WebElement element) {
        Object raw = BUILD_ALL.executeOn((JavascriptExecutor) driver, element);
        List<Candidate> out = new ArrayList<>();
        if (raw instanceof List<?> rows) {
            for (Object row : rows) {
                List<?> pair = (List<?>) row;
                out.add(new Candidate((String) pair.get(0), (String) pair.get(1)));
            }
        }
        return out;
    }

    /** Selenium IDE's choice (the first valid locator), or null if no builder succeeded. */
    public static Candidate build(WebDriver driver, WebElement element) {
        List<Candidate> all = buildAll(driver, element);
        return all.isEmpty() ? null : all.get(0);
    }

    /** The first valid locator that has an XPath equivalent (ignoring the CSS builders). */
    public static Candidate buildXPath(WebDriver driver, WebElement element) {
        return buildAll(driver, element).stream().filter(c -> c.toXPath() != null).findFirst().orElse(null);
    }

    public static By toBy(String locator) {
        int i = locator.indexOf('=');
        String type = locator.substring(0, i), value = locator.substring(i + 1);
        return switch (type) {
            case "id" -> By.id(value);
            case "name" -> By.name(value);
            case "linkText" -> By.linkText(value);
            case "css" -> By.cssSelector(value);
            case "xpath" -> By.xpath(value);
            default -> throw new IllegalArgumentException("Unknown Selenium IDE locator type: " + locator);
        };
    }
}
