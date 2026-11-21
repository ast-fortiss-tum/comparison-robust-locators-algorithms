package tests;

import org.junit.Test;

import pageobjects.FinancialPage;
import pageobjects.HomePage;
import utils.BaseTest;

public class DeleteInvoice extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		FinancialPage pp = hp.goToFinancial(driver);
		pp.deleteProposal();
		
		//assertTrue(pp.getFirstProduct().contains("1234"));
		
	}
}
