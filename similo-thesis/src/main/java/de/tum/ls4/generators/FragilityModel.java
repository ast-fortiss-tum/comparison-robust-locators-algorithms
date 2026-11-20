package de.tum.ls4.generators;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static de.tum.ls4.generators.XPathCandidate.ATTRIBUTE_PREFIX;
import static de.tum.ls4.generators.XPathCandidate.LEVEL;
import static de.tum.ls4.generators.XPathCandidate.POSITION;
import static de.tum.ls4.generators.XPathCandidate.TAG;
import static de.tum.ls4.generators.XPathCandidate.TEXT;

/**
 * Fragility weights of XPath properties, learned from how the target elements of a test suite (and their
 * ancestors) changed between two versions of the application (SIDEREAL, Leotta et al., STVR 2021, Sect. 5.2).
 * <p>
 * For every property kind (tag name, position, text, each attribute name) the model keeps a robust and a fragile
 * counter; {@code weight = fragile / (robust + fragile)}. A fully robust property gets {@link #ROBUST_WEIGHT}
 * rather than 0, a property never observed gets {@link #DEFAULT_UNSEEN_WEIGHT}, and adding a level costs the
 * constant {@link #DEFAULT_LEVEL_WEIGHT}.
 * <p>
 * Instances are immutable; {@link #learn} returns a new model with the additional observations.
 */
public final class FragilityModel {

    public static final double ROBUST_WEIGHT = 0.000001;
    public static final double DEFAULT_UNSEEN_WEIGHT = 0.5;
    public static final double DEFAULT_LEVEL_WEIGHT = 0.5;

    /**
     * The same logical element in two subsequent versions of the application (the paper's manual mapping
     * {@code l_e^{n-1} -> l_e^n}; in a test suite this is the element a locator is used for in two runs).
     */
    public record Mapping(Ancestry previous, Ancestry current) {
        public Mapping {
            Objects.requireNonNull(previous);
            Objects.requireNonNull(current);
        }
    }

    /** Robust and fragile counter of one property kind. */
    public record Counter(long robust, long fragile) {
        Counter add(boolean isRobust) {
            return isRobust ? new Counter(robust + 1, fragile) : new Counter(robust, fragile + 1);
        }

        Counter plus(Counter o) {
            return new Counter(robust + o.robust, fragile + o.fragile);
        }

        public long total() {
            return robust + fragile;
        }
    }

    private final Map<String, Counter> counters;
    private final double unseenWeight;
    private final double levelWeight;
    private final Double uniformWeight; // non-null: every property has this weight (paper RQ4 baseline)

    private FragilityModel(Map<String, Counter> counters, double unseenWeight, double levelWeight, Double uniformWeight) {
        this.counters = Map.copyOf(counters);
        this.unseenWeight = unseenWeight;
        this.levelWeight = levelWeight;
        this.uniformWeight = uniformWeight;
    }

    /** A model without observations: every property gets the unseen weight (0.5), levels 0.5. */
    public static FragilityModel empty() {
        return new FragilityModel(Map.of(), DEFAULT_UNSEEN_WEIGHT, DEFAULT_LEVEL_WEIGHT, null);
    }

    /** A model that assigns the same weight to every property, ignoring observations. */
    public static FragilityModel uniform(double weight) {
        checkWeight(weight);
        return new FragilityModel(Map.of(), weight, weight, weight);
    }

    /** Restores a model from stored counters (see {@link #counters()}). */
    public static FragilityModel of(Map<String, Counter> counters) {
        return new FragilityModel(counters, DEFAULT_UNSEEN_WEIGHT, DEFAULT_LEVEL_WEIGHT, null);
    }

    public FragilityModel withUnseenWeight(double w) {
        checkWeight(w);
        return new FragilityModel(counters, w, levelWeight, uniformWeight);
    }

    public FragilityModel withLevelWeight(double w) {
        checkWeight(w);
        return new FragilityModel(counters, unseenWeight, w, uniformWeight);
    }

    private static void checkWeight(double w) {
        if (!(w > 0 && w <= 1)) {
            throw new IllegalArgumentException("Weights must be in (0, 1]: " + w);
        }
    }

