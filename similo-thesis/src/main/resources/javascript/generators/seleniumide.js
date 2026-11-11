/*
 * Selenium IDE locator builders, ported from SeleniumHQ/selenium-ide (trunk,
 * packages/selenium-ide/src/browser/windows/PlaybackWindow/preload/locator-builders.ts).
 * Copyright 2005 Shinya Kasatani. Licensed under the Apache License, Version 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0). Changes: TypeScript removed, builders kept in the original priority
 * order, and findElement re-implemented with the semantics of the WebDriver atoms Selenium IDE uses
 * (id, linkText, name, css, xpath). Requires vendor/finder.js (css:finder).
 */
const SIDE_BUILDERS = [];

function sideAttributeValue(value) {
    if (value.indexOf("'") < 0) return "'" + value + "'";
    if (value.indexOf('"') < 0) return '"' + value + '"';
    let result = 'concat(';
    while (true) {
        const apos = value.indexOf("'");
        const quot = value.indexOf('"');
        if (apos < 0) { result += "'" + value + "'"; break; }
        else if (quot < 0) { result += '"' + value + '"'; break; }
        else if (quot < apos) { const part = value.substring(0, apos); result += "'" + part + "'"; value = value.substring(part.length); }
        else { const part = value.substring(0, quot); result += '"' + part + '"'; value = value.substring(part.length); }
        result += ',';
    }
    return result + ')';
}

