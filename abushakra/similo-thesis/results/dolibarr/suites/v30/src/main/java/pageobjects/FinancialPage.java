package pageobjects;

import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class FinancialPage {
	
	@FindBy(linkText="New invoice")
	private WebElement new_invoice;
				   
	@FindBy(xpath=".//*[@id='mainbody']/div[5]/div/div[2]/div")
	private WebElement erroMsg;
	
	@FindBy(name="ref_client")
	private WebElement ref_client;
	
	@FindBy(id="s2id_autogen1")
	private WebElement customer;
	
	@FindBy(id="s2id_autogen1_search")
	private WebElement customer2;
	
	@FindBy(id="re")
	private WebElement date;
	
	@FindBy(name="bouton")
	private WebElement create;

	@FindBy(xpath=".//*[@id='id-right']/div/div/div[6]/table/tbody/tr[1]/td[2]/div/div[1]")
	private WebElement firstProduct;
	
	// SEARCH
	
	@FindBy(id="sf_ref")
	private WebElement search_ref;

	@FindBy(xpath="//*[@id='id-right']/div/div/div[1]/form/table/tbody/tr[2]/td[3]/input")
	private WebElement search;
	
	@FindBy(xpath=".//*[@id='id-right']/div/form/table/tbody/tr[4]/td[1]/table/tbody/tr/td[1]/a[2]")
	private WebElement search_result;
	
	// DELETE
	
	@FindBy(linkText="List")
	private WebElement list;
	
	@FindBy(xpath=".//*[@id='id-right']/div/form/table/tbody/tr[4]/td[1]/table/tbody/tr/td[1]/a[2]")
	private WebElement first;
	
	@FindBy(linkText="Delete")
	private WebElement delete;
	
	@FindBy(xpath=".//*[@id='mainbody']/div[5]/div[3]/div/button[1]")
	private WebElement yes;
	
	
	
	public FinancialPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public void deleteProposal(){
		list.click();
		search_result.click();
		delete.click();
		yes.click();
	}
	
	public void addNewProposal(String s, String s1) throws InterruptedException{
		new_invoice.click();
		customer.click();
		customer2.sendKeys(s);
		Thread.sleep(1000);
		customer2.sendKeys(Keys.TAB);
		date.sendKeys(s1);
		create.click();
	}
	
	public void search(String s){
		search_ref.sendKeys(s);
		search.click();
	}
	
	public String getFirstProposal(){
		return firstProduct.getText();
	}
	
	public String getFirstSearchResult(){
		return search_result.getText();
	}
	
	public String getErrorMsg(){
		return erroMsg.getText();
	}
	
}
