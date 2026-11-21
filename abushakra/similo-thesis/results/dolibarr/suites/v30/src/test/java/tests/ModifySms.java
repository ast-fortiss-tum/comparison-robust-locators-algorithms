package tests;

import org.junit.Test;

import pageobjects.HomePage;
import pageobjects.SmsPage;
import utils.BaseTest;

public class ModifySms extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		hp.openSetupMenu();
		
		SmsPage mp = hp.openSms(driver);
		mp.smsSetup();
	}
}
