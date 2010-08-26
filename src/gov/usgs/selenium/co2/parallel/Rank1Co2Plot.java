package gov.usgs.selenium.co2.parallel;

import gov.usgs.selenium.co2.BaseCo2Plot;

import org.testng.annotations.Test;

/**
 * Plot the a rank for C02 (lots of data) 
 */
public class Rank1Co2Plot extends BaseCo2Plot {

    @Test(groups = {"all"})
    public void simpleRank() throws Throwable {
    	super.simplePlot(1);
    }
    
}

