package de.tum.ls4.generators;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import static de.tum.ls4.generators.XPathCandidate.ATTRIBUTE_PREFIX;
import static de.tum.ls4.generators.XPathCandidate.TEXT;

/**
 * SIDEREAL: statistical adaptive generation of robust XPath locators (Leotta, Ricca, Tonella, STVR 2021).
 * <p>
 * Explores the DAG of XPath expressions obtained from {@code //*} through the specialization transformations of
 * the paper (Fig. 3: add tag name, add predicate, add level), always expanding the expression with the lowest
 * fragility coefficient first (Algorithm 1). The first expanded expression that selects only the target is
 * returned; since FC strictly increases along every specialization, it has minimal FC among all locators.
 */
public final class Sidereal {

    /**
     * @param maxExpansions       search budget; when exceeded, the absolute XPath is returned.
     * @param blacklistThreshold  attributes and texts whose weight exceeds this value are never used
     *                            (paper RQ5). 1.0 disables the black list, as in standard SIDEREAL.
     */
    public record Settings(int maxExpansions, double blacklistThreshold) {
        public static final Settings DEFAULT = new Settings(5_000, 1.0);
    }

    private final FragilityModel model;
    private final Settings settings;

    public Sidereal(FragilityModel model) {
        this(model, Settings.DEFAULT);
    }

    public Sidereal(FragilityModel model, Settings settings) {
        this.model = model;
        this.settings = settings;
    }

    private record Entry(XPathCandidate candidate, String xpath, double fragility, long order,
                         XPathOracle.Match match) {
    }

    public GeneratedLocator generate(Ancestry ancestry, XPathOracle oracle) {
        PriorityQueue<Entry> generated = new PriorityQueue<>(
                Comparator.comparingDouble(Entry::fragility).thenComparingLong(Entry::order));
        Set<XPathCandidate> seen = new HashSet<>();
        long order = 0;
        int evaluated = 0;

        XPathCandidate root = XPathCandidate.root();
        String rootXPath = root.render(ancestry);
        seen.add(root);
        generated.add(new Entry(root, rootXPath, 0.0, order++, oracle.evaluate(List.of(rootXPath)).get(0)));
        evaluated++;

        int expansions = 0;
        while (!generated.isEmpty()) {
            Entry x = generated.poll();
            if (x.match().isLocator()) {
                return new GeneratedLocator(x.xpath(), x.fragility(), evaluated, false);
            }
            if (++expansions > settings.maxExpansions()) {
                break;
            }
            List<XPathCandidate> children = new ArrayList<>();
            for (XPathCandidate k : specialize(x.candidate(), ancestry)) {
                if (seen.add(k)) {
                    children.add(k);
                }
            }
            if (children.isEmpty()) {
                continue;
            }
            List<String> xpaths = children.stream().map(c -> c.render(ancestry)).toList();
            List<XPathOracle.Match> matches = oracle.evaluate(xpaths);
            evaluated += xpaths.size();
            for (int i = 0; i < children.size(); i++) {
                // Every candidate selects the target by construction; guard against oracle/rendering mismatches.
                if (matches.get(i).containsTarget()) {
                    XPathCandidate k = children.get(i);
                    generated.add(new Entry(k, xpaths.get(i), model.fragility(k), order++, matches.get(i)));
                }
            }
        }
        return new GeneratedLocator(ancestry.absoluteXPath(), Double.NaN, evaluated, true);
    }

    /** All admissible specializations of {@code w} (paper Fig. 3); package-private for tests. */
    List<XPathCandidate> specialize(XPathCandidate w, Ancestry ancestry) {
        List<XPathCandidate> out = new ArrayList<>();
        int n = w.levels();
        AncestorNode node = ancestry.node(n - 1);
        XPathCandidate.Step head = w.head();

        // transfAddTagName
        if (!head.tag() && node.hasTag()) {
            out.add(w.withHead(head.withTag()));
        }
        // transfAddPredicate: attributes, text and position, one at a time
        for (String attribute : node.attributes().keySet()) {
            if (!head.attributes().contains(attribute) && !blacklisted(ATTRIBUTE_PREFIX + attribute)) {
                out.add(w.withHead(head.withAttributes(List.of(attribute), node)));
            }
        }
        if (node.hasText() && !head.text() && !blacklisted(TEXT)) {
            out.add(w.withHead(head.withText()));
        }
        if (!head.position()) {
            out.add(w.withHead(head.withPosition()));
        }
        // transfAddLevel
        if (n < ancestry.size()) {
            out.add(w.addLevel());
        }
        return out;
    }

    private boolean blacklisted(String property) {
        return settings.blacklistThreshold() < 1.0 && model.weight(property) > settings.blacklistThreshold();
    }
}
