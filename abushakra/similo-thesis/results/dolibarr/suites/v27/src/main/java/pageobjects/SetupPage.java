package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.Select;

public class SetupPage {
	
	@FindBy(linkText="Modify")
	private WebElement modify;
	
	@FindBy(name="nom")
	private WebElement name;
	
	@FindBy(name="address")
	private WebElement address;
	
	@FindBy(name="zipcode")
	private WebElement zipcode;
	
	@FindBy(name="town")
	private WebElement town;
	
	@FindBy(id="selectcountry_id")
	private WebElement country;
	
	@FindBy(id="state_id")
	private WebElement state_province;
	
	@FindBy(name="tel")
	private WebElement phone;
	
	@FindBy(name="mail")
	private WebElement email;
	
	@FindBy(name="MAIN_INFO_SOCIETE_MANAGERS")
	private WebElement director;
	
	@FindBy(name="capital")
	private WebElement capital;
	
	@FindBy(name="save")
	private WebElement save;
	
	@FindBy(xpath="//*[@id='login']/table/tbody/tr/td")
	private WebElement version;
	
	@FindBy(xpath=".//*[@id='id-right']/div/table[2]/tbody/tr[2]/td[2]")
	private WebElement new_company;
	
	// MAIN MODULES
	
	@FindBy(xpath=".//*[@id='id-right']/div/div[2]/table/tbody/tr[4]/td[5]/a/img")
	private WebElement third_parties;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div[2]/table/tbody/tr[13]/td[5]/a/img")
	private WebElement accounting;
	
	              //".//*[@id='id-right']/div/div[2]/table/tbody/tr[21]/td[5]/a/img"
	@FindBy(xpath=".//*[@id='id-right']/div/div[2]/table/tbody/tr[26]/td[5]/a/img")
	private WebElement products;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div[2]/table/tbody/tr[21]/td[5]/a/img")
	private WebElement members;
	
	@FindBy(xpath=".//*[@id='id-right']/div/b")
	private WebElement numberOfActivatedModules;
	
	// OTHER MODULES
	
	@FindBy(xpath=".//*[@id='id-right']/div/div[2]/table/tbody/tr[3]/td[5]/a/img")
	private WebElement logs;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div[2]/table/tbody/tr[5]/td[5]/a/img")
	private WebElement margins;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div[2]/table/tbody/tr[7]/td[5]/a/img")
	private WebElement resource;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div[2]/table/tbody/tr[9]/td[5]/a/img")
	private WebElement tags; 

	// INTERFACE MODULES
	
	@FindBy(xpath=".//*[@id='id-right']/div/div[2]/table/tbody/tr[4]/td[5]/a/img")
	private WebElement external;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div[2]/table/tbody/tr[5]/td[5]/a/img")
	private WebElement ftp;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div[2]/table/tbody/tr[7]/td[5]/a/img")
	private WebElement paypal;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div[2]/table/tbody/tr[12]/td[5]/a/img")
	private WebElement lpdap;
	
	
	public SetupPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public void activateInterfaceModules() throws InterruptedException{
		//skype.click();
		//Thread.sleep(1000);
		//driver.navigate().back();
		
		external.click();
		ftp.click();
		paypal.click();
		Thread.sleep(1000);
		lpdap.click();
	}
	
	public void activateOtherModules(){
		//cron.click();
		logs.click();
		margins.click();
		resource.click();
		//tags.click();
	}
	
	public String getVersion(){
		return version.getText();
	}
	
	public String getCompany(){
		return new_company.getText();
	}
	
	public void editCompany(String[] param){
		// 3.4.0: the setup link opens the edit form directly, there is no Modify link any more
		name.clear();
		name.sendKeys(param[0]);
		address.clear();
		address.sendKeys(param[1]);
		zipcode.clear();
		zipcode.sendKeys(param[2]);
		town.clear();
		town.sendKeys(param[3]);
		new Select(country).selectByVisibleText(param[9]);
		//country.sendKeys(Keys.TAB);
		//state_province.clear();
		state_province.sendKeys(param[4]);
		phone.clear();
		phone.sendKeys(param[5]);
		email.clear();
		email.sendKeys(param[6]);
		//director.clear();
		//director.sendKeys(param[7]);
		capital.clear();
		capital.sendKeys(param[8]);
		save.click();
	}

	public void activateMainModules() throws InterruptedException {
		third_parties.click();
		Thread.sleep(1000);
		accounting.click();
		Thread.sleep(1000);
		products.click();
		Thread.sleep(1000);
		members.click();
	}
	
	public String getNumberOfActivatedModules(){
		return numberOfActivatedModules.getText();
	}
	
}
