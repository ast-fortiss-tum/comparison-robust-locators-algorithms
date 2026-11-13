/**
 * Snapshot of the DOM for EAGL (see de.tum.ls4.generators.SoupNode): the tree as BeautifulSoup's html.parser would see
 * the serialized document. A tag is [name, [attr, value, ...], [children]]; any other node is 1 if its text is "\n",
 * else 0. <template> contents are included as children (they are in the serialized HTML).
 * Returns a JSON string {tree, targets}: targets are the pre-order tag numbers of the given elements (-1 = not found).
 */
function eaglSnapshot(elements) {
    var count = 0;
    var numbers = new Map();

    function encode(node) {
        if (node.nodeType !== 1) {
            return node.nodeValue === "\n" ? 1 : 0;
        }
        numbers.set(node, count++);
        var attrs = [];
        for (var i = 0; i < node.attributes.length; i++) {
            attrs.push(node.attributes[i].name.toLowerCase(), node.attributes[i].value);
        }
        var children = [];
        var kids = node.localName === "template" && node.content ? node.content.childNodes : node.childNodes;
        for (var j = 0; j < kids.length; j++) {
            children.push(encode(kids[j]));
        }
        return [node.localName.toLowerCase(), attrs, children];
    }

    var tree = [];
    for (var k = 0; k < document.childNodes.length; k++) {
        tree.push(encode(document.childNodes[k]));
    }
    var targets = [];
    for (var t = 0; t < elements.length; t++) {
        targets.push(numbers.has(elements[t]) ? numbers.get(elements[t]) : -1);
    }
    return JSON.stringify({tree: tree, targets: targets});
}
