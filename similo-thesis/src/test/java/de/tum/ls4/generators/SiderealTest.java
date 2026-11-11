package de.tum.ls4.generators;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.tum.ls4.generators.XPathCandidate.ATTRIBUTE_PREFIX;
import static de.tum.ls4.generators.XPathCandidate.POSITION;
import static de.tum.ls4.generators.XPathCandidate.TAG;
import static de.tum.ls4.generators.XPathCandidate.TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiderealTest {

    /** DOM D of Fig. 2 in the SIDEREAL paper; the target is the first <p>. */
    private static final String PAPER_DOM = "<html><p class='a'>X</p><p class='a'>Y</p><div class='a'>X</div></html>";

    private final W3cDom dom = W3cDom.parse(PAPER_DOM);
    private final Element target = dom.find("/html/p[1]");
    private final Ancestry ancestry = dom.ancestry(target);
    private final XPathOracle oracle = dom.oracle(target);

    /** The graph G_e: every expression reachable from //* and its direct predecessors. */
    private record Graph(Map<XPathCandidate, Set<XPathCandidate>> predecessors, Map<XPathCandidate, String> xpaths,
                         Map<XPathCandidate, XPathOracle.Match> matches) {
    }

    private Graph enumerate(Sidereal sidereal) {
        Map<XPathCandidate, Set<XPathCandidate>> predecessors = new HashMap<>();
        Deque<XPathCandidate> queue = new ArrayDeque<>();
        XPathCandidate root = XPathCandidate.root();
        predecessors.put(root, new LinkedHashSet<>());
        queue.add(root);
        while (!queue.isEmpty()) {
            XPathCandidate x = queue.removeFirst();
            for (XPathCandidate k : sidereal.specialize(x, ancestry)) {
                if (!predecessors.containsKey(k)) {
                    predecessors.put(k, new LinkedHashSet<>());
                    queue.add(k);
                }
                predecessors.get(k).add(x);
            }
        }
        Map<XPathCandidate, String> xpaths = new HashMap<>();
        Map<XPathCandidate, XPathOracle.Match> matches = new HashMap<>();
        for (XPathCandidate c : predecessors.keySet()) {
            String xp = c.render(ancestry);
            xpaths.put(c, xp);
            matches.put(c, oracle.evaluate(List.of(xp)).get(0));
        }
        return new Graph(predecessors, xpaths, matches);
    }

    @Test
    void paperExampleGraphHas80VerticesWith49LocatorsAnd5BoundaryLocators() {
        Graph g = enumerate(new Sidereal(FragilityModel.empty()));

        assertEquals(80, g.predecessors().size(), "|V| (paper Eq. 1 and Fig. 4)");
        assertTrue(g.matches().values().stream().allMatch(XPathOracle.Match::containsTarget),
                "every vertex selects the target");
        assertEquals(1, g.predecessors().values().stream().filter(Set::isEmpty).count(), "only //* has in-degree 0");

        List<XPathCandidate> locators = g.predecessors().keySet().stream()
                .filter(c -> g.matches().get(c).isLocator()).toList();
        assertEquals(49, locators.size(), "|L_e|");

        Set<String> boundary = new java.util.TreeSet<>();
        for (XPathCandidate l : locators) {
            if (g.predecessors().get(l).stream().noneMatch(p -> g.matches().get(p).isLocator())) {
                boundary.add(g.xpaths().get(l));
            }
        }
        assertEquals(Set.of(
                "//p[contains(text(),'X')]",
                "//p[1]",
                "//*[1][contains(text(),'X')]",
                "//*[1][@class='a']",
                "//*/*[1]"), boundary);
    }

    @Test
    void fullAbsoluteXPathMatchesPaper() {
        assertEquals("/html[1]/p[1]", ancestry.absoluteXPath());
        XPathCandidate full = XPathCandidate.root();
        full = full.withHead(full.head().withTag().withPosition().withText()
                .withAttributes(List.of("class"), ancestry.node(0)));
        full = full.addLevel();
        full = full.withHead(full.head().withTag().withPosition());
        assertEquals("//html[1]/p[1][@class='a' and contains(text(),'X')]", full.render(ancestry));
    }

    @Test
    void uniformWeightsReturnABoundaryLocator() {
        GeneratedLocator result = new Sidereal(FragilityModel.empty()).generate(ancestry, oracle);
        assertFalse(result.fallback());
        // every boundary locator of Fig. 4 uses exactly two properties of weight 0.5
        assertEquals(0.75, result.fragility(), 1e-12);
        assertTrue(Set.of("//p[contains(text(),'X')]", "//p[1]", "//*[1][contains(text(),'X')]",
                "//*[1][@class='a']", "//*/*[1]").contains(result.xpath()), result.xpath());
    }

    @Test
    void learnedWeightsSteerTheChoice() {
        // Text and tag names never changed, positions always did: prefer //p[text] over position-based locators
        FragilityModel textStable = FragilityModel.of(Map.of(
                TAG, new FragilityModel.Counter(10, 0),
                TEXT, new FragilityModel.Counter(10, 0),
                POSITION, new FragilityModel.Counter(0, 10)));
        assertEquals("//p[contains(text(),'X')]",
                new Sidereal(textStable).generate(ancestry, oracle).xpath());

        // Positions stable, texts and tags fragile: a structural locator wins
        FragilityModel structureStable = FragilityModel.of(Map.of(
                TAG, new FragilityModel.Counter(0, 10),
                TEXT, new FragilityModel.Counter(0, 10),
                POSITION, new FragilityModel.Counter(10, 0),
                ATTRIBUTE_PREFIX + "class", new FragilityModel.Counter(2, 8)));
        assertEquals("//*/*[1]", new Sidereal(structureStable).generate(ancestry, oracle).xpath());
    }

    @Test
    void returnsTheGlobalOptimumAmongAllLocators() {
        List<FragilityModel> models = new ArrayList<>(List.of(
                FragilityModel.empty(),
                FragilityModel.uniform(0.3),
                FragilityModel.of(Map.of(TAG, new FragilityModel.Counter(1, 3), TEXT, new FragilityModel.Counter(7, 1),
                        POSITION, new FragilityModel.Counter(2, 2), ATTRIBUTE_PREFIX + "class", new FragilityModel.Counter(9, 1))),
                FragilityModel.of(Map.of(TAG, new FragilityModel.Counter(5, 0), TEXT, new FragilityModel.Counter(1, 9),
                        POSITION, new FragilityModel.Counter(3, 7))).withLevelWeight(0.05)));
        for (FragilityModel model : models) {
            Sidereal sidereal = new Sidereal(model);
            Graph g = enumerate(sidereal);
            double best = g.predecessors().keySet().stream()
                    .filter(c -> g.matches().get(c).isLocator())
                    .mapToDouble(model::fragility).min().orElseThrow();
            GeneratedLocator result = sidereal.generate(ancestry, oracle);
            assertEquals(best, result.fragility(), 1e-12, model.toString());
            assertTrue(oracle.evaluate(List.of(result.xpath())).get(0).isLocator());
        }
    }

    @Test
    void blacklistExcludesFragileAttributesAndTexts() {
        FragilityModel model = FragilityModel.of(Map.of(
                TEXT, new FragilityModel.Counter(1, 1),
                ATTRIBUTE_PREFIX + "class", new FragilityModel.Counter(1, 1)));
        Sidereal withBlacklist = new Sidereal(model, new Sidereal.Settings(5_000, 0.10));
        Graph g = enumerate(withBlacklist);
        assertTrue(g.xpaths().values().stream().noneMatch(x -> x.contains("text()") || x.contains("@class")));
        String xpath = withBlacklist.generate(ancestry, oracle).xpath();
        assertFalse(xpath.contains("text()") || xpath.contains("@class"), xpath);
    }

    @Test
    void fallsBackToAbsoluteXPathWhenBudgetIsExhausted() {
        GeneratedLocator result = new Sidereal(FragilityModel.empty(), new Sidereal.Settings(1, 1.0))
                .generate(ancestry, oracle);
        assertTrue(result.fallback());
        assertEquals("/html[1]/p[1]", result.xpath());
    }
}
