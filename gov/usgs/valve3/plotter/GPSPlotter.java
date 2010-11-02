package gov.usgs.valve3.plotter;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import gov.usgs.math.Butterworth;
import gov.usgs.math.Butterworth.FilterType;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.EllipseVectorRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.PlotException;
import gov.usgs.plot.Renderer;
import gov.usgs.plot.TextRenderer;
import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.plot.map.GeoLabel;
import gov.usgs.plot.map.GeoLabelSet;
import gov.usgs.plot.map.MapRenderer;
import gov.usgs.proj.GeoRange;
import gov.usgs.proj.TransverseMercator;
import gov.usgs.util.Log;
import gov.usgs.util.Pool;
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
import gov.usgs.vdx.data.GenericDataMatrix;
import gov.usgs.vdx.data.MatrixExporter;
import gov.usgs.vdx.data.Rank;
import gov.usgs.vdx.data.gps.GPS;
import gov.usgs.vdx.data.gps.GPSData;

import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.colt.matrix.linalg.EigenvalueDecomposition;

/**
 * TODO: check map sizes against client max height.
 * 
 * Generate images of coordinate time series and velocity maps from vdx source
 *
 * @author Dan Cervelli, Loren Antolik
 */
public class GPSPlotter extends RawDataPlotter {
	
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
	private final static Logger logger = Log.getLogger("gov.usgs.valve3.plotter.GPSPlotter"); 
	
	// variables acquired from the PlotComponent
	private PlotType	plotType;
	private String		bl;
	private boolean		se, vs, hs;

	// variables used in this class	

	private GPSData baselineData;
	private Map<Integer, GPSData> channelDataMap;	
	
	//private int columnsCount;
	private boolean selectedCols[];
	private String legendsCols[];
	
	/**
	 * Default constructor
	 */
	public GPSPlotter() {
		super();
		shape=null;
	}
	
