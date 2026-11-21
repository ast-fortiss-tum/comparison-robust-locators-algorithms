package tests;

import org.junit.Test;

import pageobjects.CommercialPage;
import pageobjects.HomePage;
import utils.BaseTest;

public class SearchProposal extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		CommercialPage pp = hp.goToCommercial(driver);
		pp.search("Apple");
		
		//assertTrue(pp.getFirstSearchResult().contains("PROV"));
		
	}
}
