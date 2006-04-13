package gov.usgs.valve3.plotter;

import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.Data;
import gov.usgs.plot.DataRenderer;
import gov.usgs.plot.HistogramRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.Renderer;
import gov.usgs.plot.ShapeRenderer;
import gov.usgs.plot.SmartTick;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.PlotHandler;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.hypo.HypocenterList.BinSize;
import gov.usgs.vdx.data.rsam.RSAMData;

import java.awt.BasicStroke;
import java.awt.Color;
import java.io.File;
import java.util.HashMap;

import cern.colt.matrix.DoubleMatrix2D;

/**
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.8  2006/04/09 18:19:36  dcervelli
 * VDX type safety changes.
 *
 * Revision 1.7  2006/01/11 00:39:13  tparker
 * fix period bug
 *
 * Revision 1.6  2006/01/10 20:53:15  tparker
 * Add RSAM event counts
 *
 * Revision 1.5  2005/12/28 02:13:31  tparker
 * Add toCSV method to support raw data export
 *
 * Revision 1.4  2005/11/03 20:26:16  tparker
 * commit due to repository weirdness. no functional changes
 *
 * Revision 1.3  2005/11/01 00:59:49  tparker
 * Add timezone per bug#68
 *
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
	
	private enum PlotType 
	{
		VALUES, COUNTS;
		
		public static PlotType fromString(String s)
		{
			if (s == null)
				return null;
			
			if (s.equals("values"))
				return VALUES;
			else if (s.equals("cnts"))
				return COUNTS;
			else 
				return null;
		}
	}

	private Valve3Plot v3Plot;
	private PlotComponent component;
	private double startTime;
	private double endTime;
	private String channel;
	private String ch;
	private double period;
	private double threshold;
	private double ratio;
	private double maxEventLength;
	private BinSize bin;
	private RSAMData rd;
	private Data data;
	private PlotType type;

	public RSAMPlotter()
	{}
	
	private void plotValues() throws Valve3Exception
	{	
		Plot plot = v3Plot.getPlot();
		
		DataRenderer dr = new DataRenderer(data);
		dr.setUnit("rsam");
		dr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		
		double dmax = data.getMax(1);
		double dmin = data.getMinData();
		double yMin, yMax;
		boolean allowExpand = true;
		if (component.isAutoScale("ys"))
		{
			double mean = data.getMean(1);
			yMin = dmin;
			yMax = Math.min(3 * mean, dmax);
		}
		else
		{
			double[] d = component.getYScale("ys", dmin, dmax);
			yMin = d[0];
			yMax = d[1];
			allowExpand = false;
		}
		if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
			throw new Valve3Exception("Illegal axis values.");
		dr.setExtents(startTime, endTime, yMin, yMax);
		
		dr.createDefaultAxis(8, 8, false, allowExpand);
		dr.createDefaultLineRenderers();
		dr.createDefaultLegendRenderer(new String[] {ch + " RSAM"});
		dr.setXAxisToTime(8);
		dr.getAxis().setLeftLabelAsText("RSAM");
		dr.getAxis().setBottomLabelAsText("Time(" + Valve3.getInstance().getTimeZoneAbbr()+ ")");
		plot.addRenderer(dr);
		plot.writePNG(Valve3.getInstance().getApplicationPath() + File.separatorChar + v3Plot.getFilename());
		component.setTranslation(dr.getDefaultTranslation(plot.getHeight()));
		component.setTranslationType("ty");
		v3Plot.addComponent(component);
		
		v3Plot.setTitle("RSAM: " + ch);
	}
	
	private void plotEvents()
	{	
		Plot plot = v3Plot.getPlot();
		
		rd.countEvents(threshold, ratio, maxEventLength);
		
		HistogramRenderer hr = new HistogramRenderer(rd.getCountsHistogram(bin));
		hr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		hr.setUnit("events per time");
		hr.setDefaultExtents();
		hr.setMinX(startTime);
		hr.setMaxX(endTime);
		hr.createDefaultAxis(8, 8, false, true);
		hr.setXAxisToTime(8);
		hr.getAxis().setLeftLabelAsText("Events per " + bin);
		hr.getAxis().setBottomLabelAsText("Time");
		plot.addRenderer(hr);
		
		DoubleMatrix2D data = null;
		
		data = rd.getCumulativeCounts();
		if (data != null && data.rows() > 0)
		{
			
			Data countData = new Data(data.toArray());
			DataRenderer dr = new DataRenderer(countData);
			dr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
			dr.createDefaultLineRenderers();
			
			Renderer[] r = dr.getLineRenderers();
			((ShapeRenderer)r[0]).color = Color.red;
			((ShapeRenderer)r[0]).stroke = new BasicStroke(2.0f);
			double cmin = countData.getData()[0][1];
			double cmax = countData.getData()[data.rows() - 1][1];		
			dr.setExtents(startTime, endTime, cmin, cmax+1);
			AxisRenderer ar = new AxisRenderer(dr);
			ar.createRightTickLabels(SmartTick.autoTick(cmin, cmax, 8, false), null);
			dr.setAxis(ar);
			
			hr.addRenderer(dr);
			hr.getAxis().setRightLabelAsText("Cumulative Counts");
		}
		
		plot.writePNG(v3Plot.getLocalFilename());
		component.setTranslation(hr.getDefaultTranslation(plot.getHeight()));
		component.setTranslationType("ty");
		v3Plot.addComponent(component);
		
		v3Plot.setTitle("RSAM Events: " + ch);
	}
	
	private void getInputs() throws Valve3Exception
	{
		endTime = component.getEndTime();
		if (Double.isNaN(endTime))
				throw new Valve3Exception("Illegal end time.");
		startTime = component.getStartTime(endTime);
		if (Double.isNaN(startTime))
			throw new Valve3Exception("Illegal start time.");

		channel = component.get("ch");
		ch = channel.replace('$', ' ').replace('_', ' ');
		
		type = PlotType.fromString(component.get("type"));
		if (type == null)
			throw new Valve3Exception("Illegal plot type.");
		
		switch(type)
		{
		case VALUES:
			period = Double.parseDouble(component.get("valuesPeriod"));
			if (period == 0)
				throw new Valve3Exception("Illegal period.");
				
			break;
		case COUNTS:
			period = Double.parseDouble(component.get("countsPeriod"));
			if (period == 0)
				throw new Valve3Exception("Illegal period.");
			threshold = Double.parseDouble(component.get("threshold"));
			if (threshold == 0)
				throw new Valve3Exception("Illegal threshold.");
			
			ratio = Double.parseDouble(component.get("ratio"));
			if (ratio == 0)
				throw new Valve3Exception("Illegal ratio.");
			
			maxEventLength = Double.parseDouble(component.get("maxEventLength"));

			String bs = Util.stringToString(component.get("cntsBin"), "day");
			bin = BinSize.fromString(bs);
			if (bin == null)
				throw new Valve3Exception("Illegal bin size option.");
			if ((endTime - startTime)/bin.toSeconds() > 1000)
				throw new Valve3Exception("Bin size too small.");

			break;
		}
	}
	
	private void getData() throws Valve3Exception
	{
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", vdxSource);
		params.put("selector", channel);
		params.put("period", Double.toString(period));
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));

		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		if (client == null)
			return;
		rd = (RSAMData)client.getBinaryData(params);
		pool.checkin(client);
		//System.out.println("data.toStringShort() = " + rd.getData().toStringShort());
		if (rd == null)
			throw new Valve3Exception("No data");
		
		double TZOffset = Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
        startTime += TZOffset;
        endTime += TZOffset;
        rd.adjustTime(TZOffset);
        
        data = new Data(rd.getData().toArray());
	}
	
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception
	{
		v3Plot = v3p;
		component = comp;
		getInputs();
		getData();

		v3Plot.setFilename(PlotHandler.getRandomFilename());

		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		
		switch(type)
		{
			case VALUES:
				plotValues();
				break;
			case COUNTS:
				plotEvents();
				break;
		}
	}
	
	public String toCSV(PlotComponent component) throws Valve3Exception
	{
		getInputs();
		getData();
		
		return data.toCSV();
	}

}