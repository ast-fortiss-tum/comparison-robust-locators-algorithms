package tests;

import org.junit.Test;

import pageobjects.HomePage;
import pageobjects.SetupPage;
import utils.BaseTest;

public class ModifyMain extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		hp.openSetupMenu();
		SetupPage sp = hp.goToNewModuleSetup(driver);
		//assertTrue(sp.getNumberOfActivatedModules().contains("0"));
		sp.activateMainModules();
		//assertTrue(sp.getNumberOfActivatedModules().contains("6"));
	}
}
