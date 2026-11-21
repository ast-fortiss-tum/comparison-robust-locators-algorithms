package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.HomePage;
import pageobjects.ThirdPartyPage;
import utils.BaseTest;

public class SearchListOfProspectsOk extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		ThirdPartyPage tp = hp.openThirdPartyPage(driver);
		
		String[] param = {"Apple", "1000", "Palo Alto"};
		
		tp.searchListOfProspects(param);
		
		assertTrue(tp.getSearched().contains("Apple"));
		
	}
}
