package gov.usgs.valve3.plotter;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import gov.usgs.math.Butterworth;
import gov.usgs.math.Butterworth.FilterType;
import gov.usgs.plot.Plot;
import gov.usgs.plot.PlotException;
import gov.usgs.plot.data.GenericDataMatrix;
import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.plot.map.GeoLabel;
import gov.usgs.plot.map.GeoLabelSet;
import gov.usgs.plot.map.MapRenderer;
import gov.usgs.plot.render.EllipseVectorRenderer;
import gov.usgs.plot.render.MatrixRenderer;
import gov.usgs.plot.render.Renderer;
import gov.usgs.plot.render.TextRenderer;
import gov.usgs.proj.GeoRange;
import gov.usgs.proj.TransverseMercator;
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
import gov.usgs.vdx.data.Column;
import gov.usgs.vdx.data.ExportData;
import gov.usgs.vdx.data.MatrixExporter;
import gov.usgs.vdx.data.Rank;
import gov.usgs.vdx.data.gps.Estimator;
import gov.usgs.vdx.data.gps.GPS;
import gov.usgs.vdx.data.gps.GPSData;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;

/**
 * TODO: check map sizes against client max height.
 * 
 * Generate images of coordinate time series and velocity maps from vdx source
 *
 * @author Dan Cervelli, Loren Antolik, Peter Cervelli
 */
public class GPSPlotter extends RawDataPlotter {
	
	private final static double CONF95 = 5.99146454710798;
	
	private enum PlotType {		
		TIME_SERIES, VELOCITY_MAP, DISPLACEMENT_MAP;		
		public static PlotType fromString(String s) {
			if (s.equals("ts")) {
				return TIME_SERIES;
			} else if (s.equals("vel")) {
				return VELOCITY_MAP;
			} else if (s.equals("dis")) {
				return DISPLACEMENT_MAP;
			} else {
				return null;
			}
		}
	}
	
	private enum ModelType {
		VELOCITY_MODEL, MEAN_MODEL;
		public static ModelType fromString(String s) {
			if (s.equals("v")) {
				return VELOCITY_MODEL;
			} else if (s.equals("m")) {
				return MEAN_MODEL;
			} else {
				return null;
			}
		}
	}
	
	// variables acquired from the PlotComponent
	private PlotType	plotType;
	private String		bl;
	private boolean		scaleErrors, showVertical, showHorizontal;
	private double		displacementBeforeStartTime;
	private double		displacementBeforeEndTime;
	private double		displacementAfterStartTime;
	private double		displacementAfterEndTime;
	private ModelType	displacementBeforeModel;
	private ModelType	displacementAfterModel;

	// variables used in this class
	private GPSData baselineData;
	private Map<Integer, GPSData> channelDataMap;
	
	private boolean selectedCols[];
	private String legendsCols[];
	
	/**
	 * Default constructor
	 */
	public GPSPlotter() {
		super();
	}
	
