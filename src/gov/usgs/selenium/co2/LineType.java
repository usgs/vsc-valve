package gov.usgs.selenium.co2;

import static com.thoughtworks.selenium.grid.tools.ThreadSafeSeleniumSessionStorage.session;

import java.awt.image.BufferedImage;
import java.net.URL;

import javax.imageio.ImageIO;

import org.testng.annotations.Test;

import gov.usgs.selenium.Support;

/**
 * Test that we can select Tilt and generate a plot.  More data than the GPS test.
 */
public class LineType extends Support {

	/**
	 * Plot using a different line type and check against standard
	 */
    @Test(groups = {"all", "compatibility"})
    public void simplePlot() throws Throwable {
    	session().open("/valve3/");
		waitForXpathCount("//li[@id='isti_gas_co2']", 1, 100, 10);
		session().click("isti_gas_co2");
		waitForXpathCount("//select[@name='selector:ch']/option", 1, 1000, LOAD_TIME);
		session().addSelection("selector:ch", "label=KNP");
		waitForXpathCount("//select[@name='selector:rk']/option", 5, 1000, LOAD_TIME);
		session().select("selector:rk", "value=1");
		session().type("startTime", "20020101");
		session().type("endTime", "20060101");
		session().select("isti_gas_co2_selector:lt", "value=t");
		session().click("submit");
		waitForXpathCount("//img[@class='pointer']", 2, 1000, LOAD_TIME * 10);
		String bookmark = session().getAttribute("//div[@id='content0']//a@href");
		BufferedImage img = ImageIO.read(new URL(session().getLocation() + bookmark));
		BufferedImage reference = ImageIO.read(getClass().getResource("/img/triangles-co2.png"));
		assert equalImages(img, reference);
	}
}
