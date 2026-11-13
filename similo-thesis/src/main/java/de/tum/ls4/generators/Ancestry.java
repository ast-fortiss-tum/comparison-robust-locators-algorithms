package de.tum.ls4.generators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The chain from a target element up to the document root element ({@code <html>}), i.e. the information
 * contained in the target's "full absolute XPath" (tag names, positions, attributes and texts at every level).
 * <p>
 * Level 0 is the target, level {@code size() - 1} is the root element.
 */
public record Ancestry(List<AncestorNode> nodes) {

    public Ancestry {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("An ancestry needs at least the target element");
        }
        nodes = List.copyOf(nodes);
    }

    public int size() {
        return nodes.size();
    }

    /** @param level 0 = target, 1 = its parent, ... */
    public AncestorNode node(int level) {
        return nodes.get(level);
    }

    public AncestorNode target() {
        return nodes.get(0);
    }

    /** Absolute XPath of the target, e.g. {@code /html[1]/body[1]/div[2]/a[1]}. */
    public String absoluteXPath() {
        return absoluteXPath(0);
    }

    /** Absolute XPath of the ancestor at the given level. */
    public String absoluteXPath(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = nodes.size() - 1; i >= level; i--) {
            AncestorNode n = nodes.get(i);
            sb.append('/');
            if (n.hasTag()) {
                sb.append(n.tag()).append('[').append(n.position()).append(']');
            } else {
                sb.append("*[").append(n.positionStar()).append(']');
            }
        }
        return sb.toString();
    }

    /**
     * Parses the structure returned by {@code generators/ancestry.js}: a list (target first) of maps with keys
     * {@code tag}, {@code position}, {@code positionStar}, {@code attributes} (list of [name, value]) and {@code text}.
     */
    public static Ancestry fromBrowser(Object raw) {
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected a list from ancestry.js but got " + raw);
        }
        List<AncestorNode> nodes = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                throw new IllegalArgumentException("Expected a map per ancestor but got " + o);
            }
            Map<String, String> attributes = new LinkedHashMap<>();
            if (m.get("attributes") instanceof List<?> attrs) {
                for (Object a : attrs) {
                    if (a instanceof List<?> pair && pair.size() == 2) {
                        attributes.put(String.valueOf(pair.get(0)), String.valueOf(pair.get(1)));
                    }
                }
            }
            nodes.add(new AncestorNode(
                    (String) m.get("tag"),
                    ((Number) m.get("position")).intValue(),
                    ((Number) m.get("positionStar")).intValue(),
                    attributes,
                    (String) m.get("text")));
        }
        return new Ancestry(nodes);
    }

    /** The inverse of {@link #fromBrowser}: plain lists and maps (e.g. for JSON persistence). */
    public List<Map<String, Object>> toRaw() {
        List<Map<String, Object>> out = new ArrayList<>(nodes.size());
        for (AncestorNode n : nodes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tag", n.tag());
            m.put("position", n.position());
            m.put("positionStar", n.positionStar());
            List<List<String>> attrs = new ArrayList<>();
            n.attributes().forEach((k, v) -> attrs.add(List.of(k, v)));
            m.put("attributes", attrs);
            m.put("text", n.text());
            out.add(m);
        }
        return out;
    }
}
