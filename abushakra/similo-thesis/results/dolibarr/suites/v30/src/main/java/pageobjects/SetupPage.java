package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class SetupPage {
	
	@FindBy(id="name")
	private WebElement name;
	
	@FindBy(id="address")
	private WebElement address;
	
	@FindBy(id="zipcode")
	private WebElement zipcode;
	
	@FindBy(id="town")
	private WebElement town;
	
	@FindBy(id="select2-chosen-2")
	private WebElement state_province;
	
	@FindBy(id="phone")
	private WebElement phone;
	
	@FindBy(id="email")
	private WebElement email;
	
	@FindBy(id="director")
	private WebElement director;
	
	@FindBy(id="capital")
	private WebElement capital;
	
	@FindBy(name="save")
	private WebElement save;
	
	@FindBy(xpath="//*[@id='login']/table/tbody/tr/td")
	private WebElement version;
	
	@FindBy(xpath=".//*[@id='id-right']/div/table[2]/tbody/tr[2]/td[2]")
	private WebElement new_company;
	
	// MAIN MODULES
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[5]/table/tbody/tr[4]/td[5]/a/img")
	private WebElement third_parties;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[5]/table/tbody/tr[13]/td[5]/a/img")
	private WebElement accounting;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[5]/table/tbody/tr[20]/td[5]/a/img")
	private WebElement leave_requests;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[5]/table/tbody/tr[21]/td[5]/a/img")
	private WebElement members;
	
	@FindBy(xpath=".//*[@id='id-right']/div/table/tbody/tr/td[3]/b")
	private WebElement numberOfActivatedModules;
	
	// OTHER MODULES
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[5]/table/tbody/tr[2]/td[5]/a/img")
	private WebElement cron;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[5]/table/tbody/tr[3]/td[5]/a/img")
	private WebElement logs;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[5]/table/tbody/tr[5]/td[5]/a/img")
	private WebElement margins;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[5]/table/tbody/tr[7]/td[5]/a/img")
	private WebElement resource;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[5]/table/tbody/tr[9]/td[5]/a/img")
	private WebElement tags; 

	// INTERFACE MODULES
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[5]/table/tbody/tr[2]/td[5]/a/img")
	private WebElement skype;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[5]/table/tbody/tr[4]/td[5]/a/img")
	private WebElement external;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[5]/table/tbody/tr[5]/td[5]/a/img")
	private WebElement ftp;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[5]/table/tbody/tr[7]/td[5]/a/img")
	private WebElement paypal;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[5]/table/tbody/tr[10]/td[5]/a/img")
	private WebElement lpdap;
	
	
	public SetupPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public void activateInterfaceModules() throws InterruptedException{
		skype.click();
		Thread.sleep(1000);
		external.click();
		ftp.click();
		paypal.click();
		Thread.sleep(1000);
		lpdap.click();
	}
	
	public void activateOtherModules(){
		cron.click();
		logs.click();
		margins.click();
		resource.click();
		tags.click();
	}
	
	public String getVersion(){
		return version.getText();
	}
	
	public String getCompany(){
		return new_company.getText();
	}
	
	public void editCompany(String[] param){
		name.sendKeys(param[0]);
		address.sendKeys(param[1]);
		zipcode.sendKeys(param[2]);
		town.sendKeys(param[3]);
		state_province.sendKeys(param[4]);
		phone.sendKeys(param[5]);
		email.sendKeys(param[6]);
		director.sendKeys(param[7]);
		capital.sendKeys(param[8]);
		save.click();
	}

	// Products and Proposals: the suite's later tests need them and its module counters (8, then 13, then 18) count them; the setup flow of the authors' app had them on by then
	@FindBy(xpath=".//*[@id='id-right']//tr[td[2][normalize-space()='Products']]/td[5]/a/img")
	private WebElement products_module;

	@FindBy(xpath=".//*[@id='id-right']//tr[td[2][normalize-space()='Proposals']]/td[5]/a/img")
	private WebElement proposals_module;

	public void activateMainModules() throws InterruptedException {
		third_parties.click();
		Thread.sleep(1000);
		accounting.click();
		Thread.sleep(1000);
		leave_requests.click();
		Thread.sleep(1000);
		members.click();
		Thread.sleep(1000);
		products_module.click();
		Thread.sleep(1000);
		proposals_module.click();
	}
	
	public String getNumberOfActivatedModules(){
		return numberOfActivatedModules.getText();
	}
	
}
