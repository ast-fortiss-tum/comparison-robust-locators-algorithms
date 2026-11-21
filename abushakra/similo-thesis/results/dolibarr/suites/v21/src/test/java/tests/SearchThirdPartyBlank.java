package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.HomePage;
import pageobjects.ThirdPartyPage;
import utils.BaseTest;

public class SearchThirdPartyBlank extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		ThirdPartyPage tp = hp.openThirdPartyPage(driver);
		
		tp.searchThirdParty("");
		
		assertTrue(tp.getSearchedCompany().contains("Apple"));
		
	}
}