function sideFindElement(locator) {
    try {
        const i = locator.indexOf('=');
        const type = locator.substring(0, i), value = locator.substring(i + 1);
        switch (type) {
            case 'id': return document.getElementById(value);
            case 'name': return document.querySelector('[name=' + JSON.stringify(value) + ']');
            case 'css': return document.querySelector(value);
            case 'linkText':
                for (const a of document.getElementsByTagName('a')) {
                    if ((a.innerText || '').replace(/\xA0/g, ' ').trim() === value) return a;
                }
                return null;
            case 'xpath':
                return document.evaluate(value, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
            default: return null;
        }
    } catch (e) {
        return null;
    }
}

function sideGetNodeNbr(current) {
    const childNodes = (current.parentNode && current.parentNode.childNodes) || [];
    let total = 0, index = -1;
    for (let i = 0; i < childNodes.length; i++) {
        const child = childNodes[i];
        if (child.nodeName == current.nodeName) {
            if (child == current) index = total;
            total++;
        }
    }
    return index;
}

function sideRelativeXPathFromParent(current) {
    const index = sideGetNodeNbr(current);
    let currentPath = '/' + current.nodeName.toLowerCase();
    if (index > 0) currentPath += '[' + (index + 1) + ']';
    return currentPath;
}

function sidePreciseXPath(xpath, e) {
    if (sideFindElement('xpath=' + xpath) != e) {
        const result = e.ownerDocument.evaluate(xpath, e.ownerDocument, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
        for (let i = 0, len = result.snapshotLength; i < len; i++) {
            const newPath = 'xpath=(' + xpath + ')[' + (i + 1) + ']';
            if (sideFindElement(newPath) == e) return newPath;
        }
    }
    return 'xpath=' + xpath;
}

// order listed dictates priority
SIDE_BUILDERS.push(['css:data-test-id', function (e) {
    for (const attr of ['data-test-id', 'data-test']) {
        const value = e.getAttribute(attr);
        if (value) return `css=[${attr}="${value}"]`;
    }
    return null;
}]);
SIDE_BUILDERS.push(['id', e => e.id ? 'id=' + e.id : null]);
SIDE_BUILDERS.push(['linkText', function (e) {
    if (e.nodeName == 'A') {
        const text = e.textContent || '';
        if (!text.match(/^\s*$/)) return 'linkText=' + text.replace(/\xA0/g, ' ').replace(/^\s*(.*?)\s*$/, '$1');
    }
    return null;
}]);
SIDE_BUILDERS.push(['name', e => e.name ? 'name=' + e.name : null]);
SIDE_BUILDERS.push(['css:data-attr', function (e) {
    const dataKeys = Object.keys(e.dataset || {});
    if (dataKeys.length) {
        const attr = dataKeys[0];
        const value = e.dataset[attr];
        const htmlAttr = 'data-' + attr.replace(/[A-Z]/g, l => '-' + l.toLowerCase());
        if (!value || value === 'true') return `css=[${htmlAttr}]`;
        return `css=[${htmlAttr}="${value}"]`;
    }
    return null;
}]);
SIDE_BUILDERS.push(['css:finder', e => 'css=' + finder(e)]);
SIDE_BUILDERS.push(['xpath:link', function (e) {
    if (e.nodeName == 'A') {
        const text = e.textContent || '';
        if (!text.match(/^\s*$/)) return sidePreciseXPath("//a[contains(text(),'" + text.replace(/^\s+/, '').replace(/\s+$/, '') + "')]", e);
    }
    return null;
}]);
SIDE_BUILDERS.push(['xpath:img', function (e) {
    if (e.nodeName == 'IMG') {
        if (e.alt != '') return sidePreciseXPath('//img[@alt=' + sideAttributeValue(e.alt) + ']', e);
        else if (e.title != '') return sidePreciseXPath('//img[@title=' + sideAttributeValue(e.title) + ']', e);
        else if (e.src != '') return sidePreciseXPath('//img[contains(@src,' + sideAttributeValue(e.src) + ')]', e);
    }
    return null;
}]);
SIDE_BUILDERS.push(['xpath:attributes', function (e) {
    const PREFERRED_ATTRIBUTES = ['id', 'name', 'value', 'type', 'action', 'onclick'];
    const attributesXPath = (name, attNames, attributes) => {
        let locator = '//' + name + '[';
        for (let i = 0; i < attNames.length; i++) {
            if (i > 0) locator += ' and ';
            const attName = attNames[i];
            locator += '@' + attName + '=' + sideAttributeValue(attributes[attName]);
            if (e.textContent && attName == 'type') locator += ' and text()=' + sideAttributeValue(e.textContent);
        }
        return sidePreciseXPath(locator + ']', e);
    };
    if (e.attributes) {
        const attsMap = {};
        for (const att of e.attributes) attsMap[att.name] = att.value;
        const names = [];
        for (const name of PREFERRED_ATTRIBUTES) {
            if (attsMap[name] != null) {
                names.push(name);
                const locator = attributesXPath(e.nodeName.toLowerCase(), names, attsMap);
                if (e == sideFindElement(locator)) return locator;
            }
        }
    }
    return null;
}]);
SIDE_BUILDERS.push(['xpath:idRelative', function (e) {
    let path = '';
    let current = e;
    while (current != null) {
        if (current.parentNode != null) {
            path = sideRelativeXPathFromParent(current) + path;
            const parentNode = current.parentNode;
            if (1 == parentNode.nodeType && parentNode.getAttribute('id')) {
                return sidePreciseXPath('//' + parentNode.nodeName.toLowerCase() + '[@id=' +
                    sideAttributeValue(parentNode.getAttribute('id')) + ']' + path, e);
            }
        } else {
            return null;
        }
        current = current.parentNode;
    }
    return null;
}]);
SIDE_BUILDERS.push(['xpath:href', function (e) {
    if (e.attributes && e.hasAttribute('href')) {
        const href = e.getAttribute('href');
        if (href.search(/^http?:\/\//) >= 0) return sidePreciseXPath('//a[@href=' + sideAttributeValue(href) + ']', e);
        return sidePreciseXPath('//a[contains(@href, ' + sideAttributeValue(href) + ')]', e);
    }
    return null;
}]);
SIDE_BUILDERS.push(['xpath:position', function (e) {
    let path = '';
    let current = e;
    while (current != null) {
        const currentPath = current.parentNode != null ? sideRelativeXPathFromParent(current) : '/' + current.nodeName.toLowerCase();
        path = currentPath + path;
        const locator = '/' + path;
        if (e == sideFindElement('xpath=' + locator)) return 'xpath=' + locator;
        current = current.parentNode;
    }
    return null;
}]);
SIDE_BUILDERS.push(['xpath:innerText', el => el.innerText ? `xpath=//${el.nodeName.toLowerCase()}[contains(.,'${el.innerText}')]` : null]);

/** All locators (in priority order) whose first match is the element: [[locator, builderName], ...]. */
function sideBuildAll(el) {
    const locators = [];
    for (const [name, builder] of SIDE_BUILDERS) {
        try {
            const locator = builder(el);
            if (locator && sideFindElement(String(locator)) == el) locators.push([String(locator), name]);
        } catch (e) {
            // like Selenium IDE: ignore failing builders
        }
    }
    return locators;
}
