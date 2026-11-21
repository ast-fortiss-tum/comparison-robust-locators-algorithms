package utils;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import tests.*;
@RunWith(Suite.class)
@SuiteClasses({
	TestLoginBlank.class,
	TestLoginKo.class,
	TestLoginOk.class,
	ModifySetup.class,
	ModifyMain.class,
	ModifyOthers.class,
	ModifyInterface.class,
	ModifyDisplay.class,
	ModifyBoxes.class,
	ModifyAlerts.class,
	ModifyEmails.class,
	OtherSetupKo.class,
	OtherSetupOk.class,
	CreateThirdPartyKo.class,
	CreateThirdPartyOk.class,
	CreateThirdPartyDuplicate.class,
	SearchThirdPartyBlank.class,
	SearchThirdPartyOk.class,
	SearchListOfProspectsBlank.class,
	SearchListOfProspectsOk.class,
	AddContactEmpty.class,
	AddContactOk.class,
	ModifyPersonalDataActive.class,
	ModifyPersonalDataInactive.class,
	CreateProductEmpty.class,
	CreateProductOk.class,
	SearchProduct.class,
	DeleteProduct.class,
	CreateFinancialEmpty.class,
	CreateFinancialOk.class,
	CreateFinancialDuplicate.class,
	ModifyFinancial.class,
	DeleteFinancial.class,
	CreateLabelEmpty.class,
	CreateLabelOk.class,
	DeleteLabel.class,
	DeleteContact.class,
	DeleteThirdPartyCompany.class,
	TestLogout.class
})
public class OrderedSuite { }
