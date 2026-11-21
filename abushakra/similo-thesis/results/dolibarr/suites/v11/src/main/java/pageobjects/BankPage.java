package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.Select;

public class BankPage {
	
	@FindBy(linkText="New financial account")
	private WebElement new_invoice;
	
	@FindBy(linkText="Categories")
	private WebElement tags;
				   
	@FindBy(xpath=".//*[contains(@class,'jnotify-notification-error')]")
	private WebElement erroMsg;
	
	@FindBy(name="ref")
	private WebElement ref;
	
	@FindBy(name="label")
	private WebElement label;
	
	@FindBy(xpath=".//*[@value='Create account']")
	private WebElement create;

	@FindBy(xpath=".//*[@id='id-right']/div/div/div[8]/table[1]/tbody/tr[1]/td[2]/div/div[1]")
	private WebElement firstProduct;
	
	// MODIFY
	
	@FindBy(linkText="1")
	private WebElement firstBankAccount;

	@FindBy(linkText="Modify")
	private WebElement modify;
	
	@FindBy(xpath=".//*[@value='Modify']")
	private WebElement modify2;
	
	// DELETE
	
	@FindBy(linkText="Bank/Cash")
	private WebElement list;
	
	@FindBy(xpath=".//*[@id='id-right']/div/form/table/tbody/tr[4]/td[1]/table/tbody/tr/td[1]/a[2]")
	private WebElement first;
	
	@FindBy(linkText="Delete")
	private WebElement delete;
	
	@FindBy(name="confirm")
	private WebElement confirm;
	
	@FindBy(xpath=".//*[@value='Validate']")
	private WebElement yes;

	// TAGS
	
	@FindBy(name="label")
	private WebElement labelName;
	
	@FindBy(xpath=".//*[@value='Add']")
	private WebElement add;
	
	@FindBy(xpath=".//*[@id='undertopmenu']/tbody/tr/td[2]/div/form/table/tbody/tr[2]/td[2]")
	private WebElement firstLabel;
	
	@FindBy(xpath=".//*[@id='id-right']/div/form/table/tbody/tr[2]/td[3]/a[1]/img")
	private WebElement modifyLabel;
	
	@FindBy(name="update")
	private WebElement updateLabel;
	
	@FindBy(xpath=".//*[@id='undertopmenu']/tbody/tr/td[2]/div/form/table/tbody/tr[2]/td[3]/a/img")
	private WebElement deleteLabel;
	
	// FILTER
	
	@FindBy(linkText="New deposit")
	private WebElement new_deposit;
	
	@FindBy(id="fdButtonNow")
	private WebElement now;
	
	@FindBy(name="filter")
	private WebElement filter;
	
	@FindBy(name="removefilter")
	private WebElement remove_filter;
	
	@FindBy(xpath=".//*[@id='undertopmenu']/tbody/tr/td[2]/div/b")
	private WebElement filter_msg; 
	
	
	public BankPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public String getFilterMsg(){
		return filter_msg.getText();
	}
	
	public void addFilter(){
		new_deposit.click();
		now.click();
		filter.click();
	}
	
	public void addFilterRemoveFilter(){
		new_deposit.click();
		now.click();
		filter.click();
		remove_filter.click();
	}
	
	public void deleteLabel(){
		tags.click();
		deleteLabel.click();
	}
	
	public void modifyLabel(String s) throws InterruptedException{
		tags.click();
		Thread.sleep(5000);
		modifyLabel.click();
		labelName.clear();
		labelName.sendKeys(s);
		updateLabel.click();
	}
	
	public String getFirstLabel(){
		return firstLabel.getText();
	}
	
	public void createLabel(String s){
		tags.click();
		labelName.sendKeys(s);
		add.click();
	}
	
	public void deleteBankAccount(){
		list.click();
		firstBankAccount.click();
		delete.click();
		new Select(confirm).selectByVisibleText("Yes");
		yes.click();
	}
	
	public void addNewBankAccount(String s, String s1) throws InterruptedException{
		new_invoice.click();
		ref.sendKeys(s);
		label.sendKeys(s1);
		create.click();
	}
	
	public void modifyBankAccount(String s, String s1) throws InterruptedException{
		firstBankAccount.click();
		modify.click();
		ref.clear();
		ref.sendKeys(s);
		label.clear();
		label.sendKeys(s1);
		modify2.click();
	}
	
	public String getFirstBankAccount(){
		return firstBankAccount.getText();
	}
	
	public String getErrorMsg(){
		return erroMsg.getText();
	}
	
}
