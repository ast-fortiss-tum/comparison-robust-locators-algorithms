package de.tum.ls4.generators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.ls4.Javascript;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EAGL (Taha, Sohn, Papadakis, "EAGL: Robust Web Element Locators via Learned Element Stability", TOSEM 2026): builds
 * an XPath from the closest uniquely identifiable ancestor (or the element itself) down to the target, choosing at
 * each level the tag, attribute and position predicted most stable by a Random Forest trained on the evolution of web
 * pages.
 * <p>
 * This is a line-by-line port of the authors' implementation ({@code Classes/PredictionClass.py}, {@code XpathClass},
 * https://github.com/hilalosoft/EAGL, commit 256fd6d, MIT License, Copyright (c) 2025 Hilal TAHA), including its
 * quirks, so that it produces the same locators: it reproduces BeautifulSoup's view of the DOM ({@link SoupNode}) and
 * the predictions of the bundled model ({@link EaglForest}). Where the original raises an exception (it does for a few
 * DOM shapes) {@link Failure} is thrown, naming the Python exception.
 * <p>
 * Verified against the original on the 48 snapshots of the thesis' pilot (wikipedia, zillow, amazon): same locator or
 * same failure for every tracked element.
 */
public final class Eagl {

    /** Raised where EAGL's implementation raises; {@link #pythonError} is the Python exception type. */
    public static final class Failure extends RuntimeException {
        public final String pythonError;

        Failure(String pythonError, String message) {
            super(pythonError + ": " + message);
            this.pythonError = pythonError;
        }
    }

    private static final double DFACTOR = 0.95;

    private static final Set<String> TIMESTAMPED = Set.of("href", "data-analytics-view-custom-item-id", "data-h", "alt",
            "data-story-id", "data-lazy-load-path", "data-url", "data-m", "action", "data-post-id", "data", "src",
            "srcset", "aria-label", "data-id", "data-video-id", "data-sprite", "data-src-swiper", "style", "data-dsqu",
            "url");

    private static final Javascript SNAPSHOT = Javascript.create()
            .addFile("generators/eagl_snapshot.js")
            .addScript("return eaglSnapshot(arguments[0]);");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final EaglForest model;

    /** EAGL with the model its implementation loads by default ({@code cross_training.joblib}; see {@link EaglForest}). */
    public Eagl() {
        this(EaglForest.bundled());
    }

    Eagl(EaglForest model) {
        this.model = model;
    }

    /** The EAGL locator of {@code element} in the page currently loaded in the browser. */
    public String generate(WebDriver driver, WebElement element) {
        return generateAll(driver, List.of(element)).get(0).xpath();
    }

    /** Result per element: the locator, or the failure (EAGL's implementation raised). */
    public record Result(String xpath, Failure failure) {
        public String xpath() {
            if (failure != null) throw failure;
            return xpath;
        }

        public boolean ok() {
            return failure == null;
        }
    }

    /** Generates locators for several elements of the same page from a single DOM snapshot. */
    public List<Result> generateAll(WebDriver driver, List<WebElement> elements) {
        String raw = (String) SNAPSHOT.executeOn((JavascriptExecutor) driver, elements);
        JsonNode snapshot;
        try {
            snapshot = JSON.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable DOM snapshot", e);
        }
        List<SoupNode> tags = new ArrayList<>();
        SoupNode.parse(snapshot.get("tree"), tags);
        List<Result> results = new ArrayList<>();
        for (JsonNode t : snapshot.get("targets")) {
            if (t.asInt() < 0) {
                results.add(new Result(null, new Failure("ValueError", "element is not in the document")));
                continue;
            }
            try {
                results.add(new Result(generate(tags.get(t.asInt())), null));
            } catch (Failure f) {
                results.add(new Result(null, f));
            }
        }
        return results;
    }

    // ---- features (soup_operations.py) ---------------------------------------------------------------------------

    /** {@code cal_length(soup, flag)}. */
    private static int calLength(SoupNode n, boolean flag) {
        if ("html".equals(n.name)) return 0;
        return n.parentCount() + (flag ? 1 : 0);
    }