	/**
	 * Initialize internal data from PlotComponent
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void getInputs(PlotComponent component) throws Valve3Exception {
		
		parseCommonParameters(component);		
		rk = component.getInt("rk");	
		bl = component.get("bl");
		if (bl != null && bl.equals("[none]")) {
			bl = null;
		}
		
		String pt = component.get("plotType");
		if ( pt == null )
			plotType = PlotType.TIME_SERIES;
		else {
			plotType	= PlotType.fromString(pt);
			if (plotType == null) {
				throw new Valve3Exception("Illegal plot type: " + pt);
			}
		}

		validateDataManipOpts(component);

		switch(plotType) {		
		case TIME_SERIES:			
			columnsCount		= columnsList.size();
			selectedCols		= new boolean[columnsCount];
			legendsCols			= new String[columnsCount];
			channelLegendsCols	= new String[columnsCount];
			bypassManipCols     = new boolean [columnsCount];
			compCount			= 0;
			
			leftLines			= 0;
			axisMap				= new LinkedHashMap<Integer, String>();
			
			// iterate through all the active columns and place them in a map if they are displayed
			for (int i = 0; i < columnsList.size(); i++) {
				Column column	= columnsList.get(i);
				selectedCols[i]	= column.checked;
				legendsCols[i]	= column.description;
				bypassManipCols[i] = column.bypassmanipulations;
				if (column.checked) {
					if(isPlotComponentsSeparately()){
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
			se = component.getBoolean("se");
			vs = component.getBoolean("vs");
			hs = component.getBoolean("hs");
			break;
		}
	}
	
	/**
	 * Gets binary data from VDX
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void getData(PlotComponent component) throws Valve3Exception {
		
		boolean gotData = false;
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();		
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("rk", Integer.toString(rk));
		addDownsamplingInfo(params);
		// checkout a connection to the database
		Pool<VDXClient> pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client		= pool.checkout();
		if (client == null)
			return;
		// create a map to hold all the channel data
		channelDataMap			= new LinkedHashMap<Integer, GPSData>();
		String[] channels		= ch.split(",");
		
		// iterate through each of the selected channels and place the data in the map
		for (String channel : channels) {
			GPSData data = null;
			params.put("ch", channel);
			try{
				data = (GPSData)client.getBinaryData(params);
			}
			catch(UtilException e){
				throw new Valve3Exception(e.getMessage());
			}
			if (data != null && data.observations() > 0) {
				gotData = true;
				data.adjustTime(component.getOffset(startTime));
				channelDataMap.put(Integer.valueOf(channel), data);
			}
		}
		
		if (!gotData) {
			throw new Valve3Exception("No data for any stations.");
		}
		
		// if a baseline was selected then retrieve that data from the database
		if (bl != null) {
			params.put("ch", bl);
			try{
				baselineData = (GPSData)client.getBinaryData(params);
			}
			catch(UtilException e){
				throw new Valve3Exception(e.getMessage()); 
			}
			if (baselineData == null || baselineData.observations() == 0) {
				throw new Valve3Exception("No baseline data.");
			}
			baselineData.adjustTime(component.getOffset(startTime));
		}
		// check back in our connection to the database
		pool.checkin(client);
	}
	
	/**
	 * Initialize MapRenderer to plot map and list of EllipseVectorRenderer2 
	 * elements which plot a station's movement
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 */
	private void plotVelocityMap(Valve3Plot v3Plot, PlotComponent component) throws Valve3Exception {
		
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
		
		GeoRange range = GeoRange.getBoundingBox(locs);
		
		Plot plot = v3Plot.getPlot();
		
		TransverseMercator proj = new TransverseMercator();
		Point2D.Double origin = range.getCenter();
		proj.setup(origin, 0, 0);

		MapRenderer mr = new MapRenderer(range, proj);
		mr.setLocationByMaxBounds(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getInt("mh"));
		
		GeoLabelSet labels = Valve3.getInstance().getGeoLabelSet();
		labels = labels.getSubset(range);
		mr.setGeoLabelSet(labels);
		
		GeoImageSet images = Valve3.getInstance().getGeoImageSet();
		RenderedImage ri = images.getMapBackground(proj, range, component.getBoxWidth());
		
		mr.setMapImage(ri);
		mr.createBox(8);
		mr.createGraticule(8, xTickMarks, yTickMarks, xTickValues, yTickValues, Color.BLACK);
		mr.createScaleRenderer();
		plot.setSize(plot.getWidth(), mr.getGraphHeight() + 60);
		double[] trans = mr.getDefaultTranslation(plot.getHeight());
		trans[4] = 0;
		trans[5] = 0;
		trans[6] = origin.x;
		trans[7] = origin.y;
		component.setTranslation(trans);
		component.setTranslationType("map");
		v3Plot.addComponent(component);
		mr.createEmptyAxis();
		if(xUnits){
			mr.getAxis().setBottomLabelAsText("Longitude");
		}
		if(yUnits){
			mr.getAxis().setLeftLabelAsText("Latitude");
		}
		plot.addRenderer(mr);
		
		double maxMag = -1E300;
		List<Renderer> vrs = new ArrayList<Renderer>();
		
		for (int cid : channelDataMap.keySet()) {
			Channel ch = channelsMap.get(cid);
			labels.add(new GeoLabel(ch.getCode(), ch.getLon(), ch.getLat()));
			GPSData stn = channelDataMap.get(cid);
			if (stn == null || stn.observations() <= 1) {
				continue;
			}
			
			DoubleMatrix2D g = null;
			g = stn.createVelocityKernel();
			DoubleMatrix2D m = GPS.solveWeightedLeastSquares(g, stn.getXYZ(), stn.getCovariance());
			DoubleMatrix2D t = GPS.createENUTransform(ch.getLon(), ch.getLat());
			DoubleMatrix2D e = GPS.getErrorParameters(g, stn.getCovariance());
			DoubleMatrix2D t2 = GPS.createFullENUTransform(ch.getLon(), ch.getLat(), 2);
			e = Algebra.DEFAULT.mult(Algebra.DEFAULT.mult(t2, e), t2.viewDice());
			DoubleMatrix2D v = m.viewPart(0, 0, 3, 1);
	
			logger.info("XYZ Velocity: " + v.getQuick(0,0) + " " + v.getQuick(1,0) + " " + v.getQuick(2,0));
			DoubleMatrix2D vt = Algebra.DEFAULT.mult(t, v);
			if (vt.getQuick(0, 0) == 0 && vt.getQuick(1, 0) == 0 && vt.getQuick(2, 0) == 0) {
				continue;
			}
			
			if (se) {
				DoubleMatrix2D gm = Algebra.DEFAULT.mult(g, m);
				DoubleMatrix2D r = stn.getXYZ().copy().assign(gm, cern.jet.math.Functions.minus);
				DoubleMatrix2D sdi = Algebra.DEFAULT.inverse(stn.getCovariance());
				DoubleMatrix2D c2 = Algebra.DEFAULT.mult(Algebra.DEFAULT.mult(r.viewDice(), sdi), r);
				double chi2 = c2.getQuick(0, 0) / (stn.getXYZ().rows() - 6);
				e.assign(cern.jet.math.Mult.mult(chi2));
			}
			
			logger.info("Velocity: " + vt);
			logger.info("Error: " + e);
			
			DoubleMatrix2D es = e.viewPart(0, 0, 2, 2);
			EigenvalueDecomposition ese = new EigenvalueDecomposition(es);
			DoubleMatrix1D evals = ese.getRealEigenvalues();
			DoubleMatrix2D evecs = ese.getV();
			logger.info("evals: " + evals);
			logger.info("evecs: " + evecs);
			double phi = Math.atan2(evecs.getQuick(0, 0), evecs.getQuick(1, 0));
			double w = Math.sqrt(evals.getQuick(0) * 5.9915);
			double h = Math.sqrt(evals.getQuick(1) * 5.9915);
			logger.info("w: " + w + ", h: " + h);
			EllipseVectorRenderer vr = new EllipseVectorRenderer();
			vr.frameRenderer = mr;
			Point2D.Double ppt = proj.forward(ch.getLonLat());
			vr.x = ppt.x;
			vr.y = ppt.y;
			vr.u = vt.getQuick(0, 0);
			vr.v = vt.getQuick(1, 0);
			vr.ellipseOrientation = phi;
			vr.ellipseWidth = Math.max(w, h) * 2;
			vr.ellipseHeight = Math.min(w, h) * 2;
			vr.z = vt.getQuick(2, 0);
			vr.displayHoriz = hs;
			vr.displayVert = vs;
			//error parameter for Z axis
			vr.sigZ =  e.getQuick(2, 2);
			maxMag = Math.max(vr.getMag(), maxMag);
			plot.addRenderer(vr);
			vrs.add(vr);
		}
		
		if (maxMag == -1E300) {
			return;
		}
		
		double scale = EllipseVectorRenderer.getBestScale(maxMag);
		logger.info("Scale: " + scale);
		double desiredLength = Math.min((mr.getMaxY() - mr.getMinY()), (mr.getMaxX() - mr.getMinX())) / 5;
		logger.info("desiredLength: " + desiredLength);
		logger.info("desiredLength/scale: " + desiredLength / scale);
		
		for (int i = 0; i < vrs.size(); i++) {
			EllipseVectorRenderer vr = (EllipseVectorRenderer)vrs.get(i);
			vr.setScale(desiredLength / scale);
		}
		
		EllipseVectorRenderer svr = new EllipseVectorRenderer();
		svr.frameRenderer = mr;
		svr.drawEllipse = false;
		svr.x = mr.getMinX();
		svr.y = mr.getMinY() + 17 / mr.getYScale();
		svr.u = desiredLength;
		svr.v = 0;
		svr.displayVert = false;
		svr.sigZ = 0;
		 
		TextRenderer tr = new TextRenderer();
		tr.x = mr.getGraphX() + 10;
		tr.y = mr.getGraphY() + mr.getGraphHeight() - 5;
		tr.text = scale + " m/year";
		plot.addRenderer(svr);
		plot.addRenderer(tr);
	}

