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

/**
 * Utility functions used to generate Robula+ xpath in the original Robula Plus paper.
 */

public class UtilsROBULAPlusNew
{

    static List<String> attributiPrioritari = new ArrayList<String>(Arrays.asList("id"));
    static List<String> ordineAttributi     = new ArrayList<String>(Arrays.asList("id", "name", "class", "title", "alt", "value"));
    static List<String> attributiBlackList  = new ArrayList<String>(Arrays.asList("href", "src", "onclick", "onload", "tabindex", "width", "height", "style", "size", "maxlength"));

    // return a list of robustXPathLocators
    public static LinkedList<XPathInfo> robustXPathLocatorFinder(Document pageDOM, ArrayList<ElementInfo> elementInfoFullPath, String absoluteXPath, String url, AlgorithmSettings settings) throws IOException
    {
	// evaluate the XPath expression on the document
	XPathExpression<Element> xpath = XPathFactory.instance().compile(absoluteXPath, Filters.element());
	List<Element> elements = xpath.evaluate(pageDOM);
	if (elements.isEmpty())
	{
	    System.out.println("XPath evaluation not valid: " + absoluteXPath.toString());
	    return new LinkedList<XPathInfo>();
	}
	else
	{
	    if (elements.size() > 1)
	    {
//		System.out.println("!!! Ambiguous XPath !!!" + xpath.getExpression() + "with url:" + url);
	    }
	    // take the element target
	    Element el = elements.get(0);
//	    System.out.println("Element target (original): " + absoluteXPath);
//	    System.out.println("Element target (used):     " + XPathHelper.getAbsolutePath(el));
	    LinkedList<XPathInfo> robustXPathLocatorsList = new LinkedList<XPathInfo>();
	    LinkedList<XPathInfo> candidateXPathLocatorsList = new LinkedList<XPathInfo>();
	    // at the beginning the candidateXPathLocatorsList contains only "//*"
	    candidateXPathLocatorsList.add(new XPathInfo("//*", Double.valueOf(Utils.precision(pageDOM, "//*", el))));
	    long count = 0;
	    boolean passato = false;
	    long inizio = 0;
	    while (!candidateXPathLocatorsList.isEmpty())
	    {
		specialize(candidateXPathLocatorsList.remove(0), pageDOM, el, candidateXPathLocatorsList, robustXPathLocatorsList, elementInfoFullPath, settings);

		if (!robustXPathLocatorsList.isEmpty())
		{
		    if (settings.euristicSelection)
		    {
			if (passato == false)
			{
			    passato = true;
			    inizio = System.currentTimeMillis();
			}
			else
			{
			    if ((System.currentTimeMillis() - inizio) >= 1000)
				break;
			}

		    }
		    else
		    {
			break;
		    }
		}
		count++;

		if (count % 10 == 0)
		{
//		    System.out.print(count + " - ");
		    if (count % 200 == 0)
			System.out.print("\n");
		}

		/*
		 * System.out.print(count + " "); if (count % 50 == 0) System.out.print("\n");
		 */
	    }
	    return robustXPathLocatorsList;
	}
    }

