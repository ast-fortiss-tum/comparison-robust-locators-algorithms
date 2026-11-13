package de.tum.ls4.locators;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Perceptual hashes of element renderings, for COLOR's image property: the element is cropped from a screenshot of
 * the viewport and reduced to a 64-bit average hash (8x8 grayscale, bit = pixel >= mean), exactly as the thesis'
 * {@code PerceptualHash.aHash64}; two hashes are compared by {@code 1 - hamming / 64}.
 */
public final class ElementImages {

    /** Viewports scanned at most when hashing many elements (long pages are hashed partially). */
    private static final int MAX_VIEWPORTS = 25;

    private static final String VIEWPORT_RECTS = """
            const out = [];
            for (const e of arguments[0]) {
              const r = e.getBoundingClientRect();
              out.push([r.left, r.top, r.width, r.height]);
            }
            return {rects: out, dpr: window.devicePixelRatio, w: window.innerWidth, h: window.innerHeight,
                    sy: window.scrollY, sh: document.documentElement.scrollHeight};""";

    private ElementImages() {
    }

    /** The thesis' aHash64 of an image. */
    public static long aHash64(BufferedImage image) {
        BufferedImage scaled = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = scaled.createGraphics();
        g.drawImage(image, 0, 0, 8, 8, null);
        g.dispose();
        int[] pixels = new int[64];
        int sum = 0;
        int i = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int p = scaled.getRGB(x, y) & 0xff;
                pixels[i++] = p;
                sum += p;
            }
        }
        double avg = sum / 64.0;
        long h = 0L;
        for (int p : pixels) h = (h << 1) | (p >= avg ? 1 : 0);
        return h;
    }

    /** Similarity of two hashes in [0, 1]; 0 if either is missing. */
    public static double similarity(Long a, Long b) {
        return a == null || b == null ? 0.0 : 1.0 - Long.bitCount(a ^ b) / 64.0;
    }

    /**
     * Hash of one element: scrolled into view and cropped from a single screenshot; null if it has no area. The scroll
     * position is restored afterwards.
     */
    public static Long hash(WebDriver driver, WebElement element) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Object originalScroll = js.executeScript("return [window.scrollX, window.scrollY];");
        try {
            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
            return hashVisible(driver, List.of(element)).get(element);
        } finally {
            js.executeScript("window.scrollTo(arguments[0][0], arguments[0][1]);", originalScroll);
        }
    }

    /**
     * Hashes of many elements, scanning the page viewport by viewport (one screenshot each) from the top; elements are
     * hashed in the first viewport that shows their top edge. Elements without area are left out. The scroll position
     * is restored afterwards.
     */
    public static Map<WebElement, Long> hashAll(WebDriver driver, List<WebElement> elements) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Object originalScroll = js.executeScript("return [window.scrollX, window.scrollY];");
        Map<WebElement, Long> hashes = new IdentityHashMap<>();
        List<WebElement> pending = new ArrayList<>(elements);
        try {
            double top = 0;
            for (int v = 0; v < MAX_VIEWPORTS && !pending.isEmpty(); v++) {
                js.executeScript("window.scrollTo(0, arguments[0]);", top);
                Map<WebElement, Long> found = hashVisible(driver, pending);
                hashes.putAll(found);
                pending.removeIf(found::containsKey);
                Map<?, ?> view = (Map<?, ?>) js.executeScript(
                        "return {sy: window.scrollY, h: window.innerHeight, sh: document.documentElement.scrollHeight};");
                double scrollY = num(view.get("sy")), height = num(view.get("h"));
                if (scrollY + height >= num(view.get("sh"))) break;
                top = scrollY + height;
            }
        } finally {
            js.executeScript("window.scrollTo(arguments[0][0], arguments[0][1]);", originalScroll);
        }
        return hashes;
    }

    /** Hashes the elements whose top edge is inside the current viewport, from one screenshot. */
    private static Map<WebElement, Long> hashVisible(WebDriver driver, List<WebElement> elements) {
        Map<WebElement, Long> out = new IdentityHashMap<>();
        Map<?, ?> info = (Map<?, ?>) ((JavascriptExecutor) driver).executeScript(VIEWPORT_RECTS, elements);
        List<?> rects = (List<?>) info.get("rects");
        double dpr = num(info.get("dpr")), vw = num(info.get("w")), vh = num(info.get("h"));
        Map<Integer, double[]> visible = new LinkedHashMap<>();
        for (int i = 0; i < elements.size(); i++) {
            List<?> r = (List<?>) rects.get(i);
            double x = num(r.get(0)), y = num(r.get(1)), w = num(r.get(2)), h = num(r.get(3));
            if (w <= 0 || h <= 0 || y < 0 || y >= vh || x + w <= 0 || x >= vw) continue;
            visible.put(i, new double[]{x, y, w, h});
        }
        if (visible.isEmpty()) return out;
        BufferedImage page = screenshot(driver);
        visible.forEach((i, r) -> {
            int x0 = clamp((int) Math.round(r[0] * dpr), page.getWidth());
            int y0 = clamp((int) Math.round(r[1] * dpr), page.getHeight());
            int x1 = clamp((int) Math.round((r[0] + r[2]) * dpr), page.getWidth());
            int y1 = clamp((int) Math.round((r[1] + r[3]) * dpr), page.getHeight());
            if (x1 > x0 && y1 > y0) out.put(elements.get(i), aHash64(page.getSubimage(x0, y0, x1 - x0, y1 - y0)));
        });
        return out;
    }

    private static BufferedImage screenshot(WebDriver driver) {
        try {
            return ImageIO.read(new ByteArrayInputStream(((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(v, max));
    }

    private static double num(Object o) {
        return ((Number) o).doubleValue();
    }
}
