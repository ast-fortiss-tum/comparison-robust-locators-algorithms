package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.ContactPage;
import pageobjects.HomePage;
import pageobjects.ThirdPartyPage;
import utils.BaseTest;

public class ModifyPersonalDataActive extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		ThirdPartyPage tp = hp.openThirdPartyPage(driver);
		
		ContactPage cp = tp.goToContactPage(driver);
		
		cp.listContacts();
		cp.modifyPersonalData();	
		
		assertTrue(cp.getAlertActive().contains("active"));
		
	}
}
