package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.Select;

public class ThirdPartyPage {
	
	// THIRD PARTY TAB
	
	@FindBy(linkText="New third party")
	private WebElement new_third_party;
	
	@FindBy(linkText="List of prospects")
	private WebElement list;
	
	@FindBy(xpath=".//*[@value='Add third party']")
	private WebElement save;
	
	@FindBy(xpath=".//*[contains(@class,'jnotify-notification-error')]")
	private WebElement error_msg;
	
	@FindBy(xpath=".//a[text()='Apple']")
	private WebElement company;
		
	// CREATE THIRD PARTY PAGE
	
	@FindBy(name="nom")
	private WebElement name;
	
	@FindBy(name="client")
	private WebElement customerprospect;
	
	@FindBy(name="code_client")
	private WebElement customer_code;
	
	@FindBy(id="status")
	private WebElement status;
	
	@FindBy(name="adresse")
	private WebElement address;
	
	@FindBy(id="zipcode")
	private WebElement zipcode;
	
	@FindBy(id="town")
	private WebElement town;
	
	@FindBy(name="email")
	private WebElement email;
	
	@FindBy(name="url")
	private WebElement url;
	
	@FindBy(name="phone")
	private WebElement phone;
	
	@FindBy(name="fax")
	private WebElement fax;
	
	@FindBy(name="idprof1")
	private WebElement idprof1;
	
	@FindBy(name="idprof2")
	private WebElement idprof2;
	
	@FindBy(name="idprof3")
	private WebElement idprof3;
	
	@FindBy(name="idprof4")
	private WebElement idprof4;
	
	@FindBy(name="idprof5")
	private WebElement idprof5;
	
	@FindBy(name="idprof6")
	private WebElement idprof6;
	
	@FindBy(name="assujtva_value")
	private WebElement vat;
	
	@FindBy(id="typent_id")
	private WebElement type;
	
	@FindBy(name="capital")
	private WebElement capital;
	
	// SEARCH 
	
	@FindBy(name="search_nom_only")
	private WebElement search_input;
	
	@FindBy(xpath=".//*[@value='Search']")
	private WebElement search_button;
	
	@FindBy(linkText="Apple")
	private WebElement searched_company;
	
	@FindBy(id="card")
	private WebElement card;
	
	// DELETE
	
	@FindBy(xpath="//*[@class='butActionDelete']")
	private WebElement delete_company;
	
	@FindBy(xpath=".//*[@id='mainbody']/div[3]/div[3]/div/button[1]")
	private WebElement alert;
	
	@FindBy(xpath=".//*[contains(@class,'jnotify-notification')]")
	private WebElement delete_message;
	
	// LIST OF PROSPECTS
	
	@FindBy(linkText="List of prospects")
	private WebElement list_of_prospects;
	
	@FindBy(name="search_nom")
	private WebElement search_nom;
	
	@FindBy(name="search_zipcode")
	private WebElement search_zipcode;

	@FindBy(name="search_ville")
	private WebElement search_town;
	
	@FindBy(name="search_state")
	private WebElement search_state;
	
	@FindBy(name="search_type_thirdparty")
	private WebElement search_type_thirdparty;
	
	@FindBy(name="search_datec")
	private WebElement search_datec;
	
	@FindBy(name="search_level_from")
	private WebElement search_level_from;
	
	@FindBy(name="search_level_to")
	private WebElement search_level_to;
	
	@FindBy(name="search_stcomm")
	private WebElement search_stcomm;
	
	@FindBy(name="search_status")
	private WebElement search_status;
	
	@FindBy(name="button_search")
	private WebElement button_search;
	
	//".//*[@id='formulaire_recherche']/table/tbody/tr[4]/td[1]/a[2]"
	@FindBy(xpath=".//*[@id='undertopmenu']/tbody/tr/td[2]/div/form/table/tbody/tr[3]/td[1]/a[2]")
	private WebElement searched;
	
	// CONTACTS
	
	@FindBy(linkText="New contact/address")
	private WebElement newContact;
	
	
	
	
	
	public ThirdPartyPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	

	
	public ContactPage goToContactPage(WebDriver driver){
		newContact.click();
		return new ContactPage(driver);
	}
	
