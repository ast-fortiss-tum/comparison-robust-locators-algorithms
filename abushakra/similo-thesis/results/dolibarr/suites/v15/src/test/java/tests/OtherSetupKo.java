package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.HomePage;
import pageobjects.OtherSetupPage;
import utils.BaseTest;

public class OtherSetupKo extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		hp.openSetupMenu();
		
		OtherSetupPage mp = hp.openOtherSetupPage(driver);
		mp.setup("", "");
		assertTrue(mp.getError().contains("required"));
	}
}
