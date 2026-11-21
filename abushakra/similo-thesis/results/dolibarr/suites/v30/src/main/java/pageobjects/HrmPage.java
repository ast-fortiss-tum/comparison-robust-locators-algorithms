package pageobjects;

import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class HrmPage {
	
	@FindBy(linkText="New")
	private WebElement newLeaveRequest;
	
	@FindBy(id="date_debut_Button")
	private WebElement date_debut_Button;
				   
	@FindBy(xpath=".//*[@id='mainbody']/div[5]/table/tbody/tr[7]/td[3]")
	private WebElement date_debut_Button_confirm;
	
	@FindBy(id="date_fin_Button")
	private WebElement date_fin_Button;
	
	@FindBy(xpath=".//*[@id='mainbody']/div[5]/table/tbody/tr[7]/td[3]")
	private WebElement date_fin_Button_confirm;

	@FindBy(id="s2id_autogen2")
	private WebElement reviewed;
	
	@FindBy(id="s2id_autogen2_search")
	private WebElement reviewed_by;
	
	@FindBy(name="bouton")
	private WebElement createLeaveRequest;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[3]/table[1]/tbody/tr[2]/td[2]/a[2]")
	private WebElement superAdmin;
	
	// DELETE
	
	@FindBy(linkText="List")
	private WebElement list;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/form/table/tbody/tr[3]/td[1]/a[2]")
	private WebElement firstLeave;
	
	@FindBy(xpath=".//*[@id='id-right']/div/div/div[4]/a[3]")
	private WebElement delete;
	
	@FindBy(xpath=".//*[@id='mainbody']/div[5]/div[3]/div/button[1]")
	private WebElement yes;
	
	
	public HrmPage(WebDriver driver){
		PageFactory.initElements(driver, this);
	}
	
	public void deleteLeaveRequest(){
		list.click();
		firstLeave.click();
		delete.click();
		yes.click();
	}
	
	public void createLeaveRequest(){
		newLeaveRequest.click();
		date_debut_Button.click();
		date_debut_Button_confirm.click();
		date_fin_Button.click();
		date_fin_Button_confirm.click();
		reviewed.click();
		reviewed_by.sendKeys("SuperAdmin");
		reviewed_by.sendKeys(Keys.TAB);
		createLeaveRequest.click();
	}
	
	public String getSuperAdmin(){
		return superAdmin.getText();
	}
	
	
	
}
