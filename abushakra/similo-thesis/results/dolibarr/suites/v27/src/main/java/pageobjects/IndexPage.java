package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class IndexPage {
	
	@FindBy(id="username")
	private WebElement username;
	
	@FindBy(id="password")
	private WebElement password;
	
	@FindBy(xpath=".//*[@id='login']/div/div[2]/input")
	private WebElement loginButton;
	
	@FindBy(xpath="//*[@id='login']/table/tbody/tr/td")
	private WebElement version;
	
	@FindBy(xpath="html/body/center/div/div")
	private WebElement errorMsg;
	
	public IndexPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public String getVersion(){
		return version.getText();
	}
	
	public String getError(){
		return errorMsg.getText();
	}
	
	public void login(String u, String p) throws InterruptedException{
		username.clear();
		username.sendKeys(u);
		password.sendKeys(p);
		loginButton.click();
	}
	
}
