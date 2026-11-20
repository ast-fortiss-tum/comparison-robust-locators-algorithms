package robulanew;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import robulanew.Utils.*;
import robulanew.XPathGeneratorUtil.*;
import robulanew.AlgorithmSettings;
import robulanew.ElementInfo;
import robulanew.XPathInfo;
import robulanew.EuristicXPath;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.jdom2.xpath.XPathHelper;

public class XPathGeneratorUtil {

    public static String getIDXPath(Element element) {
        if (element == null) {
            return "";
        }

        // Use ID if present
        String id = element.getAttributeValue("id");
        if (id != null) {
            return "//*[@id='" + id + "']";
        }

        Element parent = element.getParentElement();
        if (parent == null) {
            return "/" + element.getName();
        }

        // Find index among siblings with same name
        int index = 1;
        for (Element sibling : parent.getChildren(element.getName())) {
            if (sibling == element) {
                break;
            }
            index++;
        }

        return getIDXPath(parent) + "/" + element.getName() + "[" + index + "]";
    }

    public static String getSeleniumIDEXPath(Element element, Document document) {
        if (element == null) return "";

        String tag = element.getName();

        // Strategy 1: Unique ID
        String id = element.getAttributeValue("id");
        if (id != null && isUniqueAttribute("id", id, document)) {
            return "//*[@id='" + id + "']";
        }

        // Strategy 2: Common unique attributes
        List<String> attrs = List.of("name", "type", "role", "title", "aria-label", "placeholder");
        for (String attrName : attrs) {
            String value = element.getAttributeValue(attrName);
            if (value != null && isUniqueAttribute(attrName, value, document)) {
                return "//" + tag + "[@" + attrName + "=" + quoteXPathLiteral(value) + "]";
            }
        }

        // Strategy 3: Text content
        String text = element.getTextNormalize().replaceAll("\\s+", " ").trim();
        if (!text.isEmpty() && isUniqueText(tag, text, document)) {
            return "//" + tag + "[contains(normalize-space(text()), " + quoteXPathLiteral(text) + ")]";
        }

        // Strategy 4: Single class name
        String classAttr = element.getAttributeValue("class");
        if (classAttr != null && classAttr.split("\\s+").length == 1 &&
                isUniqueAttribute("class", classAttr, document)) {
            return "//" + tag + "[@class=" + quoteXPathLiteral(classAttr) + "]";
        }

        // Strategy 5: Fallback to indexed XPath
        int index = 1;
        Element parent = element.getParentElement();
        if (parent != null) {
            List<Element> siblings = parent.getChildren(tag);
            for (Element sibling : siblings) {
                if (sibling == element) break;
                index++;
            }
        }

        return "//" + tag + "[" + index + "]";
    }

    public static String getMontotoXPath(Element element, Document document) {
        if (element == null) return "";

        String tag = element.getName();

        List<Attribute> attributes = element.getAttributes();
        Set<String> blacklist = Set.of(
                "style", "href", "src", "onclick", "onload", "xmlns", "xmlns:xlink", "tile?apikey", "data-galink",
                "srcset", "data-src", "data-srcset", "data-original", "data-ga-action", "data-analytics", "data-tracking",
                "data-testid", "data-widget", "integrity", "crossorigin", "referrerpolicy", "ping"
        );

        List<String> predicates = new ArrayList<>();
        for (Attribute attr : attributes) {
            String name = attr.getName();
            String value = attr.getValue().trim();
            if (!blacklist.contains(name) && !value.isEmpty()) {
                predicates.add("@" + name + "=" + quoteXPathLiteral(value));
            }
        }

        if (!predicates.isEmpty()) {
            return "//" + tag + "[" + String.join(" and ", predicates) + "]";
        } else {
            // Fallback: absolute path
            return XPathHelper.getAbsolutePath(element).toString();
        }
    }

    private static String quoteXPathLiteral(String value) {
        if (value.contains("'") && value.contains("\"")) {
            String[] parts = value.split("(?=['\"])");
            List<String> quotedParts = new ArrayList<>();
            for (String part : parts) {
                if (part.equals("'")) {
                    quotedParts.add("\"'\"");
                } else if (part.equals("\"")) {
                    quotedParts.add("'\"'");
                } else {
                    quotedParts.add("'" + part + "'");
                }
            }
            return "concat(" + String.join(",", quotedParts) + ")";
        } else if (value.contains("'")) {
            return "\"" + value + "\"";
        } else {
            return "'" + value + "'";
        }
    }

    private static boolean isUniqueAttribute(String attr, String value, Document document) {
        String xpathExpr = "//*[@" + attr + "=" + quoteXPathLiteral(value) + "]";
        XPathExpression<Element> expr = XPathFactory.instance().compile(xpathExpr, Filters.element());
        List<Element> matches = expr.evaluate(document);
        return matches.size() == 1;
    }

    private static boolean isUniqueText(String tag, String text, Document document) {
        String xpathExpr = "//" + tag + "[normalize-space(text())=" + quoteXPathLiteral(text) + "]";
        XPathExpression<Element> expr = XPathFactory.instance().compile(xpathExpr, Filters.element());
        List<Element> matches = expr.evaluate(document);
        return matches.size() == 1;
    }

}

