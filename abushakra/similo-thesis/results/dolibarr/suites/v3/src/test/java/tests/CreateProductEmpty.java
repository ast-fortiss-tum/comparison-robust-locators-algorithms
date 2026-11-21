package tests;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pageobjects.HomePage;
import pageobjects.ProductsPage;
import utils.BaseTest;

public class CreateProductEmpty extends BaseTest {

	@Test
	public void test() throws InterruptedException {
		ip.login("admin", "admin");
		HomePage hp = new HomePage(driver);
		
		ProductsPage pp = hp.goToProducts(driver);
		pp.addNewProduct("", "");
		
		//assertTrue(pp.getErrorMsg().contains("Field 'Label' is required"));
		assertTrue(pp.getErrorMsg().contains("Field 'Ref.' is required"));
		
	}
}
