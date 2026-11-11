package sidereal;

import org.jdom2.*;
import org.jdom2.xpath.XPathFactory;
import java.io.IOException;
import java.util.*;
import sidereal.Utils.*;
import sidereal.ElementInfo;
import sidereal.XPathInfo;
import sidereal.EuristicXPath;

public class UtilsSIDEREAL {

	public static Map<String, Double> fragilityWeights = new HashMap<>();

	static List<String> attributiPrioritari = Arrays.asList("id");
	static List<String> ordineAttributi     = Arrays.asList("id", "name", "class", "title", "alt", "value");
	static List<String> attributiBlackList  = Arrays.asList("href", "src", "onclick", "onload", "tabindex", "width", "height", "style", "size", "maxlength");

	public static PriorityQueue<XPathInfo> siderealXPathLocatorFinder(Document pageDOM, Element target, List<ElementInfo> elementInfoFullPath) throws IOException {
		PriorityQueue<XPathInfo> candidates = new PriorityQueue<>(Comparator.comparingDouble(XPathInfo::getFragility));
		List<XPathInfo> robustLocators = new ArrayList<>();

		XPathInfo initial = new XPathInfo("//*", 1.0, computeFC(List.of()));
		candidates.add(initial);

		while (!candidates.isEmpty()) {
			XPathInfo current = candidates.poll();
			specialize(current, pageDOM, target, candidates, robustLocators, elementInfoFullPath);

			if (!robustLocators.isEmpty()) break; // Stop at first perfect match
		}

		if (robustLocators.isEmpty() && !candidates.isEmpty()) {
			robustLocators.add(candidates.peek());
		}

		return new PriorityQueue<>(robustLocators);
	}

	private static void specialize(XPathInfo currentXPath, Document document, Element target,
								   PriorityQueue<XPathInfo> candidates, List<XPathInfo> robustLocators,
								   List<ElementInfo> elementInfoFullPath) throws IOException {

		int currentLevel = Utils.getXPathNumberOfLevels(currentXPath.getXpath()) - 1;
		if (currentLevel >= elementInfoFullPath.size()) return;

		ElementInfo info = elementInfoFullPath.get(currentLevel);

		List<XPathTransformation> transformations = new ArrayList<>();
		transformations.addAll(transfAddAttribute(currentXPath.getXpath(), info));
		transformations.addAll(transfAddText(currentXPath.getXpath(), info));
		transformations.addAll(transfAddPosition(currentXPath.getXpath(), info));

		for (XPathTransformation t : transformations) {
			String xpath = t.xpath;
			double precision = Utils.precision(document, xpath, target);
			double recall = Utils.recall(document, xpath, target);
			double fc = computeFC(t.usedFeatures.stream().map(f -> fragilityWeights.getOrDefault(f, 0.5)).toList());

			XPathInfo newInfo = new XPathInfo(xpath, precision, fc);
			if (recall == 1.0 && precision == 1.0) {
				robustLocators.add(newInfo);
			} else {
				candidates.add(newInfo);
			}
		}
	}

	private static List<XPathTransformation> transfAddAttribute(String baseXpath, ElementInfo info) {
		List<XPathTransformation> results = new ArrayList<>();
		for (String attr : info.attributes) {
			String attrName = attr.split("=")[0];
			if (attributiBlackList.contains(attrName)) continue;
			String newXpath = baseXpath + "[@" + attr + "]";
			results.add(new XPathTransformation(newXpath, List.of(attrName)));
		}
		return results;
	}

	private static List<XPathTransformation> transfAddText(String baseXpath, ElementInfo info) {
		if (info.text == null || info.text.isEmpty()) return List.of();
		String newXpath = baseXpath + "[contains(text(), '" + info.text + "')]";
		return List.of(new XPathTransformation(newXpath, List.of("text")));
	}

	private static List<XPathTransformation> transfAddPosition(String baseXpath, ElementInfo info) {
		String newXpath = baseXpath + "[" + info.position + "]";
		return List.of(new XPathTransformation(newXpath, List.of("position")));
	}

	private static double computeFC(List<Double> weights) {
		double product = 1.0;
		for (double w : weights) product *= (1 - w);
		return 1 - product;
	}

	public static class XPathTransformation {
		public String xpath;
		public List<String> usedFeatures;

		public XPathTransformation(String xpath, List<String> usedFeatures) {
			this.xpath = xpath;
			this.usedFeatures = usedFeatures;
		}
	}

    /**
     * Loads the full path data for an element, given its DOM and XPath.
     * Adapted from ROBULAPlusNew, but uses sidereal.ElementInfo.
     */
    public static List<ElementInfo> loadDataFullPath(Document doc, String xPath) {
        List<ElementInfo> elementInfoFullPath = new ArrayList<>();
        try {
            XPathFactory xFactory = XPathFactory.instance();
            List<?> nodes = xFactory.compile(xPath).evaluate(doc);
            if (nodes.isEmpty()) {
                return elementInfoFullPath;
            }
            Element target = (Element) nodes.get(0);
            // Build the list of ancestors (from root to target)
            List<Element> ancestors = new ArrayList<>();
            Element current = target;
            while (current != null) {
                ancestors.add(0, current); // insert at beginning
                current = current.getParentElement();
            }
            for (Element el : ancestors) {
                // Gather attributes, skipping those in blacklist
                List<String> attrs = new ArrayList<>();
                for (Attribute attr : el.getAttributes()) {
                    String attrName = attr.getName();
                    if (!attributiBlackList.contains(attrName)) {
                        attrs.add(attrName + "=" + attr.getValue());
                    }
                }
                // Get text content (trimmed)
                String text = el.getTextNormalize();
                // Compute position among siblings with same name
                int position = 1;
                if (el.getParentElement() != null) {
                    List<Element> siblings = el.getParentElement().getChildren(el.getName());
                    for (Element sib : siblings) {
                        if (sib == el) break;
                        position++;
                    }
                }
                elementInfoFullPath.add(new ElementInfo(el.getName(), attrs, text, position));
            }
        } catch (Exception e) {
            // Handle exceptions as needed
            e.printStackTrace();
        }
        return elementInfoFullPath;
    }
}