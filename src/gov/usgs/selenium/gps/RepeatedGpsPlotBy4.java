package gov.usgs.selenium.gps;

import org.testng.annotations.Test;

/**
 * Test that we can select GPS and generate a plot.  A simple test that
 * indicates that all components are present and running.
 */
public class RepeatedGpsPlotBy4 extends BaseGpsPlot {

    @Test(groups = {"all"}, invocationCount=100, threadPoolSize=4)
    public void simplePlot() throws Throwable {
    	super.simplePlot();
    }
    
}

