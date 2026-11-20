package capture;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;

import org.openqa.selenium.*;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Wraps a WebDriver so that every findElement/findElements records what a locator-repair tool needs:
 * the rendered DOM, a full-page screenshot, the located element (absolute XPath, tag, attributes, text,
 * page coordinates) and the page-object method that asked for it. Never lets a capture failure break the test.
 * Output: $CAPTURE_OUT (or -Dcapture.out) / <test class>.<test method> / NNN.json, dom_NN.html, shot_NN.png
 */
public final class CaptureDriver implements InvocationHandler {
    private final RemoteWebDriver real;
    private final Path root;
    private static final java.util.concurrent.atomic.AtomicInteger SESSIONS = new java.util.concurrent.atomic.AtomicInteger();
    private final int session = SESSIONS.incrementAndGet();
    private int seq = 0, domIdx = 0;
    private String lastHash = null, lastDomFile = null, lastShotFile = null, lastTest = null;
    private final StringBuilder log = new StringBuilder();

    private static final String ELEMENT_JS =
        "function xp(e){if(!e||e.nodeType!==1)return '';var p=[];for(;e&&e.nodeType===1;e=e.parentNode){var i=1;for(var s=e.previousSibling;s;s=s.previousSibling){if(s.nodeType===1&&s.nodeName===e.nodeName)i++;}"
      + "var t=e.nodeName.toLowerCase();var n=0;if(e.parentNode){for(var s=e.parentNode.firstChild;s;s=s.nextSibling){if(s.nodeType===1&&s.nodeName===e.nodeName)n++;}}"
      + "p.unshift(n>1?t+'['+i+']':t);}return '/'+p.join('/');}"
      + "var out=[];for(var k=0;k<arguments.length;k++){var e=arguments[k];if(!e){out.push(null);continue;}var r=e.getBoundingClientRect();var a={};for(var j=0;j<e.attributes.length;j++){a[e.attributes[j].name]=e.attributes[j].value;}"
      + "out.push({xpath:xp(e),tag:e.tagName.toLowerCase(),attrs:a,text:(e.innerText||e.value||'').trim().substring(0,300),"
      + "rect:{x:r.left,y:r.top,w:r.width,h:r.height},pageRect:{x:r.left+window.pageXOffset,y:r.top+window.pageYOffset,w:r.width,h:r.height},"
      + "visible:!!(e.offsetWidth||e.offsetHeight||e.getClientRects().length),outerHTML:e.outerHTML.substring(0,2000)});}"
      + "return JSON.stringify({elements:out,url:location.href,title:document.title,dpr:window.devicePixelRatio,"
      + "scroll:{x:window.pageXOffset,y:window.pageYOffset},viewport:{w:window.innerWidth,h:window.innerHeight},"
      + "doc:{w:Math.max(document.documentElement.scrollWidth,document.body?document.body.scrollWidth:0),h:Math.max(document.documentElement.scrollHeight,document.body?document.body.scrollHeight:0)}});";

    /** Benchmark extraction scripts (the similo-thesis javascript directory), $CAPTURE_JS; null disables the candidate snapshot. */
    private static String extractorSrc() {
        try {
            String dir = System.getProperty("capture.js", System.getenv("CAPTURE_JS"));
            if (dir == null) return null;
            StringBuilder b = new StringBuilder();
            for (String f : new String[] {"attribute_extractor.js", "css_attribute_extractor.js", "xpath_extractor.js", "css_selector_extractor.js", "direct_text_extractor.js", "elements_extractor.js"})
                b.append(new String(Files.readAllBytes(Paths.get(dir, f)), StandardCharsets.UTF_8)).append('\n');
            return b.toString();
        } catch (Throwable t) { return null; }
    }
    private static final String EXTRACTORS = extractorSrc();
    /** arguments[0] = full flag, arguments[1..] = located elements. Same candidates as the ReproBreak import (all visible elements, SVG internals excluded). */
    private static final String SNAP_JS = EXTRACTORS == null ? null : EXTRACTORS
        + "var full=arguments[0];var els=Array.prototype.slice.call(arguments,1);"
        + "var skip={SCRIPT:1,STYLE:1,HEAD:1,META:1,LINK:1,TITLE:1,NOSCRIPT:1,TEMPLATE:1,HTML:1,BR:1,BASE:1};"
        + "function inSvg(el){return el.namespaceURI==='http://www.w3.org/2000/svg'&&el.tagName.toLowerCase()!=='svg';}"
        + "var cand=[];if(full){document.querySelectorAll('*').forEach(function(el){if(!skip[el.tagName.toUpperCase()]&&!inSvg(el)&&elementIsVisible(el))cand.push(element_extractor(el));});}"
        + "var t=els.map(function(el){return{visible:elementIsVisible(el),inSvg:inSvg(el),element:element_extractor(el)};});"
        + "return JSON.stringify({scroll:[Math.round(scrollX),Math.round(scrollY)],viewport:[innerWidth,innerHeight],elements:cand,targets:t});";

