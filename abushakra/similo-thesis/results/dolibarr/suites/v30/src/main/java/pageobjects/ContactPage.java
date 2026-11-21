package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.Select;

public class ContactPage {
	
	@FindBy(id="lastname")
	private WebElement lastname;
	
	@FindBy(id="firstname")
	private WebElement firstname;
	
	@FindBy(id="civility_id")
	private WebElement civility_id;
	
	@FindBy(id="title")
	private WebElement post_function;
	
	@FindBy(id="address")
	private WebElement address;
	
	@FindBy(id="zipcode")
	private WebElement zipcode;
	
	@FindBy(id="town")
	private WebElement town;
	
	@FindBy(id="phone_pro")
	private WebElement phone_pro;
	
	@FindBy(id="phone_perso")
	private WebElement phone_perso;
	
	@FindBy(id="fax")
	private WebElement fax;
	
	@FindBy(id="phone_mobile")
	private WebElement phone_mobile;
	
	@FindBy(id="email")
	private WebElement email;
	
	@FindBy(id="jabberid")
	private WebElement jabberid;
	
	@FindBy(id="skype")
	private WebElement skype;
	
	@FindBy(id="priv")
	private WebElement priv;
	
	@FindBy(name="add")
	private WebElement add;
	
	@FindBy(xpath=".//*[@id='mainbody']/div[5]/div/div[2]/div")
	private WebElement error_msg;
	
	@FindBy(xpath=".//*[@id='id-left']/div/div[3]/div[4]/a")
	private WebElement listContacts;
	
	@FindBy(linkText="Stocco")
	private WebElement firstContact;
	
	// delete
	
	@FindBy(linkText="Delete")
	private WebElement delete;
	
	@FindBy(xpath=".//*[@id='mainbody']/div[5]/div[3]/div/button[1]")
	private WebElement yes;
	
	// personal data
	
	@FindBy(id="perso")
	private WebElement personalData;
	
	@FindBy(linkText="Modify")
	private WebElement modify;
	
	@FindBy(id="birthdayButton")
	private WebElement birthdayButton;
	
	@FindBy(xpath=".//*[@id='mainbody']/div[5]/table/tbody/tr[6]/td[1]")
	private WebElement selected_day;
	
	@FindBy(name="birthday_alert")
	private WebElement birthday_alert;
	
	@FindBy(name="save")
	private WebElement save;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[6]/table/tbody/tr[5]/td[2]")
	private WebElement birthday_alert_active_msg;
	
	
	public ContactPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public void modifyPersonalData(){
		firstContact.click();
		personalData.click();
		modify.click();
		birthdayButton.click();
		selected_day.click();
		birthday_alert.click();
		save.click();
	}
	
	public String getAlertActive(){
		return birthday_alert_active_msg.getText();
	}
	
	public String getFirstContact(){
		return firstContact.getText();
	}
	
	public void deleteUser(){
		firstContact.click();
		delete.click();
		yes.click();
	}
	
	public void listContacts(){
		listContacts.click();
	}
	
	public String getErrorMsg(){
		return error_msg.getText();
	}
	
	public void addContact(String[] param){
		lastname.sendKeys(param[0]);
		firstname.sendKeys(param[1]);
		new Select(civility_id).selectByVisibleText("Mrs.");
		new Select(civility_id).selectByVisibleText("Mr.");
		new Select(civility_id).selectByVisibleText("Ms.");
		new Select(civility_id).selectByVisibleText("Master");
		new Select(civility_id).selectByVisibleText("Doctor");
		new Select(civility_id).selectByIndex(0);
		post_function.sendKeys(param[2]);
		address.sendKeys(param[3]);
		zipcode.sendKeys(param[4]);
		town.sendKeys(param[5]);
		phone_pro.sendKeys(param[6]);
		phone_perso.sendKeys(param[7]);
		fax.sendKeys(param[8]);
		phone_mobile.sendKeys(param[9]);
		email.sendKeys(param[10]);
		jabberid.sendKeys(param[11]);
		new Select(priv).selectByVisibleText("Shared");
		new Select(priv).selectByVisibleText("Private");
		add.click();
	}
	
}
