package de.tum.ls4.generators;

import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Map;

/**
 * VISTA (Stocco, Yandrapally, Mesbah, "Visual Web Test Repair", ESEC/FSE 2018): re-identifies an element visually.
 * At capture time the element's rendering is cropped from a screenshot (the template); at relocation time the
 * template is searched in a screenshot of the new page with OpenCV template matching (TM_CCOEFF_NORMED, as in the
 * thesis), and the element at the best-matching point is returned.
 * <p>
 * Screenshots are of the viewport: the element is scrolled into view at capture time, and at relocation time the page
 * is scanned viewport by viewport from the top.
 */
public final class Vista {

    static {
        OpenCV.loadLocally();
    }

    /** What VISTA stores for an element: its template image (PNG) and where it was. */
    public record Snapshot(byte[] template, int x, int y, int width, int height) {
    }

    /** Result of a relocation: the element and the match quality of the template, in [-1, 1]. */
    public record Match(WebElement element, double score) {
    }

    private final double minScore;

    public Vista() {
        this(0.7);
    }

    /** @param minScore matches below this correlation are rejected (no element found). */
    public Vista(double minScore) {
        this.minScore = minScore;
    }

    public static Snapshot capture(WebDriver driver, WebElement element) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
        @SuppressWarnings("unchecked")
        Map<String, Object> r = (Map<String, Object>) js.executeScript(
                "const r = arguments[0].getBoundingClientRect();"
                        + "return {x: r.left, y: r.top, w: r.width, h: r.height, dpr: window.devicePixelRatio, sy: window.scrollY};",
                element);
        double dpr = num(r.get("dpr"));
        Mat page = screenshot(driver);
        Rect crop = clamp(new Rect((int) Math.round(num(r.get("x")) * dpr), (int) Math.round(num(r.get("y")) * dpr),
                Math.max(1, (int) Math.round(num(r.get("w")) * dpr)), Math.max(1, (int) Math.round(num(r.get("h")) * dpr))), page);
        MatOfByte png = new MatOfByte();
        Imgcodecs.imencode(".png", page.submat(crop), png);
        return new Snapshot(png.toArray(), (int) num(r.get("x")), (int) (num(r.get("y")) + num(r.get("sy"))),
                (int) num(r.get("w")), (int) num(r.get("h")));
    }

    /** Finds the element whose rendering best matches the snapshot's template, or null if no good match exists. */
    public Match relocate(WebDriver driver, Snapshot snapshot) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Mat template = Imgcodecs.imdecode(new MatOfByte(snapshot.template()), Imgcodecs.IMREAD_COLOR);
        long pageHeight = ((Number) js.executeScript("return document.documentElement.scrollHeight;")).longValue();
        long viewport = ((Number) js.executeScript("return window.innerHeight;")).longValue();
        Match best = null;
        for (long top = 0; top < Math.max(pageHeight, 1); top += Math.max(1, viewport - snapshot.height())) {
            js.executeScript("window.scrollTo(0, arguments[0]);", top);
            double dpr = ((Number) js.executeScript("return window.devicePixelRatio;")).doubleValue();
            Mat page = screenshot(driver);
            if (template.cols() > page.cols() || template.rows() > page.rows()) break;
            Mat result = new Mat();
            Imgproc.matchTemplate(page, template, result, Imgproc.TM_CCOEFF_NORMED);
            Core.MinMaxLocResult mm = Core.minMaxLoc(result);
            if (best == null || mm.maxVal > best.score()) {
                // centre of the matched area, in CSS pixels of the viewport
                double cx = (mm.maxLoc.x + template.cols() / 2.0) / dpr;
                double cy = (mm.maxLoc.y + template.rows() / 2.0) / dpr;
                WebElement el = (WebElement) js.executeScript(
                        "return document.elementFromPoint(arguments[0], arguments[1]);", cx, cy);
                if (el != null) best = new Match(el, mm.maxVal);
            }
            if (top + viewport >= pageHeight) break;
        }
        if (best != null) js.executeScript("window.scrollTo(0, 0);");
        return best != null && best.score() >= minScore ? best : null;
    }

    private static Mat screenshot(WebDriver driver) {
        byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        return Imgcodecs.imdecode(new MatOfByte(png), Imgcodecs.IMREAD_COLOR);
    }

    private static Rect clamp(Rect r, Mat m) {
        int x = Math.max(0, Math.min(r.x, m.cols() - 1));
        int y = Math.max(0, Math.min(r.y, m.rows() - 1));
        return new Rect(x, y, Math.max(1, Math.min(r.width, m.cols() - x)), Math.max(1, Math.min(r.height, m.rows() - y)));
    }

    private static double num(Object o) {
        return ((Number) o).doubleValue();
    }

    /** Convenience: elements returned by {@link #relocate} as a list (empty if not found). */
    public List<WebElement> find(WebDriver driver, Snapshot snapshot) {
        Match m = relocate(driver, snapshot);
        return m == null ? List.of() : List.of(m.element());
    }
}
