package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.BankPage;
import pageobjects.HomePage;
import utils.BaseTest;

public class CreateFinancialDuplicate extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		BankPage pp = hp.goToBank(driver);
		pp.addNewBankAccount("0001", "1");
		
		assertTrue(pp.getErrorMsg().contains("Financial account label already exists"));
		
	}
}
