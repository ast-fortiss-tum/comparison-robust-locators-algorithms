package de.tum.ls4.generators;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test helper: an in-memory XML DOM standing in for the browser (same XPath 1.0 semantics). */
final class W3cDom {

    private final Document document;

    private W3cDom(Document document) {
        this.document = document;
    }

    static W3cDom parse(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            return new W3cDom(f.newDocumentBuilder().parse(new InputSource(new StringReader(xml))));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    Element find(String xpath) {
        NodeList nodes = select(xpath);
        if (nodes.getLength() != 1) {
            throw new IllegalArgumentException(xpath + " selects " + nodes.getLength() + " elements");
        }
        return (Element) nodes.item(0);
    }

    NodeList select(String xpath) {
        try {
            return (NodeList) XPathFactory.newInstance().newXPath().evaluate(xpath, document, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException(xpath, e);
        }
    }

    /** Mirrors generators/ancestry.js. */
    Ancestry ancestry(Element element) {
        List<AncestorNode> nodes = new ArrayList<>();
        for (Node n = element; n instanceof Element el; n = el.getParentNode()) {
            int position = 1;
            int positionStar = 1;
            for (Node s = el.getPreviousSibling(); s != null; s = s.getPreviousSibling()) {
                if (s instanceof Element se) {
                    positionStar++;
                    if (se.getTagName().equals(el.getTagName())) position++;
                }
            }
            Map<String, String> attributes = new LinkedHashMap<>();
            NamedNodeMap attrs = el.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                attributes.put(attrs.item(i).getNodeName(), attrs.item(i).getNodeValue());
            }
            String text = null;
            for (Node c = el.getFirstChild(); c != null; c = c.getNextSibling()) {
                if (c.getNodeType() == Node.TEXT_NODE || c.getNodeType() == Node.CDATA_SECTION_NODE) {
                    text = c.getNodeValue();
                    break;
                }
            }
            nodes.add(new AncestorNode(el.getTagName(), position, positionStar, attributes, text));
        }
        return new Ancestry(nodes);
    }

    XPathOracle oracle(Element target) {
        return xpaths -> {
            List<XPathOracle.Match> out = new ArrayList<>();
            for (String xpath : xpaths) {
                NodeList nodes = select(xpath);
                boolean contains = false;
                for (int i = 0; i < nodes.getLength(); i++) {
                    contains |= nodes.item(i) == target;
                }
                out.add(new XPathOracle.Match(nodes.getLength(), contains));
            }
            return out;
        };
    }
}
