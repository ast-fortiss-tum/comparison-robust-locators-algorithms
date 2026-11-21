package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.FinancialPage;
import pageobjects.HomePage;
import utils.BaseTest;

public class CreateInvoiceEmpty extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		FinancialPage pp = hp.goToFinancial(driver);
		pp.addNewProposal("", "");
		
		assertTrue(pp.getErrorMsg().contains("Field 'Customer' is required"));
		
	}
}
