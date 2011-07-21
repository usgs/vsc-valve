package gov.usgs.selenium.tilt;

import static com.thoughtworks.selenium.grid.tools.ThreadSafeSeleniumSessionStorage.session;

import java.awt.image.BufferedImage;
import java.net.URL;

import javax.imageio.ImageIO;

import gov.usgs.selenium.Support;

/**
 * Request a plot with the additional parameters; compare to a known good result
 * (using just the HTTP address).
 */
public class BaseHttpOnlyPlot extends Support {

    public void simplePlot(String bookmark, String image) throws Throwable {
    	session().open("/valve3/");
    	String url = session().getLocation() + bookmark;
    	System.err.println(url);
		BufferedImage img = ImageIO.read(new URL(url));
		BufferedImage reference = ImageIO.read(getClass().getResource(image));
		assert equalImages(img, reference);
    }
    
}
