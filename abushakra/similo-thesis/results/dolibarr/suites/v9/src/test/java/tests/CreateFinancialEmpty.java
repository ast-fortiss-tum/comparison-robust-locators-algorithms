package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.BankPage;
import pageobjects.HomePage;
import utils.BaseTest;

public class CreateFinancialEmpty extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		BankPage pp = hp.goToBank(driver);
		pp.addNewBankAccount("", "");
		
		//assertTrue(pp.getErrorMsg().contains("Field 'Ref.' is required"));
		assertTrue(pp.getErrorMsg().contains("Field 'Bank or cash label' is required"));
		
	}
}
