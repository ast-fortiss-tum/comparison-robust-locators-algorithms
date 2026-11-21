package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.Select;

public class CommercialPage {
	
	@FindBy(linkText="New proposal")
	private WebElement new_proposal;
				   
	@FindBy(xpath=".//*[@id='mainbody']/div[4]/div/div[2]/div")
	private WebElement erroMsg;
	
	@FindBy(name="ref_client")
	private WebElement ref_client;
	
	@FindBy(id="socid")
	private WebElement label;
	
	//@FindBy(id="s2id_autogen1_search")
	//private WebElement label2;
	
	@FindBy(xpath=".//*[@value='Create draft']")
	private WebElement create;

	@FindBy(xpath=".//*[@id='id-right']/div/div/div[6]/table/tbody/tr[1]/td[2]/div/div[1]")
	private WebElement firstProduct;
	
	// SEARCH
	
	@FindBy(name="sf_ref")
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
	
	@FindBy(xpath=".//*[@id='mainbody']/div[4]/div[3]/div/button[1]")
	private WebElement yes;
	
	
	
	public CommercialPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public void deleteProposal(){
		list.click();
		search_result.click();
		delete.click();
		yes.click();
	}
	
	public void addNewProposal(String s) throws InterruptedException{
		new_proposal.click();
		ref_client.sendKeys(s);
//		label.click();
		new Select(label).selectByVisibleText(s);
//		label.sendKeys(s1);
//		Thread.sleep(1000);
//		label2.sendKeys(Keys.TAB);
		create.click();
	}
	
	public void search(String s){
		search_ref.sendKeys(s);
		search.click();
	}
	
	public String getFirstProposal(){
		list.click();
		return first.getText();
	}
	
	public String getFirstSearchResult(){
		return search_result.getText();
	}
	
	public String getErrorMsg(){
		return erroMsg.getText();
	}
	
}