	/**
	 * Initialize internal data from PlotComponent
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void getInputs(PlotComponent comp) throws Valve3Exception {
		
		parseCommonParameters(comp);	
		
		rk = comp.getInt("rk");
		
		// Check for blName first, if exists, figure out the bl number
		// This allows the baseline channel to be passed in via name in the same
		// way that channels can be passed in via the chNames parameter
		String blName = comp.get("blName");
		if (blName != null) {
			bl = null;
			for (Channel c : channelsMap.values()) {
				if (c.getCode().equals(blName))
					bl = String.valueOf(c.getCID());
			}
		} else {
			bl = comp.get("bl");
			if (bl != null && bl.equals("[none]")) {
				bl = null;
			}
		}
		
		String pt = comp.get("plotType");
		if ( pt == null )
			plotType = PlotType.TIME_SERIES;
		else {
			plotType	= PlotType.fromString(pt);
			if (plotType == null) {
				throw new Valve3Exception("Illegal plot type: " + pt);
			}
		}

		validateDataManipOpts(comp);

		switch(plotType) {		
		case TIME_SERIES:
			columnsCount		= columnsList.size();
			selectedCols		= new boolean[columnsCount];
			legendsCols			= new String[columnsCount];
			channelLegendsCols	= new String[columnsCount];
			bypassCols			= new boolean [columnsCount];
			compCount			= 0;
			
			leftLines			= 0;
			axisMap				= new LinkedHashMap<Integer, String>();
			
			// iterate through all the active columns and place them in a map if they are displayed
			for (int i = 0; i < columnsList.size(); i++) {
				Column column	= columnsList.get(i);
				selectedCols[i]	= column.checked;
				legendsCols[i]	= column.description;
				bypassCols[i]	= column.bypassmanip;
				if (column.checked) {
					if(isPlotSeparately()){
						axisMap.put(i, "L");
						leftUnit	= column.unit;
						leftLines++;
					} else {
						if (leftUnit != null && leftUnit.equals(column.unit)) {
							axisMap.put(i, "L");
							leftLines++;
						} else if (rightUnit != null && rightUnit.equals(column.unit)) {
							axisMap.put(i, "R");
						} else if (leftUnit == null) {
							leftUnit	= column.unit;
							axisMap.put(i, "L");
							leftLines++;
						} else if (rightUnit == null) {
							rightUnit = column.unit;
							axisMap.put(i, "R");
						} else {
							throw new Valve3Exception("Too many different units.");
						}
					}
					compCount++;
				} else {
					axisMap.put(i, "");
				}
			}
			
			break;
		
		case VELOCITY_MAP:
			try {
				showHorizontal = Util.stringToBoolean(comp.getString("hs"), true);
			} catch (Exception e) {
				showHorizontal = true;
			}
			try {
				showVertical = Util.stringToBoolean(comp.getString("vs"), false);
			} catch (Exception e) {
				showVertical = false;
			}
			try {
				scaleErrors	= Util.stringToBoolean(comp.getString("se"), true);
			} catch (Exception e) {
				scaleErrors	= true;
			}
			break;
		
		case DISPLACEMENT_MAP:
			try {
				displacementBeforeStartTime = comp.parseTime(comp.getString("displacementBeforeStartTime"), 0);
			} catch (Exception e) {
				throw new Valve3Exception("Invalid Before Period Start Time.");
			}
			try {
				displacementBeforeEndTime = comp.parseTime(comp.getString("displacementBeforeEndTime"), 0);
			} catch (Exception e) {
				throw new Valve3Exception("Invalid Before Period End Time.");
			}
			try {
				displacementAfterStartTime = comp.parseTime(comp.getString("displacementAfterStartTime"), 0);
			} catch (Exception e) {
				throw new Valve3Exception("Invalid After Period Start Time.");
			}
			try {
				displacementAfterEndTime = comp.parseTime(comp.getString("displacementAfterEndTime"), 0);
			} catch (Exception e) {
				throw new Valve3Exception("Invalid After Period End Time.");
			}
			
			if (displacementBeforeStartTime < startTime) {
				throw new Valve3Exception("Before Period Start Time must be >= Start Time.");
				
			} else if (displacementBeforeEndTime <= displacementBeforeStartTime) {
				throw new Valve3Exception("Before Period End Time must be > Before Period Start Time.");
				
			} else if (displacementAfterStartTime <= displacementBeforeEndTime) {
				throw new Valve3Exception("After Period Start Time must be > Before Period End Time.");
				
			} else if (displacementAfterEndTime <= displacementAfterStartTime) {
				throw new Valve3Exception("After Period End Time must be > After Period Start Time.");
				
			} else if (displacementAfterEndTime > endTime) {
				throw new Valve3Exception("After Period End Time must be <= End Time.");
			}
			
			String dbm	= Util.stringToString(comp.get("displacementBeforeModel"), "m");
			displacementBeforeModel = ModelType.fromString(dbm);
			if (displacementBeforeModel == null) {
				throw new Valve3Exception("Illegal Before Period Model.");
			}
			
			String dam	= Util.stringToString(comp.get("displacementAfterModel"), "m");
			displacementAfterModel = ModelType.fromString(dam);
			if (displacementAfterModel == null) {
				throw new Valve3Exception("Illegal After Period Model.");
			}
			
			System.out.println("DBST:" + displacementBeforeStartTime +
								"/DBET:" + displacementBeforeEndTime + 
								"/DAST:" + displacementAfterStartTime + 
								"/DAET:" + displacementAfterEndTime);
			if (displacementBeforeModel == ModelType.MEAN_MODEL) System.out.println("DBM:mean");
			if (displacementBeforeModel == ModelType.VELOCITY_MODEL) System.out.println("DBM:velocity");
			if (displacementAfterModel == ModelType.MEAN_MODEL) System.out.println("DAM:mean");
			if (displacementAfterModel == ModelType.VELOCITY_MODEL) System.out.println("DAM:velocity");
			
			try {
				showHorizontal = Util.stringToBoolean(comp.getString("hs"), true);
			} catch (Exception e) {
				showHorizontal = true;
			}
			try {
				showVertical = Util.stringToBoolean(comp.getString("vs"), false);
			} catch (Exception e) {
				showVertical = false;
			}
			try {
				scaleErrors	= Util.stringToBoolean(comp.getString("se"), true);
			} catch (Exception e) {
				scaleErrors	= true;
			}
			break;
		}
	}
	
	/**
	 * Gets binary data from VDX
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void getData(PlotComponent comp) throws Valve3Exception {
		
		// initialize variables
		boolean exceptionThrown		= false;
		String exceptionMsg			= "";
		boolean blexceptionThrown	= false;
		String blexceptionMsg		= "";
		Pool<VDXClient> pool		= null;
		VDXClient client			= null;
		channelDataMap				= new LinkedHashMap<Integer, GPSData>();
		String[] channels			= ch.split(",");
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();		
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("rk", Integer.toString(rk));
		addDownsamplingInfo(params);
		
		// checkout a connection to the database
		pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		if (pool != null) {
			client	= pool.checkout();
		
			// iterate through each of the selected channels and place the data in the map
			for (String channel : channels) {
				params.put("ch", channel);
				GPSData data = null;
				try {
					data = (GPSData)client.getBinaryData(params);
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
				if (data != null && data.observations() > 0) {
					data.adjustTime(timeOffset);
				}
				channelDataMap.put(Integer.valueOf(channel), data);
			}
		
			// if a baseline was selected then retrieve that data from the database
			if (bl != null) {
				params.put("ch", bl);
				try {
					baselineData = (GPSData)client.getBinaryData(params);
				} catch (UtilException e) {
					blexceptionThrown	= true;
					blexceptionMsg		= e.getMessage();
				} catch (Exception e){
					blexceptionThrown	= true;
					blexceptionMsg		= e.getMessage();
				}
				
				// if data was collected
				if (baselineData != null && baselineData.observations() > 0) {
					baselineData.adjustTime(timeOffset);
				}
			}
		
			// check back in our connection to the database
			pool.checkin(client);
		}
		
		// if a data limit message exists, then throw exception
		if (exceptionThrown) {
			throw new Valve3Exception(exceptionMsg);
		}
		
		// if a data limit message exists, then throw exception
		if (blexceptionThrown) {
			throw new Valve3Exception(blexceptionMsg);
		
		// if no baseline data exists, then throw exception
		} else if (bl != null && baselineData == null) {
			throw new Valve3Exception("No data for baseline channel.");
		}
	}
	
	/**
	 * Initialize MapRenderer to plot map and list of EllipseVectorRenderer2 
	 * elements which plot a station's movement
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @param rank Rank
	 */
	private void plotVectorMap(Valve3Plot v3p, PlotComponent comp, Rank rank) throws Valve3Exception {
		
		List<Point2D.Double> locs = new ArrayList<Point2D.Double>();
		
		// add a location for each channel that is being plotted
		for (int cid : channelDataMap.keySet()) {
			GPSData data = channelDataMap.get(cid);
			if (data != null) {
				if (baselineData != null) {
					data.applyBaseline(baselineData);
				}
				locs.add(channelsMap.get(cid).getLonLat());
			}
		}
		
		// create the dimensions of the plot based on these stations
		GeoRange range = GeoRange.getBoundingBox(locs);
		
		TransverseMercator proj = new TransverseMercator();
		Point2D.Double origin = range.getCenter();
		proj.setup(origin, 0, 0);

		MapRenderer mr = new MapRenderer(range, proj);
		mr.setLocationByMaxBounds(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(), comp.getBoxMapHeight());
		v3p.getPlot().setSize(v3p.getPlot().getWidth(), mr.getGraphHeight() + 60 + 16);
		
		GeoLabelSet labels = Valve3.getInstance().getGeoLabelSet();
		labels = labels.getSubset(range);
		mr.setGeoLabelSet(labels);
		
		GeoImageSet images = Valve3.getInstance().getGeoImageSet();
		RenderedImage ri = images.getMapBackground(proj, range, comp.getBoxWidth());
		
		mr.setMapImage(ri);
		mr.createBox(8);
		mr.createGraticule(8, xTickMarks, yTickMarks, xTickValues, yTickValues, Color.BLACK);
		mr.createScaleRenderer();
		
		double[] trans = mr.getDefaultTranslation(v3p.getPlot().getHeight());
		trans[4] = startTime+timeOffset;
		trans[5] = endTime+timeOffset;
		trans[6] = origin.x;
		trans[7] = origin.y;
		mr.createEmptyAxis();
		if(xUnits){
			mr.getAxis().setBottomLabelAsText("Longitude");
		}
		if(yUnits){
			mr.getAxis().setLeftLabelAsText("Latitude");
		}
		mr.getAxis().setTopLabelAsText(getTopLabel(rank));
		v3p.getPlot().addRenderer(mr);
		
		double maxMag = -1E300;
		List<Renderer> vrs = new ArrayList<Renderer>();
		
		for (int cid : channelDataMap.keySet()) {
			Channel channel	= channelsMap.get(cid);
			GPSData data	= channelDataMap.get(cid);
			
			labels.add(new GeoLabel(channel.getCode(), channel.getLon(), channel.getLat()));
			
			if (data == null || data.observations() <= 1) {
				continue;
			}
			
			DoubleMatrix2D G = null;
			switch (plotType)
			{
				case DISPLACEMENT_MAP:
					/*
					double delta;
					double deltaMin = 1e300;
					double displacementEpoch = displacementTime;
					
					for (int i = 0; i < data.observations(); i++)
					{
						delta = Math.abs(data.getTimes().getQuick(i, 0) - displacementTime);
						if (delta < deltaMin)
						{
							deltaMin = delta;
							displacementEpoch = data.getTimes().getQuick(i, 0);
							
						}
					}

					G = data.createDisplacementKernel(displacementEpoch);
					*/
					G = data.createVelocityKernel();
					break;
					
				case VELOCITY_MAP:
					G = data.createVelocityKernel();
					break;
					
				case TIME_SERIES:
					break;

			}
			Estimator est = new Estimator(G, data.getXYZ(), data.getCovariance());
			est.solve();
			
			DoubleMatrix2D v = est.getModel().viewPart(0, 0, 3, 1);
			DoubleMatrix2D vcov = est.getModelCovariance().viewPart(0,0,3,3);

			DoubleMatrix2D t = GPS.createENUTransform(channel.getLon(), channel.getLat());
			v = Algebra.DEFAULT.mult(t, v);
			vcov = Algebra.DEFAULT.mult(Algebra.DEFAULT.mult(t, vcov), t.viewDice());


			if (scaleErrors)
				vcov.assign(cern.jet.math.Mult.mult(est.getChi2()));

			if (v.getQuick(0, 0) == 0 && v.getQuick(1, 0) == 0 && v.getQuick(2, 0) == 0) {
				continue;
			}

			double t1 = vcov.getQuick(0,0) + vcov.getQuick(1,1);
			double t2 = vcov.getQuick(0,0) - vcov.getQuick(1,1);
			double t3 = Math.sqrt(4*vcov.getQuick(1,0)*vcov.getQuick(1,0) + t2*t2);
			double w = (t1 - t3)/2 * CONF95;
			double h = (t1 + t3)/2 * CONF95;
			double phi = Math.atan2((t2 - t3)/(2*vcov.getQuick(1,0)),1);

			EllipseVectorRenderer evr = new EllipseVectorRenderer();
			evr.frameRenderer = mr;
			Point2D.Double ppt = proj.forward(channel.getLonLat());
			evr.x = ppt.x;
			evr.y = ppt.y;
			evr.u = v.getQuick(0, 0);
			evr.v = v.getQuick(1, 0);
			evr.z = v.getQuick(2, 0);
			evr.ellipseOrientation = phi;
			evr.ellipseWidth = Math.max(w, h) * 2;
			evr.ellipseHeight = Math.min(w, h) * 2;
			evr.displayHoriz = showHorizontal;
			evr.displayVert = showVertical;			
			evr.sigZ =  vcov.getQuick(2, 2);
			maxMag = Math.max(Math.max(evr.getMag(), Math.abs(evr.z)), maxMag);
			v3p.getPlot().addRenderer(evr);
			vrs.add(evr);
			
		}
		
		if (maxMag == -1E300) {
			// return;
			maxMag = 1;
		}
		
		// set the length of the legend vector to 1/5 of the width of the shortest side of the map
		double scale = EllipseVectorRenderer.getBestScale(maxMag);
		double desiredLength = Math.min((mr.getMaxY() - mr.getMinY()), (mr.getMaxX() - mr.getMinX())) / 5;
		// logger.info("Scale: " + scale);
		// logger.info("desiredLength: " + desiredLength);
		// logger.info("desiredLength/scale: " + desiredLength / scale);
		
		for (int i = 0; i < vrs.size(); i++) {
			EllipseVectorRenderer evr = (EllipseVectorRenderer)vrs.get(i);
			evr.setScale(desiredLength / scale);
		}
		
		// draw the legend vector
		EllipseVectorRenderer svr = new EllipseVectorRenderer();
		svr.frameRenderer = mr;
		svr.drawEllipse = false;
		svr.x = mr.getMinX();
		svr.y = mr.getMinY();
		svr.u = desiredLength;
		svr.v = 0;
		svr.z = desiredLength;
		svr.displayHoriz	= true;
		svr.displayVert		= false;
		svr.colorHoriz = Color.BLACK;
		svr.colorVert  = Color.BLACK;
		svr.sigZ = 0;
		v3p.getPlot().addRenderer(svr);
		
		// draw the legend vector units
		TextRenderer tr = new TextRenderer();
		switch (plotType) {
			case DISPLACEMENT_MAP:
				tr.text = scale + " meters";
				break;				
			case VELOCITY_MAP:
				tr.text = scale + " meters/year";
				break;
			default:
				tr.text = scale + " meters/year";
				break;
		}
		tr.x = mr.getGraphX() + 10;
		tr.y = mr.getGraphY() + mr.getGraphHeight() - 5;
		v3p.getPlot().addRenderer(tr);
		
		comp.setTranslation(trans);
		comp.setTranslationType("map");
		v3p.addComponent(comp);
	}

