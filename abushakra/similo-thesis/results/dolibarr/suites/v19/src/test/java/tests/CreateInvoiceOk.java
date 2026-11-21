package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.FinancialPage;
import pageobjects.HomePage;
import utils.BaseTest;

public class CreateInvoiceOk extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		FinancialPage pp = hp.goToFinancial(driver);
		pp.addNewProposal("Apple", "1/1/2016");
		
		assertTrue(pp.getFirstProposal().contains("PROV"));
		
	}
}
