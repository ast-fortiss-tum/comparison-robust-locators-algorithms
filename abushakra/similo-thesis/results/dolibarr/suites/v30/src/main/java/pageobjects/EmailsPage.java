package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.Select;

public class EmailsPage {
	
	@FindBy(linkText="Modify")
	private WebElement modify;
				   
	@FindBy(id="MAIN_DISABLE_ALL_MAILS")
	private WebElement disable_emails;
	
	@FindBy(name="MAIN_MAIL_EMAIL_FROM")
	private WebElement email_from;
	
	@FindBy(name="save")
	private WebElement save;

	public EmailsPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public void emailSetup() throws InterruptedException {
		modify.click();
		new Select(disable_emails).selectByVisibleText("Yes");
		email_from.clear();
		email_from.sendKeys("andrea.stocco@dibris.unige.it");
		save.click();
	}
	
}
