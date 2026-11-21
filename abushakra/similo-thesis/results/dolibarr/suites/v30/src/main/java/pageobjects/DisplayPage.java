package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.Select;

public class DisplayPage {
	
	@FindBy(linkText="Modify")
	private WebElement modify;
	
	@FindBy(xpath=".//*[@id='id-right']/div/form/table[3]/tbody/tr[3]/td/table/tbody/tr/td/div/div[1]/input")
	private WebElement skin_1;
	
	@FindBy(xpath=".//*[@id='id-right']/div/form/table[3]/tbody/tr[3]/td/table/tbody/tr/td/div/div[2]/input")
	private WebElement skin_2;
	
	@FindBy(xpath=".//*[@id='id-right']/div/form/table[3]/tbody/tr[3]/td/table/tbody/tr/td/div/div[3]/input")
	private WebElement skin_3;
	
	@FindBy(xpath=".//*[@id='id-right']/div/form/table[3]/tbody/tr[3]/td/table/tbody/tr/td/div/div[4]/input")
	private WebElement skin_4;
	
	@FindBy(xpath=".//*[@id='id-right']/div/form/table[3]/tbody/tr[3]/td/table/tbody/tr/td/div/div[5]/input")
	private WebElement skin_5;
	
	@FindBy(id="MAIN_SEARCHFORM_SOCIETE")
	private WebElement companies;
	
	@FindBy(id="MAIN_SEARCHFORM_CONTACT")
	private WebElement contacts;
			
	@FindBy(id="MAIN_SEARCHFORM_PRODUITSERVICE")
	private WebElement products; 
	
	@FindBy(id="MAIN_SHOW_LOGO")
	private WebElement show_logo;
	
	@FindBy(id="main_disable_javascript")
	private WebElement javascript;
	
	@FindBy(name="MAIN_START_WEEK")
	private WebElement first_day;	
	
	@FindBy(id="main_home")
	private WebElement login_message;
	
	@FindBy(id="main_motd")
	private WebElement message_of_the_day;
	
	@FindBy(xpath=".//*[@id='id-right']/div/form/div/input")
	private WebElement save;
	
	@FindBy(xpath="//*[@id='login']/table/tbody/tr/td")
	private WebElement version;

	
	public DisplayPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public void modifyDisplay() throws InterruptedException {
		modify.click();
		skin_1.click();
		skin_2.click();
		skin_3.click();
		skin_4.click();
		skin_5.click();
		new Select(companies).selectByVisibleText("Yes");
		new Select(contacts).selectByVisibleText("Yes");
		new Select(products).selectByVisibleText("Yes");
		new Select(show_logo).selectByVisibleText("Yes");
		new Select(javascript).selectByVisibleText("No");
		new Select(first_day).selectByVisibleText("Monday");
		login_message.sendKeys("");
		message_of_the_day.sendKeys("");
		save.click();
	}
	
	public String getVersion(){
		return version.getText();
	}
	
}
