package gov.usgs.selenium.tilt;

import static com.thoughtworks.selenium.grid.tools.ThreadSafeSeleniumSessionStorage.session;

import java.awt.image.BufferedImage;
import java.net.URL;

import javax.imageio.ImageIO;

import gov.usgs.selenium.Support;

import org.testng.annotations.Test;

/**
 * Plot two channels and compare with a "known good" plot.
 * 
 * The reference was selected by hand and is clearly de-trended.
 */
public class MultiTiltPlot extends Support {

    @Test(groups = {"all", "compatibility"})
    public void simplePlot() throws Throwable {
    	session().open("/valve3/");
		waitForXpathCount("//li[@id='isti_deformation_tilt']", 1, 100, 10);
		session().click("isti_deformation_tilt");
		waitForXpathCount("//select[@name='selector:ch']/option", 2, 1000, LOAD_TIME);
		session().addSelection("selector:ch", "label=POC");
		session().addSelection("selector:ch", "label=SMC");
		session().type("startTime", "20090501");
		session().type("endTime", "20090601");
		session().click("submit");
		waitForXpathCount("//img[@class='pointer']", 2, 1000, LOAD_TIME);
		String bookmark = session().getAttribute("//div[@id='content0']//a@href");
		BufferedImage img = ImageIO.read(new URL(session().getLocation() + bookmark));
		BufferedImage reference = ImageIO.read(getClass().getResource("/img/multi-tilt.png"));
		assert equalImages(img, reference);
    }
    
}
