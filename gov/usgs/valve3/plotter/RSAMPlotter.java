package gov.usgs.valve3.plotter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.DataPointRenderer;
import gov.usgs.plot.DefaultFrameDecorator;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.PlotException;
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
import gov.usgs.vdx.data.ExportData;
import gov.usgs.vdx.data.GenericDataMatrix;
import gov.usgs.vdx.data.HistogramExporter;
import gov.usgs.vdx.data.hypo.HypocenterList.BinSize;
import gov.usgs.vdx.data.MatrixExporter;
import gov.usgs.vdx.data.rsam.RSAMData;

import cern.colt.matrix.DoubleMatrix2D;

/**
 * Get RSAM information from vdx server and  
 * generate images of RSAM values and RSAM event count histograms
 * in files with random names.
 *
 * @author Dan Cervelli, Loren Antolik
 */
public class RSAMPlotter extends RawDataPlotter {
	
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
	private Map<Integer, RSAMData> channelDataMap;
	private double threshold;
	private double ratio;
	private double maxEventLength;
	private BinSize bin;
	private PlotType plotType;
	
	protected String label;


	/**
	 * Default constructor
	 */
	public RSAMPlotter() {
		super();
		label	= "RSAM";
		ranks = false;
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
		
		switch(plotType) {
		
		case VALUES:
			break;
			
		case COUNTS:
			if (component.get("threshold") != null) {
				threshold = component.getDouble("threshold");
				if (threshold == 0)
					throw new Valve3Exception("Illegal threshold.");
			} else
				threshold = 50;
			
			if (component.get("ratio") != null) {
				ratio = component.getDouble("ratio");
				if (ratio == 0)
					throw new Valve3Exception("Illegal ratio.");
			} else
				ratio = 1.3;
			
			if (component.get("maxEventLength") != null) {
				maxEventLength = component.getDouble("maxEventLength");
			} else
				maxEventLength = 300;
			
			String bs	= Util.stringToString(component.get("cntsBin"), "hour");
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
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("plotType", plotType.toString());
		addDownsamplingInfo(params);
		
		// checkout a connection to the database
		Pool<VDXClient> pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client		= pool.checkout();
		if (client == null) {
			return;
		}
	
		// create a map to hold all the channel data
		channelDataMap		= new LinkedHashMap<Integer, RSAMData>();
		String[] channels	= ch.split(",");
		
		boolean gotData = false;
		
		// iterate through each of the selected channels and place the data in the map
		for (String channel : channels) {
			params.put("ch", channel);
			RSAMData data = null;
			try {
				data = (RSAMData)client.getBinaryData(params);
			} catch (UtilException e) {
				data = null; 
			}
			
			// if data was collected
			if (data != null && data.rows() > 0) {
				data.adjustTime(component.getOffset(startTime));
				gotData = true;
			}
			channelDataMap.put(Integer.valueOf(channel), data);
		}
		
		// check back in our connection to the database
		pool.checkin(client);
		
		// if no data exists, then throw exception
		if (channelDataMap.size() == 0 || !gotData) {
			throw new Valve3Exception("No data for any channel.");
		}
	}
	
	/**
	 * Initialize DataRenderer, add it to plot, remove mean from rsam data if needed 
	 * and render rsam values to PNG image in local file
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @param channel Channel
	 * @param rd RSAMData
	 * @param displayCount ?
	 * @param dh display height
	 * @throws Valve3Exception
	 */
	protected void plotValues(Valve3Plot v3Plot, PlotComponent component, Channel channel, RSAMData rd, int displayCount, int dh) throws Valve3Exception {
		boolean      forExport = (v3Plot == null);	// = "prepare data for export"
		
		GenericDataMatrix gdm = new GenericDataMatrix(rd.getData());
		double timeOffset = component.getOffset(startTime);
		// Remove the mean from the first column (this needs to be user controlled)
		if (removeBias) {
			gdm.add(1, -gdm.mean(1));
		}
		
		String channelCode = channel.getCode().replace('$', ' ').replace('_', ' ').replace(',', '/');
		
		if ( forExport ) {
			// Add column header to csvHdrs
			csvHdrs.append(",");
			csvHdrs.append(channel.getCode().replace('$', '_').replace(',', '/') + "_RSAM");
			// Initialize data for export; add to set for CSV
			ExportData ed = new ExportData( csvIndex, new MatrixExporter(gdm.getData(), ranks, axisMap) );
			csvData.add( ed );
			csvIndex++;
			return;
		}
		double yMin = 1E300;
		double yMax = -1E300;
		boolean allowExpand = true;
		
		if (component.isAutoScale("ysL")) {
			double buff;
			yMin	= Math.min(yMin, gdm.min(1));
			yMax	= Math.max(yMax, gdm.max(1));
			buff	= (yMax - yMin) * 0.05;
			yMin	= yMin - buff;
			yMax	= yMax + buff;
		} else {
			double[] ys = component.getYScale("ysL", yMin, yMax);
			yMin = ys[0];
			yMax = ys[1];
			allowExpand = false;
			if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
				throw new Valve3Exception("Illegal axis values.");
		}
		
		MatrixRenderer mr = new MatrixRenderer(gdm.getData(), ranks);
		mr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
		mr.setExtents(startTime+timeOffset, endTime+timeOffset, yMin, yMax);
		mr.createDefaultAxis(8,8,xTickMarks,yTickMarks, false, allowExpand, xTickValues, yTickValues);
		mr.setXAxisToTime(8, xTickMarks, xTickValues);	
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
		    mr.createDefaultLegendRenderer(new String[] {channelCode + " " + label});
		}
		if(yUnits){
			mr.getAxis().setLeftLabelAsText(label);
		}
		if(xUnits){
			mr.getAxis().setBottomLabelAsText(component.getTimeZone().getID() + " Time (" + Util.j2KToDateString(startTime+timeOffset, "yyyy-MM-dd HH:mm:ss") + " to " + Util.j2KToDateString(endTime+timeOffset, "yyyy-MM-dd HH:mm:ss")+ ")");	
		}
		if(yLabel){
			DefaultFrameDecorator.addLabel(mr, channel.getCode(), Location.LEFT);
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
	 * @param channel Channel
	 * @param rd RSAMData
	 * @param displayCount ?
	 * @param dh display height
	 * @throws Valve3Exception
	 */
	private void plotEvents(Valve3Plot v3Plot, PlotComponent component, Channel channel, RSAMData rd, int displayCount, int dh) throws Valve3Exception {
		boolean      forExport = (v3Plot == null);	// = "prepare data for export"

		if (threshold > 0) {
			rd.countEvents(threshold, ratio, maxEventLength);
		}
		double timeOffset = component.getOffset(startTime);
		String channelCode = channel.getCode().replace('$', ' ').replace('_', ' ').replace(',', '/');
		
		HistogramExporter hr = new HistogramExporter(rd.getCountsHistogram(bin));
		hr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
		hr.setDefaultExtents();
		hr.setMinX(startTime+timeOffset);
		hr.setMaxX(endTime+timeOffset);
		hr.createDefaultAxis(8,8,xTickMarks,yTickMarks, false, true, xTickValues, yTickValues);
		hr.setXAxisToTime(8, xTickMarks, xTickValues);	
		if(yUnits){
			hr.getAxis().setLeftLabelAsText("Events per " + bin);
		}
		if(xUnits){
			hr.getAxis().setBottomLabelAsText(component.getTimeZone().getID() + " Time (" + Util.j2KToDateString(startTime+timeOffset, "yyyy-MM-dd HH:mm:ss") + " to " + Util.j2KToDateString(endTime+timeOffset, "yyyy-MM-dd HH:mm:ss")+ ")");	
		}
		if ( forExport ) {
			// Add column header to csvHdrs
			csvHdrs.append(",");
			csvHdrs.append(channel.getCode().replace('$', '_').replace(',', '/') + String.format( "_EventsPer%s", bin ));
			// Initialize data for export; add to set for CSV
			csvData.add( new ExportData( csvIndex, hr ) );
			csvIndex++;
		}

		DoubleMatrix2D data	= null;		
		data				= rd.getCumulativeCounts();
		if (data != null && data.rows() > 0) {
			
			double cmin = data.get(0, 1);
			double cmax = data.get(data.rows() - 1, 1);	
			
			MatrixExporter mr = new MatrixExporter(data, ranks);
			mr.setAllVisible(true);
			mr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
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
			if(yTickValues){
				ar.createRightTickLabels(SmartTick.autoTick(cmin, cmax, 8, false), null);
			}
			mr.setAxis(ar);
			
			hr.addRenderer(mr);
			if(yUnits){
				hr.getAxis().setRightLabelAsText("Cumulative Counts");
			}
			if(yLabel){
				DefaultFrameDecorator.addLabel(mr, channel.getCode(), Location.LEFT);
			}
			if ( forExport ) {
				// Add column header to csvHdrs
				csvHdrs.append(",");
				csvHdrs.append(channel.getCode().replace('$', '_').replace(',', '/') + String.format( "_CumulativeCount", bin ));
				// Initialize data for export; add to set for CSV
				csvData.add( new ExportData( csvIndex, mr ) );
				csvIndex++;
			}
		}
		
		if ( forExport )
			return;
			
		if(isDrawLegend) hr.createDefaultLegendRenderer(new String[] {channelCode + " Events"});
		v3Plot.getPlot().addRenderer(hr);
		
		component.setTranslation(hr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		v3Plot.addComponent(component);	
	}

	/**
	 * If v3Plot is null, prepare data for exporting
	 * Otherwise, Loop through the list of channels and create plots
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3Plot, PlotComponent component) throws Valve3Exception {
		boolean      forExport = (v3Plot == null);	// = "prepare data for export"
		
		/// calculate how many graphs we are going to build (number of channels)
		compCount			= channelDataMap.size();
		
		// setting up variables to decide where to plot this component
		int displayCount	= 0;
		int dh				= component.getBoxHeight();
		
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
					plotValues(v3Plot, component, channel, data, displayCount, dh);
					break;
				case COUNTS:
					plotEvents(v3Plot, component, channel, data, displayCount, dh);
					break;
			}
			displayCount++;
		}
		
		if ( !forExport )
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
	 * If v3p is null, prepare data for export -- assumes csvData, csvData & csvIndex initialized
	 * @param v3p Valve3Plot
	 * @param comp PlotComponent
	 * @throws Valve3Exception
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception, PlotException {

		channelsMap	= getChannels(vdxSource, vdxClient);
		getInputs(comp);
		getData(comp);
		
		plotData(v3p, comp);

		if ( v3p != null ) {
			Plot plot = v3p.getPlot();
			plot.setBackgroundColor(Color.white);
			plot.writePNG(v3p.getLocalFilename());
		}
	}
}