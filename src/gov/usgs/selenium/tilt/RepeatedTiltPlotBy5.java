package gov.usgs.selenium.tilt;

import org.testng.annotations.Test;

/**
 * Test that we can select Tilt and generate a plot.  More data than the GPS test.
 */
public class RepeatedTiltPlotBy5 extends BaseTiltPlot {

    @Test(groups = {"all"}, invocationCount=50, threadPoolSize=5)
    public void simplePlot() throws Throwable {
    	super.simplePlot();
    }
    
}

