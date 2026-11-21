package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.ContactPage;
import pageobjects.HomePage;
import pageobjects.ThirdPartyPage;
import utils.BaseTest;

public class AddContactOk extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		ThirdPartyPage tp = hp.openThirdPartyPage(driver);
		
		String[] param = {"Stocco", "Andrea", "PhD student", "MSc", "somewhere in Italy", 
				"18010", "Genova", "1234", "5678", "9012", "as@mail.it", "tsigalko18"};
		
		ContactPage cp = tp.goToContactPage(driver);
		
		cp.addContact(param);
		
		cp.listContacts();
		
		assertTrue(cp.getFirstContact().contains("Stocco"));
		
	}
}
