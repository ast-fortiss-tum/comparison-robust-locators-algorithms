package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.HomePage;
import utils.BaseTest;

public class TestLoginOk extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		assertTrue(hp.getLoggedUser().contains("SuperAdmin"));
	}
}
