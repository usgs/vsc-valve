package gov.usgs.selenium.tilt;

import static com.thoughtworks.selenium.grid.tools.ThreadSafeSeleniumSessionStorage.session;
import gov.usgs.selenium.Support;

/**
 * Test that we can select Tilt and generate a plot.  More data than the GPS test.
 */
public class BaseTiltPlot extends Support {

	/**
	 * The test, which is invoked by subclasses in various ways.
	 */
    public void simplePlot() throws Throwable {
    	session().open("/valve3/");
		waitForXpathCount("//li[@id='isti_deformation_tilt']", 1, 100, 10);
		session().click("isti_deformation_tilt");
		waitForXpathCount("//select[@name='selector:ch']/option", 2, 1000, LOAD_TIME);
		session().addSelection("selector:ch", "label=POC");
		session().type("startTime", "2009");
		session().type("endTime", "20100101");
		session().click("submit");
		waitForXpathCount("//img[@class='pointer']", 2, 1000, LOAD_TIME);
	}
}
