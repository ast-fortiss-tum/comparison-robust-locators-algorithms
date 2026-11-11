package robulanew;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jdom2.Content;
import org.jdom2.Content.CType;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

import robulanew.XPathInfo;
import robulanew.EuristicXPath;

/**
 * Utility functions used to generate Robula+ xpath in the original Robula Plus paper.
 */

public class Utils
{

    // method calculating Recall value
    public static double recall(Document d, String xPath, Element target)
    {
	XPathExpression<Element> xpath = XPathFactory.instance().compile(xPath, Filters.element());
	List<Element> elements = xpath.evaluate(d);
	if (elements.contains(target))
	    return 1.0;
	else
	    return 0.0;
    }

    public static boolean myContains(List<XPathInfo> list, String e)
    {
	for (XPathInfo temp : list)
	{
	    if (temp.getXpath().toString().equals(e))
	    {
		return true;
	    }
	}
	return false;
    }

    // method calculating Precision value
    public static double precision(Document document, String xPath, Element target)
    {
	XPathExpression<Element> xpath = XPathFactory.instance().compile(xPath, Filters.element());
	List<Element> elements = xpath.evaluate(document);
	if (elements.contains(target))
	{
		return 1.0 / elements.size();
	}
	else
	    return 0.0;
    }

    public static int numberOfSubString(String str, String findStr)
    {

	int lastIndex = 0;
	int count = 0;

	while (lastIndex != -1)
	{

	    lastIndex = str.indexOf(findStr, lastIndex);

	    if (lastIndex != -1)
	    {
		count++;
		lastIndex += findStr.length();
	    }
	}
	return count;

    }

    public static String removeAttributesValues(String xpath)
    {

	String output = "";
	boolean insideSingleQuote = false;
	boolean insideDoubleQuote = false;
	for (int i = 0; i < xpath.length(); i++)
	{

	    if (xpath.charAt(i) == '"' && insideDoubleQuote == false && insideSingleQuote == false)
	    {
		insideDoubleQuote = true;

	    }
	    else if (xpath.charAt(i) == '"' && insideDoubleQuote == true && insideSingleQuote == false)
	    {
		insideDoubleQuote = false;
	    }
	    else if (xpath.charAt(i) == '\'' && insideDoubleQuote == false && insideSingleQuote == false)
	    {
		insideSingleQuote = true;
	    }
	    else if (xpath.charAt(i) == '\'' && insideDoubleQuote == false && insideSingleQuote == true)
	    {
		insideSingleQuote = false;
	    }

	    if (insideDoubleQuote == false && insideSingleQuote == false)
	    {
		output = output + xpath.charAt(i);
	    }

	}
	output = output.replaceAll("'", "_").replaceAll("\"", "_");
	return output;
    }

    public static int getXPathNumberOfLevels(String string)
    {
	String cleaned = Utils.removeAttributesValues(string);
	cleaned = cleaned.replace("//", "/");
	return Utils.numberOfSubString(cleaned, "/");
    }

    // Returns the siblings' position of a node
    public static int getPosition(Element node, List<Content> siblingsAndCurrentNode)
    {
	int position = 1;
	if (siblingsAndCurrentNode != null)
	{
	    for (Content c : siblingsAndCurrentNode)
	    {
		if (c.getCType() == CType.Element)
		{
		    Element e = (Element) c;
		    if (c.equals(node))
		    {
			return position;
		    }
		    else if (!c.equals(node) && e.getName() == node.getName())
		    {
			position++;
		    }
		}
	    }
	}
	return position;
    }

    // Returns the siblings' position of a * node
    public static int getPositionStar(Element node, List<Content> siblingsAndCurrentNode)
    {
	int position = 1;
	if (siblingsAndCurrentNode != null)
	{
	    for (Content c : siblingsAndCurrentNode)
	    {
		if (c.getCType() == CType.Element)
		{
		    if (c.equals(node))
		    {
			return position;
		    }
		    else if (!c.equals(node))
		    {
			position++;
		    }
		}
	    }
	}
	return position;
    }

}
