package tests;

import org.junit.Test;

import pageobjects.FinancialPage;
import pageobjects.HomePage;
import utils.BaseTest;

public class SearchInvoice extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		FinancialPage pp = hp.goToFinancial(driver);
		pp.search("Apple");
		
		//assertTrue(pp.getFirstSearchResult().contains("PROV"));
		
	}
}
