package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.HomePage;
import utils.BaseTest;

public class TestLogout extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		hp.logout();
		
		assertTrue(ip.getVersion().contains("Dolibarr 3.9.0-beta"));
		
	}
}
