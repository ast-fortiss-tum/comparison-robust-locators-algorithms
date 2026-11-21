package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.HomePage;
import pageobjects.HrmPage;
import utils.BaseTest;

public class CreateLeaveRequest extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		HrmPage pp = hp.goToHrm(driver);
		pp.createLeaveRequest();
		
		assertTrue(pp.getSuperAdmin().contains("SuperAdmin"));
		
	}
}