    /** {@code cal_depth(dom)} (as implemented: the depth of the first child, doubled plus one if it has attributes). */
    private static int calDepth(SoupNode n) {
        int max = 0;
        for (SoupNode child : n.contents) {
            if (!child.isTag() && child.newline) continue;
            int d = calLength(child, false);
            if (d > max) {
                max = d;
                if (child.isTag() && !child.attrs.isEmpty()) max += max + 1;
            }
        }
        return max;
    }

    /** {@code get_siblings_soup(soup)}: (position, nr_siblings). */
    private static double[] siblings(SoupNode n) {
        int previous = n.previousSiblings().size();
        int count = 1 + previous + n.nextSiblings().size();
        return new double[]{(previous + 1) / (double) count, count};
    }

    /** {@code get_siblings_soup(soup, True)}: (position among same-tag siblings, has same-tag siblings). */
    private static int samePosition(SoupNode n) {
        int count = 1;
        for (SoupNode s : n.previousSiblings()) if (n.name.equals(s.name)) count++;
        return count;
    }

    private static boolean hasSameSiblings(SoupNode n) {
        for (SoupNode s : n.previousSiblings()) if (n.name.equals(s.name)) return true;
        for (SoupNode s : n.nextSiblings()) if (n.name.equals(s.name)) return true;
        return false;
    }

    /** {@code get_siblings_attr(soup, attr)}. */
    private static double attributePosition(SoupNode n, String attr) {
        int count = 0;
        for (String a : n.attrs.keySet()) {
            count++;
            if (a.equals(attr)) return count / (double) n.attrs.size();
        }
        throw new Failure("TypeError", "attribute " + attr + " not found");
    }

    private static double bool(boolean b) {
        return b ? 1 : 0;
    }

    // ---- PredictionClass.py --------------------------------------------------------------------------------------

    /** {@code element_stability_prediction_attribute(DP, element, distance, attributes)}. */
    private String predictAttribute(SoupNode el, int distance, List<String> attributes) {
        int counter = 1;
        int length = calLength(el, true);
        int depth = calDepth(el);
        double highest = -1;
        String returned = null;
        for (String attribute : attributes) {
            int position = counter;
            int nrChildren = attribute.equals("class") ? asList(el.attrs.get("class")).size() : 1;
            int nrSiblings = el.attrs.size() - 1;
            double prediction = 1.0 - model.predict(position, length, depth, nrSiblings, nrChildren, bool(true));
            double robustness = prediction * Math.pow(DFACTOR, distance);
            if (robustness > highest) {
                highest = robustness;
                returned = attribute;
            }
            counter += counter + 1;
        }
        if (returned == null) throw new Failure("AssertionError", "no attribute");
        return returned;
    }

    private record Closest(List<SoupNode> unique, String attribute, Integer sc) {
    }

    /** {@code find_closest_unique_elements(DP, soup_element)}. */
    private Closest findClosestUniqueElements(SoupNode element) {
        Integer sc = null;
        SoupNode root = element.root();
        int distance = 0;
        List<SoupNode> unique = null; // a Python local: may be read before assignment (UnboundLocalError)
        List<SoupNode> results = root.findAllByName(element.name, true, 2);
        if (results.size() == 1) return new Closest(results, null, null);
        List<String> uniqueAttributes = new ArrayList<>();
        for (String attribute : element.attrs.keySet()) {
            if (TIMESTAMPED.contains(attribute) || attribute.startsWith("data")) continue;
            if (attribute.equals("class")) {
                List<String> classes = asList(element.attrs.get(attribute));
                if (classes.isEmpty()) continue;
                for (int c = 0; c < classes.size(); c++) {
                    unique = root.findAllByAttr(attribute, classes.get(c), true, 2);
                    if (unique.size() == 1) sc = c;
                }
            } else {
                unique = root.findAllByAttr(attribute, element.attrs.get(attribute), true, 2);
            }
            if (unique.size() == 1) uniqueAttributes.add(attribute);
        }
        if (!uniqueAttributes.isEmpty()) {
            String attribute = uniqueAttributes.size() > 1
                    ? predictAttribute(element, distance, uniqueAttributes) : uniqueAttributes.get(0);
            Object value = attribute.equals("class") ? classAt(element, sc) : element.attrs.get(attribute);
            return new Closest(root.findAllByAttr(attribute, value, true, 2), attribute, sc);
        }
        for (SoupNode parent = element.parent; parent != null; parent = parent.parent) {
            distance++;
            results = root.findAllByName(parent.name, true, 2);
            if (results.size() == 1) return new Closest(results, null, sc);
            uniqueAttributes = new ArrayList<>();
            for (String attribute : parent.attrs.keySet()) {
                if (TIMESTAMPED.contains(attribute)) continue;
                if (attribute.equals("class")) {
                    List<String> classes = asList(parent.attrs.get(attribute));
                    for (int c = 0; c < classes.size(); c++) {
                        unique = root.findAllByAttr(attribute, classes.get(c), true, 2);
                        if (unique.size() == 1) {
                            uniqueAttributes.add(attribute);
                            sc = c;
                        }
                    }
                } else {
                    unique = root.findAllByAttr(attribute, parent.attrs.get(attribute), true, 2);
                }
                if (unique == null) throw new Failure("UnboundLocalError", "local variable 'unique' referenced before assignment");
                if (unique.size() == 1) uniqueAttributes.add(attribute);
            }
            if (!uniqueAttributes.isEmpty()) {
                String attribute = uniqueAttributes.size() > 1
                        ? predictAttribute(parent, distance, uniqueAttributes) : uniqueAttributes.get(0);
                Object value = attribute.equals("class") ? classAt(parent, sc) : parent.attrs.get(attribute);
                return new Closest(root.findAllByAttr(attribute, value, true, 2), attribute, sc);
            }
        }
        // EAGL returns the document itself here; unreachable in practice (<html> is always unique by tag name)
        throw new Failure("AssertionError", "no unique ancestor");
    }

