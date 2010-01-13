package gov.usgs.valve3.plotter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.HistogramRenderer;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.Renderer;
import gov.usgs.plot.ShapeRenderer;
import gov.usgs.plot.SmartTick;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.Channel;
import gov.usgs.vdx.data.GenericDataMatrix;
import gov.usgs.vdx.data.hypo.HypocenterList.BinSize;
import gov.usgs.vdx.data.rsam.RSAMData;

import cern.colt.matrix.DoubleMatrix2D;

/**
 * Get RSAM information from vdx server and  
 * generate images of RSAM values and RSAM event count histograms
 * in files with random names.
 *
 * @author Dan Cervelli, Loren Antolik
 */
public class RSAMPlotter extends Plotter {
	
	private enum PlotType {
		VALUES, COUNTS;		
		public static PlotType fromString(String s) {
			if (s == null) {
				return null;			
			} else if (s.equals("values")) {
				return VALUES;
			} else if (s.equals("cnts")) {
				return COUNTS;
			} else {
				return null;
			}
		}
	}

	private Valve3Plot v3Plot;
	private PlotComponent component;
	int compCount;
	private Map<Integer, RSAMData> channelDataMap;
	private static Map<Integer, Channel> channelsMap;
	private double startTime;
	private double endTime;
	protected String ch;
	private boolean removeBias;
	private int period;
	private double threshold;
	private double ratio;
	private double maxEventLength;
	private BinSize bin;
	private RSAMData rd;
	private PlotType plotType;
	private GenericDataMatrix gdm;
	
	protected String label;
	protected Logger logger;
	
	public final boolean ranks	= false;

	/**
	 * Default constructor
	 */
	public RSAMPlotter() {
		label	= "RSAM";
		logger	= Logger.getLogger("gov.usgs.vdx");
	}

	/**
	 * Initialize internal data from PlotComponent
	 * @throws Valve3Exception
	 */	
	private void getInputs() throws Valve3Exception {
		
		ch	= component.get("ch");
		if (ch == null || ch.length() <= 0) {
			throw new Valve3Exception("Illegal channel.");
		}
		
		endTime = component.getEndTime();
		if (Double.isNaN(endTime))
			throw new Valve3Exception("Illegal end time.");
		
		startTime = component.getStartTime(endTime);
		if (Double.isNaN(startTime))
			throw new Valve3Exception("Illegal start time.");
		
		plotType	= PlotType.fromString(component.get("plotType"));
		if (plotType == null) {
			throw new Valve3Exception("Illegal plot type.");
		}
	
		removeBias	= false;
		String bias	= component.get("rb");
		if (bias != null && bias.toUpperCase().equals("T"))
			removeBias = true;
		
		switch(plotType) {
		
		case VALUES:
			period = Integer.parseInt(component.get("valuesPeriod"));
			if (period == 0)
				throw new Valve3Exception("Illegal period.");
				
			break;
			
		case COUNTS:
			if (component.get("countsPeriod") != null) {
				period = Integer.parseInt(component.get("countsPeriod"));
				if (period == 0)
					throw new Valve3Exception("Illegal period.");
			} else {
				period = 600;
			}
			
			if (component.get("threshold") != null) {
				threshold = Double.parseDouble(component.get("threshold"));
				if (threshold == 0)
					throw new Valve3Exception("Illegal threshold.");
			}
			
			if (component.get("ratio") != null) {
				ratio = Double.parseDouble(component.get("ratio"));
				if (ratio == 0)
					throw new Valve3Exception("Illegal ratio.");
			}
			
			if (component.get("maxEventLength") != null) {
				maxEventLength = Double.parseDouble(component.get("maxEventLength"));
			}
			
			String bs	= Util.stringToString(component.get("cntsBin"), "day");
			bin			= BinSize.fromString(bs);
			if (bin == null)
				throw new Valve3Exception("Illegal bin size option.");
			if ((endTime - startTime)/bin.toSeconds() > 1000)
				throw new Valve3Exception("Bin size too small.");

			break;
		}
	}

