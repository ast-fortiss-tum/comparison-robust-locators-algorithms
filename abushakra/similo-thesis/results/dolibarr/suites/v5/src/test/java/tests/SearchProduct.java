package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.HomePage;
import pageobjects.ProductsPage;
import utils.BaseTest;

public class SearchProduct extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		ProductsPage pp = hp.goToProducts(driver);
		pp.search("1234");
		
		assertTrue(pp.getFirstProduct().contains("1234"));
		
	}
}