    /** One level of {@code XpathClass.xpath_list}: the element and the predicted stability of its tag and attributes. */
    private record Level(SoupNode soup, LinkedHashMap<String, Double> values) {
    }

    /** {@code XpathClass.element_stability_prediction(element, distance)} (unscaled features, as in EAGL_excutor.py). */
    private Level predictLevel(SoupNode el, int distance) {
        if (el == null) throw new Failure("AttributeError", "'NoneType' object has no attribute 'name'");
        int length = calLength(el, true);
        int depth = calDepth(el);
        int nrChildren = el.contents.size() + el.attrs.size();
        double[] s = siblings(el);
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        if ("soup".equals(el.name)) throw new Failure("AttributeError", "<soup> tag shadows the element");
        values.put(el.name, model.predict(s[0], length, depth, s[1], nrChildren, bool(false)) * Math.pow(DFACTOR, distance));
        for (String attr : el.attrs.keySet()) {
            if (TIMESTAMPED.contains(attr)) continue;
            if (attr.equals("soup")) throw new Failure("AttributeError", "soup attribute shadows the element");
            double prediction = 1 - model.predict(attributePosition(el, attr), calLength(el, true) + 1, 0,
                    el.attrs.size() - 1, 1, bool(true));
            values.put(attr, prediction * Math.pow(DFACTOR, distance));
        }
        return new Level(el, values);
    }

    /** A step of the generated XPath: (xpath_list index, attribute or tag, value, position). */
    private record Step(int index, String attribute, Object value, Integer position) {
    }

