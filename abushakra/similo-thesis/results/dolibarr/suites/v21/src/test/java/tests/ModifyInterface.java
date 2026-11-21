package tests;

import org.junit.Test;

import pageobjects.HomePage;
import pageobjects.SetupPage;
import utils.BaseTest;

public class ModifyInterface extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		hp.openSetupMenu();
		
		SetupPage sp = hp.openInterfaceModules(driver);
		//assertTrue(sp.getNumberOfActivatedModules().contains("12"));
		sp.activateInterfaceModules();
		//assertTrue(sp.getNumberOfActivatedModules().contains("16"));
		
	}
}
