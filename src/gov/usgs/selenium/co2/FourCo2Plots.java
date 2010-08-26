package gov.usgs.selenium.co2;

import org.testng.annotations.Test;

/**
 * Plot the 4 different ranks for C02 (lots of data) 
 */
public class FourCo2Plots extends BaseCo2Plot {

    @Test(groups = {"all"})
    public void simpleRank1() throws Throwable {
    	super.simplePlot(1);
    }
    
    @Test(groups = {"all"})
    public void simpleRank2() throws Throwable {
    	super.simplePlot(2);
    }
    
    @Test(groups = {"all"})
    public void simpleRank3() throws Throwable {
    	super.simplePlot(3);
    }
    
    @Test(groups = {"all"})
    public void simpleRank4() throws Throwable {
    	super.simplePlot(4);
    }
    
}

