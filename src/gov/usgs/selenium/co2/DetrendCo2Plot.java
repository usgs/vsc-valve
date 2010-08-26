package gov.usgs.selenium.co2;

import static com.thoughtworks.selenium.grid.tools.ThreadSafeSeleniumSessionStorage.session;

import java.awt.image.BufferedImage;
import java.net.URL;

import javax.imageio.ImageIO;

import gov.usgs.selenium.Support;

import org.testng.annotations.Test;

/**
 * Plot CO2 data detrended, then get bookmark location, grab the image, and compare.
 * 
 * The reference was selected by hand and is clearly de-trended.
 */
public class DetrendCo2Plot extends Support {

    @Test(groups = {"all", "compatibility"})
    public void simplePlot() throws Throwable {
    	session().open("/valve3/");
		waitForXpathCount("//li[@id='isti_deformation_gps']", 1, 100, 10);
		session().click("isti_gas_co2");
		waitForXpathCount("//select[@name='selector:ch']/option", 1, 1000, LOAD_TIME);
		session().addSelection("selector:ch", "label=KNP");
		session().type("startTime", "20090529042625986");
		session().type("endTime", "20090529060151224");
		session().check("detrend");
		session().click("submit");
		waitForXpathCount("//img[@class='pointer']", 2, 1000, LOAD_TIME);
		String bookmark = session().getAttribute("//div[@id='content0']//a@href");
		BufferedImage img = ImageIO.read(new URL(session().getLocation() + bookmark));
		BufferedImage reference = ImageIO.read(getClass().getResource("/img/detrend-co2.png"));
		assert equalImages(img, reference);
    }
    
}