	/**
	 * Gets binary data from VDX
	 * @throws Valve3Exception
	 */
	private void getData() throws Valve3Exception {
		
		boolean gotData = false;
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("plotType", plotType.toString());
		params.put("period", Integer.toString(period));

		// checkout a connection to the database
		Pool<VDXClient> pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client		= pool.checkout();
		if (client == null)
			return;
		
		double TZOffset = Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
		
		// create a map to hold all the channel data
		channelDataMap		= new LinkedHashMap<Integer, RSAMData>();
		String[] channels	= ch.split(",");
		
		// iterate through each of the selected channels and place the data in the map
		for (String channel : channels) {
			params.put("ch", channel);
			RSAMData data = (RSAMData)client.getBinaryData(params);
			if (data != null && data.rows() > 0) {
				gotData = true;
				data.adjustTime(TZOffset);
				channelDataMap.put(Integer.valueOf(channel), data);
			}
		}
		
		if (!gotData) {
			throw new Valve3Exception("No data for any stations.");
		}
		
		// adjust the start and end times
        startTime	   += TZOffset;
        endTime		   += TZOffset;

        // check back in our connection to the database
		pool.checkin(client);
	}
	
	/**
	 * Initialize DataRenderer, add it to plot, remove mean from rsam data if needed 
	 * and render rsam values to PNG image in local file
	 * @throws Valve3Exception
	 */
	protected void plotValues(Channel channel, RSAMData rd, int displayCount, int dh) throws Valve3Exception {
		
		GenericDataMatrix gdm = new GenericDataMatrix(rd.getData());
		
		// Remove the mean from the first column (this needs to be user controlled)
		if (removeBias) {
			gdm.add(1, -gdm.mean(1));
		}
		
		String channelCode = channel.getCode().replace('$', ' ').replace('_', ' ').replace(',', '/');
		
		/*
		double[] d	= component.getYScale("ys", gdm.min(1), gdm.max(1));
		double yMin	= d[0];
		double yMax	= d[1];
		if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
			throw new Valve3Exception("Illegal axis values.");
		*/

		double yMin = 1E300;
		double yMax = -1E300;
		boolean allowExpand = true;
		
		if (component.isAutoScale("ys")) {
			double buff;
			yMin	= Math.min(yMin, gdm.min(1));
			yMax	= Math.max(yMax, gdm.max(1));
			buff	= (yMax - yMin) * 0.05;
			yMin	= yMin - buff;
			yMax	= yMax + buff;
		} else {
			double[] ys = component.getYScale("ysR", yMin, yMax);
			yMin = ys[0];
			yMax = ys[1];
			allowExpand = false;
			if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
				throw new Valve3Exception("Illegal axis values.");
		}
		
		MatrixRenderer mr = new MatrixRenderer(gdm.getData(), ranks);
		mr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
		mr.setExtents(startTime, endTime, yMin, yMax);		
		mr.createDefaultAxis(8, 8, false, allowExpand);
		mr.setXAxisToTime(8);		
		mr.setAllVisible(true);
		mr.createDefaultLineRenderers();
		mr.createDefaultLegendRenderer(new String[] {channelCode + " " + label});
		mr.getAxis().setLeftLabelAsText(label);
		mr.getAxis().setBottomLabelAsText("Time (" + Valve3.getInstance().getTimeZoneAbbr()+ ")");
		
		component.setTranslation(mr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		v3Plot.getPlot().addRenderer(mr);
		v3Plot.addComponent(component);
	}
	
	/**
	 * Initialize HistogramRenderer, add it to plot, and 
	 * render event count histogram to PNG image in local file
	 */
	private void plotEvents(Channel channel, RSAMData rd, int displayCount, int dh) {

		if (threshold > 0) {
			rd.countEvents(threshold, ratio, maxEventLength);
		}
		
		String channelCode = channel.getCode().replace('$', ' ').replace('_', ' ').replace(',', '/');
		
		HistogramRenderer hr = new HistogramRenderer(rd.getCountsHistogram(bin));
		hr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
		hr.setDefaultExtents();
		hr.setMinX(startTime);
		hr.setMaxX(endTime);
		hr.createDefaultAxis(8, 8, false, true);
		hr.setXAxisToTime(8);
		hr.getAxis().setLeftLabelAsText("Events per " + bin);
		hr.getAxis().setBottomLabelAsText("Time (" + Valve3.getInstance().getTimeZoneAbbr()+ ")");
		
		DoubleMatrix2D data	= null;		
		data				= rd.getCumulativeCounts();
		if (data != null && data.rows() > 0) {
			
			double cmin = data.get(0, 1);
			double cmax = data.get(data.rows() - 1, 1);	
			
			MatrixRenderer mr = new MatrixRenderer(data, ranks);
			mr.setAllVisible(true);
			mr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
			mr.setExtents(startTime, endTime, cmin, cmax + 1);
			mr.createDefaultLineRenderers();
			
			Renderer[] r = mr.getLineRenderers();
			((ShapeRenderer)r[0]).color		= Color.red;
			((ShapeRenderer)r[0]).stroke	= new BasicStroke(2.0f);
			AxisRenderer ar = new AxisRenderer(mr);
			ar.createRightTickLabels(SmartTick.autoTick(cmin, cmax, 8, false), null);
			mr.setAxis(ar);
			
			hr.addRenderer(mr);
			hr.getAxis().setRightLabelAsText("Cumulative Counts");
		}
		
		hr.createDefaultLegendRenderer(new String[] {channelCode + " Events"});
		
		component.setTranslation(hr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		v3Plot.getPlot().addRenderer(hr);
		v3Plot.addComponent(component);	
	}

	/**
	 * Loop through the list of channels and create plots
	 * @throws Valve3Exception
	 */
	public void plotData() throws Valve3Exception {
		
		/// calculate how many graphs we are going to build (number of channels)
		compCount			= channelDataMap.size();
		
		// setting up variables to decide where to plot this component
		int displayCount	= 0;
		int dh				= component.getBoxHeight() / compCount;
		
		for (int cid : channelDataMap.keySet()) {
			
			// get the relevant information for this channel
			Channel channel	= channelsMap.get(cid);
			RSAMData data	= channelDataMap.get(cid);
			
			// verify their is something to plot
			if (data == null || data.rows() == 0) {
				continue;
			}
			
			switch(plotType) {
				case VALUES:
					plotValues(channel, data, displayCount, dh);
					break;
				case COUNTS:
					plotEvents(channel, data, displayCount, dh);
					break;
			}
			displayCount++;
		}
		
		switch(plotType) {
			case VALUES:
				v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Values");
				break;
			case COUNTS:
				v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Events");
				break;
		}
	}

	/**
	 * Concrete realization of abstract method. 
	 * Generate PNG images for values or event count histograms (depends from plot type) to file with random name.
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception {
		v3Plot		= v3p;
		component	= comp;
		channelsMap	= getChannels(vdxSource, vdxClient);
		getInputs();
		getData();
		
		plotData();

		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		plot.writePNG(v3Plot.getLocalFilename());
	}

	/**
	 * @return CSV string of RSAM values data described by given PlotComponent
	 */
	public String toCSV(PlotComponent c) throws Valve3Exception {
		component = c;
		getInputs();
		getData();
		
		switch(plotType) {
			case VALUES:
				return gdm.toCSV();
			case COUNTS:
				rd.countEvents(threshold, ratio, maxEventLength);
				return rd.getCountsCSV();
		}
		return null;
	}

	/**
	 * Initialize list of channels for given vdx source
	 * @param source	vdx source name
	 * @param client	vdx name
	 */
	private static Map<Integer, Channel> getChannels(String source, String client) {
		Map<Integer, Channel> channels;	
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", source);
		params.put("action", "channels");
		Pool<VDXClient> pool	= Valve3.getInstance().getDataHandler().getVDXClient(client);
		VDXClient cl			= pool.checkout();
		List<String> chs		= cl.getTextData(params);
		pool.checkin(cl);
		channels				= Channel.fromStringsToMap(chs);
		return channels;
	}
}