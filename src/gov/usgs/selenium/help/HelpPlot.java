package gov.usgs.selenium.help;

import static com.thoughtworks.selenium.grid.tools.ThreadSafeSeleniumSessionStorage.session;

import org.testng.annotations.Test;

import gov.usgs.selenium.Support;

/**
 * Test that we can select Tilt and generate a plot.  More data than the GPS test.
 */
public class HelpPlot extends Support {

	/**
	 * Create a help popup and check it is displayed.
	 */
    @Test(groups = {"all", "compatibility"})
    public void simplePlot() throws Throwable {
    	session().open("/valve3/");
		waitForXpathCount("//li[@id='isti_deformation_gps']", 1, 100, 10);
		session().click("isti_deformation_gps");
		waitForXpathCount("//div[@id='isti_deformation_gps_pane_options_0-\']//img[@class='fr helpimg']", 1, 100, 10);
		session().click("//div[@id='isti_deformation_gps_pane_options_0-\']//img[@class='fr helpimg']");
		session().waitForPopUp("help", "1000");
		session().selectWindow("name=help");
		Thread.sleep(1000); // text load
		assert session().isTextPresent("GPS User's Guide");
	}
}
