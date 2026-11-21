package tests;

import org.junit.Test;

import pageobjects.BankPage;
import pageobjects.HomePage;
import utils.BaseTest;

public class DeleteLabel extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		BankPage pp = hp.goToBank(driver);
		pp.deleteLabel();

	}
}
