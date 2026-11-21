package pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class AlertsPage {

	@FindBy(linkText = "Modify")
	private WebElement modify;

	@FindBy(name = "MAIN_DELAY_PROPALS_TO_CLOSE")
	private WebElement main_delay_to_close;

	@FindBy(name = "MAIN_DELAY_PROPALS_TO_BILL")
	private WebElement main_delay_to_bill;

	@FindBy(name = "MAIN_DELAY_CUSTOMER_BILLS_UNPAYED")
	private WebElement main_delay_bill_unpayed;

	@FindBy(name = "MAIN_DELAY_TRANSACTIONS_TO_CONCILIATE")
	private WebElement main_delay_transactions_conciliate;

	@FindBy(name = "MAIN_DELAY_CHEQUES_TO_DEPOSIT")
	private WebElement main_delay_cheques_deposit;

	@FindBy(name = "MAIN_DELAY_MEMBERS")
	private WebElement main_delay_members;

	@FindBy(xpath = ".//*[@id='id-right']/div/form/div/input")
	private WebElement save;

	public AlertsPage(WebDriver driver) {
		PageFactory.initElements(driver, this);
	}

	public void changeToleranceDelays(String[] param) throws InterruptedException {
		modify.click();
		main_delay_to_close.clear();
		main_delay_to_close.sendKeys(param[0]);
		main_delay_to_bill.clear();
		main_delay_to_bill.sendKeys(param[1]);
		main_delay_bill_unpayed.clear();
		main_delay_bill_unpayed.sendKeys(param[2]);
		main_delay_transactions_conciliate.clear();
		main_delay_transactions_conciliate.sendKeys(param[3]);
		main_delay_cheques_deposit.clear();
		main_delay_cheques_deposit.sendKeys(param[4]);
		main_delay_members.clear();
		main_delay_members.sendKeys(param[5]);
		save.click();
	}

}
