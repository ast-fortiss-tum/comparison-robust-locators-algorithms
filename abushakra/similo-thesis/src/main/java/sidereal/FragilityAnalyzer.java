package sidereal;

import org.jdom2.*;
import org.jdom2.input.SAXBuilder;
import org.jdom2.xpath.XPathFactory;
import org.jdom2.xpath.XPathHelper;
import org.jdom2.filter.Filters;

import java.io.File;
import java.util.*;

public class FragilityAnalyzer {

    public static Map<String, Double> computeFragilityWeights(Document oldDOM, Document newDOM, List<String> attributeNames) {
        Map<String, Integer> changed = new HashMap<>();
        Map<String, Integer> unchanged = new HashMap<>();

        XPathFactory xFactory = XPathFactory.instance();
        List<Element> oldElements = new ArrayList<>();
        oldDOM.getRootElement().getDescendants(Filters.element()).forEachRemaining(oldElements::add);

        for (Element oldEl : oldElements) {
            String absPath = XPathHelper.getAbsolutePath(oldEl).toString();
            try {
                Element newEl = xFactory.compile(absPath, Filters.element()).evaluateFirst(newDOM);
                if (newEl == null) continue;

                for (String attr : attributeNames) {
                    String oldVal = oldEl.getAttributeValue(attr);
                    String newVal = newEl.getAttributeValue(attr);
                    if (oldVal == null && newVal == null) continue;
                    if (Objects.equals(oldVal, newVal)) {
                        unchanged.put(attr, unchanged.getOrDefault(attr, 0) + 1);
                    } else {
                        changed.put(attr, changed.getOrDefault(attr, 0) + 1);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        Map<String, Double> weights = new HashMap<>();
        for (String attr : attributeNames) {
            int ch = changed.getOrDefault(attr, 0);
            int unch = unchanged.getOrDefault(attr, 0);
            int total = ch + unch;
            if (total == 0) {
                weights.put(attr, 0.5); // default weight
            } else {
                weights.put(attr, ch / (double) total);
            }
        }

        return weights;
    }

    public static Document loadDOMFromFile(String filePath) throws Exception {
        SAXBuilder saxBuilder = new SAXBuilder();
        return saxBuilder.build(new File(filePath));
    }

    // Example usage
    public static void main(String[] args) throws Exception {
        Document oldDOM = loadDOMFromFile("/path/to/old.html");
        Document newDOM = loadDOMFromFile("/path/to/new.html");
        List<String> attrs = List.of("id", "class", "name", "title", "alt", "value");
        Map<String, Double> weights = computeFragilityWeights(oldDOM, newDOM, attrs);

        System.out.println("Fragility Weights:");
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }
}