	/**
	 * Initialize MatrixRenderers for left and right axis, adds them to plot
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3Plot, PlotComponent component) throws Valve3Exception {
		PlotType	corePlotType = plotType; 		// Copy of plotType used to decide how to process
		boolean     forExport = (v3Plot == null);	// = "prepare data for export"
		//int         csvIndex = 0;
		
		if ( forExport )
			// Export is treated as a time series, even if requested plot was a velocity map
			corePlotType = PlotType.TIME_SERIES;
		
		int displayCount = 0, dh = 0;
		Rank rank = null;
		String rankLegend = null, baselineLegend = null;
		
		switch (corePlotType) {
			case TIME_SERIES:
				
				// calculate how many graphs we are going to build (number of channels * number of components)
				compCount			= channelDataMap.size() * compCount;
				
				// setting up variables to decide where to plot this component
				displayCount	= 0;
				dh				= component.getBoxHeight();
				
				// setup the display for the legend
				rank	= new Rank();
				if (rk == 0) {
					rank	= rank.bestPossible();
					if ( !forExport )
						v3Plot.setExportable( false );
					else
						throw new Valve3Exception( "Exports for Best Possible Rank not allowed" );
				} else {
					rank	= ranksMap.get(rk);
				}
				rankLegend	= rank.getName();
				
				// if a baseline was chosen then setup the display for the legend
				baselineLegend = "";
				if (baselineData != null) {
					baselineLegend = "-" + channelsMap.get(Integer.valueOf(bl)).getCode();
				}
				for (int cid : channelDataMap.keySet()) {
					
					// get the relevant information for this channel
					Channel channel	= channelsMap.get(cid);
					GPSData data	= channelDataMap.get(cid);
					
					// verify their is something to plot
					if (data == null || data.observations() == 0) {
						continue;
					}
					// convert the GPSData object to a generic data matrix and subtract out the mean
					GenericDataMatrix gdm	= new GenericDataMatrix(data.toTimeSeries(baselineData));
					for (int i = 0; i < columnsCount; i++) {
						if ( bypassManipCols[i] )
							continue;
						if (doDespike) { gdm.despike(i + 2, despikePeriod ); }
						if (doDetrend) { gdm.detrend(i + 2); }
						if (filterPick != 0) {
							Butterworth bw = new Butterworth();
							FilterType ft = FilterType.BANDPASS;
							Double singleBand = 0.0;
							switch(filterPick) {
								case 1: // Bandpass
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
									data.filter(bw, true); */
									break;
								case 2: // Running median
									gdm.set2median( i+2, filterPeriod );
									break;
								case 3: // Running mean
									gdm.set2mean( i+2, filterPeriod );
									break;
							}
						}
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
						for (int i = 0; i < columnsList.size(); i++) {
							if ( !axisMap.get(i).equals("") ) {
								csvHdrs.append(String.format( ",%s%s_%s", channel.getCode(), baselineLegend, legendsCols[i] ));
							}
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
						
						if(isPlotComponentsSeparately()){
							// create an individual matrix renderer for each component selected
							for (int i = 0; i < columnsList.size(); i++) {
								Column col = columnsList.get(i);
								if(col.checked){
									MatrixRenderer leftMR	= getLeftMatrixRenderer(component, channel, gdm, displayCount, dh, i, col.unit);
									MatrixRenderer rightMR	= getRightMatrixRenderer(component, channel, gdm, displayCount, dh, i);
									v3Plot.getPlot().addRenderer(leftMR);
									if (rightMR != null)
										v3Plot.getPlot().addRenderer(rightMR);
									component.setTranslation(leftMR.getDefaultTranslation(v3Plot.getPlot().getHeight()));
									component.setTranslationType("ty");
									v3Plot.addComponent(component);
									displayCount++;	
								}
							}
						} else {
							compCount = channelDataMap.size();
							MatrixRenderer leftMR	= getLeftMatrixRenderer(component, channel, gdm, displayCount, dh, -1, leftUnit);
							MatrixRenderer rightMR	= getRightMatrixRenderer(component, channel, gdm, displayCount, dh, -1);
							v3Plot.getPlot().addRenderer(leftMR);
							if (rightMR != null)
								v3Plot.getPlot().addRenderer(rightMR);
							component.setTranslation(leftMR.getDefaultTranslation(v3Plot.getPlot().getHeight()));
							component.setTranslationType("ty");
							v3Plot.addComponent(component);
							displayCount++;
						}
					}
				}
				break;
				
			case VELOCITY_MAP:
				v3Plot.setExportable( false );
				plotVelocityMap(v3Plot, component);
				break;
		}
		
		switch(corePlotType) {
			case TIME_SERIES:
				if ( !forExport ) {
					v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Time Series");
					addSuppData( vdxSource, vdxClient, v3Plot, component );
				}
				break;
			case VELOCITY_MAP:
				v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Velocity Field");
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
		forExport = (v3p == null);	// = "prepare data for export"
		channelsMap	= getChannels(vdxSource, vdxClient);
		ranksMap	= getRanks(vdxSource, vdxClient);
		columnsList	= getColumns(vdxSource, vdxClient);
		getInputs(comp);
		getData(comp);
		
		plotData(v3p, comp);
		
		if ( !forExport ) {
			Plot plot = v3p.getPlot();
			plot.setBackgroundColor(Color.white);
			plot.writePNG(v3p.getLocalFilename());
		}
	}
}