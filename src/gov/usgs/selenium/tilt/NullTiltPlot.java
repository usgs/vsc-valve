package gov.usgs.selenium.tilt;

import static com.thoughtworks.selenium.grid.tools.ThreadSafeSeleniumSessionStorage.session;

import java.awt.image.BufferedImage;
import java.net.URL;

import javax.imageio.ImageIO;

import gov.usgs.selenium.Support;

import org.testng.annotations.Test;

/**
 * Using the modified data with null values, check against an images with a
 * verified gap.
 */
public class NullTiltPlot extends Support {

    @Test(groups = {"all", "compatibility"})
    public void simplePlot() throws Throwable {
    	session().open("/valve3/");
		waitForXpathCount("//li[@id='isti_deformation_tilt']", 1, 100, 10);
		session().click("isti_deformation_tilt");
		waitForXpathCount("//select[@name='selector:ch']/option", 2, 1000, LOAD_TIME);
		session().addSelection("selector:ch", "label=POC");
		session().type("startTime", "20011224145934588");
		session().type("endTime", "20020301154842352");
		session().click("submit");
		waitForXpathCount("//img[@class='pointer']", 2, 1000, LOAD_TIME);
		String bookmark = session().getAttribute("//div[@id='content0']//a@href");
		BufferedImage img = ImageIO.read(new URL(session().getLocation() + bookmark));
		BufferedImage reference = ImageIO.read(getClass().getResource("/img/null-tilt.png"));
		assert equalImages(img, reference);
    }
    
}

