/**
 * Ancestor chain of an element, from the element itself up to the document element (<html>), as used by the
 * robust XPath generators (ROBULA+, SIDEREAL).
 *
 * For every level: tag (lower-case local name, or null when the element is not in the XHTML namespace and thus
 * cannot be selected by name in an XPath over an HTML document), position among same-tag siblings, position among
 * all element siblings, attributes in DOM order and the trimmed value of the first text-node child (what XPath
 * text() returns inside contains()).
 *
 * @param {Element} element
 * @returns {Array<Object>}
 */
function extractAncestry(element) {
    const XHTML = 'http://www.w3.org/1999/xhtml';
    const chain = [];
    for (let el = element; el && el.nodeType === Node.ELEMENT_NODE; el = el.parentElement) {
        let position = 1;
        let positionStar = 1;
        for (let s = el.previousElementSibling; s; s = s.previousElementSibling) {
            positionStar++;
            if (s.localName === el.localName && s.namespaceURI === el.namespaceURI) position++;
        }
        const attributes = [];
        for (const a of el.attributes) {
            // Namespaced attributes (xlink:href, ...) cannot be addressed as @name in an HTML document
            if (!a.namespaceURI) attributes.push([a.name, a.value]);
        }
        let text = null;
        for (const child of el.childNodes) {
            if (child.nodeType === Node.TEXT_NODE || child.nodeType === Node.CDATA_SECTION_NODE) {
                text = child.nodeValue.trim() || null;
                break;
            }
        }
        chain.push({
            tag: el.namespaceURI === XHTML ? el.localName.toLowerCase() : null,
            position: position,
            positionStar: positionStar,
            attributes: attributes,
            text: text
        });
    }
    return chain;
}
