package gov.usgs.valve3.plotter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.DataPointRenderer;
import gov.usgs.plot.DefaultFrameDecorator;
import gov.usgs.plot.HistogramRenderer;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.Renderer;
import gov.usgs.plot.ShapeRenderer;
import gov.usgs.plot.SmartTick;
import gov.usgs.plot.DefaultFrameDecorator.Location;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.util.UtilException;
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
public class RatSAMPlotter extends RawDataPlotter {
	
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

	int compCount;
	private static Map<Integer, Channel> channelsMap;
	private int cid1, cid2;
	private boolean removeBias;
	private double threshold;
	private double ratio;
	private double maxEventLength;
	private BinSize bin;
	private RSAMData rd;
	private PlotType plotType;
	private GenericDataMatrix gdm;
	RSAMData data;
	
	protected String label;

	/**
	 * Default constructor
	 */
	public RatSAMPlotter() {
		super();
		label	= "RatSAM";
		ranks	= false;
	}

	/**
	 * Initialize internal data from PlotComponent
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */	
	protected void getInputs(PlotComponent component) throws Valve3Exception {
		
		parseCommonParameters(component);
		
		String pt = component.get("plotType");
		if ( pt == null )
			plotType = PlotType.VALUES;
		else {
			plotType	= PlotType.fromString(pt);
			if (plotType == null) {
				throw new Valve3Exception("Illegal plot type: " + pt);
			}
		}
	
		try{
			removeBias = component.getBoolean("rb");
		} catch (Valve3Exception ex){
			removeBias = false;
		}
		
		switch(plotType) {
		
		case VALUES:
			break;
			
		case COUNTS:
			if (component.get("threshold") != null) {
				threshold = component.getDouble("threshold");
				if (threshold == 0)
					throw new Valve3Exception("Illegal threshold.");
			}
			
			if (component.get("ratio") != null) {
				ratio = component.getDouble("ratio");
				if (ratio == 0)
					throw new Valve3Exception("Illegal ratio.");
			}
			
			if (component.get("maxEventLength") != null) {
				maxEventLength = component.getDouble("maxEventLength");
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
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	protected void getData(PlotComponent component) throws Valve3Exception {
		
		boolean gotData = false;
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "ratdata");
		params.put("ch", ch);
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("plotType", plotType.toString());
		addDownsamplingInfo(params);
		// checkout a connection to the database
		Pool<VDXClient> pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client		= pool.checkout();
		if (client == null)
			return;
		try{
			data = (RSAMData)client.getBinaryData(params);
		}
		catch(UtilException e){
			throw new Valve3Exception(e.getMessage()); 
		}
		if (data != null && data.rows() > 0) {
			gotData = true;
			data.adjustTime(component.getOffset(startTime));
		}
		
		if (!gotData) {
			throw new Valve3Exception("No data for any stations.");
		}
        // check back in our connection to the database
		pool.checkin(client);
	}
	
	/**
	 * Initialize DataRenderer, add it to plot, remove mean from rsam data if needed 
	 * and render rsam values to PNG image in local file
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @param channel1 Channel
	 * @param channel2 Channel
	 * @param rd RSAMData
	 * @throws Valve3Exception
	 */
	protected void plotValues(Valve3Plot v3Plot, PlotComponent component, Channel channel1, Channel channel2, RSAMData rd) throws Valve3Exception {
		
		GenericDataMatrix gdm = new GenericDataMatrix(rd.getData());
		
		// Remove the mean from the first column (this needs to be user controlled)
		if (removeBias) {
			gdm.add(1, -gdm.mean(1));
		}
		
		String channelCode1 = channel1.getCode().replace('$', ' ').replace('_', ' ').replace(',', '/');
		String channelCode2 = channel2.getCode().replace('$', ' ').replace('_', ' ').replace(',', '/');
		
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
		double timeOffset = component.getOffset(startTime);
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
		mr.setLocation(component.getBoxX(), component.getBoxY() + 8, component.getBoxWidth(), component.getBoxHeight() - 16);
		mr.setExtents(startTime+timeOffset, endTime+timeOffset, yMin, yMax);
		mr.createDefaultAxis(xTickMarks?8:0, yTickMarks?8:0, false, allowExpand, yTickValues);
		mr.setXAxisToTime(xTickMarks?8:0, xTickValues);	
		mr.setAllVisible(true);
		if(shape==null){
			mr.createDefaultPointRenderers(component.getColor());
		} else {
			if (shape.equals("l")) {
				mr.createDefaultLineRenderers(component.getColor());
			} else {
				mr.createDefaultPointRenderers(shape.charAt(0), component.getColor());
			}
		}
		if(isDrawLegend){
			mr.createDefaultLegendRenderer(new String[] {channelCode1 + "/" + channelCode2 + " " + label});
		}
		if(yUnits){
			mr.getAxis().setLeftLabelAsText(label);
		}
		if(xUnits){
			mr.getAxis().setBottomLabelAsText(component.getTimeZone().getID() + " Time (" + Util.j2KToDateString(startTime+timeOffset, "yyyy MM dd") + " to " + Util.j2KToDateString(endTime+timeOffset, "yyyy MM dd")+ ")");	
		}
		if(yLabel){
			DefaultFrameDecorator.addLabel(mr, "channelCode1" + "-" + "channelCode2", Location.LEFT);
		}
		component.setTranslation(mr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		v3Plot.getPlot().addRenderer(mr);
		v3Plot.addComponent(component);
	}
	
	/**
	 * Initialize HistogramRenderer, add it to plot, and 
	 * render event count histogram to PNG image in local file
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @param channel1 Channel
	 * @param channel2 Channel
	 * @param rd RSAMData
	 * @throws Valve3Exception
	 */
	private void plotEvents(Valve3Plot v3Plot, PlotComponent component, Channel channel1, Channel channel2, RSAMData rd) throws Valve3Exception {

		if (threshold > 0) {
			rd.countEvents(threshold, ratio, maxEventLength);
		}
		double timeOffset = component.getOffset(startTime);
		String channelCode1 = channel1.getCode().replace('$', ' ').replace('_', ' ').replace(',', '/');
		String channelCode2 = channel2.getCode().replace('$', ' ').replace('_', ' ').replace(',', '/');
		
		HistogramRenderer hr = new HistogramRenderer(rd.getCountsHistogram(bin));
		hr.setLocation(component.getBoxX(), component.getBoxY() + 8, component.getBoxWidth(), component.getBoxHeight() - 16);
		hr.setDefaultExtents();
		hr.setMinX(startTime+timeOffset);
		hr.setMaxX(endTime+timeOffset);
		hr.createDefaultAxis(xTickMarks?8:0, yTickMarks?8:0, false, true, yTickValues);
		hr.setXAxisToTime(xTickMarks?8:0, xTickValues);
		if(yUnits){
			hr.getAxis().setLeftLabelAsText("Events per " + bin);
		}
		if(xUnits){
			hr.getAxis().setBottomLabelAsText(component.getTimeZone().getID() + " Time (" + Util.j2KToDateString(startTime+timeOffset, "yyyy MM dd") + " to " + Util.j2KToDateString(endTime+timeOffset, "yyyy MM dd")+ ")");	
		}
		if(yLabel){
			DefaultFrameDecorator.addLabel(hr, "channelCode1" + "-" + "channelCode2", Location.LEFT);
		}
		DoubleMatrix2D data	= null;		
		data				= rd.getCumulativeCounts();
		if (data != null && data.rows() > 0) {
			
			double cmin = data.get(0, 1);
			double cmax = data.get(data.rows() - 1, 1);	
			
			MatrixRenderer mr = new MatrixRenderer(data, ranks);
			mr.setAllVisible(true);
			mr.setLocation(component.getBoxX(), component.getBoxY() + 8, component.getBoxWidth(), component.getBoxHeight() - 16);
			mr.setExtents(startTime+timeOffset, endTime+timeOffset, cmin, cmax + 1);
			Renderer[] r = null;
			if(shape==null){
				mr.createDefaultPointRenderers(component.getColor());
			} else {
				if (shape.equals("l")) {
					mr.createDefaultLineRenderers(component.getColor());
					r = mr.getLineRenderers();
					((ShapeRenderer)r[0]).color		= Color.red;
					((ShapeRenderer)r[0]).stroke	= new BasicStroke(2.0f);
				} else {
					mr.createDefaultPointRenderers(shape.charAt(0), component.getColor());
					r = mr.getPointRenderers();
					((DataPointRenderer)r[0]).color		= Color.red;
				}
			}
			AxisRenderer ar = new AxisRenderer(mr);
			ar.createRightTickLabels(SmartTick.autoTick(cmin, cmax, 8, false), null);
			mr.setAxis(ar);
			
			hr.addRenderer(mr);
			hr.getAxis().setRightLabelAsText("Cumulative Counts");
		}
		
		if(isDrawLegend) hr.createDefaultLegendRenderer(new String[] {channelCode1 + "/" + channelCode2 + " Events"});
		
		component.setTranslation(hr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		v3Plot.getPlot().addRenderer(hr);
		v3Plot.addComponent(component);	
	}

	/**
	 * Loop through the list of channels and create plots
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3Plot, PlotComponent component) throws Valve3Exception {

		String[] channels	= ch.split(",");
		cid1				= Integer.valueOf(channels[0]);
		cid2				= Integer.valueOf(channels[1]);
		Channel channel1 = channelsMap.get(cid1);
		Channel channel2 = channelsMap.get(cid2);
			
		switch(plotType) {
			case VALUES:
				plotValues(v3Plot, component, channel1, channel2, data);
				v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Values");
				break;
			case COUNTS:
				plotEvents(v3Plot, component, channel1, channel2, data);
				v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Events");
				break;
		}
	}

	/**
	 * Concrete realization of abstract method. 
	 * Generate PNG images for values or event count histograms (depends from plot type) to file with random name.
	 * @param v3p Valve3Plot
	 * @param comp PlotComponent
	 * @throws Valve3Exception
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception {
		channelsMap	= getChannels(vdxSource, vdxClient);
		getInputs(comp);
		getData(comp);
		
		plotData(v3p, comp);

		Plot plot = v3p.getPlot();
		plot.setBackgroundColor(Color.white);
		plot.writePNG(v3p.getLocalFilename());
	}

}