    // method to specialize an XPath expression via transformations
    protected static void specialize(XPathInfo currentXPath, Document document, Element target, LinkedList<XPathInfo> candidateXPathLocatorsList, List<XPathInfo> robustXPathLocatorsList, ArrayList<ElementInfo> elementInfoFullPath, AlgorithmSettings settings)
	    throws IOException
    {

	boolean prioritari;

	// indici array 0..n quindi levo 1
	int currentElementPos = Utils.getXPathNumberOfLevels(currentXPath.getXpath()) - 1;
	ElementInfo elementInfo = elementInfoFullPath.get(currentElementPos);

	// Transformation: convert a "*" into a tag name
	List<String> transfConvertStar = transfConvertStar(currentXPath.getXpath(), elementInfo.tagName);
	evaluateXPaths(document, target, candidateXPathLocatorsList, robustXPathLocatorsList, transfConvertStar, settings.orderedInsert);

	if (!currentXPath.getXpath().startsWith("//*") || settings.specializeStars == true)
	{
	    // Transformation: add an attribute
	    prioritari = true;
	    List<String> transfAddAttribute = transfAddAttribute(currentXPath.getXpath(), elementInfo.attributes, elementInfo.tagName, prioritari);
	    evaluateXPaths(document, target, candidateXPathLocatorsList, robustXPathLocatorsList, transfAddAttribute, settings.orderedInsert);

	    // Transformation: add text
	    List<String> transfAddText = transfAddText(currentXPath.getXpath(), elementInfo.text, elementInfo.tagName);
	    evaluateXPaths(document, target, candidateXPathLocatorsList, robustXPathLocatorsList, transfAddText, settings.orderedInsert);

	    // Transformation: add an attribute
	    prioritari = false;
	    List<String> transfAddAttributeNonPrioritari = transfAddAttribute(currentXPath.getXpath(), elementInfo.attributes, elementInfo.tagName, prioritari);
	    evaluateXPaths(document, target, candidateXPathLocatorsList, robustXPathLocatorsList, transfAddAttributeNonPrioritari, settings.orderedInsert);

	    if (settings.powerSetAttributes)
	    {
		// Transformation: add a set of attribute
		List<String> transfAddSetAttribute = transfAddSetAttribute(currentXPath.getXpath(), elementInfo.setAttributes, elementInfo.tagName);
		evaluateXPaths(document, target, candidateXPathLocatorsList, robustXPathLocatorsList, transfAddSetAttribute, settings.orderedInsert);
	    }

	    // Transformation: add a position attribute
	    List<String> transfAddPosition = transfAddPosition(currentXPath.getXpath(), elementInfo);
	    evaluateXPaths(document, target, candidateXPathLocatorsList, robustXPathLocatorsList, transfAddPosition, settings.orderedInsert);
	}

	int levelsAbsoluteXPath = Utils.getXPathNumberOfLevels(XPathHelper.getAbsolutePath(target));

	// -1 in quanto non contiamo /html
	if ((Utils.getXPathNumberOfLevels(currentXPath.getXpath())) < (levelsAbsoluteXPath - 1))
	{
	    // Transformation: add a //* on top without double slashes
	    List<String> transfAddLevel = transfAddLevel(currentXPath.getXpath());
	    evaluateXPaths(document, target, candidateXPathLocatorsList, robustXPathLocatorsList, transfAddLevel, settings.orderedInsert);

	}
    }

    private static void evaluateXPaths(Document document, Element target, List<XPathInfo> candidateXPathLocatorsList, List<XPathInfo> robustXPathLocatorsList, List<String> trans, boolean orderedInsert)
    {
	if (trans != null)
	{
	    for (String xPath : trans)
	    {
		double precision = Utils.precision(document, xPath, target);
		double recall = Utils.recall(document, xPath, target);
		if (recall == 1.0 && precision == 1.0)
		{
		    if (!Utils.myContains(robustXPathLocatorsList, xPath))
			robustXPathLocatorsList.add(new XPathInfo(xPath, 1.0));
		}
		else if (recall > 0.0 && !Utils.myContains(candidateXPathLocatorsList, xPath))
		{
		    if (orderedInsert)
		    { // inserimento ordinato
			if (!candidateXPathLocatorsList.isEmpty())
			{
			    for (int i = 0; i < candidateXPathLocatorsList.size(); i++)
			    {
				if (candidateXPathLocatorsList.get(i).getPrecision() < precision)
				{
				    candidateXPathLocatorsList.add(i, new XPathInfo(xPath, precision));
				    break;
				}
			    }
			    candidateXPathLocatorsList.add(new XPathInfo(xPath, precision));
			}
			else
			    candidateXPathLocatorsList.add(new XPathInfo(xPath, precision));
		    }
		    else
		    {
			// inserimento non ordinato
			candidateXPathLocatorsList.add(new XPathInfo(xPath, Double.valueOf(precision)));
		    }
		}
	    }
	}
    }

