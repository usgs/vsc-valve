package gov.usgs.selenium.co2;

import static com.thoughtworks.selenium.grid.tools.ThreadSafeSeleniumSessionStorage.session;
import gov.usgs.selenium.Support;

/**
 * Test that we can select Tilt and generate a plot.  More data than the GPS test.
 */
public class BaseCo2Plot extends Support {

	/**
	 * The test, which is invoked by subclasses in various ways.
	 * 
	 * @param rank The rank to select (1-4; 0 is best)
	 */
    public void simplePlot(int rank) throws Throwable {
    	session().open("/valve3/");
		waitForXpathCount("//li[@id='isti_gas_co2']", 1, 100, 10);
		session().click("isti_gas_co2");
		waitForXpathCount("//select[@name='selector:ch']/option", 1, 1000, LOAD_TIME);
		session().addSelection("selector:ch", "label=KNP");
		waitForXpathCount("//select[@name='selector:rk']/option", 5, 1000, LOAD_TIME);
		session().select("selector:rk", "value=" + rank);
//		session().type("startTime", "2000");
//		session().type("endTime", "2010");
		session().type("startTime", "20020101");
		session().type("endTime", "20060101");
		session().click("submit");
		waitForXpathCount("//img[@class='pointer']", 2, 1000, LOAD_TIME * 10);
	}
}
