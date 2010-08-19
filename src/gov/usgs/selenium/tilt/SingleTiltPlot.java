package gov.usgs.selenium.tilt;

import org.testng.annotations.Test;

/**
 * Test that we can select Tilt and generate a plot.  More data than the GPS test.
 */
public class SingleTiltPlot extends BaseTiltPlot {

    @Test(groups = {"all"})
    public void simplePlot() throws Throwable {
    	super.simplePlot();
    }
    
}