    public Map<String, Counter> counters() {
        return counters;
    }

    /** Weight (probability of changing) of one property, in (0, 1]. */
    public double weight(String property) {
        if (uniformWeight != null) {
            return uniformWeight;
        }
        if (LEVEL.equals(property)) {
            return levelWeight;
        }
        Counter c = counters.get(property);
        if (c == null || c.total() == 0) {
            return unseenWeight;
        }
        double w = (double) c.fragile() / c.total();
        return w == 0 ? ROBUST_WEIGHT : w;
    }

    /** Whether the property has been observed at least once (and thus has a learned weight). */
    public boolean isObserved(String property) {
        Counter c = counters.get(property);
        return c != null && c.total() > 0;
    }

    /** Fragility coefficient FC(x) = 1 - prod_{z in S_x} (1 - weight(z)) (paper Eq. 2). */
    public double fragility(Collection<String> properties) {
        double product = 1.0;
        for (String p : properties) {
            product *= 1.0 - weight(p);
        }
        return 1.0 - product;
    }

    public double fragility(XPathCandidate candidate) {
        return fragility(candidate.properties());
    }

    /**
     * Adds the observations of one version pair: all mappings must relate the same two versions of the
     * application. Within a version pair, each ancestor node of the previous version is counted once, even when
     * it lies on the path of several targets (normalized weight counting, paper Sect. 5.2.3).
     */
    public FragilityModel learn(List<Mapping> versionPair) {
        Map<String, Counter> updated = new HashMap<>(counters);
        Set<String> countedFixedLevel = new HashSet<>();
        Set<String> countedAnyLevel = new HashSet<>();

        for (Mapping m : versionPair) {
            Ancestry prev = m.previous();
            Ancestry curr = m.current();

            // Tag names and positions: fixed-level matching, from the target upwards.
            int maxLevel = Math.min(prev.size(), curr.size());
            for (int level = 0; level < maxLevel; level++) {
                if (!countedFixedLevel.add(prev.absoluteXPath(level))) {
                    continue;
                }
                AncestorNode p = prev.node(level);
                AncestorNode c = curr.node(level);
                count(updated, TAG, Objects.equals(p.tag(), c.tag()));
                count(updated, POSITION, p.position() == c.position());
            }

            // Attributes and texts: exact name/value match at any level of the current path.
            Set<String> currentAttributes = new HashSet<>();
            Set<String> currentTexts = new HashSet<>();
            for (AncestorNode c : curr.nodes()) {
                c.attributes().forEach((k, v) -> currentAttributes.add(k + "\u0000" + v));
                if (c.hasText()) currentTexts.add(c.text());
            }
            for (int level = 0; level < prev.size(); level++) {
                if (!countedAnyLevel.add(prev.absoluteXPath(level))) {
                    continue;
                }
                AncestorNode p = prev.node(level);
                p.attributes().forEach((k, v) ->
                        count(updated, ATTRIBUTE_PREFIX + k, currentAttributes.contains(k + "\u0000" + v)));
                if (p.hasText()) {
                    count(updated, TEXT, currentTexts.contains(p.text()));
                }
            }
        }
        return new FragilityModel(updated, unseenWeight, levelWeight, uniformWeight);
    }

    /** Combines the counters of two models (e.g. learned on different version pairs). */
    public FragilityModel merge(FragilityModel other) {
        Map<String, Counter> merged = new HashMap<>(counters);
        other.counters.forEach((k, v) -> merged.merge(k, v, Counter::plus));
        return new FragilityModel(merged, unseenWeight, levelWeight, uniformWeight);
    }

    private static void count(Map<String, Counter> counters, String property, boolean robust) {
        counters.merge(property, new Counter(0, 0).add(robust), Counter::plus);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FragilityModel{");
        counters.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                sb.append(e.getKey()).append('=').append(String.format("%.4f", weight(e.getKey())))
                        .append(" (r=").append(e.getValue().robust()).append(", f=").append(e.getValue().fragile())
                        .append("), "));
        return sb.append(LEVEL).append('=').append(weight(LEVEL)).append('}').toString();
    }
}
