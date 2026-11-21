package tests;

import org.junit.Test;

import pageobjects.DisplayPage;
import pageobjects.HomePage;
import utils.BaseTest;

public class ModifyDisplay extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		hp.openSetupMenu();
		
		DisplayPage mp = hp.openDisplay(driver);
		mp.modifyDisplay();
		
	}
}
