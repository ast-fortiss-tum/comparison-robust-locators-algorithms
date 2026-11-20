package de.tum.ls4.generators;

/** Quoting of string literals for XPath 1.0, which has no escape characters. */
public final class XPathLiteral {

    private XPathLiteral() {
    }

    public static String quote(String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }
        // Both quote kinds present: concat('a', "'", 'b', ...)
        StringBuilder sb = new StringBuilder("concat(");
        String[] parts = value.split("'", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(", \"'\", ");
            }
            sb.append('\'').append(parts[i]).append('\'');
        }
        return sb.append(')').toString();
    }
}
