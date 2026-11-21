package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.HomePage;
import pageobjects.SetupPage;
import utils.BaseTest;

public class DeleteSetup extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		SetupPage sp = hp.goToNewSetup(driver);
		
		String[] param = {"", "", "", "", "",
				"", "", "", ""};
		
		sp.editCompany(param);
		assertTrue(sp.getCompany().contains(""));
	}
}
