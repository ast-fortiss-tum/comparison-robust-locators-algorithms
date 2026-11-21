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

		String[] param = { "", "", "", "", "", "", "", "", "", "", "", "" };

		ContactPage cp = tp.goToContactPage(driver);

		cp.addContact(param);

		assertTrue(cp.getErrorMsg().contains("Field 'Lastname / Label' is required"));

	}
}
