package tests;

import org.junit.Test;

import pageobjects.HomePage;
import pageobjects.ProductsPage;
import utils.BaseTest;

public class DeleteProduct extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		ProductsPage pp = hp.goToProducts(driver);
		pp.deleteProduct();
		
		//assertTrue(pp.getFirstProduct().contains("1234"));
		
	}
}
