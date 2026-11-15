// usage: node capture.mjs <version> <static build dir> <out dir>
// Renders an Excalidraw build in headless Chromium, drives the same scripted UI states, and snapshots every state
// (all visible elements with the benchmark's extractor, DOM, full-page screenshot).
import { chromium } from 'playwright-core';
import http from 'node:http'; import fs from 'node:fs'; import path from 'node:path';
const [version, dir, out] = process.argv.slice(2);
const JS = '/Users/stocco/repos/tools/abushakra-comparison-robust-locators-algorithms/similo-thesis/src/main/resources/javascript';
const WRAP = '/Users/stocco/repos/tools/abushakra-comparison-robust-locators-algorithms/similo-thesis/scripts/reprobreak/snapshot_wrapper.js';
const src = ['attribute_extractor.js','css_attribute_extractor.js','xpath_extractor.js','css_selector_extractor.js','direct_text_extractor.js','elements_extractor.js']
  .map(f => fs.readFileSync(path.join(JS, f), 'utf8')).join('\n') + '\n' + fs.readFileSync(WRAP, 'utf8');
const mime = { '.html':'text/html', '.js':'text/javascript', '.css':'text/css', '.json':'application/json', '.svg':'image/svg+xml', '.png':'image/png', '.woff2':'font/woff2', '.woff':'font/woff', '.ttf':'font/ttf', '.wasm':'application/wasm' };
const server = http.createServer((q, r) => {
  let p = decodeURIComponent(q.url.split('?')[0]); if (p.endsWith('/')) p += 'index.html';
  let f = path.join(dir, p); if (!fs.existsSync(f) || fs.statSync(f).isDirectory()) f = path.join(dir, 'index.html');
  r.writeHead(200, { 'content-type': mime[path.extname(f)] || 'application/octet-stream' }); fs.createReadStream(f).pipe(r);
}).listen(0);
const port = server.address().port;
fs.mkdirSync(out, { recursive: true });
const exe = fs.readdirSync(process.env.HOME + '/Library/Caches/ms-playwright').filter(x => x.startsWith('chromium-')).map(x => process.env.HOME + '/Library/Caches/ms-playwright/' + x)[0];
const find = (d) => { for (const e of fs.readdirSync(d, { withFileTypes: true })) { const p = path.join(d, e.name); if (e.isDirectory()) { const r = find(p); if (r) return r; } else if (e.name === 'Google Chrome for Testing' || e.name === 'chrome' ) return p; } };
const browser = await chromium.launch({ executablePath: find(exe), headless: true, args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
page.on('pageerror', e => console.log('pageerror', String(e).slice(0, 120)));
// the app must not reach the network for fonts or collaboration
await page.route(u => !u.href.startsWith('http://localhost:' + port), r => r.abort());
await page.goto(`http://localhost:${port}/`, { waitUntil: 'load' });
await page.waitForSelector('[data-testid="toolbar-rectangle"]', { timeout: 60000 });
await page.waitForTimeout(2000);
let n = 0;
async function snap(name) {
  await page.waitForTimeout(800);
  await page.evaluate((s) => { if (!window.__rbSnapshot) (0, eval)(s); }, src);
  const data = await page.evaluate(() => window.__rbSnapshot([]));
  n++; const base = path.join(out, `${String(n).padStart(2, '0')}-${name}`);
  fs.writeFileSync(base + '.json', JSON.stringify({ state: name, ...data }));
  await page.screenshot({ path: base + '.png', fullPage: true });
  console.log(version, name, data.elements.length, 'elements', data.elements.filter(e => e.attributes && e.attributes['data-testid']).length, 'with testid');
}
const tryDo = async (label, fn) => { try { await fn(); } catch (e) { console.log('step failed', label, String(e).slice(0, 100)); } };
await snap('initial');
const menuTrigger = version === 'v0.14.0' ? 'dropdown-menu-button' : 'main-menu-trigger'; // the menu button's test id was renamed in between
await tryDo('menu', async () => { await page.click(`[data-testid="${menuTrigger}"]`, { timeout: 5000 }); await snap('main-menu'); await page.keyboard.press('Escape'); });
await tryDo('rect', async () => {
  await page.keyboard.press('r'); // the tool radio inputs are visually hidden: use the shortcut
  await page.mouse.move(700, 400); await page.mouse.down(); await page.mouse.move(900, 520, { steps: 8 }); await page.mouse.up();
  await snap('rectangle-selected');
});
await tryDo('text', async () => { await page.keyboard.press('t'); await page.mouse.click(1000, 700); await page.keyboard.type('hello'); await page.keyboard.press('Escape'); await snap('text-added'); });
await tryDo('library', async () => { await page.keyboard.press('0'); await page.waitForTimeout(500); await snap('library-open'); });
await browser.close(); server.close();