    // First transformation: it converts a "*" into a tag name
    public static List<String> transfConvertStar(String xpath, String tag)
    {
	List<String> outputTrans1 = new LinkedList<String>();
	if (xpath.startsWith("//*"))
	{
	    outputTrans1.add("//" + tag + xpath.substring(3));

	    return outputTrans1;
	}
	else
	    return null;
    }

    // Second transformation: add an attribute to a node
    public static List<String> transfAddAttribute(String xpath, ArrayList<String> listaAttributi, String tagCorrente, boolean prioritari)
    {
	List<String> outputTrans = new LinkedList<String>();

	int index;
	if (!listaAttributi.isEmpty())
	{
	    String tagCorrenteXPath;
	    if (xpath.startsWith("//*"))
	    {
		index = 3;
		tagCorrenteXPath = "*";
	    }
	    else
	    {
		index = tagCorrente.length() + 2;
		tagCorrenteXPath = tagCorrente;
	    }

	    if (xpath.length() == index || xpath.charAt(index) == '/')
	    {

		for (String attributo : listaAttributi)
		{
		    String[] attri = attributo.split("=");
		    if (prioritari == true)
		    {
			if (!attributiPrioritari.contains(attri[0]))
			    break;
		    }
		    else
		    {
			if (attributiPrioritari.contains(attri[0]))
			    break;
		    }

		    String attr = "[@" + attributo + "]";
		    // inseriamo all'interno di un XPath
		    if (xpath.length() > index)
		    {
			outputTrans.add("//" + tagCorrenteXPath + attr + xpath.substring(index));
		    }
		    else
		    {
			outputTrans.add("//" + tagCorrenteXPath + attr);
		    }
		}
	    }
	}

	return outputTrans;
    }

    // Second transformation: add an attribute to a node
    public static List<String> transfAddSetAttribute(String xpath, LinkedHashSet<Set<String>> powerSetAttributes, String tagCorrente)
    {
	List<String> outputTrans = new LinkedList<String>();

	int index;
	if (!powerSetAttributes.isEmpty())
	{
	    String tagCorrenteXPath;
	    if (xpath.startsWith("//*"))
	    {
		index = 3;
		tagCorrenteXPath = "*";
	    }
	    else
	    {
		index = tagCorrente.length() + 2;
		tagCorrenteXPath = tagCorrente;
	    }

	    if (xpath.length() == index || xpath.charAt(index) == '/')
	    {

		for (Set<String> setAttributes : powerSetAttributes)
		{
		    if (!setAttributes.isEmpty() || !(setAttributes.size() <= 1))
		    {
			String attr = "[";

			for (String attributo : setAttributes)
			{
			    attr = attr + "@" + attributo + " and ";
			}

			attr = (String) attr.subSequence(0, attr.length() - 5);
			attr = attr + "]";
			// inseriamo all'interno di un XPath
			if (xpath.length() > index)
			{
			    outputTrans.add("//" + tagCorrenteXPath + attr + xpath.substring(index));
			}
			else
			{
			    outputTrans.add("//" + tagCorrenteXPath + attr);
			}
		    }
		}
	    }
	}

	return outputTrans;
    }

    /*
     * Third transformation: add child position to a node
     */
    public static List<String> transfAddPosition(String xpath, ElementInfo elementInfo)
    {
	List<String> outputTrans = new LinkedList<String>();
	if (!xpath.startsWith("//body"))// evitare body[1]
	{
	    int index;

	    String tagCorrenteXPath;
	    if (xpath.startsWith("//*"))
	    {
		index = 3;
		tagCorrenteXPath = "*";
	    }
	    else
	    {
		index = elementInfo.tagName.length() + 2;
		tagCorrenteXPath = elementInfo.tagName;
	    }

	    if (xpath.length() == index || xpath.charAt(index) == '/' || xpath.charAt(index + 1) == '@' || xpath.charAt(index + 1) == 'c')
	    {
		int posizione;
		if (tagCorrenteXPath.endsWith("*"))

		    posizione = elementInfo.positionStar;
		else
		    posizione = elementInfo.position;

		String pos = "[" + posizione + "]";
		if (xpath.length() > index)
		{
		    outputTrans.add("//" + tagCorrenteXPath + pos + xpath.substring(index));
		}
		else
		{
		    outputTrans.add("//" + tagCorrenteXPath + pos);
		}

	    }
	}
	return outputTrans;
    }

