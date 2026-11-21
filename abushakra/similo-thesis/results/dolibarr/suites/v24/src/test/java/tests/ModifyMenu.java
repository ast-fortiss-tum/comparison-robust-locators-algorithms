package tests;

import org.junit.Test;

import pageobjects.HomePage;
import pageobjects.MenuPage;
import utils.BaseTest;

public class ModifyMenu extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		hp.openSetupMenu();
		
		MenuPage mp = hp.openMenus(driver);
		mp.modifyMenu();
		
	}
}
