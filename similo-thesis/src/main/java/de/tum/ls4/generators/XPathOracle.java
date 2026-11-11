package de.tum.ls4.generators;

import java.util.List;

/**
 * Evaluates candidate XPath expressions against the page in which the target element lives.
 * Implementations evaluate a whole batch at once so that a live browser needs one round trip per batch.
 */
@FunctionalInterface
public interface XPathOracle {

    /**
     * @param count          number of elements the expression selects (-1 if the expression is invalid).
     * @param containsTarget whether the target element is among them.
     */
    record Match(int count, boolean containsTarget) {
        /** A locator selects exactly the target: query(x, D) = {e}. */
        public boolean isLocator() {
            return count == 1 && containsTarget;
        }
    }

    /** Returns one {@link Match} per expression, in the same order. */
    List<Match> evaluate(List<String> xpaths);
}