    /*
     * Fourth transformation: add "//*" at the top removing double slash
     */
    public static List<String> transfAddLevel(String xpath)
    {
	xpath = "//*" + xpath.substring(1);
	List<String> res = new LinkedList<String>();
	res.add(xpath);
	return res;
    }

    /*
     * Sixth transformation: add a text predicate to a node
     */
	public static List<String> transfAddText(String xpath, String testo, String tagCorrente) {
		List<String> outputTrans = new LinkedList<String>();

		if (!tagCorrente.contains("body")) {
			int index;
			if (testo != null && !testo.isEmpty()) {
				String tagCorrenteXPath;
				if (xpath.startsWith("//*")) {
					index = 3;
					tagCorrenteXPath = "*";
				} else {
					index = tagCorrente.length() + 2;
					tagCorrenteXPath = tagCorrente;
				}

				if (xpath.length() == index || xpath.charAt(index) == '/' || xpath.charAt(index + 1) == '@') {
					String quoteWrappedText;

					if (testo.contains("'") && !testo.contains("\"")) {
						// Use double quotes
						quoteWrappedText = "[contains(text(),\"" + testo + "\")]";
					} else if (testo.contains("'") && testo.contains("\"")) {
						// Use concat with split on '
						String[] parts = testo.split("'");
						StringBuilder concatBuilder = new StringBuilder("contains(text(),concat(");
						for (int i = 0; i < parts.length; i++) {
							concatBuilder.append("'").append(parts[i]).append("'");
							if (i < parts.length - 1) {
								concatBuilder.append(", \"'\", ");
							}
						}
						concatBuilder.append("))");
						quoteWrappedText = "[" + concatBuilder.toString() + "]";
					} else {
						// Use single quotes
						quoteWrappedText = "[contains(text(),'" + testo + "')]";
					}

					if (xpath.length() > index) {
						outputTrans.add("//" + tagCorrenteXPath + quoteWrappedText + xpath.substring(index));
					} else {
						outputTrans.add("//" + tagCorrenteXPath + quoteWrappedText);
					}
				}
			}
		}

		return outputTrans;
	}

	public static String getAbsolutePathFromJDOMElement(Element element){
		return XPathHelper.getAbsolutePath(element).toString();
	}