	/**
	 * Initialize MatrixRenderers for left and right axis, adds them to plot
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3p, PlotComponent comp, Rank rank) throws Valve3Exception {
		
		// setup the display for the legend
		String rankLegend		= rank.getName();
		
		// if a baseline was chosen then setup the display for the legend
		String baselineLegend = "";
		if (baselineData != null) {
			baselineLegend = "-" + channelsMap.get(Integer.valueOf(bl)).getCode();
		}
		
		switch (plotType) {
		
			case TIME_SERIES:
				// calculate the number of plot components that will be displayed per channel
				int channelCompCount = 0;
				if(isPlotSeparately()){
					for(Column col: columnsList){
						if(col.checked){
							channelCompCount++;
						}
					}
				} else {
					channelCompCount = 1;
				}
				
				// total components is components per channel * number of channels
				compCount = channelCompCount * channelDataMap.size();
				
				// setting up variables to decide where to plot this component
				int currentComp		= 1;
				int compBoxHeight	= comp.getBoxHeight();
				
				for (int cid : channelDataMap.keySet()) {
					
					// get the relevant information for this channel
					Channel channel	= channelsMap.get(cid);
					GPSData data	= channelDataMap.get(cid);
					
					// verify their is something to plot
					if (data == null || data.observations() == 0) {
						v3p.setHeight(v3p.getHeight() - channelCompCount * compBoxHeight);
						Plot plot	= v3p.getPlot();
						plot.setSize(plot.getWidth(), plot.getHeight() - channelCompCount * compBoxHeight);
						compCount = compCount - channelCompCount;
						continue;
					}
					
					// convert the GPSData object to a generic data matrix and subtract out the mean
					GenericDataMatrix gdm	= new GenericDataMatrix(data.toTimeSeries(baselineData));
					for (int i = 0; i < columnsCount; i++) {
						if ( bypassCols[i] )
							continue;
						if (doDespike) { gdm.despike(i + 2, despikePeriod ); }
						if (doDetrend) { gdm.detrend(i + 2); }
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
									gdm.filter(bw, i+2, true);
									break;
								case 2: // Running median
									gdm.set2median( i+2, filterPeriod );
									break;
								case 3: // Running mean
									gdm.set2mean( i+2, filterPeriod );
									break;
							}
						}
						if (doArithmetic) { gdm.doArithmetic(i+2, arithmeticType, arithmeticValue); }
						if (debiasPick != 0 ) {
							double bias = 0.0;
							switch ( debiasPick ) {
								case 1: // remove mean 
									bias = gdm.mean(i+2);
									break;
								case 2: // remove initial value
									bias = gdm.first(i+2);
									break;
								case 3: // remove user value
									bias = debiasValue;
									break;
							}
							gdm.add(i + 2, -bias);
						}
					}
					
					if ( forExport ) {
						
						// Add column headers to csvHdrs
						int i = 0;
						for (Column col: columnsList) {
							if ( !axisMap.get(i).equals("") ) {
								String[] hdr = {null, null, channel.getCode() + baselineLegend, col.name};
								csvHdrs.add(hdr);
							}
							i++;
						}
						// Initialize data for export; add to set for CSV
						ExportData ed = new ExportData( csvIndex, new MatrixExporter(gdm.getData(), ranks, axisMap) );
						csvIndex++;
						csvData.add( ed );
						
					} else {
						// set up the legend 
						for (int i = 0; i < legendsCols.length; i++) {
							channelLegendsCols[i] = String.format("%s%s %s %s", channel.getCode(), baselineLegend, rankLegend, legendsCols[i]);
						}

						// create an individual matrix renderer for each component selected
						if (isPlotSeparately()) {
							for (int i = 0; i < columnsList.size(); i++) {
								Column col = columnsList.get(i);
								if(col.checked){
									MatrixRenderer leftMR	= getLeftMatrixRenderer(comp, channel, gdm, currentComp, compBoxHeight, i, col.unit);
									MatrixRenderer rightMR	= getRightMatrixRenderer(comp, channel, gdm, currentComp, compBoxHeight, i, leftMR.getLegendRenderer());
									if (rightMR != null)
										v3p.getPlot().addRenderer(rightMR);
									v3p.getPlot().addRenderer(leftMR);
									comp.setTranslation(leftMR.getDefaultTranslation(v3p.getPlot().getHeight()));
									comp.setTranslationType("ty");
									v3p.addComponent(comp);
									currentComp++;	
								}
							}
						} else {
							MatrixRenderer leftMR	= getLeftMatrixRenderer(comp, channel, gdm, currentComp, compBoxHeight, -1, leftUnit);
							MatrixRenderer rightMR	= getRightMatrixRenderer(comp, channel, gdm, currentComp, compBoxHeight, -1, leftMR.getLegendRenderer());
							if (rightMR != null)
								v3p.getPlot().addRenderer(rightMR);
							v3p.getPlot().addRenderer(leftMR);
							comp.setTranslation(leftMR.getDefaultTranslation(v3p.getPlot().getHeight()));
							comp.setTranslationType("ty");
							v3p.addComponent(comp);
							currentComp++;
						}
					}
				}
				if (!forExport) {
					if(channelDataMap.size()>1){
						v3p.setCombineable(false);
					} else {
						v3p.setCombineable(true);
					}
					v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Time Series");
					addSuppData( vdxSource, vdxClient, v3p, comp );
					addMetaData( vdxSource, vdxClient, v3p, comp );
				}
				break;
				
			case VELOCITY_MAP:
				if (!forExport) {
					v3p.setCombineable(false);
					v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Velocity Field");
				}
				plotVectorMap(v3p, comp, rank);
				break;
				
			case DISPLACEMENT_MAP:
				if (!forExport) {
					v3p.setCombineable(false);
					v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Displacement Field");
				}
				plotVectorMap(v3p, comp, rank);
				break;
		}
	}

	/**
	 * Concrete realization of abstract method. 
	 * Generate PNG image to local file.
	 * If v3p is null, prepare data for export -- assumes csvData, csvData & csvIndex initialized
	 * @param v3p Valve3Plot
	 * @param comp PlotComponent
	 * @throws Valve3Exception
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception, PlotException {
		
		forExport	= (v3p == null);
		channelsMap	= getChannels(vdxSource, vdxClient);
		ranksMap	= getRanks(vdxSource, vdxClient);
		columnsList	= getColumns(vdxSource, vdxClient);
		comp.setPlotter(this.getClass().getName());		
		getInputs(comp);
		
		// get the rank object for this request
		Rank rank	= new Rank();
		if (rk == 0) {
			rank	= rank.bestAvailable();
		} else {
			rank	= ranksMap.get(rk);
		}
		
		// set the exportable based on the output and plot type
		switch (plotType) {
		
		case TIME_SERIES:
			
			// plot configuration
			if (!forExport) {
				v3p.setExportable(true);
			}
			
			/* if (!forExport) {
				if (rk == 0) {
					v3p.setExportable(false);
				} else {
					v3p.setExportable(true);
				}
				
			// export configuration
			} else {
				if (rk == 0) {
					throw new Valve3Exception( "Data Export Not Available for Best Available Rank");
				}
			} */
			break;
			
		case VELOCITY_MAP:
			
			// plot configuration
			if (!forExport) {
				v3p.setExportable(false);
				
			// export configuration
			} else {
				throw new Valve3Exception("Data Export Not Available for GPS Velocity Map");
			}
			break;
		
		case DISPLACEMENT_MAP:
			
			// plot configuration
			if (!forExport) {
				v3p.setExportable(false);
				
			// export configuration
			} else {
				throw new Valve3Exception("Data Export Not Available for GPS Displacement Map");
			}
			break;
		}
		
		// this is a legitimate request so lookup the data from the database and plot it
		getData(comp);		
		plotData(v3p, comp, rank);
		
		if (!forExport) 
			writeFile(v3p);
	}

	/**
	 * 
	 * @return plot top label text
	 */
	private String getTopLabel(Rank rank) {
		StringBuilder top = new StringBuilder(100);
		top.append(rank.getName() + " Vectors between ");
		top.append(Util.j2KToDateString(startTime+timeOffset, dateFormatString));
		top.append(" and ");
		top.append(Util.j2KToDateString(endTime+timeOffset, dateFormatString));
		top.append(" " + timeZoneID + " Time");
		return top.toString();
	}
}