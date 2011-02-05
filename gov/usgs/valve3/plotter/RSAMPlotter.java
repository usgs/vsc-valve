package gov.usgs.valve3.plotter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import gov.usgs.math.Butterworth;
import gov.usgs.math.Butterworth.FilterType;
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

	private PlotType plotType;
	private Map<Integer, RSAMData> channelDataMap;
	
	DoubleMatrix2D countsData	= null;	
	
	private double threshold;
	private double ratio;
	private double maxEventLength;
	private BinSize bin;


	/**
	 * Default constructor
	 */
	public RSAMPlotter() {
		super();
		ranks = false;
	}

	/**
	 * Initialize internal data from PlotComponent
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */	
	protected void getInputs(PlotComponent component) throws Valve3Exception {
		
		parseCommonParameters(component);
		
		channelLegendsCols	= new String  [1];
		
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
			leftLines	= 0;
			axisMap		= new LinkedHashMap<Integer, String>();
			validateDataManipOpts(component);
			axisMap.put(0, "L");
			leftUnit	= "RSAM";
			leftLines++;
			break;
			
		case COUNTS:
			if (component.get("threshold") != null) {
				threshold = component.getDouble("threshold");
				if (threshold <= 0)
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
		
		// initialize variables
		boolean gotData			= false;
		Pool<VDXClient> pool	= null;
		VDXClient client		= null;
		channelDataMap			= new LinkedHashMap<Integer, RSAMData>();
		String[] channels		= ch.split(",");
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("plotType", plotType.toString());
		addDownsamplingInfo(params);
		
		// checkout a connection to the database
		pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		if (pool != null) {
			client	= pool.checkout();
		
			// iterate through each of the selected channels and place the data in the map
			for (String channel : channels) {
				params.put("ch", channel);
				RSAMData data = null;
				try {
					data = (RSAMData)client.getBinaryData(params);
				} catch (Exception e) {
					data = null; 
				}
				
				// if data was collected
				if (data != null && data.rows() > 0) {
					data.adjustTime(timeOffset);
					gotData = true;
				}
				channelDataMap.put(Integer.valueOf(channel), data);
			}
			
			// check back in our connection to the database
			pool.checkin(client);
		}
		
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
	protected void plotValues(Valve3Plot v3Plot, PlotComponent component, Channel channel, RSAMData data, int currentComp, int compBoxHeight) throws Valve3Exception {

		// get the relevant information for this channel
		channel.setCode(channel.getCode().replace('$', ' ').replace('_', ' ').replace(',', '/'));
		GenericDataMatrix gdm	= new GenericDataMatrix(data.getData());
		channelLegendsCols[0]	= String.format("%s %s", channel.getCode(), leftUnit);	
		
		if (doDespike) { gdm.despike(1, despikePeriod ); }
		if (doDetrend) { gdm.detrend(1); }
		if (filterPick != 0) {
			switch(filterPick) {
				case 1: // Bandpass
					Butterworth bw = new Butterworth();
					FilterType ft = FilterType.BANDPASS;
					Double singleBand = 0.0;
					if ( !Double.isNaN(filterMax) ) {
						if ( filterMax <= 0 )
							throw new Valve3Exception("Illegal max hertz value.");
					} else {
						ft = FilterType.HIGHPASS;
						singleBand = filterMin;
					}
					if ( !Double.isNaN(filterMin) ) {
						if ( filterMin <= 0 )
							throw new Valve3Exception("Illegal min hertz value.");
					} else {
						ft = FilterType.LOWPASS;
						singleBand = filterMax;
					}
					/* SBH
					if ( ft == FilterType.BANDPASS )
						bw.set(ft, 4, gdm.getSamplingRate(), filterMin, filterMax);
					else
						bw.set(ft, 4, gdm.getSamplingRate(), singleBand, 0);
					gdm.filter(bw, true); */
					break;
				case 2: // Running median
					gdm.set2median(1, filterPeriod );
					break;
				case 3: // Running mean
					gdm.set2mean(1, filterPeriod );
			}
		}
		if (debiasPick != 0 ) {
			double bias = 0.0;
			switch ( debiasPick ) {
				case 1: // remove mean 
					bias = gdm.mean(1);
					break;
				case 2: // remove initial value
					bias = gdm.first(1);
					break;
				case 3: // remove user value
					bias = debiasValue;
					break;
			}
			gdm.add(1, -bias);
		}	
		
		if (forExport) {
			
			// Add column header to csvHdrs
			csvHdrs.append(",");
			csvHdrs.append(channel.getCode().replace('$', '_').replace(',', '/') + "_RSAM");
			
			// Initialize data for export; add to set for CSV
			ExportData ed = new ExportData( csvIndex, new MatrixExporter(gdm.getData(), ranks, axisMap) );
			csvData.add( ed );
			
		} else {
			try {
				MatrixRenderer leftMR	= getLeftMatrixRenderer(component, channel, gdm, currentComp, compBoxHeight, 0, leftUnit);
				v3Plot.getPlot().addRenderer(leftMR);
				component.setTranslation(leftMR.getDefaultTranslation(v3Plot.getPlot().getHeight()));
				component.setTranslationType("ty");
				v3Plot.addComponent(component);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
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
	private void plotEvents(Valve3Plot v3Plot, PlotComponent component, Channel channel, RSAMData rd, int currentComp, int compBoxHeight) throws Valve3Exception {

		// get the relevant information for this channel
		channel.setCode(channel.getCode().replace('$', ' ').replace('_', ' ').replace(',', '/'));
		rd.countEvents(threshold, ratio, maxEventLength);
		
		// setup the histogram renderer with this data
		HistogramExporter hr = new HistogramExporter(rd.getCountsHistogram(bin));
		hr.setLocation(component.getBoxX(), component.getBoxY() + (currentComp - 1) * compBoxHeight + 8, component.getBoxWidth(), compBoxHeight - 16);
		hr.setDefaultExtents();
		hr.setMinX(startTime+timeOffset);
		hr.setMaxX(endTime+timeOffset);
		
		// x axis decorations
		if (currentComp == compCount) {
			hr.createDefaultAxis(8,8,xTickMarks,yTickMarks, false, true, xTickValues, yTickValues);
			hr.setXAxisToTime(8, xTickMarks, xTickValues);	
			if(xUnits){
				hr.getAxis().setBottomLabelAsText(timeZoneID + " Time (" + Util.j2KToDateString(startTime+timeOffset, dateFormatString) + " to " + Util.j2KToDateString(endTime+timeOffset, dateFormatString)+ ")");	
			}
			if (xLabel) {}
			
		// don't display xTickValues for top and middle components, only for bottom component
		} else {
			hr.createDefaultAxis(8,8,xTickMarks,yTickMarks, false, true, false, yTickValues);
			hr.setXAxisToTime(8, xTickMarks, false);
		}
		
		// y axis decorations
		if(yUnits){
			hr.getAxis().setLeftLabelAsText("Events per " + bin);
		}
		if (yLabel)	{
			DefaultFrameDecorator.addLabel(hr, channel.getCode(), Location.LEFT);
		}
		
		// legend decorations			
		if (isDrawLegend) {
			hr.createDefaultLegendRenderer(new String[] {channel.getCode() + " Events"});
		}
		
		if (forExport) {
			
			// Add column header to csvHdrs
			csvHdrs.append(",");
			csvHdrs.append(channel.getCode().replace('$', '_').replace(',', '/') + String.format( "_EventsPer%s", bin ));
			
			// Initialize data for export; add to set for CSV
			ExportData ed = new ExportData (csvIndex, hr);
			csvData.add(ed);
			csvIndex++;
		}

		countsData	= rd.getCumulativeCounts();
		if (countsData != null && countsData.rows() > 0) {
			
			double cmin = countsData.get(0, 1);
			double cmax = countsData.get(countsData.rows() - 1, 1);
			
			MatrixExporter mr = new MatrixExporter(countsData, ranks);
			mr.setAllVisible(true);
			mr.setLocation(component.getBoxX(), component.getBoxY() + (currentComp - 1) * compBoxHeight + 8, component.getBoxWidth(), compBoxHeight - 16);
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
					((DataPointRenderer)r[0]).color	= Color.red;
				}
			}
			
			// create the axis for the right hand side
			AxisRenderer ar = new AxisRenderer(mr);
			if (yTickValues) {
				ar.createRightTickLabels(SmartTick.autoTick(cmin, cmax, 8, false), null);
			}
			mr.setAxis(ar);
			
			hr.addRenderer(mr);
			if(yUnits){
				hr.getAxis().setRightLabelAsText("Cumulative Counts");
			}
			
			if (forExport) {
				
				// Add column header to csvHdrs
				csvHdrs.append(",");
				csvHdrs.append(channel.getCode().replace('$', '_').replace(',', '/') + String.format( "_CumulativeCount", bin ));
				
				// Initialize data for export; add to set for CSV
				ExportData ed = new ExportData(csvIndex, mr);
				csvData.add(ed);
				csvIndex++;
			}
		}
		
		if (!forExport) {
			v3Plot.getPlot().addRenderer(hr);		
			component.setTranslation(hr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
			component.setTranslationType("ty");
			v3Plot.addComponent(component);
		}
	}

	/**
	 * If v3Plot is null, prepare data for exporting
	 * Otherwise, Loop through the list of channels and create plots
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3Plot, PlotComponent component) throws Valve3Exception {
		
		// calculate the number of plot components that will be displayed per channel
		int channelCompCount = 1;
		
		// total components is components per channel * number of channels
		compCount = channelCompCount * channelDataMap.size();
		
		// setting up variables to decide where to plot this component
		int currentComp		= 1;
		int compBoxHeight	= component.getBoxHeight();
		
		for (int cid : channelDataMap.keySet()) {
			
			// get the relevant information for this channel
			Channel channel	= channelsMap.get(cid);
			RSAMData data	= channelDataMap.get(cid);
			
			// if there is no data for this channel, then resize the plot window 
			if (data == null || data.rows() == 0) {
				v3Plot.setHeight(v3Plot.getHeight() - channelCompCount * component.getBoxHeight());
				Plot plot	= v3Plot.getPlot();
				plot.setSize(plot.getWidth(), plot.getHeight() - channelCompCount * component.getBoxHeight());
				compCount = compCount - channelCompCount;
				continue;
			}
			
			switch(plotType) {
				case VALUES:
					plotValues(v3Plot, component, channel, data, currentComp, compBoxHeight);
					break;
				case COUNTS:
					plotEvents(v3Plot, component, channel, data, currentComp, compBoxHeight);
					break;
			}
			currentComp++;
		}
		
		if (!forExport) {
			switch(plotType) {
				case VALUES:
					v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Values");
					break;
				case COUNTS:
					v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Events");
					break;
			}
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
		
		forExport	= (v3p == null);
		channelsMap	= getChannels(vdxSource, vdxClient);
		
		getInputs(comp);
		getData(comp);
		
		plotData(v3p, comp);

		if (!forExport) {
			Plot plot = v3p.getPlot();
			plot.setBackgroundColor(Color.white);
			plot.writePNG(v3p.getLocalFilename());
		}
	}
}