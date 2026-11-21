package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class HomePage {
	
	@FindBy(linkText="admin")
	private WebElement loggedUser;
	
	@FindBy(linkText="Mandatory setup parameters are not yet defined")
	private WebElement setupMandatoryParameters;
	
	@FindBy(linkText="Setup")
	private WebElement setup;
	
	@FindBy(linkText="Setup -> Company/foundation")
	private WebElement setupCompany;
	
	@FindBy(linkText="Modules")
	private WebElement modules;
	
	@FindBy(linkText="Other modules")
	private WebElement other_modules;
	
	@FindBy(linkText="Interfaces modules")
	private WebElement interfaces_modules;
	
	@FindBy(linkText="Menus")
	private WebElement menus;
	
	@FindBy(linkText="Display")
	private WebElement display;
	
	@FindBy(linkText="Boxes")
	private WebElement boxes;
	
	@FindBy(linkText="Alerts")
	private WebElement alerts;
	
	@FindBy(linkText="E-mails")
	private WebElement emails;
	
	@FindBy(linkText="Sms")
	private WebElement sms;
	
	@FindBy(linkText="Other setup")
	private WebElement other_setup;
	
	@FindBy(id="mainmenua_companies")
	private WebElement third_party;
	
	@FindBy(id="mainmenua_products")
	private WebElement products;
	
	@FindBy(id="mainmenua_commercial")
	private WebElement commercial;
	
	@FindBy(id="mainmenua_accountancy")
	private WebElement accountancy;
	
	@FindBy(id="mainmenua_bank")
	private WebElement bank;
	
	@FindBy(id="mainmenua_holiday")
	private WebElement hrm;
	
	@FindBy(xpath="//img[@class='login']")
	private WebElement logout;
	
	public HomePage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public void logout(){
		logout.click();
	}
	
	public HrmPage goToHrm(WebDriver driver){
		hrm.click();
		return new HrmPage(driver);
	}
	
	public BankPage goToBank(WebDriver driver){
		bank.click();
		return new BankPage(driver);
	}
	
	public FinancialPage goToFinancial(WebDriver driver){
		accountancy.click();
		return new FinancialPage(driver);
	}
	
	public CommercialPage goToCommercial(WebDriver driver){
		commercial.click();
		return new CommercialPage(driver);
	}
	
	public ProductsPage goToProducts(WebDriver driver){
		products.click();
		return new ProductsPage(driver);
	}
	
	public String getLoggedUser(){
		return loggedUser.getText();
	}
	
	public SetupPage goToSetup(WebDriver driver) throws InterruptedException{
		setup.click();
		setupCompany.click();
		return new SetupPage(driver);
	}
	
	@FindBy(linkText="Setup -> Company/foundation")
	private WebElement setupCompanyLink;

	public SetupPage goToNewSetup(WebDriver driver) throws InterruptedException{
		//setupMandatoryParameters.click();
		setupCompanyLink.click();
		
		return new SetupPage(driver);
	}
	

	public SetupPage goToNewModuleSetup(WebDriver driver) throws InterruptedException{
		modules.click();
		
		return new SetupPage(driver);
	}
	
	public void openSetupMenu(){
		setup.click();
	}
	
	public SetupPage openOtherModules(WebDriver driver){
		modules.click();
		other_modules.click();
		return new SetupPage(driver);
	}
	
	public SetupPage openInterfaceModules(WebDriver driver){
		modules.click();
		interfaces_modules.click();
		return new SetupPage(driver);
	}
	
	public MenuPage openMenus(WebDriver driver){
		menus.click();
		return new MenuPage(driver);
	}
	
	public DisplayPage openDisplay(WebDriver driver){
		display.click();
		return new DisplayPage(driver);
	}
	
	public BoxesPage openBoxes(WebDriver driver){
		boxes.click();
		return new BoxesPage(driver);
	}
	
	public AlertsPage openAlerts(WebDriver driver){
		alerts.click();
		return new AlertsPage(driver);
	}
	
	public EmailsPage openEmails(WebDriver driver){
		emails.click();
		return new EmailsPage(driver);
	}
	
	public SmsPage openSms(WebDriver driver){
		sms.click();
		return new SmsPage(driver);
	}
	
	public OtherSetupPage openOtherSetupPage(WebDriver driver){
		other_setup.click();
		return new OtherSetupPage(driver);
	}
	
	public ThirdPartyPage openThirdPartyPage(WebDriver driver){
		third_party.click();
		return new ThirdPartyPage(driver);
	}
	
}
