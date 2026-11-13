package de.tum.ls4.generators;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One level of the ancestor chain of a target element (the target itself or one of its ancestors).
 *
 * @param tag          lower-case tag name, or {@code null} if the element cannot be addressed by name in an
 *                     XPath over the page (e.g. SVG/MathML elements inside an HTML document).
 * @param position     1-based position among the preceding siblings with the same tag (XPath {@code tag[n]}).
 * @param positionStar 1-based position among all element siblings (XPath {@code *[n]}).
 * @param attributes   the element's attributes in DOM order.
 * @param text         the trimmed value of the element's first text-node child, or {@code null} if there is
 *                     none or it is blank. This is what XPath {@code text()} sees inside {@code contains()}.
 */
public record AncestorNode(String tag, int position, int positionStar, Map<String, String> attributes, String text) {

    public AncestorNode {
        tag = tag == null || tag.isEmpty() ? null : tag.toLowerCase(Locale.ROOT);
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes == null ? Map.of() : attributes));
        text = text == null || text.isBlank() ? null : text.trim();
        if (position < 1 || positionStar < 1) {
            throw new IllegalArgumentException("Positions are 1-based: " + position + ", " + positionStar);
        }
    }

    public boolean hasTag() {
        return tag != null;
    }

    public boolean hasText() {
        return text != null;
    }
}
