package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.Select;

public class HrmPage {
	
	//@FindBy(linkText="Make a leave request")
	@FindBy(linkText="Apply for holidays")
	private WebElement newLeaveRequest;
	
	@FindBy(id="date_debut_Button")
	private WebElement date_debut_Button;
				   
	@FindBy(xpath=".//*[@class='dpSelected']")
	private WebElement date_debut_Button_confirm;
	
	@FindBy(id="date_fin_Button")
	private WebElement date_fin_Button;
	
	@FindBy(xpath=".//*[@class='dpSelected']")
	private WebElement date_fin_Button_confirm;

	@FindBy(id="valideur")
	private WebElement reviewed;
	
//	@FindBy(id="s2id_autogen2_search")
//	private WebElement reviewed_by;
	
	@FindBy(name="bouton")
	private WebElement createLeaveRequest;
	
	@FindBy(linkText="SuperAdmin")
	private WebElement superAdmin;
	
	// DELETE
	
	//@FindBy(linkText="Leaves")
	@FindBy(linkText="Holidays")
	private WebElement list;
	
	//@FindBy(xpath=".//*[@id='id-right']/div/form/table/tbody/tr[3]/td[1]/a[2]")
	@FindBy(linkText="1")
	private WebElement firstLeave;
	
	@FindBy(linkText="Delete")
	private WebElement delete;
	
	@FindBy(xpath=".//*[@id='mainbody']/div[4]/div[3]/div/button[1]")
	private WebElement yes;
	
	
	public HrmPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public void deleteLeaveRequest(){
		list.click();
		firstLeave.click();
		//superAdmin.click();
		delete.click();
		yes.click();
	}
	
	public void createLeaveRequest(){
		newLeaveRequest.click();
		date_debut_Button.click();
		date_debut_Button_confirm.click();
		date_fin_Button.click();
		date_fin_Button_confirm.click();
		new Select(reviewed).selectByVisibleText("SuperAdmin");
//		reviewed_by.sendKeys("SuperAdmin");
//		reviewed_by.sendKeys(Keys.TAB);
		createLeaveRequest.click();
	}
	
	public String getSuperAdmin(){
		return superAdmin.getText();
	}
	
	
	
}
