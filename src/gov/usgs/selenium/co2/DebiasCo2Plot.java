package gov.usgs.selenium.co2;

import static com.thoughtworks.selenium.grid.tools.ThreadSafeSeleniumSessionStorage.session;

import java.awt.image.BufferedImage;
import java.net.URL;

import javax.imageio.ImageIO;

import gov.usgs.selenium.Support;

import org.testng.annotations.Test;

/**
 * Remove bias in various ways and compare with saved images.
 */
public class DebiasCo2Plot extends Support {

    @Test(groups = {"all", "compatibility"})
    public void simplePlot() throws Throwable {
    	session().open("/valve3/");
		waitForXpathCount("//li[@id='isti_deformation_gps']", 1, 100, 10);
		session().click("isti_gas_co2");
		waitForXpathCount("//select[@name='selector:ch']/option", 1, 1000, LOAD_TIME);
		session().addSelection("selector:ch", "label=KNP");
		session().type("startTime", "20090101");
		session().type("endTime", "20090301");
		
		session().click("submit");
		waitForXpathCount("//img[@class='pointer']", 2, 1000, LOAD_TIME);
		String bookmark = session().getAttribute("//div[@id='content0']//a@href");
		BufferedImage img = ImageIO.read(new URL(session().getLocation() + bookmark));
		BufferedImage reference = ImageIO.read(getClass().getResource("/img/bias-none.png"));
		assert equalImages(img, reference);
		
		removeBias(1, "label=Remove Mean", "/img/bias-mean.png");
		removeBias(2, "label=Remove Initial Value", "/img/bias-initial.png");
		
		session().type("isti_gas_co2_dmo_debias_period", "-100");
		removeBias(3, "label=Remove User Value", "/img/bias-user.png");
    }
    
    private void removeBias(int count, String label, String image) 
    throws Throwable {
		session().select("isti_gas_co2_dmo_debias_pick", label);
		session().click("submit");
		waitForXpathCount("//div[@id='content" + count + "']//a", 1, 1000, LOAD_TIME);
		String bookmark = session().getAttribute("//div[@id='content" + count + "']//a@href");
		BufferedImage img = ImageIO.read(new URL(session().getLocation() + bookmark));
		BufferedImage reference = ImageIO.read(getClass().getResource(image));
		assert equalImages(img, reference);
    	
    }
    
}
