// Inserts snapshot captures into a Cypress/Playwright test file, right before the statement that uses a locator.
// Usage: node instrument.mjs <spec.json>  with spec {file, framework, captures: [{id, line, locator}], jsDir, wrapper}
// Prints a JSON report {id: "ok" | reason}. Rewrites the file in place.
import fs from 'node:fs';
import path from 'node:path';
import ts from 'typescript';

const spec = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const src = fs.readFileSync(spec.file, 'utf8');
const kind = /\.tsx?$/.test(spec.file) ? (spec.file.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS)
    : (spec.file.endsWith('x') ? ts.ScriptKind.JSX : ts.ScriptKind.JS);
const sf = ts.createSourceFile(spec.file, src, ts.ScriptTarget.Latest, true, kind);
const norm = (s) => s.replace(/\s+/g, '').replace(/["`]/g, "'");

function balanced(s) { // drop the unbalanced trailing ')' the mining regex sometimes keeps
    s = s.trim();
    let depth = 0;
    for (let i = 0; i < s.length; i++) {
        if (s[i] === '(') depth++;
        else if (s[i] === ')') { depth--; if (depth === 0) return s.slice(0, i + 1); }
    }
    return s;
}

const calls = [];
(function walk(n) { if (ts.isCallExpression(n)) calls.push(n); ts.forEachChild(n, walk); })(sf);
const lineOf = (pos) => sf.getLineAndCharacterOfPosition(pos).line + 1;

const SELECTOR_ONLY = new Set(['$', 'waitForSelector', 'click', '$eval', '$$', 'fill', 'hover', 'isVisible', 'textContent', 'innerText', 'type', 'press', 'check', 'focus', 'dblclick']);
const report = {};
const inserts = [];
for (const c of spec.captures) {
    const want = balanced(c.locator);
    const m = /^([\w$]+)\.([\w$]+)\(/.exec(want);
    if (!m) { report[c.id] = 'unparsable locator'; continue; }
    const [, recv, method] = m;
    const cands = calls.filter((n) => ts.isPropertyAccessExpression(n.expression) && n.expression.name.text === method
        && n.expression.expression.getText(sf) === recv
        && lineOf(n.getStart(sf)) <= c.line && c.line <= lineOf(n.getEnd()));
    let call = cands.find((n) => norm(n.getText(sf)) === norm(want))
        || cands.find((n) => norm(n.getText(sf)).startsWith(norm(want).slice(0, -1)))
        || (cands.length === 1 ? cands[0] : null);
    if (!call) { report[c.id] = `call not found (${cands.length} candidates)`; continue; }
    if (call.arguments.length === 0) { report[c.id] = 'no arguments'; continue; }
    let expr;
    if (spec.framework === 'cypress') expr = `cy.get(${call.arguments[0].getText(sf)})`;
    else if (SELECTOR_ONLY.has(method)) expr = `${recv}.locator(${call.arguments[0].getText(sf)})`;
    else expr = call.getText(sf);
    // innermost statement containing the call, without crossing a function boundary
    let n = call, crossed = false;
    while (n.parent && !(ts.isBlock(n.parent) || ts.isSourceFile(n.parent) || ts.isCaseClause(n.parent) || ts.isDefaultClause(n.parent))) {
        if (ts.isArrowFunction(n.parent) || ts.isFunctionExpression(n.parent) || ts.isFunctionDeclaration(n.parent)) crossed = true;
        n = n.parent;
    }
    if (crossed) { report[c.id] = 'locator inside an expression-bodied closure'; continue; }
    let fn = n.parent;
    while (fn && !(ts.isArrowFunction(fn) || ts.isFunctionExpression(fn) || ts.isFunctionDeclaration(fn) || ts.isMethodDeclaration(fn))) fn = fn.parent;
    const isAsync = !!fn && (ts.getCombinedModifierFlags(fn) & ts.ModifierFlags.Async) !== 0;
    const stmtStart = n.getStart(sf);
    const lineStart = src.lastIndexOf('\n', stmtStart - 1) + 1;
    const indent = src.slice(lineStart, stmtStart).match(/^\s*/)[0];
    const id = JSON.stringify(String(c.id));
    let code;
    if (spec.framework === 'cypress') code = `${expr}.then(($rbEls) => __rbCaptureCy($rbEls, ${id}));`;
    else if (isAsync) code = `await __rbCapture(${expr}, ${id});`;
    else { report[c.id] = 'enclosing function is not async'; continue; }
    inserts.push({ pos: stmtStart, text: code + '\n' + indent, id: c.id, expr });
    report[c.id] = 'ok';
}

const jsDir = spec.jsDir;
const scripts = ['attribute_extractor.js', 'css_attribute_extractor.js', 'xpath_extractor.js', 'css_selector_extractor.js',
    'direct_text_extractor.js', 'elements_extractor.js'].map((f) => fs.readFileSync(path.join(jsDir, f), 'utf8'));
const snapshotSrc = scripts.join('\n') + '\n' + fs.readFileSync(spec.wrapper, 'utf8');

const helper = spec.framework === 'cypress' ? `

function __rbCaptureCy($rbEls, id) {
  const els = Array.prototype.slice.call($rbEls || []);
  const win = els.length ? els[0].ownerDocument.defaultView : null;
  if (!win) return;
  if (!win.__rbSnapshot) win.eval(__RB_SRC);
  const data = win.__rbSnapshot(els);
  const counts = (globalThis.__rbCount = globalThis.__rbCount || {});
  const n = (counts[id] = (counts[id] || 0) + 1);
  data.matches = els.length;
  cy.writeFile('/rb/out/' + id + '-' + n + '.json', JSON.stringify(data), { log: false });
  cy.screenshot('rb-' + id + '-' + n, { capture: 'viewport', log: false, overwrite: true });
}
var __RB_SRC = ${JSON.stringify(snapshotSrc)};
` : `

async function __rbCapture(loc, id) {
  try {
    const fs = await import('node:fs');
    const page = loc.page();
    try { await loc.first().waitFor({ state: 'attached', timeout: 10000 }); } catch (e) {}
    const handles = await loc.elementHandles();
    const counts = (globalThis.__rbCount = globalThis.__rbCount || {});
    const n = (counts[id] = (counts[id] || 0) + 1);
    let browser = 'unknown';
    try { browser = page.context().browser().browserType().name(); } catch (e) {}
    const base = '/rb/out/' + id + '-' + n + '-' + browser;
    await page.evaluate((src) => { if (!window.__rbSnapshot) (0, eval)(src); }, __RB_SRC);
    const data = await page.evaluate((els) => window.__rbSnapshot(els), handles);
    data.matches = handles.length;
    fs.writeFileSync(base + '.json', JSON.stringify(data));
    await page.screenshot({ path: base + '.png' });
  } catch (e) { console.log('RBCAPTURE-ERROR ' + id + ' ' + e); }
}
var __RB_SRC = ${JSON.stringify(snapshotSrc)};
`;

let out = src;
for (const ins of inserts.sort((a, b) => b.pos - a.pos)) out = out.slice(0, ins.pos) + ins.text + out.slice(ins.pos);
if (inserts.length) fs.writeFileSync(spec.file, out + helper);
console.log(JSON.stringify({ report, inserted: inserts.map((i) => ({ id: i.id, expr: i.expr })) }));
