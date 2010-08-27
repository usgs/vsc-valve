package gov.usgs.selenium.tilt;

import static com.thoughtworks.selenium.grid.tools.ThreadSafeSeleniumSessionStorage.session;

import java.awt.image.BufferedImage;
import java.net.URL;

import javax.imageio.ImageIO;

import gov.usgs.selenium.Support;

import org.testng.annotations.Test;

/**
 * Request a plot with the additional parameters; compare to a known good result.
 */
public class ModifiedAxesPlot extends Support {

    @Test(groups = {"all", "compatibility"})
    public void simplePlot() throws Throwable {
    	session().open("/valve3/");
    	String bookmark = "valve3.jsp?a=plot&o=png&tz=HST&w=1000&h=210&n=1&x.0=75&y.0=19&w.0=850&h.0=150&mh.0=600&chCnt.0=1&src.0=isti_deformation_tilt&st.0=20090101000000000&et.0=20090201235959999&lg.0=true&selectedStation.0=Remove%20Mean&ch.0=15&dataTypes.0=0.0&plotType.0=ts&az.0=n&azval.0=&rk.0=1&ds.0=None&dsInt.0=&lt.0=l&despike_period.0=&filter_arg1.0=&filter_arg2.0=&debias_period.0=&despike.0=F&detrend.0=F&dmo_fl.0=0&dmo_db.0=1&radial.0=T&tangential.0=T&xTilt.0=F&yTilt.0=F&magnitude.0=F&azimuth.0=F&holeTemp.0=F&boxTemp.0=F&instVolt.0=F&rainfall.0=F&ysLMin.0=Auto&ysLMax.0=Auto&ysRMin.0=Auto&ysRMax.0=Auto&vs.0=Auto&plotSeparately.0=false&xTickMarks.0=1&xTickValues.0=0&xUnits.0=0&xLabel.0=1&yTickMarks.0=1&yTickValues.0=0&yUnits.0=0&yLabel.0=0";
    	String url = session().getLocation() + bookmark;
    	System.err.println(url);
		BufferedImage img = ImageIO.read(new URL(url));
		BufferedImage reference = ImageIO.read(getClass().getResource("/img/no-axes-tilt.png"));
		assert equalImages(img, reference);
    }
    
}
