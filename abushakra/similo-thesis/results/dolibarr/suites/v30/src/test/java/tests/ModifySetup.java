package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.HomePage;
import pageobjects.SetupPage;
import utils.BaseTest;

public class ModifySetup extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		SetupPage sp = hp.goToNewSetup(driver);
		String[] param = {"Miniclip", "Piazza della Vittoria", "16131", "Genova", "Genova",
				"123456", "miniclip@mail.it", "Andrea Stocco", "1000"};
		sp.editCompany(param);
		assertTrue(sp.getCompany().contains("Miniclip"));
	}
}
