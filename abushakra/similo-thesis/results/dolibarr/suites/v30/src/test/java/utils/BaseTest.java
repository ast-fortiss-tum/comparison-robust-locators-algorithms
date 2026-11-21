package utils;

import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriver;

import io.github.bonigarcia.wdm.PhantomJsDriverManager;
import io.github.bonigarcia.wdm.WebDriverManager;
import pageobjects.IndexPage;

public class BaseTest {

	protected static WebDriver driver;
	protected static IndexPage ip;
	protected static int browser;

	@Before
	public void setUp() throws InterruptedException {
		browser = 0;
		driver = getDriver(browser);
		driver.manage().timeouts().implicitlyWait(1, TimeUnit.SECONDS);
		driver.manage().timeouts().setScriptTimeout(1, TimeUnit.SECONDS);
		driver.manage().window().maximize();

		driver.get("http://dol-web/dolibarr/dolibarr_" + Version.getVersion() + "/htdocs/index.php");

		ip = new IndexPage(driver);
	}

	@After
	public void tearDown() {
		ip = null;
		driver.quit();
		driver = null;
	}

	public static WebDriver getDriver(int browser) {
		if (driver == null) {
			switch (browser) {
			case 0:
				
				driver = SettleDriver.wrap(capture.CaptureDriver.wrapIfEnabled(remote()));
				return driver;
			case 1:
				PhantomJsDriverManager.getInstance().setup();
				driver = new PhantomJSDriver();
				return driver;
			}
		}
		return driver;
	}


	/** The authors worked with an Italian browser: Dolibarr preselects the company country from the browser language (the v30 suite never selects one). */
	private static org.openqa.selenium.chrome.ChromeOptions localeOptions() {
		org.openqa.selenium.chrome.ChromeOptions o = new org.openqa.selenium.chrome.ChromeOptions();
		o.addArguments("--lang=it-IT");
		java.util.Map<String, Object> prefs = new java.util.HashMap<String, Object>();
		prefs.put("intl.accept_languages", "it-IT,it");
		o.setExperimentalOption("prefs", prefs);
		return o;
	}

	private static WebDriver remote() {
		try { return new org.openqa.selenium.remote.RemoteWebDriver(new java.net.URL("http://localhost:4455/wd/hub"), localeOptions()); }
		catch (java.net.MalformedURLException e) { throw new RuntimeException(e); }
	}

}
