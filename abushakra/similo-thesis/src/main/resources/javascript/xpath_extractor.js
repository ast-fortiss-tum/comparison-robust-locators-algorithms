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
 * Generate a robust XPath of the given element using Robula+.
 * @param {Element} element - The DOM element for which the XPath is needed.
 * @returns {string} The XPath of the element.
 */

function xpath_robulaplus_extractor(element) {

    let robula = new RobulaPlus();
    return robula.getRobustXPath(element, document);
}

/**
 * Extracts the XPath of the given element.
 * @param element - The DOM element for which the XPath is needed.
 * @returns {string} The XPath of the element.
 */
function xpath_selenium_extractor(element) {
    return "/" + generateXpathSegments(element, false).join('/');
}

/**
 * Attempts to generate a Montoto-XPath expression for a given element.
 * @param {Element} element
 * @returns {string} A robust XPath expression
 */
function xpath_montoto_extractor(element) {
    function getAttrPredicates(el) {
        const attrs = Array.from(el.attributes || []);
        const blacklist = ['style', 'href', 'src', 'onclick', 'onload', 'xmlns', 'xmlns:xlink', 'tile?apikey', 'data-galink',
            // namespace declarations
            'xmlns', 'xmlns:xlink',

            // rich media and lazy loading
            'srcset', 'data-src', 'data-srcset', 'data-original', 'data-galink', 'data-ga-action',

            // known tracking and analytics attributes
            'data-analytics', 'data-tracking', 'data-testid', 'data-widget',

            // anything suspiciously complex
            'tile?apikey', 'integrity', 'crossorigin', 'referrerpolicy', 'ping'];
        const predicates = [];

        for (const attr of attrs) {
            if (!blacklist.includes(attr.name) && attr.value.trim()) {
                predicates.push(`@${attr.name}=${quoteAttributeValue(attr.value.trim())}`);
            }
        }

        return predicates;
    }

    function getTextPredicate(el) {
        const rawText = el.textContent?.replace(/\s+/g, ' ').trim();
        if (rawText && rawText.length > 1) {
            if (rawText.includes("'") && rawText.includes('"')) {
                const parts = rawText.split(/('|")/).map(p =>
                    p === "'" ? `"'"` : p === `"` ? `'\"'` : `'${p}'`
                );
                return `contains(normalize-space(.), concat(${parts.join(",")}))`;
            } else if (rawText.includes("'")) {
                return `contains(normalize-space(.), "${rawText}")`;
            } else {
                return `contains(normalize-space(.), '${rawText}')`;
            }
        }
        return null;
    }

    function buildXPathChain(el) {
        const segments = [];

        while (el && el.nodeType === Node.ELEMENT_NODE) {
            const tag = el.tagName.toLowerCase();
            const predicates = [];

            const textPred = getTextPredicate(el);
            if (textPred) predicates.push(textPred);

            predicates.push(...getAttrPredicates(el));

            const predStr = predicates.length > 0 ? `[${predicates.join(' and ')}]` : '';
            segments.unshift(`${tag}${predStr}`);

            const xpath = '//' + segments.join('/');
            const found = document.evaluate(xpath, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
            if (found.snapshotLength === 1 && found.snapshotItem(0) === element) {
                return xpath;
            }

            el = el.parentElement;
        }

        // Fallback
        return xpath_chrome_extractor(element);
    }

    return buildXPathChain(element);
}

function xpath_selenium_ide_extractor(el) {
    function isUniqueAttr(attrName, value) {
        return document.querySelectorAll(`[${attrName}="${CSS.escape(value)}"]`).length === 1;
    }

    const tag = el.tagName.toLowerCase();

    // Strategy 1: ID
    if (el.id && isUniqueAttr('id', el.id)) {
        return `//*[@id='${el.id}']`;
    }

    // Strategy 2: Common unique attributes
    const attrs = ['name', 'type', 'role', 'title', 'aria-label', 'placeholder'];
    for (const attr of attrs) {
        const value = el.getAttribute(attr);
        if (value && isUniqueAttr(attr, value)) {
            return `//${tag}[@${attr}='${value}']`;
        }
    }

    // Strategy 3: Text content (only if element has direct text)
    const rawText = el.textContent || '';
    const normalizedText = rawText.replace(/\s+/g, ' ').trim();
    const snippet = normalizedText.length > 50 ? normalizedText.slice(0, 50) : normalizedText;

    if (snippet && isUniqueText(tag, snippet)) {
        return `//${tag}[contains(normalize-space(text()), ${quoteAttributeValue(snippet)})]`;
    }

    // Strategy 4: Class name fallback (only if simple class)
    const cls = el.getAttribute('class');
    if (cls && cls.split(/\s+/).length === 1 && isUniqueAttr('class', cls)) {
        return `//${tag}[@class='${cls}']`;
    }

    // Strategy 5: Fallback: tag with index among siblings
    let index = 1;
    let sibling = el.previousElementSibling;
    while (sibling) {
        if (sibling.tagName === el.tagName) index++;
        sibling = sibling.previousElementSibling;
    }

    return `//${tag}[${index}]`;

    function isUniqueText(tag, text) {
        const xpath = `//${tag}[normalize-space(text())=${quoteAttributeValue(text)}]`;
        const result = document.evaluate(xpath, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
        return result.snapshotLength === 1;
    }
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

"use strict";

function quoteAttributeValue(value) {
    if (value.includes("'") && value.includes('"')) {
        return 'concat(' + value.split(/('|")/).map(part => {
            if (part === "'") return '"\'"';
            if (part === '"') return "'\"'";
            return `'${part}'`;
        }).join(',') + ')';
    } else if (value.includes("'")) {
        return `"${value}"`;
    } else {
        return `'${value}'`;
    }
}

class RobulaPlus {
    constructor(options) {
        this.attributePriorizationList = ['name', 'class', 'title', 'alt', 'value'];
        this.attributeBlackList = ['href', 'src', 'onclick', 'onload', 'tabindex', 'width', 'height', 'style', 'size', 'maxlength',
            // namespace declarations
            'xmlns', 'xmlns:xlink',

            // rich media and lazy loading
            'srcset', 'data-src', 'data-srcset', 'data-original', 'data-galink', 'data-ga-action',

            // known tracking and analytics attributes
            'data-analytics', 'data-tracking', 'data-testid', 'data-widget',

            // anything suspiciously complex
            'tile?apikey', 'integrity', 'crossorigin', 'referrerpolicy', 'ping'];
        if (options) {
            this.attributePriorizationList = options.attributePriorizationList;
            this.attributeBlackList = options.attributeBlackList;
        }
    }

    getRobustXPath(element, document) {
        if (!element || !document.body.contains(element)) {
            const docURL = document.location?.href || 'unknown';
            const elemDocURL = element?.ownerDocument?.location?.href || 'unknown';
            const elemInfo = element ? element.tagName + (element.id ? `#${element.id}` : '') : 'null';

            throw new Error(`Document does not contain the given element!\n` +
                `Element: ${elemInfo}\n` +
                `Element Document URL: ${elemDocURL}\n` +
                `Current Document URL: ${docURL}`);
        }
        const xPathList = [new XPath('//*')];
        while (xPathList.length > 0) {
            const xPath = xPathList.shift();
            let temp = [];
            temp = temp.concat(this.transfConvertStar(xPath, element));
            temp = temp.concat(this.transfAddId(xPath, element));
            temp = temp.concat(this.transfAddText(xPath, element));
            temp = temp.concat(this.transfAddAttribute(xPath, element));
            temp = temp.concat(this.transfAddAttributeSet(xPath, element));
            temp = temp.concat(this.transfAddPosition(xPath, element));
            temp = temp.concat(this.transfAddLevel(xPath, element));
            temp = [...new Set(temp)];
            for (const x of temp) {
                if (this.uniquelyLocate(x.getValue(), element, document)) {
                    return x.getValue();
                }
                xPathList.push(x);
            }
        }
        throw new Error('Internal Error: xPathList.shift returns undefined');
    }

    getElementByXPath(xPath, document) {
        return document.evaluate(xPath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
    }

    uniquelyLocate(xPath, element, document) {
        const nodesSnapshot = document.evaluate(xPath, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
        return nodesSnapshot.snapshotLength === 1 && nodesSnapshot.snapshotItem(0) === element;
    }

    transfConvertStar(xPath, element) {
        const output = [];
        const ancestor = this.getAncestor(element, xPath.getLength() - 1);
        if (xPath.startsWith('//*')) {
            output.push(new XPath('//' + ancestor.tagName.toLowerCase() + xPath.substring(3)));
        }
        return output;
    }

    transfAddId(xPath, element) {
        const output = [];
        const ancestor = this.getAncestor(element, xPath.getLength() - 1);
        if (ancestor.id && !xPath.headHasAnyPredicates()) {
            const newXPath = new XPath(xPath.getValue());
            const idVal = quoteAttributeValue(ancestor.id);
            newXPath.addPredicateToHead(`[@id=${idVal}]`);
            output.push(newXPath);
        }
        return output;
    }

    transfAddText(xPath, element) {
        const output = [];
        const ancestor = this.getAncestor(element, xPath.getLength() - 1);
        const text = ancestor.textContent?.trim();
        if (text && !xPath.headHasPositionPredicate() && !xPath.headHasTextPredicate()) {
            let predicate;
            if (text.includes("'") && text.includes('"')) {
                const parts = text.split(/('|")/).map(p =>
                    p === "'" ? `"'"` : p === `"` ? `'\"'` : `'${p}'`
                );
                predicate = `[contains(normalize-space(.), concat(${parts.join(",")}))]`;
            } else if (text.includes("'")) {
                predicate = `[contains(normalize-space(.),"${text}")]`;
            } else {
                predicate = `[contains(normalize-space(.),'${text}')]`;
            }

            const newXPath = new XPath(xPath.getValue());
            newXPath.addPredicateToHead(predicate);
            output.push(newXPath);
        }
        return output;
    }

    transfAddAttribute(xPath, element) {
        const output = [];
        const ancestor = this.getAncestor(element, xPath.getLength() - 1);
        if (!xPath.headHasAnyPredicates()) {
            for (const priorityAttribute of this.attributePriorizationList) {
                for (const attribute of ancestor.attributes) {
                    if (attribute.name === priorityAttribute) {
                        const newXPath = new XPath(xPath.getValue());
                        newXPath.addPredicateToHead(`[@${attribute.name}=${quoteAttributeValue(attribute.value)}]`);
                        output.push(newXPath);
                        break;
                    }
                }
            }
            for (const attribute of ancestor.attributes) {
                if (!this.attributeBlackList.includes(attribute.name) &&
                    !this.attributePriorizationList.includes(attribute.name)) {
                    const newXPath = new XPath(xPath.getValue());
                    newXPath.addPredicateToHead(`[@${attribute.name}=${quoteAttributeValue(attribute.value)}]`);
                    output.push(newXPath);
                }
            }
        }
        return output;
    }

    transfAddAttributeSet(xPath, element) {
        const output = [];
        const ancestor = this.getAncestor(element, xPath.getLength() - 1);
        if (!xPath.headHasAnyPredicates()) {
            this.attributePriorizationList.unshift('id');
            let attributes = [...ancestor.attributes];
            attributes = attributes.filter(attribute => !this.attributeBlackList.includes(attribute.name));
            let attributePowerSet = this.generatePowerSet(attributes).filter(set => set.length >= 2);
            for (const attributeSet of attributePowerSet) {
                attributeSet.sort(this.elementCompareFunction.bind(this));
            }
            attributePowerSet.sort((set1, set2) => {
                if (set1.length < set2.length) return -1;
                if (set1.length > set2.length) return 1;
                for (let i = 0; i < set1.length; i++) {
                    if (set1[i] !== set2[i]) {
                        return this.elementCompareFunction(set1[i], set2[i]);
                    }
                }
                return 0;
            });
            this.attributePriorizationList.shift();
            for (const attributeSet of attributePowerSet) {
                let predicate = `[@${attributeSet[0].name}=${quoteAttributeValue(attributeSet[0].value)}`;
                for (let i = 1; i < attributeSet.length; i++) {
                    predicate += ` and @${attributeSet[i].name}=${quoteAttributeValue(attributeSet[i].value)}`;
                }
                predicate += ']';
                const newXPath = new XPath(xPath.getValue());
                newXPath.addPredicateToHead(predicate);
                output.push(newXPath);
            }
        }
        return output;
    }

    transfAddPosition(xPath, element) {
        const output = [];
        const ancestor = this.getAncestor(element, xPath.getLength() - 1);
        if (!xPath.headHasPositionPredicate()) {
            let position = 1;
            if (xPath.startsWith('//*')) {
                position = Array.from(ancestor.parentNode.children).indexOf(ancestor) + 1;
            } else {
                for (const child of ancestor.parentNode.children) {
                    if (ancestor === child) break;
                    if (ancestor.tagName === child.tagName) position++;
                }
            }
            const newXPath = new XPath(xPath.getValue());
            newXPath.addPredicateToHead(`[${position}]`);
            output.push(newXPath);
        }
        return output;
    }

    transfAddLevel(xPath, element) {
        const output = [];
        if (xPath.getLength() - 1 < this.getAncestorCount(element)) {
            output.push(new XPath('//*' + xPath.substring(1)));
        }
        return output;
    }

    generatePowerSet(input) {
        return input.reduce((subsets, value) => subsets.concat(subsets.map(set => [value, ...set])), [[]]);
    }

    elementCompareFunction(attr1, attr2) {
        for (const element of this.attributePriorizationList) {
            if (element === attr1.name) return -1;
            if (element === attr2.name) return 1;
        }
        return 0;
    }

    getAncestor(element, index) {
        let output = element;
        for (let i = 0; i < index; i++) {
            if (output && output.parentElement) {
                output = output.parentElement;            }
        }
        return output;
    }

    getAncestorCount(element) {
        let count = 0;
        while (element && element.parentElement) {
            element = element.parentElement;
            count++;
        }
        return count;
    }
}

class XPath {
    constructor(value) {
        this.value = value;
    }

    getValue() {
        return this.value;
    }

    startsWith(value) {
        return this.value.startsWith(value);
    }

    substring(value) {
        return this.value.substring(value);
    }

    headHasAnyPredicates() {
        return this.value.split('/')[2]?.includes('[');
    }

    headHasPositionPredicate() {
        const splitXPath = this.value.split('/');
        const regExp = new RegExp('[\\[]\\d+[\\]]');
        return splitXPath[2]?.includes('position()') || splitXPath[2]?.includes('last()') || regExp.test(splitXPath[2]);
    }

    headHasTextPredicate() {
        return this.value.split('/')[2]?.includes('text()');
    }

    addPredicateToHead(predicate) {
        const splitXPath = this.value.split('/');
        splitXPath[2] += predicate;
        this.value = splitXPath.join('/');
    }

    getLength() {
        return this.value.split('/').filter(Boolean).length;
    }
}

class RobulaPlusOptions {
    constructor() {
        this.attributePriorizationList = ['name', 'class', 'title', 'alt', 'value'];
        this.attributeBlackList = ['href', 'src', 'onclick', 'onload', 'tabindex', 'width', 'height', 'style', 'size', 'maxlength',
            // namespace declarations
            'xmlns', 'xmlns:xlink',

            // rich media and lazy loading
            'srcset', 'data-src', 'data-srcset', 'data-original', 'data-galink', 'data-ga-action',

            // known tracking and analytics attributes
            'data-analytics', 'data-tracking', 'data-testid', 'data-widget',

            // anything suspiciously complex
            'tile?apikey', 'integrity', 'crossorigin', 'referrerpolicy', 'ping'];
    }
}