    public static ArrayList<ElementInfo> loadDataFullPath(Document pageDOM, String absoluteXPath, boolean debug, String sitedate) {

		ArrayList<ElementInfo> elementInfoFullPath = new ArrayList<ElementInfo>();

		XPathExpression<Element> xpath = XPathFactory.instance().compile(absoluteXPath, Filters.element());
		List<Element> matchedElements = xpath.evaluate(pageDOM);
		if (matchedElements.isEmpty()) {
			System.out.println("************************************************************");
			System.out.println("Il seguente XPATH ritorna 0 elementi sulla pagina target old: " + absoluteXPath);
			System.out.println("************************************************************");
		}
//	 else if (debug && matchedElements.size() > 1) {
//		Element chosen = matchedElements.get(0);
//		System.out.println("[DEBUG] XPath matched " + matchedElements.size() + " element(s): " + absoluteXPath + " on site: " + sitedate + " and chose: " + XPathHelper.getAbsolutePath(chosen).toString());
//	}

			Element currentElement = matchedElements.get(0);

			while (!absoluteXPath.equals("/html")) {
				ElementInfo currentElementInfo = new ElementInfo();

				currentElementInfo.tagName = currentElement.getName();
				currentElementInfo.position = Utils.getPosition(currentElement, currentElement.getParent().getContent());
				currentElementInfo.positionStar = Utils.getPositionStar(currentElement, currentElement.getParent().getContent());

				ArrayList<String> attributes = new ArrayList<String>();
				// create the elementsAttributesMap
				if (currentElement.hasAttributes()) {
					List<Attribute> currentElementAttributesList = currentElement.getAttributes();
					{
						for (Attribute atr : currentElementAttributesList) {
							if (!attributiBlackList.contains(atr.getName())) {
								if (atr.getValue().contains("\""))
									attributes.add(atr.getName() + "=\'" + atr.getValue() + "\'");
								else
									attributes.add(atr.getName() + "=\"" + atr.getValue() + "\"");
							}
						}
					}
				}

				for (String attributo : ordineAttributi) {
					String currentAttrValue = "";
					for (String attrValue : attributes) {
						if (attrValue.startsWith(attributo + "=")) {
							currentAttrValue = attrValue;
						}
					}

					if (!currentAttrValue.equals("")) {
						currentElementInfo.attributes.add(currentAttrValue);
						attributes.remove(attributes.indexOf(currentAttrValue));
					}

				}

				currentElementInfo.attributes.addAll(attributes);

				// create the elementsTextMap
				if (!currentElement.getTextTrim().isEmpty()) {
					// currentElementInfo.text = currentElement.getTextNormalize().trim();
					currentElementInfo.text = currentElement.getTextTrim();
				}

				elementInfoFullPath.add(currentElementInfo);
				absoluteXPath = XPathHelper.getAbsolutePath(currentElement.getParentElement()).toString();
				currentElement = currentElement.getParentElement();
			}

			return elementInfoFullPath;
		}

    public static void createSet(ArrayList<ElementInfo> elementInfoFullPath)
    {

	for (ElementInfo element : elementInfoFullPath)
	{

	    List<String> attributes = new ArrayList<String>(element.attributes);

	    Set<String> attributesForPowerSet = new LinkedHashSet<String>();

	    for (String attributo : ordineAttributi)
	    {
		String currentAttrValue = "";
		for (String attrValue : attributes)
		{
		    if (attrValue.startsWith(attributo + "="))
		    {
			currentAttrValue = attrValue;
		    }
		}

		if (!currentAttrValue.equals(""))
		{
		    attributesForPowerSet.add(currentAttrValue);
		    attributes.add(attributes.remove(attributes.indexOf(currentAttrValue)));
		}

	    }

	    // Set<String> attributes = new LinkedHashSet<String>(element.attributes);
	    element.setAttributes = powerSet(attributesForPowerSet);
	}

    }

    public static <T> LinkedHashSet<Set<T>> powerSet(Set<T> originalSet)
    {
	LinkedHashSet<Set<T>> sets = new LinkedHashSet<Set<T>>();
	if (originalSet.isEmpty())
	{
	    sets.add(new LinkedHashSet<T>());
	    return sets;
	}
	List<T> list = new ArrayList<T>(originalSet);
	T head = list.get(0);
	Set<T> rest = new LinkedHashSet<T>(list.subList(1, list.size()));
	for (Set<T> set : powerSet(rest))
	{
	    Set<T> newSet = new LinkedHashSet<T>();
	    newSet.add(head);
	    newSet.addAll(set);
	    sets.add(newSet);
	    sets.add(set);
	}
	return sets;

    }

    public static void orderSet(ArrayList<ElementInfo> elementInfoFullPath)
    {

	for (ElementInfo element : elementInfoFullPath)
	{
	    element.setAttributes = orderPowerSet(element.setAttributes);
	}

    }

    static <T> LinkedHashSet<Set<T>> orderPowerSet(Set<Set<T>> setsInput)
    {
	LinkedHashSet<Set<T>> setsOutput = new LinkedHashSet<Set<T>>();

	while (!setsInput.isEmpty())
	{
	    int minSize = Integer.MAX_VALUE;
	    Set<T> aux = null;
	    for (Set<T> set : setsInput)
	    {
		if (set.size() <= minSize)
		{
		    aux = set;
		    minSize = set.size();
		}
	    }

	    setsOutput.add(aux);
	    setsInput.remove(aux);
	}
	return setsOutput;
    }
}