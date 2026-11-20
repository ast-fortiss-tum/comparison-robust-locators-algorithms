package de.tum.ls4.generators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares the Java port with EAGL's own implementation on golden fixtures produced by
 * {@code scripts/eagl/export_for_java.py} (the BeautifulSoup tree of each snapshot of the thesis' pilot sites, the
 * locator EAGL generated for each tracked element, and model inputs with sklearn's predictions).
 */
class EaglGoldenTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static List<JsonNode> fixtures() throws Exception {
        Path dir = Path.of(EaglGoldenTest.class.getResource("/eagl/golden").toURI());
        List<JsonNode> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.sorted().toList()) {
                try (InputStream in = new GZIPInputStream(Files.newInputStream(p))) {
                    out.add(JSON.readTree(in));
                } catch (IOException e) {
                    throw new IllegalStateException(p.toString(), e);
                }
            }
        }
        return out;
    }

    @Test
    void forestReproducesSklearnPredictionsExactly() throws Exception {
        EaglForest forest = EaglForest.bundled();
        int checked = 0;
        for (JsonNode page : fixtures()) {
            for (JsonNode v : page.get("vectors")) {
                double[] x = new double[6];
                for (int i = 0; i < 6; i++) x[i] = v.get(i).isBoolean() ? (v.get(i).asBoolean() ? 1 : 0) : v.get(i).asDouble();
                assertEquals(v.get(6).asDouble(), forest.predict(x), 0.0, v.toString());
                checked++;
            }
        }
        assertTrue(checked > 1000, "vectors checked: " + checked);
    }

    @Test
    void generatesTheSameLocatorsAsTheOriginal() throws Exception {
        Eagl eagl = new Eagl(EaglForest.bundled());
        int total = 0, failures = 0;
        List<String> mismatches = new ArrayList<>();
        for (JsonNode page : fixtures()) {
            List<SoupNode> tags = new ArrayList<>();
            SoupNode.parse(page.get("tree"), tags);
            for (JsonNode t : page.get("targets")) {
                String expected = t.get(1).asText();
                String actual;
                try {
                    actual = eagl.generate(tags.get(t.get(0).asInt()));
                } catch (Eagl.Failure f) {
                    actual = "!" + f.pythonError;
                    failures++;
                }
                total++;
                if (!expected.equals(actual)) {
                    mismatches.add(page.get("site").asText() + " " + page.get("date").asText() + " #" + t.get(0)
                            + "\n  python: " + expected + "\n  java:   " + actual);
                }
            }
        }
        assertEquals(1176, total);
        assertEquals(19, failures, "EAGL's implementation fails on 19 of the pilot's elements");
        assertTrue(mismatches.isEmpty(), mismatches.size() + " mismatches:\n" + String.join("\n", mismatches.subList(0, Math.min(10, mismatches.size()))));
    }
}
