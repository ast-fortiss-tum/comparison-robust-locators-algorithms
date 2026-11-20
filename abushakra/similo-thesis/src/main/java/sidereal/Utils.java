package sidereal;

import java.util.*;
import org.jdom2.*;
import org.jdom2.Content.CType;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

public class Utils
{
	public static double recall(Document d, String xPath, Element target) {
		XPathExpression<Element> xpath = XPathFactory.instance().compile(xPath, Filters.element());
		List<Element> elements = xpath.evaluate(d);
		return elements.contains(target) ? 1.0 : 0.0;
	}

	public static boolean myContains(List<XPathInfo> list, String e) {
		for (XPathInfo temp : list) {
			if (temp.getXpath().equals(e)) {
				return true;
			}
		}
		return false;
	}

	public static double precision(Document document, String xPath, Element target) {
		XPathExpression<Element> xpath = XPathFactory.instance().compile(xPath, Filters.element());
		List<Element> elements = xpath.evaluate(document);
		return elements.contains(target) ? 1.0 / elements.size() : 0.0;
	}

	public static int numberOfSubString(String str, String findStr) {
		int lastIndex = 0, count = 0;
		while ((lastIndex = str.indexOf(findStr, lastIndex)) != -1) {
			count++;
			lastIndex += findStr.length();
		}
		return count;
	}

	public static String removeAttributesValues(String xpath) {
		StringBuilder output = new StringBuilder();
		boolean inSingle = false, inDouble = false;
		for (char c : xpath.toCharArray()) {
			if (c == '"' && !inDouble && !inSingle) inDouble = true;
			else if (c == '"' && inDouble) inDouble = false;
			else if (c == '\'' && !inDouble && !inSingle) inSingle = true;
			else if (c == '\'' && inSingle) inSingle = false;
			else if (!inDouble && !inSingle) output.append(c);
		}
		return output.toString().replaceAll("[\"']", "_");
	}

	public static int getXPathNumberOfLevels(String string) {
		String cleaned = removeAttributesValues(string).replace("//", "/");
		return numberOfSubString(cleaned, "/");
	}

	public static int getPosition(Element node, List<Content> siblings) {
		int pos = 1;
		for (Content c : siblings) {
			if (c.getCType() == CType.Element) {
				Element e = (Element) c;
				if (c.equals(node)) return pos;
				else if (e.getName().equals(node.getName())) pos++;
			}
		}
		return pos;
	}

	public static int getPositionStar(Element node, List<Content> siblings) {
		int pos = 1;
		for (Content c : siblings) {
			if (c.getCType() == CType.Element) {
				if (c.equals(node)) return pos;
				pos++;
			}
		}
		return pos;
	}

	// SIDEREAL-specific: compute fragility coefficient from weights
	public static double computeFragilityCoefficient(List<Double> weights) {
		double product = 1.0;
		for (double w : weights) product *= (1 - w);
		return 1.0 - product;
	}
}