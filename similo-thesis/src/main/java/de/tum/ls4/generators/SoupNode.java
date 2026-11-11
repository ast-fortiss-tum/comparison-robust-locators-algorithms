package de.tum.ls4.generators;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A DOM tree with the semantics of BeautifulSoup 4.11 ({@code html.parser}), as seen by EAGL's implementation: every
 * non-element node (text, comment, doctype, ...) is a "string" child that counts as a sibling; tag and attribute names
 * are lower case; multi-valued attributes such as {@code class} are lists of their whitespace-separated values; and
 * {@link #findAll} matches attribute values like {@code Tag.find_all(attrs=...)}.
 * <p>
 * Encoding read by {@link #parse}: a tag is {@code [name, [attr, value, ...], [children]]}; a string node is {@code 1}
 * if its text is exactly "\n", else {@code 0}.
 */
final class SoupNode {

    /** bs4 4.11 {@code HTMLTreeBuilder.DEFAULT_CDATA_LIST_ATTRIBUTES}. */
    private static final Map<String, Set<String>> MULTI_VALUED = Map.ofEntries(
            Map.entry("*", Set.of("class", "accesskey", "dropzone")),
            Map.entry("a", Set.of("rel", "rev")), Map.entry("link", Set.of("rel", "rev")),
            Map.entry("td", Set.of("headers")), Map.entry("th", Set.of("headers")),
            Map.entry("form", Set.of("accept-charset")), Map.entry("object", Set.of("archive")),
            Map.entry("area", Set.of("rel")), Map.entry("icon", Set.of("sizes")),
            Map.entry("iframe", Set.of("sandbox")), Map.entry("output", Set.of("for")));

    static final String DOCUMENT = "[document]";

    /** Tag name; null for string nodes. */
    final String name;
    /** For string nodes: the text is exactly "\n". */
    final boolean newline;
    /** Attribute values in document order: String, or List&lt;String&gt; for multi-valued attributes. */
    final Map<String, Object> attrs;
    final List<SoupNode> contents = new ArrayList<>();
    SoupNode parent;
    private int indexInParent;

    private SoupNode(String name, boolean newline, Map<String, Object> attrs) {
        this.name = name;
        this.newline = newline;
        this.attrs = attrs;
    }

    boolean isTag() {
        return name != null;
    }

    /** Parses the encoding above; {@code tags} receives the tags in pre-order (the numbering of targets). */
    static SoupNode parse(JsonNode documentChildren, List<SoupNode> tags) {
        SoupNode doc = new SoupNode(DOCUMENT, false, Collections.emptyMap());
        for (JsonNode c : documentChildren) doc.add(build(c, tags));
        return doc;
    }

    private static SoupNode build(JsonNode n, List<SoupNode> tags) {
        if (!n.isArray()) return new SoupNode(null, n.asInt() == 1, Collections.emptyMap());
        String name = n.get(0).asText();
        Map<String, Object> attrs = new LinkedHashMap<>();
        JsonNode a = n.get(1);
        for (int i = 0; i + 1 < a.size(); i += 2) {
            String key = a.get(i).asText();
            JsonNode v = a.get(i + 1);
            if (v.isArray()) { // already split (golden fixtures produced by bs4)
                List<String> values = new ArrayList<>();
                v.forEach(x -> values.add(x.asText()));
                attrs.put(key, values);
            } else {
                attrs.put(key, isMultiValued(name, key) ? splitWhitespace(v.asText()) : v.asText());
            }
        }
        SoupNode tag = new SoupNode(name, false, attrs);
        tags.add(tag);
        for (JsonNode c : n.get(2)) tag.add(build(c, tags));
        return tag;
    }

    private void add(SoupNode child) {
        child.parent = this;
        child.indexInParent = contents.size();
        contents.add(child);
    }

    private static boolean isMultiValued(String tag, String attr) {
        return MULTI_VALUED.get("*").contains(attr) || MULTI_VALUED.getOrDefault(tag, Set.of()).contains(attr);
    }

    /** {@code re.findall(r"\S+", value)} with Python's notion of whitespace ({@code str.isspace}). */
    static List<String> splitWhitespace(String value) {
        List<String> out = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            if (isPythonSpace(cp)) {
                if (start >= 0) out.add(value.substring(start, i));
                start = -1;
            } else if (start < 0) {
                start = i;
            }
            i += Character.charCount(cp);
        }
        if (start >= 0) out.add(value.substring(start));
        return out;
    }

    private static boolean isPythonSpace(int cp) {
        byte d = Character.getDirectionality(cp);
        return Character.getType(cp) == Character.SPACE_SEPARATOR || d == Character.DIRECTIONALITY_WHITESPACE
                || d == Character.DIRECTIONALITY_PARAGRAPH_SEPARATOR || d == Character.DIRECTIONALITY_SEGMENT_SEPARATOR;
    }

    boolean hasAttr(String key) {
        return key != null && attrs.containsKey(key);
    }

    /** Siblings before this node, nearest first ({@code previous_siblings}). */
    List<SoupNode> previousSiblings() {
        if (parent == null) return List.of();
        List<SoupNode> out = new ArrayList<>(parent.contents.subList(0, indexInParent));
        Collections.reverse(out);
        return out;
    }

    List<SoupNode> nextSiblings() {
        return parent == null ? List.of() : parent.contents.subList(indexInParent + 1, parent.contents.size());
    }

    /** {@code len(list(node.parents))}: ancestors up to and including the document. */
    int parentCount() {
        int n = 0;
        for (SoupNode p = parent; p != null; p = p.parent) n++;
        return n;
    }

    /** The topmost ancestor (the document). */
    SoupNode root() {
        SoupNode r = this;
        while (r.parent != null) r = r.parent;
        return r;
    }

    // ---- find_all ----------------------------------------------------------------------------------------------

    /** {@code find_all(name, recursive=recursive, limit=limit)}; limit 0 = unlimited. */
    List<SoupNode> findAllByName(String tagName, boolean recursive, int limit) {
        List<SoupNode> out = new ArrayList<>();
        collect(this, recursive, limit, out, t -> Objects.equals(t.name, tagName));
        return out;
    }

    /** {@code find_all(attrs={key: value}, recursive=recursive, limit=limit)}; value is a String or a List. */
    List<SoupNode> findAllByAttr(String key, Object value, boolean recursive, int limit) {
        List<SoupNode> out = new ArrayList<>();
        collect(this, recursive, limit, out, t -> matches(t.attrs.get(key), value));
        return out;
    }

    private interface TagTest {
        boolean test(SoupNode tag);
    }

    private static boolean collect(SoupNode node, boolean recursive, int limit, List<SoupNode> out, TagTest test) {
        for (SoupNode c : node.contents) {
            if (!c.isTag()) continue;
            if (test.test(c)) {
                out.add(c);
                if (limit > 0 && out.size() >= limit) return true;
            }
            if (recursive && collect(c, true, limit, out, test)) return true;
        }
        return false;
    }

    /** bs4 4.11 {@code SoupStrainer._matches(markup, match_against)} for attribute values. */
    static boolean matches(Object markup, Object against) {
        if (markup instanceof List<?> values) {
            for (Object item : values) {
                if (matches(item, against)) return true;
            }
            return matches(String.join(" ", castStrings(values)), against);
        }
        if (markup == null) {
            return against == null || "".equals(against) || (against instanceof List<?> l && l.isEmpty());
        }
        if (against instanceof List<?> options) {
            for (Object option : options) {
                if (matches(markup, option)) return true;
            }
            return false;
        }
        return markup.equals(against);
    }

    @SuppressWarnings("unchecked")
    private static List<String> castStrings(List<?> l) {
        return (List<String>) l;
    }

    @Override
    public String toString() {
        return isTag() ? "<" + name + " " + attrs + ">" : (newline ? "\"\\n\"" : "\"…\"");
    }
}