	public String getSearched(){
		return searched.getText();
	}
	
	public void searchListOfProspects(String[] param){
		list_of_prospects.click();
		search_nom.sendKeys(param[0]);
		//search_zipcode.sendKeys(param[1]);
		search_town.sendKeys(param[1]);
		//search_state.sendKeys(param[3]);
//		new Select(search_type_thirdparty).selectByVisibleText("Large company");
//		new Select(search_type_thirdparty).selectByVisibleText("Private individual");
//		new Select(search_type_thirdparty).selectByVisibleText("Other");
//		new Select(search_type_thirdparty).selectByVisibleText("Medium company");
//		new Select(search_type_thirdparty).selectByVisibleText("Governmental");
//		new Select(search_type_thirdparty).selectByVisibleText("Small company");
		//search_datec.sendKeys(param[3]);
		new Select(search_level_from).selectByVisibleText("High");
		new Select(search_level_from).selectByVisibleText("Medium");
		new Select(search_level_from).selectByVisibleText("Low");
		new Select(search_level_from).selectByVisibleText("None");
		new Select(search_level_from).selectByIndex(0);
		new Select(search_level_to).selectByVisibleText("None");
		new Select(search_level_to).selectByVisibleText("Low");
		new Select(search_level_to).selectByVisibleText("Medium");
		new Select(search_level_to).selectByVisibleText("High");
		new Select(search_level_to).selectByIndex(0);
//		new Select(search_stcomm).selectByVisibleText("Do not contact");
//		new Select(search_stcomm).selectByVisibleText("To contact");
//		new Select(search_stcomm).selectByVisibleText("Contact in progress");
//		new Select(search_stcomm).selectByVisibleText("Contacted");
//		new Select(search_stcomm).selectByVisibleText("Never contacted");
//		new Select(search_status).selectByVisibleText("Closed");
//		new Select(search_status).selectByVisibleText("Open");
		button_search.click();
	}
	
	public void deleteThirdParty(){
		list.click();
		searched_company.click();
		card.click();
		delete_company.click();
		alert.click();
	}
	
	public String getDeleteMsg(){
		return delete_message.getText();
	}
	
	public void searchThirdParty(String s){
		search_input.sendKeys(s);
		search_button.click();
	}
	
	public String getErrorMsg(){
		return error_msg.getText();
	}
	
	public String getInsertedCompany() throws InterruptedException{
		list.click();
		return company.getText();
	}
	
	public String getSearchedCompany(){
		return searched_company.getText();
	}
	
	public void createNewThirdParty(String[] param){
		new_third_party.click();
		name.sendKeys(param[0]);
		new Select(customerprospect).selectByVisibleText("Nor prospect, nor customer");
		new Select(customerprospect).selectByVisibleText("Customer");
		new Select(customerprospect).selectByVisibleText("Prospect");
		new Select(customerprospect).selectByVisibleText("Prospect / Customer");
		customer_code.sendKeys(param[1]);
//		new Select(status).selectByVisibleText("Closed");
//		new Select(status).selectByVisibleText("Open");
		address.sendKeys(param[2]);
		zipcode.sendKeys(param[3]);
		town.sendKeys(param[4]);
		email.sendKeys(param[5]);
		url.sendKeys(param[6]);
		phone.sendKeys(param[7]);
		fax.sendKeys(param[8]);
		idprof1.sendKeys(param[9]);
		idprof2.sendKeys(param[10]);
		idprof3.sendKeys(param[11]);
		idprof4.sendKeys(param[12]);
//		idprof5.sendKeys(param[13]);
//		idprof6.sendKeys(param[14]);
		new Select(vat).selectByVisibleText("No");
		new Select(vat).selectByVisibleText("Yes");
		new Select(type).selectByVisibleText("Governmental");
		new Select(type).selectByVisibleText("Large company");
		new Select(type).selectByVisibleText("Medium company");
		new Select(type).selectByVisibleText("Other");
		new Select(type).selectByVisibleText("Private individual");
		new Select(type).selectByVisibleText("Small company");
		capital.sendKeys(param[15]);
		save.click();
	}
	
	
}
