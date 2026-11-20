package de.tum.ls4.generators;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RobulaPlusTest {

    private static String generate(String xml, String targetXPath) {
        W3cDom dom = W3cDom.parse(xml);
        Element target = dom.find(targetXPath);
        GeneratedLocator result = new RobulaPlus().generate(dom.ancestry(target), dom.oracle(target));
        assertFalse(result.fallback());
        assertEquals(1, dom.select(result.xpath()).getLength(), result.xpath());
        assertEquals(target, dom.select(result.xpath()).item(0), result.xpath());
        return result.xpath();
    }

    @Test
    void prefersIdOverText() {
        assertEquals("//*[@id='login']", generate(
                "<html><body><div><a id='login' href='/x'>Login</a><a href='/y'>Login</a></div></body></html>",
                "//a[@id='login']"));
    }

    @Test
    void usesTextWhenThereIsNoId() {
        assertEquals("//*[contains(text(),'Login')]", generate(
                "<html><body><div><a href='/x'>Help</a><a href='/y'>Login</a></div></body></html>",
                "//a[2]"));
    }

    @Test
    void neverUsesBlacklistedAttributes() {
        // Only href distinguishes the links: ROBULA+ must fall back to structure
        String xpath = generate(
                "<html><body><div><a href='/x'>Go</a><a href='/y'>Go</a></div></body></html>", "//a[2]");
        assertFalse(xpath.contains("@href"), xpath);
        assertEquals("//*[2]", xpath);
    }

    @Test
    void addsLevelsToDisambiguate() {
        // breadth-first: //*/* is expanded before any 2-level text variant, and its child is already unique
        assertEquals("//*[@class='menu']/*", generate(
                "<html><body><ul class='menu'><li>Home</li></ul><ul class='footer'><li>Home</li></ul></body></html>",
                "//ul[@class='menu']/li"));
    }

    @Test
    void quotesLiteralsWithBothQuoteKinds() {
        assertEquals("'a'", XPathLiteral.quote("a"));
        assertEquals("\"it's\"", XPathLiteral.quote("it's"));
        assertEquals("concat('say \"it', \"'\", 's\"')", XPathLiteral.quote("say \"it's\""));
        String xpath = generate("<html><body><p>say \"it's\"</p><p>other</p></body></html>", "//p[1]");
        assertEquals("//*[contains(text(),concat('say \"it', \"'\", 's\"'))]", xpath);
    }
}
