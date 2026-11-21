package tests;

import org.junit.Test;

import pageobjects.HomePage;
import pageobjects.SetupPage;
import utils.BaseTest;

public class ModifyOthers extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		hp.openSetupMenu();
		
		SetupPage sp = hp.openOtherModules(driver);
		//assertTrue(sp.getNumberOfActivatedModules().contains("6"));
		sp.activateOtherModules();
		//assertTrue(sp.getNumberOfActivatedModules().contains("12"));
		
	}
}
