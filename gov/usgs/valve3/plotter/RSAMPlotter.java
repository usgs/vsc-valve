package gov.usgs.valve3.plotter;

import gov.usgs.plot.Data;
import gov.usgs.plot.DataRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.util.Pool;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.PlotHandler;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.rsam.RSAMData;

import java.awt.Color;
import java.io.File;
import java.util.HashMap;

/**
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.2  2005/10/27 21:35:26  tparker
 * Add timezone per bug #68
 *
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
public class RSAMPlotter extends Plotter
{
	public RSAMPlotter()
	{}
		
	public void plot(Valve3Plot v3Plot, PlotComponent component)
	{
		v3Plot.setFilename(PlotHandler.getRandomFilename());
		double end = component.getEndTime();
		double start = component.getStartTime(end);
		if (Double.isNaN(start) || Double.isNaN(end))
		{
			// return an error
			return;
		}
		
		String channel = component.get("ch");
		String ch = channel.replace('$', ' ').replace('_', ' ');
		double period = Double.parseDouble(component.get("period"));
		
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", vdxSource);
		params.put("selector", channel);
		params.put("period", Double.toString(period));
		params.put("st", Double.toString(start));
		params.put("et", Double.toString(end));

		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		RSAMData rd = (RSAMData)client.getData(params);
		pool.checkin(client);
		
		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		
		double[][] dd = rd.getData().toArray();
        for (int i = 0; i < dd.length; i++)
        	dd[i][0] +=  Valve3.getInstance().getTimeZoneOffset() * 60 * 60;

        start += Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
        end += Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
		Data d = new Data(dd);
		
		double dmax = d.getMax(1);
		double mean = d.getMean(1);
		
		double max = Math.min(2 * mean, dmax);
	
		DataRenderer dr = new DataRenderer(d);
		dr.setUnit("rsam");
		dr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		dr.setExtents(start, end, d.getMinData(), max);
		dr.createDefaultAxis(8, 8, false, true);
		dr.createDefaultLineRenderers();
		dr.createDefaultLegendRenderer(new String[] {ch + " RSAM"});
		dr.setXAxisToTime(8);
		dr.getAxis().setLeftLabelAsText("RSAM");
		dr.getAxis().setBottomLabelAsText("Time(" + Valve3.getInstance().getTimeZoneAbbr()+ ")");//(Data from " + Valve.DATE_FORMAT.format(Util.j2KToDate(d.getMinTime())) +
//				" to " + Valve.DATE_FORMAT.format(Util.j2KToDate(d.getMaxTime())) + ")");
		plot.addRenderer(dr);
		plot.writePNG(Valve3.getInstance().getApplicationPath() + File.separatorChar + v3Plot.getFilename());
		component.setTranslation(dr.getDefaultTranslation(plot.getHeight()));
		component.setTranslationType("ty");
		v3Plot.addComponent(component);
		
		v3Plot.setTitle("RSAM: " + ch);
	}
}
