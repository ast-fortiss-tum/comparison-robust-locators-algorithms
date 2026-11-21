package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.BankPage;
import pageobjects.HomePage;
import utils.BaseTest;

public class AddFilterRemoveFilter extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		BankPage pp = hp.goToBank(driver);
		pp.addFilterRemoveFilter();
		
		assertTrue(pp.getFilterMsg().contains("No checks waiting for deposit."));
		
	}
}
