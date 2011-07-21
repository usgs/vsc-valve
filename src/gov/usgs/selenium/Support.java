package gov.usgs.selenium;

import static com.thoughtworks.selenium.grid.tools.ThreadSafeSeleniumSessionStorage.closeSeleniumSession;
import static com.thoughtworks.selenium.grid.tools.ThreadSafeSeleniumSessionStorage.session;
import static com.thoughtworks.selenium.grid.tools.ThreadSafeSeleniumSessionStorage.startSeleniumSession;
import static org.testng.AssertJUnit.fail;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Parameters;

/**
 * Common code for Selenium tests.
 */
public class Support {

    public static final String TIMEOUT = "120000";
    public static final int LOAD_TIME = 60;

    @BeforeMethod(groups = {"all"}, alwaysRun = true)
    @Parameters({"seleniumHost", "seleniumPort", "browser", "webSite"})
    protected void startSession(String seleniumHost, int seleniumPort, String browser, String webSite) {
        startSeleniumSession(seleniumHost, seleniumPort, browser, webSite);
        session().setTimeout(TIMEOUT);
    }

    @AfterMethod(groups = {"all"}, alwaysRun = true)
    protected void closeSession() {
        closeSeleniumSession();
    }
   
    /**
     * Check that the given number of elements exist, waiting for a given time.
     * 
     * @param path The element selector
     * @param count The expected number of elements
     * @param pause How long to sleep for a "tick"
     * @param timeout How many ticks, maximum, to wait
     * @throws Throwable
     */
    protected void waitForXpathCount(String path, int count, int pause, int timeout) throws Throwable {
		for (int second = 0;; second++) {
			if (second >= timeout) fail("timeout");
			try { if (new Integer(count).equals(session().getXpathCount(path))) break; } catch (Exception e) {}
			Thread.sleep(pause);
		}
    }

    /**
     * Compare two images.
     * 
     * @param img One image
     * @param reference The other
     * @return True if the pixels in the two images are equal.
     */
    protected boolean equalImages(BufferedImage img, BufferedImage reference) {
		int[] imgPix = toPixels(img);
		int[] refPix = toPixels(reference);
		if (imgPix.length != refPix.length) return false;
		for (int i = 0; i < imgPix.length; ++i) {
			if (imgPix[i] != refPix[i]) return false;
		}
		return true;
    }
    
    /**
     * Retrieve the pixels (as an array of integers) from the image.
     * 
     * @param image The image to extract pixels from.
     * @return An array of pixels (arbitrary format - used only for equality comparison).
     */
    private int[] toPixels(BufferedImage image) {
    	Raster raster = image.getData();
    	int w = raster.getWidth();
    	int h = raster.getHeight();
    	int[] pixels = new int[w*h*3];
    	raster.getPixels(0, 0, w, h, pixels);
    	return pixels;
    }
    
}
