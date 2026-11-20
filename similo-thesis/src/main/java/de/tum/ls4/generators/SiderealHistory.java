package de.tum.ls4.generators;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * SIDEREAL across test runs: learns the fragility model from how the elements used by a test suite change from one
 * run (version of the application) to the next, and keeps it, with each element's last ancestry and locator, in a JSON
 * file.
 * <p>
 * In every run, call {@link #record} for each element the suite uses, under a stable key (e.g. "login.submit"), once
 * the element has been found (by its locator, or by any repair). The element's ancestry in the previous run and in
 * this run form the paper's mapping l<sup>n-1</sup> → l<sup>n</sup>; the mappings of a run are one version pair and are
 * counted with the paper's normalization. Locators are generated with the model learned so far, including this run's
 * mappings. {@link #save} adds this run's observations to the stored counters and writes the file.
 *
 * <pre>{@code
 * SiderealHistory history = SiderealHistory.open(Path.of("sidereal-history.json"));
 * WebElement submit = driver.findElement(history.locator("login.submit", By.id("submit")));
 * history.record(driver, "login.submit", submit);   // learns, regenerates the locator for the next run
 * ...
 * history.save();                                     // at the end of the run
 * }</pre>
 * Note that a run in which nothing changed counts every property as robust: save once per version of the
 * application under test (e.g. per release or nightly build) rather than after every local run if versions are rare.
 */
public final class SiderealHistory {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private record Entry(Ancestry ancestry, String xpath) {
    }

    private final Path file;
    private FragilityModel stored;
    private final Map<String, Entry> elements;
    private final List<FragilityModel.Mapping> pending = new ArrayList<>();
    private final Set<String> recordedThisRun = new HashSet<>();

    private SiderealHistory(Path file, FragilityModel stored, Map<String, Entry> elements) {
        this.file = file;
        this.stored = stored;
        this.elements = elements;
    }

    /** Opens the history in {@code file}, or starts an empty one if the file does not exist. */
    public static SiderealHistory open(Path file) {
        if (!Files.exists(file)) return new SiderealHistory(file, FragilityModel.empty(), new LinkedHashMap<>());
        try {
            Map<String, Object> root = JSON.readValue(file.toFile(), new TypeReference<>() {
            });
            Map<String, FragilityModel.Counter> counters = new TreeMap<>();
            ((Map<?, ?>) root.get("counters")).forEach((k, v) -> {
                List<?> rf = (List<?>) v;
                counters.put((String) k, new FragilityModel.Counter(((Number) rf.get(0)).longValue(), ((Number) rf.get(1)).longValue()));
            });
            Map<String, Entry> elements = new LinkedHashMap<>();
            ((Map<?, ?>) root.get("elements")).forEach((k, v) -> {
                Map<?, ?> e = (Map<?, ?>) v;
                elements.put((String) k, new Entry(Ancestry.fromBrowser(e.get("ancestry")), (String) e.get("xpath")));
            });
            return new SiderealHistory(file, FragilityModel.of(counters), elements);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read SIDEREAL history " + file, e);
        }
    }

    /** The model learned from the saved runs and this run's observations so far. */
    public FragilityModel model() {
        return pending.isEmpty() ? stored : stored.learn(pending);
    }

    /** The locator generated for {@code key} in the latest run, or {@code fallback} if there is none yet. */
    public By locator(String key, By fallback) {
        Entry e = elements.get(key);
        return e == null || e.xpath() == null ? fallback : By.xpath(e.xpath());
    }

    /**
     * Records that {@code element} is the element of {@code key} in this run: pairs it with its previous ancestry (if
     * any) for learning, and generates its SIDEREAL locator with the current model.
     */
    public GeneratedLocator record(WebDriver driver, String key, WebElement element) {
        return record(key, LiveDom.ancestry(driver, element), LiveDom.oracle(driver, element));
    }

    /** As {@link #record(WebDriver, String, WebElement)}, for a given ancestry and oracle. */
    GeneratedLocator record(String key, Ancestry current, XPathOracle oracle) {
        Entry previous = elements.get(key);
        if (previous != null && recordedThisRun.add(key)) {
            pending.add(new FragilityModel.Mapping(previous.ancestry(), current));
        }
        recordedThisRun.add(key);
        GeneratedLocator locator = new Sidereal(model()).generate(current, oracle);
        elements.put(key, new Entry(current, locator.xpath()));
        return locator;
    }

    /** Adds this run's observations to the stored model and writes the file (atomically). */
    public void save() {
        stored = model();
        pending.clear();
        recordedThisRun.clear();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", "sidereal-history/1");
        Map<String, List<Long>> counters = new TreeMap<>();
        stored.counters().forEach((k, c) -> counters.put(k, List.of(c.robust(), c.fragile())));
        root.put("counters", counters);
        Map<String, Object> out = new LinkedHashMap<>();
        elements.forEach((k, e) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("xpath", e.xpath());
            m.put("ancestry", e.ancestry().toRaw());
            out.put(k, m);
        });
        root.put("elements", out);
        try {
            Path dir = file.toAbsolutePath().getParent();
            if (dir != null) Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, ".sidereal", ".json");
            JSON.writeValue(tmp.toFile(), root);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write SIDEREAL history " + file, e);
        }
    }
}
