package de.tum.ls4.locators;

import de.tum.ls4.artifacts.ArtifactData;
import de.tum.ls4.artifacts.Element;
import de.tum.ls4.artifacts.SimpleSimiloStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.nio.file.Files;
import java.nio.file.Path;

import static de.tum.ls4.utils.DockerUtils.cleanDatabase;
import static de.tum.ls4.utils.DockerUtils.setupDatabase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * COLOR end to end with the artifact store: the element (with its image hash) is recorded on first use and the broken
 * locator is repaired on an evolved page. Needs Docker (MariaDB) and Chrome, and the database settings, e.g.
 * {@code JAVA_TOOL_OPTIONS="-Dartifact_database.host=127.0.0.1:3306 -Dartifact_database.user=root
 * -Dartifact_database.password=my-secret-pw"}; skipped otherwise.
 */
@Tag("browser")
@Tag("database")
class ByColorStoreTest {

    private static final String PAGE = """
            <!DOCTYPE html><html><head><title>c</title></head><body style="font: 16px sans-serif">
              <form><input name="q" type="text">
                <button id="save" class="btn" type="submit" style="background:#2a6;color:#fff;padding:8px">Save</button>
                <button class="btn" type="button" style="background:#ccc;padding:8px">Cancel</button>
              </form></body></html>""";

    private static ChromeDriver driver;
    private static Path page;

    @BeforeAll
    static void start() throws Exception {
        try {
            setupDatabase();
            cleanDatabase();
            new SimpleSimiloStore();
        } catch (RuntimeException e) {
            Assumptions.abort("No artifact database: " + e.getMessage());
        }
        try {
            if (Files.exists(Path.of("drivers/chromedriver"))) {
                System.setProperty("webdriver.chrome.driver", "drivers/chromedriver");
            }
            driver = new ChromeDriver(new ChromeOptions().addArguments("--headless=new"));
        } catch (Exception e) {
            Assumptions.abort("Chrome not available: " + e.getMessage());
        }
        page = Files.createTempFile("color", ".html");
        Files.writeString(page, PAGE);
    }

    @AfterAll
    static void stop() throws Exception {
        if (driver != null) driver.quit();
        if (page != null) Files.deleteIfExists(page);
    }

    @Test
    void recordsTheImageHashAndRepairsABrokenLocator() {
        driver.get(page.toUri().toString());
        WebElement save = driver.findElement(By.id("save"));
        By color = Similocator.color(By.id("save"));
        assertEquals(save, driver.findElement(color), "first use records the element");

        ArtifactData<Element> stored = new SimpleSimiloStore().getArtifact(driver.getCurrentUrl(), "save");
        assertNotNull(stored, "recorded in the store");
        assertNotNull(stored.artifact().attributes().get(ByColor.IMAGE_HASH_ATTRIBUTE), "with its image hash");

        // evolve: the id is gone (the locator breaks) and the class changes; the rendering stays the same
        ((JavascriptExecutor) driver).executeScript(
                "const b = document.getElementById('save'); b.removeAttribute('id'); b.className = 'btn btn-ok';");
        WebElement repaired = driver.findElement(color);
        assertEquals("Save", repaired.getText(), "COLOR finds the same button");
        assertEquals(save, repaired);
    }
}
