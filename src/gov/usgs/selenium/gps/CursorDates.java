package gov.usgs.selenium.gps;

import static com.thoughtworks.selenium.grid.tools.ThreadSafeSeleniumSessionStorage.session;

import org.testng.annotations.Test;

import gov.usgs.selenium.Support;

/**
 * Plot GPS, click on image, and check that date ranges change in the sequence
 * expected.
 */
public class CursorDates extends Support {

    @Test(groups = {"all", "compatibility"})
    public void simplePlot() throws Throwable {
    	session().open("/valve3/");
		waitForXpathCount("//li[@id='isti_deformation_gps']", 1, 100, 10);
		session().click("isti_deformation_gps");
		waitForXpathCount("//select[@name='selector:ch']/option", 374, 1000, LOAD_TIME);
		session().addSelection("selector:ch", "label=AINP");
		session().type("startTime", "20090101");
		session().type("endTime", "20100101");
		session().click("submit");
		waitForXpathCount("//img[@class='pointer']", 2, 1000, LOAD_TIME);
		// now get actual start/end values as references
		String start = session().getValue("startTime");
		System.err.println(start);
		assert start.equals("20090101000000000");
		String end = session().getValue("endTime");
		System.err.println(end);
		assert end.equals("20100101235959999");
		// click
		session().clickAt("//img[@class='pointer']", "100,1000");
		// check start changed
		String start2 = session().getValue("startTime");
		System.err.println(start2);
		assert ! start.equals(start2);
		String end2 = session().getValue("endTime");
		System.err.println(end2);
		assert end.equals(end2);
		// click
		session().clickAt("//img[@class='pointer']", "400,1000");
		// check end changed
		String start3 = session().getValue("startTime");
		System.err.println(start3);
		assert start2.equals(start3);
		String end3 = session().getValue("endTime");
		System.err.println(end3);
		assert ! end.equals(end3);
    }

}
