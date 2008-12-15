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
import gov.usgs.vdx.data.rsam.EWRSAMData;
import gov.usgs.vdx.data.rsam.RSAMData;

import java.awt.BasicStroke;
import java.awt.Color;
import java.io.File;
import java.util.HashMap;

import cern.colt.matrix.DoubleMatrix2D;

/**
 * Get RSAM information from vdx server and  
 * generate images of RSAM values and RSAM event count histograms
 * in files with random names.
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
	private boolean removeBias;
	private String channel;
	protected String ch;
	private double period;
	private double threshold;
	private double ratio;
	private double maxEventLength;
	private BinSize bin;
	private RSAMData rd;
	private Data data;
	private PlotType type;
	protected String label;

	/**
	 * Default constructor
	 */
	public RSAMPlotter()
	{
		label = "RSAM";
	}
	
	/**
	 * Initialize DataRenderer, add it to plot, remove mean from rsam data if needed 
	 * and render rsam values to PNG image in local file
	 * @throws Valve3Exception
	 */
	protected void plotValues() throws Valve3Exception
	{	
		Plot plot = v3Plot.getPlot();
		
		DataRenderer dr = new DataRenderer(data);
		dr.setUnit("rsam");
		dr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		
//		Remove the mean from the first column (this needs to be user controlled)
		if (removeBias)
			data.unbias(1);
		
		double[] d = component.getYScale("ys", data.getMin(1), data.getMax(1));
		double yMin = d[0];
		double yMax = d[1];

		if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
			throw new Valve3Exception("Illegal axis values.");
		
		dr.setExtents(startTime, endTime, yMin, yMax);
		
		dr.createDefaultAxis(8, 8, false, component.isAutoScale("ys"));
		dr.createDefaultLineRenderers();
		dr.createDefaultLegendRenderer(new String[] {ch + " " + label});
		dr.setXAxisToTime(8);
		dr.getAxis().setLeftLabelAsText(label);
		dr.getAxis().setBottomLabelAsText("Time(" + Valve3.getInstance().getTimeZoneAbbr()+ ")");
		plot.addRenderer(dr);
		plot.writePNG(Valve3.getInstance().getApplicationPath() + File.separatorChar + v3Plot.getFilename());
		component.setTranslation(dr.getDefaultTranslation(plot.getHeight()));
		component.setTranslationType("ty");
		v3Plot.addComponent(component);
		
		v3Plot.setTitle(label + ": " + ch);
	}
	
	/**
	 * Initialize HistogramRenderer, add it to plot, and 
	 * render event count histogram to PNG image in local file
	 */
	private void plotEvents()
	{	
		Plot plot = v3Plot.getPlot();		
		
		
		HistogramRenderer hr;
		if (threshold > 0)
			rd.countEvents(threshold, ratio, maxEventLength);
		hr = new HistogramRenderer(rd.getCountsHistogram(bin));
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
		
		v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + ": " + component.get("selectedStation"));		
	}

	/**
	 * Initialize internal data from PlotComponent component
	 * @throws Valve3Exception
	 */	
	private void getInputs() throws Valve3Exception
	{
		endTime = component.getEndTime();
		if (Double.isNaN(endTime))
				throw new Valve3Exception("Illegal end time.");
		startTime = component.getStartTime(endTime);
		if (Double.isNaN(startTime))
			throw new Valve3Exception("Illegal start time.");

		channel = component.get("ch");
		ch = channel.replace('$', ' ').replace('_', ' ').replace(',', '/');
	
		removeBias = false;
		String bias = component.get("rb");
		if (bias != null && bias.toUpperCase().equals("T"))
			removeBias = true;
		
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
			if (component.get("countsPeriod") != null)
			{
				period = Double.parseDouble(component.get("countsPeriod"));
				if (period == 0)
					throw new Valve3Exception("Illegal period.");
			}
			else
				period = 600;
			
			if (component.get("threshold") != null)
			{
				threshold = Double.parseDouble(component.get("threshold"));
				if (threshold == 0)
					throw new Valve3Exception("Illegal threshold.");
			}
			
			if (component.get("ratio") != null)
			{
				ratio = Double.parseDouble(component.get("ratio"));
				if (ratio == 0)
					throw new Valve3Exception("Illegal ratio.");
			}
			
			if (component.get("maxEventLength") != null)
			{
				maxEventLength = Double.parseDouble(component.get("maxEventLength"));
			}
			String bs = Util.stringToString(component.get("cntsBin"), "day");
			bin = BinSize.fromString(bs);
			if (bin == null)
				throw new Valve3Exception("Illegal bin size option.");
			if ((endTime - startTime)/bin.toSeconds() > 1000)
				throw new Valve3Exception("Bin size too small.");

			break;
		}
	}

	/**
	 * Gets binary rsam data from VDX
	 * @throws Valve3Exception
	 */
	private void getData() throws Valve3Exception
	{
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", vdxSource);
		params.put("selector", channel);
		params.put("period", Double.toString(period));
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("type", type.toString());

		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		if (client == null)
			return;
		
		rd = (RSAMData)client.getBinaryData(params);
		pool.checkin(client);

		if (rd == null)
			throw new Valve3Exception("RSAMPlotter: No data");
		
		double TZOffset = Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
        startTime += TZOffset;
        endTime += TZOffset;
        rd.adjustTime(TZOffset);
        if (rd.getData() == null)
        	throw new Valve3Exception("RSAMPlotter: Empty data");
        
        data = new Data(rd.getData().toArray());
	}

	/**
	 * Concrete realization of abstract method. 
	 * Generate PNG images for values or event count histograms (depends from plot type) to file with random name.
	 * @see Plotter
	 */
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

	/**
	 * @return CSV string of RSAM values data described by given PlotComponent
	 */
	public String toCSV(PlotComponent c) throws Valve3Exception
	{
		component = c;
		getInputs();
		getData();
		
		if (type == PlotType.VALUES)
		{
			return data.toCSV();
		} else {
			rd.countEvents(threshold, ratio, maxEventLength);
			return rd.getCountsCSV();
		}
	}

}