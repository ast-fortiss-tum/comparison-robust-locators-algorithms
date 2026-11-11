function attribute_extractor(element) {
    const items = {};
    for (let index = 0; index < element.attributes.length; ++index) {
        items[element.attributes[index].name] = element.attributes[index].value
    }
    return items;
}

function css_attribute_extractor(element) {
    const computedStyles = window.getComputedStyle(element);
    const styles = {};
    for (let i = 0; i < computedStyles.length; i++) {
        const prop = computedStyles[i];
        styles[prop] = computedStyles.getPropertyValue(prop);
    }
    return styles;
}

function css_selector_extractor(element) {
    if (!(element instanceof Element)) return;

    if (element.id) return '#' + cssEscape(element.id);

    const path = [];
    while (element && element.nodeType === Node.ELEMENT_NODE) {
        let selector = element.nodeName.toLowerCase();

        if (element.className && typeof element.className === 'string') {
            const classes = element.className.split(/\s+/).filter(Boolean);
            selector += classes.length ? '.' + classes.map(name => cssEscape(name)).join('.') : '';
        }

        let nth = 1, hasSameTypeSib = false;
        let sib = element.previousElementSibling;
        while (true) {
            if (!sib) break; // Exit loop if sib is null or undefined

            if (sib.nodeName.toLowerCase() === selector) {
                nth++;
                hasSameTypeSib = true;
            }

            sib = sib.previousElementSibling;
        }

        if (hasSameTypeSib) {
            selector += ':nth-of-type(' + nth + ')';
        }

        path.unshift(selector);
        element = element.parentNode;
    }

    return path.join(' > ');
}

function cssEscape(value) {
    return String(value).replace(/[!"#$%&'()*+,./:;<=>?@[\\]^{|}~]/g, '\\$&');
}

function direct_text_extractor(element) {
    let node = arguments[0].firstChild;
    while (node) {
        if (node.nodeType === 3) {  // Check if it's a text node
            return node.nodeValue.trim();
        }
        node = node.nextSibling;
    }
    return null;  // Return null if no direct text found
}

/**
 * Calculate the index of the current element among its siblings of the same tag name.
 * @param {Element} element - The current element.
 * @returns {number} The index of the current element among its siblings.
 */
function getElementIndex(element) {
    let count = 0;
    let sibling = element;
    while (sibling) {
        if (sibling.localName === element.localName) count++;
        sibling = sibling.previousElementSibling;
    }
    return count;
}

/**
 * Determine if we should append an index to the element in its XPath segment.
 * @param {Element} element - The DOM element to check.
 * @returns {boolean} True if the element should have an index appended, false otherwise.
 */
function shouldAppendIndex(element) {
    const siblings = element.parentElement ? Array.from(element.parentElement.children) : [];
    const similarSiblings = siblings.filter(el => el.localName === element.localName).length;
    return similarSiblings > 1;
}

/**
 * Generate the XPath segments for the given element, considering its hierarchy.
 * @param {Element} element - The element for which the XPath segment is being generated.
 * @param {boolean} cleanIndexed  - True if the XPath should contain indexes for all elements, false otherwise.
 * @returns {Array} An array of XPath segments representing the hierarchy of the element.
 */
function generateXpathSegments(element, cleanIndexed) {
    // Return empty array for invalid nodes
    if (!element || element.nodeType !== 1) {
        return [];
    }

    let index;
    if (cleanIndexed) {
        index = shouldAppendIndex(element) ? `[${getElementIndex(element)}]` : '';
    } else {
        index = `[${getElementIndex(element)}]`;
    }

    const currentSegment = element instanceof HTMLElement
        ? `${element.localName}${index}`
        : `*[local-name()="${element.localName}"]${index}`;

    const parentElement = element.parentElement instanceof Element ? element.parentElement : null;
    return [...generateXpathSegments(parentElement, cleanIndexed), currentSegment];
}

/**
 * Extracts the XPath of the given element.
 * @param {Element} element - The DOM element for which the XPath is needed.
 * @returns {string} The XPath of the element.
 */
function xpath_chrome_extractor(element) {
    return "/" + generateXpathSegments(element, true).join('/');
}

/**
 * Extracts the XPath of the given element.
 * @param element - The DOM element for which the XPath is needed.
 * @returns {string} The XPath of the element.
 */
function xpath_selenium_extractor(element) {
    return "/" + generateXpathSegments(element, false).join('/');
}

function isUniqueID(element) {
    return element.id && document.getElementById(element.id) === element;
}

function generateIdXpathSegments(element) {
    if (!element || element.nodeType !== 1) {
        return [];
    }

    if (isUniqueID(element)) {
        return [`//*[@id='${element.id}']`];
    }

    const appendIndex = shouldAppendIndex(element);
    const index = appendIndex ? `[${getElementIndex(element)}]` : '';

    const xpathSegment = element instanceof HTMLElement
        ? `${element.localName}${index}`
        : `*[local-name()="${element.localName}"]${index}`;

    const parentSegments = generateIdXpathSegments(element.parentNode);
    parentSegments.push(xpathSegment);
    return parentSegments;
}

function id_xpath_extractor(element) {
    const segments = generateIdXpathSegments(element);
    const xpath = '/' + segments.join('/');
    return segments.length > 0 ? xpath.replaceAll(/\/\/\/+/g, '//') : null;
}

function element_extractor(element) {
    const rect = element.getBoundingClientRect();

    return {
        // Basic Propertiesgoo
        tag: element.tagName,
        innerText: element.innerText || null,
        textContent: element.textContent || null,
        nodeText: direct_text_extractor(element) || null,
        childrenLength: element.children.length,

        // Identification Properties
        xpath: {
            chrome: xpath_chrome_extractor(element),
            selenium: xpath_selenium_extractor(element),
            idxpath: id_xpath_extractor(element)
        },
        url: window.location.href,
        cssSelector: css_selector_extractor(element),

        // Position & Size
        location: [Math.round(rect.x), Math.round(rect.y)],
        dimension: [Math.round(rect.width), Math.round(rect.height)],

        // Attributes & Styles
        attributes: attribute_extractor(element),
        cssStyles: css_attribute_extractor(element)
    };
}

function element_extractor_string(element) {
    return JSON.stringify(element_extractor(element));
}

function getElementByXPath(xPath, document) {
        const elem = document.evaluate(xPath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
        return elem;
    }

