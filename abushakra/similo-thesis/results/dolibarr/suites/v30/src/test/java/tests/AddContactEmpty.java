package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.ContactPage;
import pageobjects.HomePage;
import pageobjects.ThirdPartyPage;
import utils.BaseTest;

public class AddContactEmpty extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		ThirdPartyPage tp = hp.openThirdPartyPage(driver);
		
		String[] param = new String[12];
		
		ContactPage cp = tp.goToContactPage(driver);
		
		cp.addContact(param);
		
		assertTrue(cp.getErrorMsg().contains("Field 'Last name / Label' is required"));
		
	}
}