    /** {@code XpathClass.generate_locating_strategy_Xpath(soup_element)}. */
    String generate(SoupNode target) {
        Closest closest = findClosestUniqueElements(target);
        if (closest.unique().size() != 1) throw new Failure("AssertionError", "closest unique element is not unique");
        SoupNode first = closest.unique().get(0);
        String saved = closest.attribute();
        Integer sc = closest.sc();
        List<Level> levels = new ArrayList<>();
        SoupNode current = target;
        int distance = 0;
        if (first != target) {
            while (true) {
                levels.add(predictLevel(current, distance));
                if (first == current) break;
                current = current == null ? null : current.parent;
                distance++;
            }
        }
        List<Step> xpath = new ArrayList<>();
        if (saved == null) {
            xpath.add(new Step(levels.size() - 1, first.name, null, 1));
        } else {
            Object value = saved.equals("class") ? classAt(first, sc) : first.attrs.get(saved);
            xpath.add(new Step(levels.size() - 1, saved, value, null));
        }
        if (first != target) xpath.addAll(generateRelocXpath(levels));

        StringBuilder out = new StringBuilder("/");
        if (xpath.size() == 1) {
            if ("class".equals(saved)) {
                out.append(classStep(classAt(first, sc)));
            } else if (first.hasAttr(saved)) {
                out.append("/*[@").append(saved).append("=\"").append(str(first.attrs.get(saved))).append("\"]");
            } else {
                out.append('/').append(first.name);
            }
            return out.toString();
        }
        for (Step level : xpath) {
            if (level.position() != null && level.position() == -1) out.append('/');
            if (level.value() != null) {
                if ("class".equals(level.attribute())) {
                    out.append(classStep(str(level.value())));
                } else {
                    SoupNode soup = levels.get(pyIndex(levels, level.index())).soup();
                    if (!soup.hasAttr(level.attribute())) throw new Failure("KeyError", level.attribute());
                    out.append("/*[@").append(level.attribute()).append("=\"")
                            .append(str(soup.attrs.get(level.attribute()))).append("\"]");
                }
                if (level.position() != null && level.position() != 0 && level.position() != -1) {
                    out.append('[').append(level.position()).append(']');
                }
            } else {
                SoupNode soup = levels.get(pyIndex(levels, level.index())).soup();
                if (level.attribute() == null) throw new Failure("TypeError", "can only concatenate str (not \"NoneType\") to str");
                if (hasSameSiblings(soup)) {
                    out.append('/').append(level.attribute()).append('[').append(samePosition(soup)).append(']');
                } else {
                    out.append('/').append(level.attribute());
                }
            }
        }
        return out.toString();
    }

    /** {@code XpathClass.generate_reloc_xpath()}; Python's loop variables keep their values across iterations. */
    private List<Step> generateRelocXpath(List<Level> levels) {
        SoupNode lastElement = levels.get(levels.size() - 1).soup();
        int index = levels.size() - 2;
        int lastIndex = levels.size() - 1;
        String attribute = null;
        Object indexValue = null;
        Integer unique = 0;
        List<Step> greedy = new ArrayList<>();
        String selectedClass = null;
        Integer classIndex = null;
        Integer position = null;
        int uniqueness;
        Map<Integer, Integer> posList = null;
        while (index != -1) {
            double highest = -1;
            List<Step> divList = new ArrayList<>();
            for (int i = index; i >= 0; i--) {
                Level level = levels.get(i);
                SoupNode soup = level.soup();
                for (Map.Entry<String, Double> entry : level.values().entrySet()) {
                    String key = entry.getKey();
                    double value = entry.getValue();
                    if (key.equals("href") || TIMESTAMPED.contains(key)) continue;
                    double robustness;
                    if (soup.hasAttr(key)) {
                        Object attrValue = soup.attrs.get(key);
                        if (isFalsy(attrValue)) continue;
                        if (key.equals("class")) {
                            int local = 10000000;
                            for (String oneClass : asList(attrValue)) {
                                int[] p = calPosition(lastElement, soup, key, oneClass, false);
                                uniqueness = p[0];
                                position = p[1] == Integer.MIN_VALUE ? null : p[1];
                                if (uniqueness < local) {
                                    selectedClass = oneClass;
                                    local = uniqueness;
                                    classIndex = i;
                                }
                            }
                            uniqueness = local;
                        } else {
                            int[] p = calPosition(lastElement, soup, key, attrValue, i != lastIndex - 1);
                            uniqueness = p[0];
                            position = p[1] == Integer.MIN_VALUE ? null : p[1];
                        }
                        robustness = Math.pow(value, uniqueness);
                    } else if (i == lastIndex - 1) {
                        int[] p = calPosition(lastElement, soup, null, null, false);
                        uniqueness = p[0];
                        position = p[1] == Integer.MIN_VALUE ? null : p[1];
                        robustness = Math.pow(value, uniqueness);
                    } else {
                        robustness = 1;
                        uniqueness = 1;
                        posList = new HashMap<>();
                        for (int j = i; j < lastIndex; j++) {
                            int[] p = calPosition(levels.get(j + 1).soup(), levels.get(j).soup(), null, null, false);
                            uniqueness = p[0] * uniqueness;
                            robustness = Math.pow(value, uniqueness) * robustness;
                            position = p[1] == Integer.MIN_VALUE ? null : p[1];
                            posList.put(j, position);
                        }
                    }
                    if (robustness > highest && position != null) {
                        if (soup.hasAttr(key)) {
                            if (key.equals("class")) {
                                index = classIndex;
                                attribute = key;
                                indexValue = selectedClass;
                            } else {
                                index = i;
                                attribute = key;
                                indexValue = soup.attrs.get(key);
                            }
                            unique = position;
                            divList = new ArrayList<>();
                            highest = robustness;
                        } else if (i == lastIndex - 1) {
                            index = i;
                            attribute = key;
                            indexValue = null;
                            unique = position;
                            highest = robustness;
                            divList = new ArrayList<>();
                        } else {
                            divList = new ArrayList<>();
                            for (int j = lastIndex - 1; j > i - 1; j--) {
                                index = j;
                                attribute = levels.get(j).soup().name;
                                indexValue = null;
                                unique = posList.get(j);
                                divList.add(new Step(index, attribute, indexValue, unique));
                            }
                            highest = robustness;
                        }
                    }
                }
            }
            if (!divList.isEmpty()) {
                greedy.addAll(divList);
            } else {
                greedy.add(new Step(index, attribute, indexValue, unique));
            }
            lastElement = levels.get(pyIndex(levels, index)).soup();
            lastIndex = index;
            index = index - 1;
        }
        if (greedy.get(greedy.size() - 1).index() != 0) throw new Failure("AssertionError", "greedy xpath does not end at the target");
        return greedy;
    }