    private CaptureDriver(RemoteWebDriver real, Path root) {
        this.real = real;
        this.root = root;
    }

    /** Plain (unwrapped) remote driver for installer helpers that use a local ChromeDriver in the original suites. */
    public static WebDriver plainRemote(org.openqa.selenium.chrome.ChromeOptions o, String url) {
        try { return new RemoteWebDriver(new java.net.URL(url), o); } catch (java.net.MalformedURLException e) { throw new RuntimeException(e); }
    }

    /** Wrap a RemoteWebDriver. Output root: -Dcapture.out, else $CAPTURE_OUT, else ./capture-out. */
    public static WebDriver wrap(RemoteWebDriver real) {
        String out = System.getProperty("capture.out", System.getenv("CAPTURE_OUT"));
        Path root = Paths.get(out == null ? "capture-out" : out);
        CaptureDriver h = new CaptureDriver(real, root);
        return (WebDriver) Proxy.newProxyInstance(CaptureDriver.class.getClassLoader(),
            new Class<?>[] {WebDriver.class, JavascriptExecutor.class, TakesScreenshot.class, HasCapabilities.class,
                            WrapsDriver.class, org.openqa.selenium.interactions.Interactive.class},
            h);
    }

    @Override
    public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
        String name = m.getName();
        if (name.equals("getWrappedDriver")) return real;
        if (name.equals("quit")) { flushLog(); }
        Object result;
        try {
            result = m.invoke(real, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        if (name.equals("findElement") && result instanceof WebElement) {
            capture(args[0], Collections.singletonList((WebElement) result));
        } else if (name.equals("findElements") && result instanceof List && !((List<?>) result).isEmpty()) {
            List<WebElement> els = new ArrayList<>();
            for (Object o : (List<?>) result) { if (els.size() < 20) els.add((WebElement) o); }
            capture(args[0], els);
        }
        return result;
    }

    private void capture(Object by, List<WebElement> els) {
        try {
            String[] who = caller();
            String test = who[0], poMethod = who[1], poFrame = who[2];
            Path dir = root.resolve(String.format("s%03d_%d", session, ProcessHandle.current().pid()));
            Files.createDirectories(dir);
            Object[] arr = els.toArray();
            String meta = (String) real.executeScript(ELEMENT_JS, arr);
            String dom = (String) real.executeScript("return document.documentElement.outerHTML;");
            String hash = hex(MessageDigest.getInstance("SHA-1").digest((dom + meta.hashCode() + "").getBytes(StandardCharsets.UTF_8)));
            String domHash = hex(MessageDigest.getInstance("SHA-1").digest(dom.getBytes(StandardCharsets.UTF_8)));
            boolean newDom = !domHash.equals(lastHash);
            String snap = null;
            if (SNAP_JS != null) {
                try {
                    Object[] a = new Object[arr.length + 1]; a[0] = newDom; System.arraycopy(arr, 0, a, 1, arr.length);
                    snap = (String) real.executeScript(SNAP_JS, a);
                } catch (Throwable t) { log("snapshot failed: " + t); }
            }
            if (newDom) {
                if (snap != null) Files.write(dir.resolve(String.format("snap_%02d.json", domIdx + 1)), snap.getBytes(StandardCharsets.UTF_8));
                domIdx++;
                lastDomFile = String.format("dom_%02d.html", domIdx);
                lastShotFile = String.format("shot_%02d.png", domIdx);
                Files.write(dir.resolve(lastDomFile), dom.getBytes(StandardCharsets.UTF_8));
                byte[] png = fullPage(meta);
                if (png != null) Files.write(dir.resolve(lastShotFile), png); else lastShotFile = null;
                lastHash = domHash;
            }
            seq++;
            String json = "{\"seq\":" + seq + ",\"test\":" + q(test) + ",\"po_method\":" + q(poMethod) + ",\"po_frame\":" + q(poFrame)
                + ",\"by\":" + q(String.valueOf(by)) + ",\"dom\":" + q(lastDomFile) + ",\"shot\":" + (lastShotFile == null ? "null" : q(lastShotFile))
                + ",\"time\":" + System.currentTimeMillis() + ",\"page\":" + meta
                + (snap == null ? "" : ",\"targets\":" + targetsOf(snap)) + "}";
            Files.write(dir.resolve(String.format("%03d.json", seq)), json.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            log("capture failed: " + t);
        }
    }

    /** Full-page PNG through CDP over the grid's HTTP endpoint (captureBeyondViewport); falls back to the viewport screenshot. */
    private byte[] fullPage(String meta) {
        try {
            int w = intAfter(meta, "\"doc\":{\"w\":"), h = intAfter(meta, ",\"h\":", meta.indexOf("\"doc\":"));
            String hub = System.getProperty("capture.hub", System.getenv("CAPTURE_HUB") == null ? "http://localhost:4444" : System.getenv("CAPTURE_HUB"));
            String body = "{\"cmd\":\"Page.captureScreenshot\",\"params\":{\"format\":\"png\",\"captureBeyondViewport\":true,\"fromSurface\":true,"
                + "\"clip\":{\"x\":0,\"y\":0,\"width\":" + Math.max(w, 1) + ",\"height\":" + Math.min(Math.max(h, 1), 16000) + ",\"scale\":1}}}";
            java.net.http.HttpRequest rq = java.net.http.HttpRequest.newBuilder(java.net.URI.create(hub + "/session/" + real.getSessionId() + "/goog/cdp/execute"))
                .header("Content-Type", "application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
            String resp = java.net.http.HttpClient.newHttpClient().send(rq, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
            int i = resp.indexOf("\"data\":\"");
            if (i >= 0) { i += 8; int j = resp.indexOf('"', i); return Base64.getDecoder().decode(resp.substring(i, j)); }
            log("cdp http screenshot: " + resp.substring(0, Math.min(200, resp.length())));
        } catch (Throwable t) { log("cdp screenshot failed: " + t); }
        try { return real.getScreenshotAs(OutputType.BYTES); } catch (Throwable t) { log("screenshot failed: " + t); return null; }
    }

    /** The "targets" array of a snapshot (the located elements as the benchmark's extractor sees them). */
    private static String targetsOf(String snap) {
        int i = snap.indexOf("\"targets\":");
        return i < 0 ? "null" : snap.substring(i + 10, snap.length() - 1);
    }

    private static int intAfter(String s, String key) { return intAfter(s, key, 0); }
    private static int intAfter(String s, String key, int from) {
        int i = s.indexOf(key, from); if (i < 0) return 0; i += key.length();
        int j = i; while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
        return j > i ? Integer.parseInt(s.substring(i, j)) : 0;
    }

    /** {test class.method, first po.* method on the stack, its frame}. */
    private static String[] caller() {
        String test = "unknown", po = "", frame = "";
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String c = e.getClassName();
            if (c.startsWith("capture.") || c.startsWith("java.") || c.startsWith("jdk.") || c.startsWith("org.openqa") || c.startsWith("com.sun")) continue;
            if (c.startsWith("org.junit") || c.startsWith("org.apache") || c.startsWith("sun.")) continue;
            boolean isPo = c.startsWith("po.") || c.contains(".po.");
            if (isPo && po.isEmpty()) { po = c + "." + e.getMethodName(); frame = e.getFileName() + ":" + e.getLineNumber(); }
            if (!isPo && !c.endsWith("BaseTest") && !c.endsWith("PageObject")) { test = c + "." + e.getMethodName(); }
        }
        return new String[] {test, po, frame};
    }

    private static String q(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) { case '"': b.append("\\\""); break; case '\\': b.append("\\\\"); break; case '\n': b.append("\\n"); break; case '\r': b.append("\\r"); break; case '\t': b.append("\\t"); break;
                default: if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
        }
        return b.append('"').toString();
    }
    private static String hex(byte[] d) { StringBuilder b = new StringBuilder(); for (byte x : d) b.append(String.format("%02x", x)); return b.toString(); }
    private void log(String s) { log.append(s).append('\n'); }
    private void flushLog() { try { if (log.length() > 0) { Files.createDirectories(root); Files.write(root.resolve("capture-errors.log"), log.toString().getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); log.setLength(0);} } catch (Throwable ignored) {} }
}
