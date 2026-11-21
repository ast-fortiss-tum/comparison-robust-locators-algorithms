package tests;

import org.junit.Test;

import pageobjects.AlertsPage;
import pageobjects.HomePage;
import utils.BaseTest;

public class ModifyAlerts extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		hp.openSetupMenu();
		
		String[] param = {"31", "7", "31", "62", "0", "31"};
		AlertsPage mp = hp.openAlerts(driver);
		mp.changeToleranceDelays(param);
		
	}
}
