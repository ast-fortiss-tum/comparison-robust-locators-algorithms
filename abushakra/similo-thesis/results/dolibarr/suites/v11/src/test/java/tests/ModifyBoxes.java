package tests;

import org.junit.Test;

import pageobjects.BoxesPage;
import pageobjects.HomePage;
import utils.BaseTest;

public class ModifyBoxes extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		hp.openSetupMenu();
		
		BoxesPage mp = hp.openBoxes(driver);
		
		mp.changeMaxNumberOfBoxes("3");
		mp.changeMaxNumberOfBoxes("");
		
	}
}
