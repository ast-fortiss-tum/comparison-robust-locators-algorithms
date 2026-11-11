package de.tum.ls4.generators;

/**
 * Result of a locator generation.
 *
 * @param xpath       the generated XPath locator.
 * @param fragility   its fragility coefficient under the model used (NaN for generators without a model).
 * @param evaluated   number of candidate expressions evaluated against the page.
 * @param fallback    true if no locator was found within the search budget and the absolute XPath was returned.
 */
public record GeneratedLocator(String xpath, double fragility, int evaluated, boolean fallback) {
}
