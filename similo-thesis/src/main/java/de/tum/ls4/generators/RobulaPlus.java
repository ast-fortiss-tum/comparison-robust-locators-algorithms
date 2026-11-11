package de.tum.ls4.generators;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ROBULA+ (Leotta, Stocco, Ricca, Tonella, JSEP 2016): generates robust XPath locators by refining {@code //*}
 * with a fixed heuristic order of transformations, a prioritized attribute list and an attribute black list.
 * <p>
 * Candidates are explored breadth-first (FIFO); for each expanded expression the transformations are applied in
 * the order convertStar, addId, addText, addAttribute, addAttributeSet, addPosition, addLevel, and the first
 * generated expression that selects only the target is returned.
 */
public final class RobulaPlus {

    /** Attributes considered first, in this order. */
    public static final List<String> PRIORITY = List.of("name", "class", "title", "alt", "value");
    /** Attributes considered intrinsically fragile and never used. */
    public static final Set<String> BLACKLIST = Set.of(
            "href", "src", "onclick", "onload", "tabindex", "width", "height", "style", "size", "maxlength");
    /** Attributes eligible for attribute sets (power set), in priority order. */
    private static final List<String> SET_ATTRIBUTES = List.of("id", "name", "class", "title", "alt", "value");

    private final int maxExpansions;

    public RobulaPlus() {
        this(5_000);
    }

    public RobulaPlus(int maxExpansions) {
        this.maxExpansions = maxExpansions;
    }

    public GeneratedLocator generate(Ancestry ancestry, XPathOracle oracle) {
        Deque<XPathCandidate> queue = new ArrayDeque<>();
        Set<XPathCandidate> seen = new HashSet<>();
        XPathCandidate root = XPathCandidate.root();
        queue.add(root);
        seen.add(root);
        int evaluated = 0;
        int expansions = 0;

        while (!queue.isEmpty() && expansions++ < maxExpansions) {
            XPathCandidate w = queue.removeFirst();
            List<XPathCandidate> children = new ArrayList<>();
            for (XPathCandidate k : specialize(w, ancestry)) {
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
                if (matches.get(i).isLocator()) {
                    return new GeneratedLocator(xpaths.get(i), Double.NaN, evaluated, false);
                }
            }
            for (int i = 0; i < children.size(); i++) {
                if (matches.get(i).containsTarget()) {
                    queue.addLast(children.get(i));
                }
            }
        }
        return new GeneratedLocator(ancestry.absoluteXPath(), Double.NaN, evaluated, true);
    }

    /** The ROBULA+ transformations applicable to {@code w}, in heuristic order; package-private for tests. */
    List<XPathCandidate> specialize(XPathCandidate w, Ancestry ancestry) {
        List<XPathCandidate> out = new ArrayList<>();
        int n = w.levels();
        AncestorNode node = ancestry.node(n - 1);
        XPathCandidate.Step head = w.head();
        boolean isBody = "body".equals(node.tag());

        // transfConvertStar
        if (!head.tag() && node.hasTag()) {
            out.add(w.withHead(head.withTag()));
        }
        // transfAddId: only on a head without predicates
        if (!head.hasPredicates() && node.attributes().containsKey("id")) {
            out.add(w.withHead(head.withAttributes(List.of("id"), node)));
        }
        // transfAddText: head without text and position (attributes allowed)
        if (node.hasText() && !head.text() && !head.position() && !isBody) {
            out.add(w.withHead(head.withText()));
        }
        // transfAddAttribute: one non-id, non-blacklisted attribute, priority attributes first
        if (!head.hasPredicates()) {
            for (String a : orderedAttributes(node)) {
                if (!"id".equals(a)) {
                    out.add(w.withHead(head.withAttributes(List.of(a), node)));
                }
            }
        }
        // transfAddAttributeSet: sets of >= 2 priority attributes, smaller sets first
        if (!head.hasPredicates()) {
            List<String> eligible = SET_ATTRIBUTES.stream().filter(node.attributes()::containsKey).toList();
            for (List<String> set : subsetsBySize(eligible)) {
                out.add(w.withHead(head.withAttributes(set, node)));
            }
        }
        // transfAddPosition
        if (!head.position() && !(head.tag() && isBody)) {
            out.add(w.withHead(head.withPosition()));
        }
        // transfAddLevel: never above the level below <html>
        if (n < ancestry.size() - 1) {
            out.add(w.addLevel());
        }
        return out;
    }

    private static List<String> orderedAttributes(AncestorNode node) {
        List<String> ordered = new ArrayList<>();
        for (String p : PRIORITY) {
            if (node.attributes().containsKey(p)) ordered.add(p);
        }
        for (String a : node.attributes().keySet()) {
            if (!ordered.contains(a) && !BLACKLIST.contains(a)) ordered.add(a);
        }
        return ordered;
    }

    /** Subsets with at least two elements, ordered by size, then by the order of {@code items}. */
    private static List<List<String>> subsetsBySize(List<String> items) {
        List<List<String>> result = new ArrayList<>();
        int n = items.size();
        for (int size = 2; size <= n; size++) {
            collect(items, size, 0, new ArrayList<>(), result);
        }
        return result;
    }

    private static void collect(List<String> items, int size, int from, List<String> current, List<List<String>> out) {
        if (current.size() == size) {
            out.add(List.copyOf(current));
            return;
        }
        for (int i = from; i < items.size(); i++) {
            current.add(items.get(i));
            collect(items, size, i + 1, current, out);
            current.remove(current.size() - 1);
        }
    }
}
