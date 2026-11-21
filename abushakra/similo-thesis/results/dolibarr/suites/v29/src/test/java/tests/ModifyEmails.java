package tests;

import org.junit.Test;

import pageobjects.EmailsPage;
import pageobjects.HomePage;
import utils.BaseTest;

public class ModifyEmails extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		hp.openSetupMenu();
		
		EmailsPage mp = hp.openEmails(driver);
		mp.emailSetup();
	}
}
