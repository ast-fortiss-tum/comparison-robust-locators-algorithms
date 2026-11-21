package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.Select;

public class SmsPage {
	
	@FindBy(linkText="Modify")
	private WebElement modify;
				   
	@FindBy(id="MAIN_DISABLE_ALL_SMS")
	private WebElement disable_sms;
	
	@FindBy(name="MAIN_MAIL_SMS_FROM")
	private WebElement sms_from;
	
	@FindBy(name="save")
	private WebElement save;

	public SmsPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public void smsSetup() throws InterruptedException {
		modify.click();
		new Select(disable_sms).selectByVisibleText("Yes");
		sms_from.clear();
		sms_from.sendKeys("123456");
		save.click();
	}
	
}
