package de.tum.ls4.generators;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;

import static de.tum.ls4.generators.XPathCandidate.ATTRIBUTE_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiderealHistoryTest {

    /** Version 1 and 2 of a page: the id of the target changes (generated ids), its class and text do not. */
    private static final String V1 = "<html><body><form><button id='ember41' class='save'>Save</button>"
            + "<button id='ember42' class='btn'>Cancel</button></form></body></html>";
    private static final String V2 = "<html><body><form><button id='ember97' class='save'>Save</button>"
            + "<button id='ember98' class='btn'>Cancel</button></form></body></html>";

    private static GeneratedLocator record(SiderealHistory history, String page) {
        W3cDom dom = W3cDom.parse(page);
        Element target = dom.find("//button[1]");
        GeneratedLocator locator = history.record("form.save", dom.ancestry(target), dom.oracle(target));
        assertEquals(1, dom.select(locator.xpath()).getLength(), locator.xpath());
        assertEquals(target, dom.select(locator.xpath()).item(0), locator.xpath());
        return locator;
    }

    @Test
    void learnsAcrossRunsAndPersists(@TempDir Path dir) {
        Path file = dir.resolve("sidereal-history.json");

        // run 1: no history yet, nothing to learn
        SiderealHistory run1 = SiderealHistory.open(file);
        record(run1, V1);
        assertTrue(run1.model().counters().isEmpty());
        run1.save();
        assertTrue(Files.exists(file));

        // run 2: the element is paired with its run-1 ancestry; the id changed, the class did not
        SiderealHistory run2 = SiderealHistory.open(file);
        assertTrue(run2.locator("form.save", By.id("x")) instanceof By.ByXPath, "run 1's locator is available");
        record(run2, V2);
        assertEquals(1.0, run2.model().weight(ATTRIBUTE_PREFIX + "id"));
        assertEquals(FragilityModel.ROBUST_WEIGHT, run2.model().weight(ATTRIBUTE_PREFIX + "class"));
        run2.save();

        // run 3 (new process): the learned counters are read back and the locator avoids the fragile id
        SiderealHistory run3 = SiderealHistory.open(file);
        assertEquals(run2.model().counters(), run3.model().counters());
        GeneratedLocator locator = record(run3, V2);
        assertFalse(locator.xpath().contains("@id"), locator.xpath());
        assertEquals(locator.xpath(), ((By.ByXPath) run3.locator("form.save", null)).getRemoteParameters().value());
    }

    @Test
    void countsAnElementOncePerRun(@TempDir Path dir) {
        SiderealHistory history = SiderealHistory.open(dir.resolve("h.json"));
        record(history, V1);
        history.save();
        record(history, V2);
        record(history, V2); // used twice in the same run: one mapping
        assertEquals(1, history.model().counters().get(ATTRIBUTE_PREFIX + "id").fragile());
    }
}
