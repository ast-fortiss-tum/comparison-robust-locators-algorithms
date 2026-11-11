package de.tum.ls4.generators;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Entry point for generating robust XPath locators for an element of the page currently loaded in the browser.
 *
 * <pre>{@code
 * WebElement login = driver.findElement(By.id("login"));
 * By robula   = RobustLocators.robulaPlus(driver, login);
 * By sidereal = RobustLocators.sidereal(driver, login, model);   // model learned from previous versions
 * }</pre>
 */
public final class RobustLocators {

    private RobustLocators() {
    }

    public static GeneratedLocator generateRobulaPlus(WebDriver driver, WebElement element) {
        return new RobulaPlus().generate(LiveDom.ancestry(driver, element), LiveDom.oracle(driver, element));
    }

    public static GeneratedLocator generateSidereal(WebDriver driver, WebElement element, FragilityModel model) {
        return new Sidereal(model).generate(LiveDom.ancestry(driver, element), LiveDom.oracle(driver, element));
    }

    /**
     * EAGL with its bundled cross-project stability model. Where EAGL's implementation fails for the element (it does
     * for a few DOM shapes), the absolute XPath is returned with {@code fallback = true}.
     */
    public static GeneratedLocator generateEagl(WebDriver driver, WebElement element) {
        try {
            return new GeneratedLocator(new Eagl().generate(driver, element), Double.NaN, 0, false);
        } catch (Eagl.Failure e) {
            return new GeneratedLocator(LiveDom.absoluteXPath(driver, element), Double.NaN, 0, true);
        }
    }

    public static By eagl(WebDriver driver, WebElement element) {
        return By.xpath(generateEagl(driver, element).xpath());
    }

    public static By robulaPlus(WebDriver driver, WebElement element) {
        return By.xpath(generateRobulaPlus(driver, element).xpath());
    }

    /** SIDEREAL with the given fragility model; use {@link FragilityModel#empty()} when there is no history yet. */
    public static By sidereal(WebDriver driver, WebElement element, FragilityModel model) {
        return By.xpath(generateSidereal(driver, element, model).xpath());
    }
}
