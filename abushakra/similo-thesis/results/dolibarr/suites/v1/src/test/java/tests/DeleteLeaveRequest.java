package tests;

import org.junit.Test;

import pageobjects.HomePage;
import pageobjects.HrmPage;
import utils.BaseTest;

public class DeleteLeaveRequest extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		HrmPage pp = hp.goToHrm(driver);
		pp.deleteLeaveRequest();
				
	}
}
