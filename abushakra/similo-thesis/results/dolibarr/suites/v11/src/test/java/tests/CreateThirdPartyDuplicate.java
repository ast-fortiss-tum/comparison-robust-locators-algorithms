package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.HomePage;
import pageobjects.ThirdPartyPage;
import utils.BaseTest;

public class CreateThirdPartyDuplicate extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		ThirdPartyPage tp = hp.openThirdPartyPage(driver);
		
		
		String[] param = {"Apple", "001", "Apple St. 1/1", "1000", "Palo Alto", "apple@mail.it", "www.apple.com",
				"123456", "123457", "ap1", "ap2", "ap3", "ap4", "ap5", "ap6", "20000000000"};
		
		tp.createNewThirdParty(param);
		assertTrue(tp.getErrorMsg().contains("Duplicate"));
		
	}
}
