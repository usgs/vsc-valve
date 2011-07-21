package gov.usgs.selenium.gps;

import org.testng.annotations.Test;

/**
 * Test that we can select GPS and generate a plot.  A simple test that
 * indicates that all components are present and running.
 */
public class SingleGpsPlot extends BaseGpsPlot {

    @Test(groups = {"all", "compatibility"})
    public void simplePlot() throws Throwable {
    	super.simplePlot();
    }
    
}

