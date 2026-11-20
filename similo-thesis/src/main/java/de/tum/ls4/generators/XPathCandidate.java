package de.tum.ls4.generators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

/**
 * A candidate XPath expression for a target element, kept in structured form so that its properties (tag names,
 * predicates and levels) are known exactly. A candidate always spans a contiguous chain of levels ending at the
 * target: {@code //head/.../target}.
 * <p>
 * {@code steps.get(0)} describes the target level, {@code steps.get(levels() - 1)} the topmost level (the "head",
 * which is the one the specialization transformations act on).
 */
public final class XPathCandidate {

    /** Property names used for fragility weights. Attributes are {@code ATTRIBUTE_PREFIX + name}. */
    public static final String TAG = "tag";
    public static final String POSITION = "position";
    public static final String TEXT = "text";
    public static final String LEVEL = "level";
    public static final String ATTRIBUTE_PREFIX = "attr:";

    /**
     * The constraints used at one level.
     *
     * @param tag        whether the tag name is used ({@code p}) instead of {@code *}.
     * @param position   whether a position predicate is used.
     * @param attributes names of the attributes used as predicates, kept in the element's DOM order.
     * @param text       whether a text predicate is used.
     */
    public record Step(boolean tag, boolean position, List<String> attributes, boolean text) {
        public static final Step EMPTY = new Step(false, false, List.of(), false);

        public Step {
            attributes = List.copyOf(attributes);
        }

        public boolean hasPredicates() {
            return position || text || !attributes.isEmpty();
        }

        Step withTag() {
            return new Step(true, position, attributes, text);
        }

        Step withPosition() {
            return new Step(tag, true, attributes, text);
        }

        Step withText() {
            return new Step(tag, position, attributes, true);
        }

        /** Adds attributes, keeping them in the DOM order given by {@code node}. */
        Step withAttributes(List<String> names, AncestorNode node) {
            List<String> ordered = new ArrayList<>();
            for (String name : node.attributes().keySet()) {
                if (attributes.contains(name) || names.contains(name)) {
                    ordered.add(name);
                }
            }
            return new Step(tag, position, ordered, text);
        }
    }

    private final List<Step> steps;

    private XPathCandidate(List<Step> steps) {
        this.steps = List.copyOf(steps);
    }

    /** The most general expression, {@code //*}, which selects every element. */
    public static XPathCandidate root() {
        return new XPathCandidate(List.of(Step.EMPTY));
    }

    public int levels() {
        return steps.size();
    }

    public Step head() {
        return steps.get(steps.size() - 1);
    }

    public List<Step> steps() {
        return Collections.unmodifiableList(steps);
    }

    XPathCandidate withHead(Step head) {
        List<Step> copy = new ArrayList<>(steps);
        copy.set(copy.size() - 1, head);
        return new XPathCandidate(copy);
    }

    /** {@code //w} becomes {@code //* /w}: a new, unconstrained level on top. */
    XPathCandidate addLevel() {
        List<Step> copy = new ArrayList<>(steps);
        copy.add(Step.EMPTY);
        return new XPathCandidate(copy);
    }

    /**
     * The properties composing this expression, as used by the fragility coefficient: one entry per tag name,
     * position, text and attribute predicate, plus one {@link #LEVEL} per level beyond the first.
     */
    public List<String> properties() {
        List<String> props = new ArrayList<>();
        for (Step s : steps) {
            if (s.tag) props.add(TAG);
            if (s.position) props.add(POSITION);
            if (s.text) props.add(TEXT);
            for (String a : s.attributes) props.add(ATTRIBUTE_PREFIX + a);
        }
        for (int i = 1; i < steps.size(); i++) props.add(LEVEL);
        return props;
    }

    /**
     * Renders the expression for the given ancestry. The position predicate is placed directly after the node test
     * ({@code p[2][@class='a']}) so that it denotes the element's position among its (same-tag or all) siblings,
     * independently of the other predicates. Hence every candidate selects the target.
     */
    public String render(Ancestry ancestry) {
        StringBuilder sb = new StringBuilder("/");
        for (int level = steps.size() - 1; level >= 0; level--) {
            Step s = steps.get(level);
            AncestorNode n = ancestry.node(level);
            sb.append('/');
            sb.append(s.tag ? n.tag() : "*");
            if (s.position) {
                sb.append('[').append(s.tag ? n.position() : n.positionStar()).append(']');
            }
            if (!s.attributes.isEmpty() || s.text) {
                StringJoiner preds = new StringJoiner(" and ", "[", "]");
                for (String a : s.attributes) {
                    preds.add("@" + a + "=" + XPathLiteral.quote(n.attributes().get(a)));
                }
                if (s.text) {
                    preds.add("contains(text()," + XPathLiteral.quote(n.text()) + ")");
                }
                sb.append(preds);
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof XPathCandidate other && steps.equals(other.steps);
    }

    @Override
    public int hashCode() {
        return steps.hashCode();
    }

    @Override
    public String toString() {
        return "XPathCandidate" + steps;
    }
}
