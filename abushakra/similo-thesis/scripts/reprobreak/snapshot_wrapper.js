// Appended to the benchmark's extraction scripts (src/main/resources/javascript): defines window.__rbSnapshot.
// Candidates are all visible elements (not only the 24 tags of elements_extractor), SVG internals excluded, so that
// every target a Cypress/Playwright locator can select is a candidate.
window.__rbSnapshot = function (targets) {
    var skip = { SCRIPT: 1, STYLE: 1, HEAD: 1, META: 1, LINK: 1, TITLE: 1, NOSCRIPT: 1, TEMPLATE: 1, HTML: 1, BR: 1, BASE: 1 };
    function inSvg(el) { return el.namespaceURI === 'http://www.w3.org/2000/svg' && el.tagName.toLowerCase() !== 'svg'; }
    var elements = [];
    document.querySelectorAll('*').forEach(function (el) {
        if (!skip[el.tagName.toUpperCase()] && !inSvg(el) && elementIsVisible(el)) elements.push(element_extractor(el));
    });
    var t = Array.prototype.map.call(targets || [], function (el) {
        var main = el.ownerDocument === document;
        return { main: main, visible: main && elementIsVisible(el), inSvg: inSvg(el),
                 element: main ? element_extractor(el) : null };
    });
    var dt = document.doctype ? '<!DOCTYPE ' + document.doctype.name + '>' : '';
    return { url: location.href, viewport: [innerWidth, innerHeight], scroll: [Math.round(scrollX), Math.round(scrollY)],
             dom: dt + document.documentElement.outerHTML, elements: elements, targets: t };
};
