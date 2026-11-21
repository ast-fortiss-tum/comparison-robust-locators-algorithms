package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class OtherSetupPage {
	
	@FindBy(name="constname")
	private WebElement name;
				   
	@FindBy(name="constvalue")
	private WebElement value;
	
	@FindBy(name="Button")
	private WebElement add;
	
	@FindBy(xpath=".//*[contains(@class,'jnotify-notification-error')]")
	private WebElement error;
	              
	@FindBy(xpath=".//*[@id='id-right']/div/table[2]/tbody/tr[6]/td[1]")
	private WebElement new_entry;
	
	@FindBy(id="value_3")
	private WebElement value3;
	
	@FindBy(id="check_3")
	private WebElement check3;
	
	@FindBy(name="delete")
	private WebElement delete;

	@FindBy(xpath=".//*[@id='id-right']/div/table[2]/tbody/tr[6]/td[1]")
	private WebElement recordSaved;
	
	public OtherSetupPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public void setup(String n, String v) throws InterruptedException {
		name.sendKeys(n);
		value.sendKeys(v);
		add.click();
	}
	
	public String getError(){
		return error.getText();
	}
	
	public String getRecordSaved(){
		return recordSaved.getText();
	}
	
	public String getNewEntry(){
		return new_entry.getText();
	}

	public void updateSetup() throws InterruptedException {
		value3.clear();
		check3.click();
		check3.click();
		delete.click();
	}
	
}
