package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class ProductsPage {
	
	@FindBy(linkText="New product")
	private WebElement new_product;
				   
	@FindBy(xpath=".//*[@id='mainbody']/div[5]/div/div[2]/div")
	private WebElement erroMsg;
	
	@FindBy(name="ref")
	private WebElement ref;
	
	@FindBy(name="label")
	private WebElement label;
	
	@FindBy(xpath=".//*[@id='id-right']/div/form/div/div[2]/input")
	private WebElement create;

	@FindBy(xpath=".//*[@id='id-right']/div/div/div[7]/table/tbody/tr[1]/td[2]/div/div")
	private WebElement firstProduct;
	
	// SEARCH
	
	@FindBy(id="sref")
	private WebElement search_ref;

	@FindBy(xpath="//*[@id='id-right']/div/div/div[1]/form/table/tbody/tr[2]/td[3]/input")
	private WebElement search;
	
	// DELETE
	
	@FindBy(linkText="List")
	private WebElement list;
	
	@FindBy(linkText="1234")
	private WebElement first;
	
	@FindBy(id="action-delete")
	private WebElement delete;
	
	@FindBy(xpath=".//*[@id='mainbody']/div[5]/div[3]/div/button[1]")
	private WebElement yes;
	
	
	
	public ProductsPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public void deleteProduct(){
		list.click();
		first.click();
		delete.click();
		yes.click();
	}
	
	public void addNewProduct(String s, String s1){
		new_product.click();
		ref.sendKeys(s);
		label.sendKeys(s1);
		create.click();
	}
	
	public void search(String s){
		search_ref.sendKeys(s);
		search.click();
	}
	
	public String getFirstProduct(){
		return firstProduct.getText();
	}
	
	public String getErrorMsg(){
		return erroMsg.getText();
	}
	
}
