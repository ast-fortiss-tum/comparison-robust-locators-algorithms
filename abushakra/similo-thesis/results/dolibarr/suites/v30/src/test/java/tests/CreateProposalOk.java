package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.CommercialPage;
import pageobjects.HomePage;
import utils.BaseTest;

public class CreateProposalOk extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		CommercialPage pp = hp.goToCommercial(driver);
		pp.addNewProposal("1234", "Apple");
		
		assertTrue(pp.getFirstProposal().contains("PROV"));
		
	}
}
