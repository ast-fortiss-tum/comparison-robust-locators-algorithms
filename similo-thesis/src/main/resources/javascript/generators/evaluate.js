/**
 * Evaluates a batch of XPath expressions on the document of the target element.
 *
 * @param {Array<string>} xpaths
 * @param {Element} target
 * @returns {Array<Array>} one [count, containsTarget] pair per expression; count is -1 for an invalid expression.
 */
function evaluateXPaths(xpaths, target) {
    const doc = target.ownerDocument;
    return xpaths.map(xpath => {
        try {
            const result = doc.evaluate(xpath, doc, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
            let containsTarget = false;
            for (let i = 0; i < result.snapshotLength; i++) {
                if (result.snapshotItem(i) === target) {
                    containsTarget = true;
                    break;
                }
            }
            return [result.snapshotLength, containsTarget];
        } catch (e) {
            return [-1, false];
        }
    });
}
