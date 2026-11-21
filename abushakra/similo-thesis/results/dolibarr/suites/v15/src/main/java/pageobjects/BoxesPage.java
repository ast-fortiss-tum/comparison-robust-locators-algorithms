package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class BoxesPage {
	
	@FindBy(name="MAIN_BOXES_MAXLINES")
	private WebElement max_lines;
	
	@FindBy(name="Button")
	private WebElement save;
	
	@FindBy(xpath="//*[@id='login']/table/tbody/tr/td")
	private WebElement version;

	
	public BoxesPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public void changeMaxNumberOfBoxes(String max) throws InterruptedException {
		max_lines.sendKeys(max);
		save.click();
	}
	
	public String getVersion(){
		return version.getText();
	}
	
}
