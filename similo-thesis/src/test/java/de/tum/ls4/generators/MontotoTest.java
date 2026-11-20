package de.tum.ls4.generators;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MontotoTest {

    private static String generate(String xml, String targetXPath) {
        W3cDom dom = W3cDom.parse(xml);
        Element target = dom.find(targetXPath);
        GeneratedLocator result = new Montoto().generate(dom.ancestry(target), dom.oracle(target));
        assertFalse(result.fallback(), result.xpath());
        assertEquals(1, dom.select(result.xpath()).getLength(), result.xpath());
        assertEquals(target, dom.select(result.xpath()).item(0));
        return result.xpath();
    }

    @Test
    void usesTextAndAllAttributesOfTheTargetFirst() {
        assertEquals("//a[contains(text(),'Login') and @class='btn' and @href='/login']", generate(
                "<html><body><a class='btn' href='/login'>Login</a><a class='btn' href='/help'>Help</a></body></html>",
                "//a[1]"));
    }

    @Test
    void addsAncestorsWithTheirAttributesUntilUnique() {
        assertEquals("//ul[@class='menu']/li[contains(text(),'Home')]", generate(
                "<html><body><ul class='menu'><li>Home</li></ul><ul class='footer'><li>Home</li></ul></body></html>",
                "//ul[@class='menu']/li"));
    }

    @Test
    void fallsBackToTheAbsoluteXPathWhenEvenTheFullChainIsAmbiguous() {
        W3cDom dom = W3cDom.parse("<html><body><p>x</p><p>x</p></body></html>");
        Element target = dom.find("//p[2]");
        GeneratedLocator result = new Montoto().generate(dom.ancestry(target), dom.oracle(target));
        assertEquals(true, result.fallback());
        assertEquals("/html[1]/body[1]/p[2]", result.xpath());
    }
}
