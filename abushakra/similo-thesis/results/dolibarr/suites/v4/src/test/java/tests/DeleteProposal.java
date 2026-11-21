package tests;

import org.junit.Test;

import pageobjects.CommercialPage;
import pageobjects.HomePage;
import utils.BaseTest;

public class DeleteProposal extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		CommercialPage pp = hp.goToCommercial(driver);
		pp.deleteProposal();
		
		//assertTrue(pp.getFirstProduct().contains("1234"));
		
	}
}
