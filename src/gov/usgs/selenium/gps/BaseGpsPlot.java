package gov.usgs.selenium.gps;

import static com.thoughtworks.selenium.grid.tools.ThreadSafeSeleniumSessionStorage.session;
import gov.usgs.selenium.Support;

/**
 * Test that we can select GPS and generate a plot.  A simple test that
 * indicates that all components are present and running.
 */
public class BaseGpsPlot extends Support {

	/**
	 * The test, which is invoked by subclasses in various ways.
	 */
    public void simplePlot() throws Throwable {
    	session().open("/valve3/");
		waitForXpathCount("//li[@id='isti_deformation_gps']", 1, 100, 10);
		session().click("isti_deformation_gps");
		waitForXpathCount("//select[@name='selector:ch']/option", 374, 1000, LOAD_TIME);
		session().addSelection("selector:ch", "label=AINP");
		session().type("startTime", "2009");
		session().type("endTime", "2010");
		session().click("submit");
		waitForXpathCount("//img[@class='pointer']", 2, 1000, LOAD_TIME);
	}
}
