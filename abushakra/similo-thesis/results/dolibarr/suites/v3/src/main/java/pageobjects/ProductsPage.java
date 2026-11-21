package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class ProductsPage {
	
	@FindBy(linkText="New product")
	private WebElement new_product;
				   
	@FindBy(xpath=".//*[@id='undertopmenu']/tbody/tr/td[2]/div/form/div")
	private WebElement erroMsg;
	
	@FindBy(name="ref")
	private WebElement ref;
	
	@FindBy(name="libelle")
	private WebElement label;
	
	@FindBy(xpath=".//*[@value='Create']")
	private WebElement create;

	@FindBy(xpath=".//*[@id='id-right']/div/div/div[7]/table/tbody/tr[1]/td[2]/div/div")
	private WebElement firstProduct;
	
	// SEARCH
	
	@FindBy(name="sref")
	private WebElement search_ref;

	@FindBy(xpath=".//*[@id='undertopmenu']/tbody/tr/td[2]/div/table[2]/tbody/tr/td[1]/form/table/tbody/tr[2]/td[3]/input")
	private WebElement search;
	
	// DELETE
	
	@FindBy(linkText="List")
	private WebElement list;
	
	@FindBy(linkText="1234")
	private WebElement first;
	
	@FindBy(xpath=".//*[@class='butActionDelete']")
	private WebElement delete;
	
	@FindBy(xpath=".//*[@id='mainbody']/div[3]/div[3]/div/button[1]")
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
		list.click();
		return first.getText();
	}
	
	public String getErrorMsg(){
		return erroMsg.getText();
	}
	
}
