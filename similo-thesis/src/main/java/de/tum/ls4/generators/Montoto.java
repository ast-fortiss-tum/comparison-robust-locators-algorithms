package de.tum.ls4.generators;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Montoto et al.'s XPath generation for "change-resilient" locators (Automated browsing in AJAX websites, DKE 2011),
 * as described in the ROBULA+ and SIDEREAL papers: the algorithm first tries to identify the target by its tag, text
 * and attribute values; if that expression is not a locator, the ancestors and their attribute values are added one
 * level at a time (predicates combined with "and") until the expression is unique or the root is reached. If even the
 * full chain is ambiguous, the absolute XPath is returned.
 * <p>
 * No implementation by the original authors is available; this follows the published description.
 */
public final class Montoto {

    public GeneratedLocator generate(Ancestry ancestry, XPathOracle oracle) {
        List<String> steps = new ArrayList<>();
        int evaluated = 0;
        for (int level = 0; level < ancestry.size(); level++) {
            steps.add(0, step(ancestry.node(level), level == 0));
            String xpath = "//" + String.join("/", steps);
            evaluated++;
            if (oracle.evaluate(List.of(xpath)).get(0).isLocator()) {
                return new GeneratedLocator(xpath, Double.NaN, evaluated, false);
            }
        }
        return new GeneratedLocator(ancestry.absoluteXPath(), Double.NaN, evaluated, true);
    }

    /** {@code tag[contains(text(),'t') and @a='x' and ...]}; text is used for the target only. */
    static String step(AncestorNode node, boolean isTarget) {
        String test = node.hasTag() ? node.tag() : "*";
        StringJoiner predicates = new StringJoiner(" and ", "[", "]");
        predicates.setEmptyValue("");
        if (isTarget && node.hasText()) {
            predicates.add("contains(text()," + XPathLiteral.quote(node.text()) + ")");
        }
        node.attributes().forEach((name, value) -> predicates.add("@" + name + "=" + XPathLiteral.quote(value)));
        return test + predicates;
    }
}
