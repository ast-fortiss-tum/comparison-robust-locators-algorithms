package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.Select;

public class MenuPage {
	
	@FindBy(linkText="Modify")
	private WebElement modify;
	
	@FindBy(id="MAIN_MENU_STANDARD")
	private WebElement menu_standard;
	
	@FindBy(id="MAIN_MENUFRONT_STANDARD")
	private WebElement menu_front_standard;
	
	@FindBy(id="MAIN_MENU_SMARTPHONE")
	private WebElement menu_smartphone;
	
	@FindBy(id="MAIN_MENUFRONT_SMARTPHONE")
	private WebElement menu_front_smartphone;
	
	@FindBy(name="save")
	private WebElement save;
	
	@FindBy(xpath="//*[@id='login']/table/tbody/tr/td")
	private WebElement version;
	
	
	public MenuPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public void modifyMenu() throws InterruptedException {
		modify.click();
		new Select(menu_standard).selectByVisibleText("eldy_backoffice");
		new Select(menu_front_standard).selectByVisibleText("eldy_backoffice");
		new Select(menu_smartphone).selectByVisibleText("eldy_backoffice");
		new Select(menu_front_smartphone).selectByVisibleText("eldy_backoffice");
		save.click();
	}
	
	public String getVersion(){
		return version.getText();
	}
	
}