    /**
     * {@code cal_position(root, element, attribute, text_content, multilevel)}: (uniqueness, position), where position
     * is {@link Integer#MIN_VALUE} for None and -1 for "unique among descendants".
     */
    private static int[] calPosition(SoupNode root, SoupNode element, String attribute, Object text, boolean multilevel) {
        int uniqueness;
        if (multilevel) {
            if (attribute != null) {
                if (attribute.equals("class")) throw new IllegalStateException("unreachable in EAGL");
                uniqueness = root.findAllByAttr(attribute, text, true, 0).size();
            } else {
                uniqueness = root.findAllByName(element.name, true, 0).size();
            }
        } else {
            uniqueness = attribute != null ? root.findAllByAttr(attribute, text, false, 0).size()
                    : root.findAllByName(element.name, false, 0).size();
        }
        List<SoupNode> elements = attribute != null ? root.findAllByAttr(attribute, text, false, 0)
                : root.findAllByName(element.name, false, 0);
        int position = 0;
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i) == element) {
                position = i + 1;
                break;
            }
        }
        if (multilevel && uniqueness == 1) return new int[]{uniqueness, -1};
        if (position < 1) return new int[]{uniqueness, Integer.MIN_VALUE};
        return new int[]{uniqueness, position};
    }

    // ---- Python semantics helpers ---------------------------------------------------------------------------------

    private static String classStep(String value) {
        return "/*[contains(concat(\" \", normalize-space(@class), \" \"), \" " + value + " \")]";
    }

    private static String classAt(SoupNode el, Integer sc) {
        if (sc == null) throw new Failure("TypeError", "list indices must be integers or slices, not NoneType");
        List<String> classes = asList(el.attrs.get("class"));
        int i = sc < 0 ? classes.size() + sc : sc;
        if (i < 0 || i >= classes.size()) throw new Failure("IndexError", "list index out of range");
        return classes.get(i);
    }

    /** String concatenation in Python: a list operand raises TypeError. */
    private static String str(Object value) {
        if (value instanceof String s) return s;
        throw new Failure("TypeError", "can only concatenate str (not \"list\") to str");
    }

    @SuppressWarnings("unchecked")
    private static List<String> asList(Object value) {
        if (value instanceof List<?> l) return (List<String>) l;
        throw new Failure("TypeError", "not a multi-valued attribute: " + value);
    }

    private static boolean isFalsy(Object value) {
        return value == null || (value instanceof String s && s.isEmpty()) || (value instanceof List<?> l && l.isEmpty());
    }

    /** Python list indexing (negative indices count from the end). */
    private static int pyIndex(List<?> list, int i) {
        int j = i < 0 ? list.size() + i : i;
        if (j < 0 || j >= list.size()) throw new Failure("IndexError", "list index out of range");
        return j;
    }
}
