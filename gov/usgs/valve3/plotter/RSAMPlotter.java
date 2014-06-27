package gov.usgs.valve3.plotter;

import gov.usgs.math.BinSize;
import gov.usgs.math.Butterworth;
import gov.usgs.math.Butterworth.FilterType;
import gov.usgs.plot.Plot;
import gov.usgs.plot.PlotException;
import gov.usgs.plot.data.GenericDataMatrix;
import gov.usgs.plot.data.RSAMData;
import gov.usgs.plot.decorate.DefaultFrameDecorator;
import gov.usgs.plot.decorate.DefaultFrameDecorator.Location;
import gov.usgs.plot.decorate.SmartTick;
import gov.usgs.plot.render.AxisRenderer;
import gov.usgs.plot.render.DataPointRenderer;
import gov.usgs.plot.render.MatrixRenderer;
import gov.usgs.plot.render.Renderer;
import gov.usgs.plot.render.ShapeRenderer;
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
import gov.usgs.vdx.data.HistogramExporter;
import gov.usgs.vdx.data.MatrixExporter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

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
	protected void getInputs(PlotComponent comp) throws Valve3Exception {
		
		parseCommonParameters(comp);
		
		channelLegendsCols	= new String  [1];
		
		String pt = comp.get("plotType");
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
			validateDataManipOpts(comp);
			axisMap.put(0, "L");
			leftUnit	= "RSAM";
			leftLines++;
			break;
			
		case COUNTS:
			if (comp.get("threshold") != null) {
				threshold = comp.getDouble("threshold");
				if (threshold <= 0)
					throw new Valve3Exception("Illegal threshold.");
			} else
				threshold = 50;
			
			if (comp.get("ratio") != null) {
				ratio = comp.getDouble("ratio");
				if (ratio == 0)
					throw new Valve3Exception("Illegal ratio.");
			} else
				ratio = 1.3;
			
			if (comp.get("maxEventLength") != null) {
				maxEventLength = comp.getDouble("maxEventLength");
			} else
				maxEventLength = 300;
			
			String bs	= Util.stringToString(comp.get("cntsBin"), "hour");
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
	protected void getData(PlotComponent comp) throws Valve3Exception {
		
		// initialize variables
		boolean exceptionThrown	= false;
		String exceptionMsg		= "";
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
				} catch (UtilException e) {
					exceptionThrown	= true;
					exceptionMsg	= e.getMessage();
					break;
				} catch (Exception e) {
					exceptionThrown	= true;
					exceptionMsg	= e.getMessage();
					break;
				}
				
				// if data was collected
				if (data != null && data.rows() > 0) {
					data.adjustTime(timeOffset);
					
				// if no data was in the database, spoof the data to get an empty plot
				} else if (data == null) {
					ArrayList<double[]> list = new ArrayList<double[]>((int) (1));
					double[] d = new double[] { Double.NaN, Double.NaN };
					list.add(d);
					data = new RSAMData(list);					
				}
				
				channelDataMap.put(Integer.valueOf(channel), data);
			}
			
			// check back in our connection to the database
			pool.checkin(client);
		}
		
		// if a data limit message exists, then throw exception
		if (exceptionThrown) {
			throw new Valve3Exception(exceptionMsg);
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
	protected void plotValues(Valve3Plot v3p, PlotComponent comp, Channel channel, RSAMData data, int currentComp, int compBoxHeight) throws Valve3Exception {

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
							throw new Valve3Exception("Illegal max period value.");
					} else {
						ft = FilterType.LOWPASS;
						singleBand = filterMin;
					}
					if ( !Double.isNaN(filterMin) ) {
						if ( filterMin <= 0 )
							throw new Valve3Exception("Illegal min period value.");
					} else {
						ft = FilterType.HIGHPASS;
						singleBand = filterMax;
					}
					if ( ft == FilterType.BANDPASS )
						bw.set(ft, 4, Math.pow(filterPeriod, -1), Math.pow(filterMax, -1), Math.pow(filterMin, -1));
					else
						bw.set(ft, 4, Math.pow(filterPeriod, -1), Math.pow(singleBand, -1), 0);
					gdm.filter(bw, 1, true);
					break;
				case 2: // Running median
					gdm.set2median(1, filterPeriod );
					break;
				case 3: // Running mean
					gdm.set2mean(1, filterPeriod );
			}
		}
		if (doArithmetic) { gdm.doArithmetic(1, arithmeticType, arithmeticValue); }
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
			String[] hdr = {null, null, channel.getCode().replace('$', '_').replace(',', '/'), "RSAM" };
			csvHdrs.add( hdr );
			
			// Initialize data for export; add to set for CSV
			ExportData ed = new ExportData( csvIndex, new MatrixExporter(gdm.getData(), ranks, axisMap) );
			csvData.add( ed );
			
		} else {
			try {
				MatrixRenderer leftMR	= getLeftMatrixRenderer(comp, channel, gdm, currentComp, compBoxHeight, 0, leftUnit);
				v3p.getPlot().addRenderer(leftMR);
				comp.setTranslation(leftMR.getDefaultTranslation(v3p.getPlot().getHeight()));
				comp.setTranslationType("ty");
				v3p.addComponent(comp);
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
	private void plotEvents(Valve3Plot v3p, PlotComponent comp, Channel channel, RSAMData rd, int currentComp, int compBoxHeight) throws Valve3Exception {

		// get the relevant information for this channel
		channel.setCode(channel.getCode().replace('$', ' ').replace('_', ' ').replace(',', '/'));
		rd.countEvents(threshold, ratio, maxEventLength);
		
		// setup the histogram renderer with this data
		HistogramExporter hr = new HistogramExporter(rd.getCountsHistogram(bin));
		hr.setLocation(comp.getBoxX(), comp.getBoxY() + (currentComp - 1) * compBoxHeight, comp.getBoxWidth(), compBoxHeight - 16);
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
			String[] hdr = {null, null, channel.getCode().replace('$', '_').replace(',', '/'), "EventsPer" + bin };
			csvHdrs.add( hdr );
			
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
			mr.setLocation(comp.getBoxX(), comp.getBoxY() + (currentComp - 1) * compBoxHeight, comp.getBoxWidth(), compBoxHeight - 16);
			mr.setExtents(startTime+timeOffset, endTime+timeOffset, cmin, cmax + 1);
			Renderer[] r = null;
			if(shape==null){
				mr.createDefaultPointRenderers(comp.getColor());
			} else {
				if (shape.equals("l")) {
					mr.createDefaultLineRenderers(comp.getColor());
					r = mr.getLineRenderers();
					((ShapeRenderer)r[0]).color		= Color.red;
					((ShapeRenderer)r[0]).stroke	= new BasicStroke(2.0f);
				} else {
					mr.createDefaultPointRenderers(shape.charAt(0), comp.getColor());
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
				String[] hdr = {null, null, channel.getCode().replace('$', '_').replace(',', '/'), "CumulativeCount" + bin };
				csvHdrs.add( hdr );
				
				// Initialize data for export; add to set for CSV
				ExportData ed = new ExportData(csvIndex, mr);
				csvData.add(ed);
				csvIndex++;
			}
		}
		
		if (!forExport) {
			v3p.getPlot().addRenderer(hr);		
			comp.setTranslation(hr.getDefaultTranslation(v3p.getPlot().getHeight()));
			comp.setTranslationType("ty");
			v3p.addComponent(comp);
		}
	}

	/**
	 * If v3Plot is null, prepare data for exporting
	 * Otherwise, Loop through the list of channels and create plots
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception {
		
		// calculate the number of plot components that will be displayed per channel
		int channelCompCount = 1;
		
		// total components is components per channel * number of channels
		compCount = channelCompCount * channelDataMap.size();
		
		// setting up variables to decide where to plot this component
		int currentComp		= 1;
		int compBoxHeight	= comp.getBoxHeight();
		
		for (int cid : channelDataMap.keySet()) {
			
			// get the relevant information for this channel
			Channel channel	= channelsMap.get(cid);
			RSAMData data	= channelDataMap.get(cid);
			
			// if there is no data for this channel, then resize the plot window 
			if (data == null || data.rows() == 0) {
				v3p.setHeight(v3p.getHeight() - channelCompCount * comp.getBoxHeight());
				Plot plot	= v3p.getPlot();
				plot.setSize(plot.getWidth(), plot.getHeight() - channelCompCount * comp.getBoxHeight());
				compCount = compCount - channelCompCount;
				continue;
			}
			
			switch(plotType) {
				case VALUES:
					plotValues(v3p, comp, channel, data, currentComp, compBoxHeight);
					break;
				case COUNTS:
					plotEvents(v3p, comp, channel, data, currentComp, compBoxHeight);
					break;
			}
			currentComp++;
		}
		if (!forExport) {
			switch(plotType) {
				case VALUES:
					if(channelDataMap.size()!=1){
						v3p.setCombineable(false);
					} else {
						v3p.setCombineable(true);
					}
					if (!vdxSource.contains("winston"))
						addMetaData(vdxSource, vdxClient, v3p, comp);
					v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Values");
					break;
				case COUNTS:
					v3p.setCombineable(false);
					if (!vdxSource.contains("winston"))
						addMetaData(vdxSource, vdxClient, v3p, comp);
					v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Events");
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
		comp.setPlotter(this.getClass().getName());
		getInputs(comp);
		
		// plot configuration
		if (!forExport) {
			v3p.setExportable(true);
		}
		
		// this is a legitimate request so lookup the data from the database and plot it
		getData(comp);		
		plotData(v3p, comp);
		
		if (!forExport) 
			writeFile(v3p);
	}
}