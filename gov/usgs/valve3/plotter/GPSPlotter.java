package gov.usgs.valve3.plotter;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.EllipseVectorRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.Renderer;
import gov.usgs.plot.SmartTick;
import gov.usgs.plot.TextRenderer;
import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.plot.map.GeoLabel;
import gov.usgs.plot.map.GeoLabelSet;
import gov.usgs.plot.map.MapRenderer;
import gov.usgs.proj.GeoRange;
import gov.usgs.proj.TransverseMercator;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.Channel;
import gov.usgs.vdx.data.Column;
import gov.usgs.vdx.data.GenericDataMatrix;
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
public class GPSPlotter extends Plotter {
	
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
	
	// variables acquired from the PlotComponent
	private String		ch;
	private double		startTime,endTime;
	private PlotType	plotType;
	private int			rk;
	private String		bl;
	private boolean		se,vs,hs;

	// variables used in this class	
	private Valve3Plot v3Plot;
	private PlotComponent component;
	int compCount;
	private GPSData baselineData;
	private Map<Integer, GPSData> channelDataMap;	
	private static Map<Integer, Channel> channelsMap;
	private static Map<Integer, Rank> ranksMap;
	private static List<Column> columnsList;
	
	private String leftUnit;
	private String rightUnit;
	private Map<Integer, String> axisMap;
	private int leftLines;
	private int leftTicks;
	
	private int columnsCount;
	private boolean selectedCols[];
	private String legendsCols[];
	private String channelLegendsCols[];
	
	public final boolean ranks	= true;
	
	protected Logger logger;

	/**
	 * Default constructor
	 */
	public GPSPlotter() {
		logger		= Logger.getLogger("gov.usgs.vdx");		
	}
	
	/**
	 * Initialize internal data from PlotComponent
	 * @throws Valve3Exception
	 */
	public void getInputs() throws Valve3Exception {
		
		ch = component.get("ch");
		if (ch == null || ch.length() <= 0) {
			throw new Valve3Exception("Illegal channel.");
		}
		
		rk = Util.stringToInt(component.get("rk"));
		if (rk < 0) {
			throw new Valve3Exception("Illegal rank.");
		}
		
		endTime	= component.getEndTime();
		if (Double.isNaN(endTime)) {
			throw new Valve3Exception("Illegal end time.");
		}
		
		startTime = component.getStartTime(endTime);
		if (Double.isNaN(startTime)) {
			throw new Valve3Exception("Illegal start time.");
		}		
		
		bl = component.get("bl");
		if (bl.equals("[none]")) {
			bl = null;
		}
		
		plotType	= PlotType.fromString(component.get("plotType"));
		if (plotType == null) {
			throw new Valve3Exception("Illegal plot type.");
		}
		
		switch(plotType) {		
		case TIME_SERIES:			
			columnsCount		= columnsList.size();
			selectedCols		= new boolean[columnsCount];
			legendsCols			= new String[columnsCount];
			channelLegendsCols	= new String[columnsCount];
			compCount			= 0;
			
			leftLines			= 0;
			axisMap				= new LinkedHashMap<Integer, String>();
			
			// iterate through all the active columns and place them in a map if they are displayed
			for (int i = 0; i < columnsList.size(); i++) {
				Column column	= columnsList.get(i);
				boolean display	= Util.stringToBoolean(component.get(column.name));
				selectedCols[i]	= display;
				legendsCols[i]	= column.description;
				if (display) {
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
					compCount++;
				} else {
					axisMap.put(i, "");
				}
			}
			
			break;
		
		case VELOCITY_MAP:
			se = component.get("se").equals("T");
			vs = component.get("vs").equals("T");
			hs = component.get("hs").equals("T");
			
			break;
		}
	}
	
	/**
	 * Gets binary data from VDX
	 * @throws Valve3Exception
	 */
	public void getData() throws Valve3Exception {
		
		boolean gotData = false;
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();		
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("rk", Integer.toString(rk));
		
		// checkout a connection to the database
		Pool<VDXClient> pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client		= pool.checkout();
		if (client == null)
			return;
		
		double TZOffset = Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
		
		// create a map to hold all the channel data
		channelDataMap			= new LinkedHashMap<Integer, GPSData>();
		String[] channels		= ch.split(",");
		
		// iterate through each of the selected channels and place the data in the map
		for (String channel : channels) {
			params.put("ch", channel);
			GPSData data = (GPSData)client.getBinaryData(params);
			if (data != null && data.observations() > 0) {
				gotData = true;
				data.adjustTime(TZOffset);
				channelDataMap.put(Integer.valueOf(channel), data);
			}
		}
		
		if (!gotData) {
			throw new Valve3Exception("No data for any stations.");
		}
		
		// if a baseline was selected then retrieve that data from the database
		if (bl != null) {
			params.put("ch", bl);
			baselineData = (GPSData)client.getBinaryData(params);
			if (baselineData == null || baselineData.observations() == 0) {
				throw new Valve3Exception("No baseline data.");
			}
			baselineData.adjustTime(TZOffset);
		}
		
		// adjust the start and end times
		startTime	+= TZOffset;
		endTime		+= TZOffset;
		
		// check back in our connection to the database
		pool.checkin(client);
	}
	
	/**
	 * Initialize MatrixRenderer for left plot axis
	 * @throws Valve3Exception
	 */
	private MatrixRenderer getLeftMatrixRenderer(Channel channel, GenericDataMatrix gdm, int displayCount, int dh, int index) throws Valve3Exception {	
		
		MatrixRenderer mr = new MatrixRenderer(gdm.getData(), ranks);
		mr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);

		double yMin = 1E300;
		double yMax = -1E300;
		boolean allowExpand = true;
			
		mr.setAllVisible(false);

		if (axisMap.get(index).equals("L")) {
			mr.setVisible(index, true);
			if (component.isAutoScale("ysL")) {
				double buff;
				yMin	= Math.min(yMin, gdm.min(index + 2));
				yMax	= Math.max(yMax, gdm.max(index + 2));
				buff	= (yMax - yMin) * 0.05;
				yMin	= yMin - buff;
				yMax	= yMax + buff;
				
				/*
				// least squares method of computing y axis boundaries
				double[] lsq	= gdm.leastSquares(index + 2);
				yMin	= lsq[0] * gdm.getStartTime() + lsq[1];
				yMax	= lsq[0] * gdm.getEndTime() + lsq[1];
				if (yMin > yMax) {
					double temp = yMin;
					yMin		= yMax;
					yMax		= temp;
				}
				double dy	= Math.max(0.02, Math.abs((yMax - yMin) * 1.8));
				yMin		= yMin - dy;
				yMax		= yMax + dy;
				*/

			} else {
				double[] ys = component.getYScale("ysL", yMin, yMax);
				yMin = ys[0];
				yMax = ys[1];
				allowExpand = false;
				if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
					throw new Valve3Exception("Illegal axis values.");
			}
		}
		
		mr.setExtents(startTime, endTime, yMin, yMax);	
		mr.createDefaultAxis(8, 8, false, allowExpand);
		mr.setXAxisToTime(8);
		mr.getAxis().setLeftLabelAsText(leftUnit);
		mr.createDefaultPointRenderers();
		mr.createDefaultLegendRenderer(channelLegendsCols);
		leftTicks = mr.getAxis().leftTicks.length;
		
		if (displayCount + 1 == compCount) {
			mr.getAxis().setBottomLabelAsText("Time (" + Valve3.getInstance().getTimeZoneAbbr()+ ")");	
		}
		
		return mr;
	}

	/**
	 * Initialize MatrixRenderer for right plot axis
	 * @throws Valve3Exception
	 */
	private MatrixRenderer getRightMatrixRenderer(Channel channel, GenericDataMatrix gdm, int displayCount, int dh, int index) throws Valve3Exception {
		
		if (rightUnit == null)
			return null;
		
		MatrixRenderer mr = new MatrixRenderer(gdm.getData(), ranks);
		mr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);

		double yMin = 1E300;
		double yMax = -1E300;
			
		mr.setAllVisible(false);

		if (axisMap.get(index).equals("R")) {
			mr.setVisible(index, true);
			if (component.isAutoScale("ysL")) {
				double buff;
				yMin	= Math.min(yMin, gdm.min(index + 2));
				yMax	= Math.max(yMax, gdm.max(index + 2));
				buff	= (yMax - yMin) * 0.05;
				yMin	= yMin - buff;
				yMax	= yMax + buff;

			} else {
				double[] ys = component.getYScale("ysL", yMin, yMax);
				yMin = ys[0];
				yMax = ys[1];
				if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
					throw new Valve3Exception("Illegal axis values.");
			}
		}
		
		mr.setExtents(startTime, endTime, yMin, yMax);
		
		AxisRenderer ar = new AxisRenderer(mr);
		ar.createRightTickLabels(SmartTick.autoTick(yMin, yMax, leftTicks, false), null);
		// ar.createRightTickLabels(SmartTick.autoTick(yMin, yMax, 8, allowExpand), null);
		mr.setAxis(ar);
		mr.getAxis().setRightLabelAsText(rightUnit);
		mr.createDefaultPointRenderers(leftLines);
		mr.createDefaultLegendRenderer(channelLegendsCols, leftLines);
		
		return mr;
	}
	
	/**
	 * Initialize DataRenderer to plot time series and adds renderer to plot
	 */
	/*
	private void plotTimeSeries (Channel channel, GenericDataMatrix gdm, int displayCount, int dh) throws Valve3Exception {
		
		double yMin			= 1E300;
		double yMax			= -1E300;
		boolean allowExpand	= true;
		double dy			= 0.0;
		double[] lsq, ys;
		
		for (int i = 0; i < columnsList.size(); i++) {

			if (component.isAutoScale("ysL")) {
				lsq		= gdm.leastSquares(i + 2);
				yMin	= lsq[0] * gdm.getStartTime() + lsq[1];
				yMax	= lsq[0] * gdm.getEndTime() + lsq[1];
				if (yMin > yMax) {
					double temp = yMin;
					yMin = yMax;
					yMax = temp;
				}
				dy		= Math.max(0.02, Math.abs((yMax - yMin) * 1.8));
				yMin	= yMin - dy;
				yMax	= yMax + dy;
	
			} else {
				ys = component.getYScale("ysL", yMin, yMax);
				yMin = ys[0];
				yMax = ys[1];
				allowExpand = false;
				if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
					throw new Valve3Exception("Illegal axis values.");
			}
			
			// setup the plot component
			PlotComponent pc = new PlotComponent();
			pc.setSource(component.getSource());
			pc.setBoxX(component.getBoxX());
			pc.setBoxY(component.getBoxY());
			pc.setBoxWidth(component.getBoxWidth());
	
			MatrixRenderer mr = new MatrixRenderer(gdm.getData(), ranks);
			mr.setAllVisible(false);
			mr.setVisible(i, true);
			mr.setLocation(pc.getBoxX(), pc.getBoxY() + displayCount * dh + 8, pc.getBoxWidth(), dh - 24);				
			mr.setExtents(startTime, endTime, yMin, yMax);
			mr.createDefaultAxis(8, 4, false, allowExpand);
			mr.setXAxisToTime(8, true);
			mr.getAxis().setLeftLabelAsText("Meters");
			mr.createDefaultPointRenderers();
			mr.createDefaultLegendRenderer(channelLegendsCols);
			
			if (displayCount == compCount) {
				mr.getAxis().setBottomLabelAsText("Time (" + Valve3.getInstance().getTimeZoneAbbr()+ ")");	
			}
		
			pc.setTranslation(mr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
			pc.setTranslationType("ty");
			v3Plot.getPlot().addRenderer(mr);
			v3Plot.addComponent(pc);
		}
	}
	*/

	/**
	 * Initialize MapRenderer to plot map and list of EllipseVectorRenderer2 
	 * elements which plot a station's movement
	 */
	private void plotVelocityMap() {
		
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
		mr.setLocationByMaxBounds(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), Integer.parseInt(component.get("mh")));
		
		GeoLabelSet labels = Valve3.getInstance().getGeoLabelSet();
		labels = labels.getSubset(range);
		mr.setGeoLabelSet(labels);
		
		GeoImageSet images = Valve3.getInstance().getGeoImageSet();
		RenderedImage ri = images.getMapBackground(proj, range, component.getBoxWidth());
		
		mr.setMapImage(ri);
		mr.createBox(8);
		mr.createGraticule(8, true);
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
		mr.getAxis().setBottomLabelAsText("Longitude");
		mr.getAxis().setLeftLabelAsText("Latitude");
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
	
			System.out.println("XYZ Velocity: " + v.getQuick(0,0) + " " + v.getQuick(1,0) + " " + v.getQuick(2,0));
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
			
			System.out.println("Velocity: " + vt);
			System.out.println("Error: " + e);
			
			DoubleMatrix2D es = e.viewPart(0, 0, 2, 2);
			EigenvalueDecomposition ese = new EigenvalueDecomposition(es);
			DoubleMatrix1D evals = ese.getRealEigenvalues();
			DoubleMatrix2D evecs = ese.getV();
			System.out.println("evals: " + evals);
			System.out.println("evecs: " + evecs);
			double phi = Math.atan2(evecs.getQuick(0, 0), evecs.getQuick(1, 0));
			double w = Math.sqrt(evals.getQuick(0) * 5.9915);
			double h = Math.sqrt(evals.getQuick(1) * 5.9915);
			System.out.printf("w: %f h: %f\n", w, h);
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
			
			maxMag = Math.max(vr.getMag(), maxMag);
			plot.addRenderer(vr);
			vrs.add(vr);
		}
		
		if (maxMag == -1E300) {
			return;
		}
		
		double scale = EllipseVectorRenderer.getBestScale(maxMag);
		System.out.println("Scale: " + scale);
		double desiredLength = Math.min((mr.getMaxY() - mr.getMinY()), (mr.getMaxX() - mr.getMinX())) / 5;
		System.out.println("desiredLength: " + desiredLength);
		System.out.println("desiredLength/scale: " + desiredLength / scale);
		
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
		 
		TextRenderer tr = new TextRenderer();
		tr.x = mr.getGraphX() + 10;
		tr.y = mr.getGraphY() + mr.getGraphHeight() - 5;
		tr.text = scale + " m/year";
		plot.addRenderer(svr);
		plot.addRenderer(tr);
	}

	/**
	 * Initialize MatrixRenderers for left and right axis, adds them to plot
	 * @throws Valve3Exception
	 */
	public void plotData() throws Valve3Exception {
		
		switch (plotType) {
			case TIME_SERIES:
				
				// calculate how many graphs we are going to build (number of channels * number of components)
				compCount			= channelDataMap.size() * compCount;
				
				// setting up variables to decide where to plot this component
				int displayCount	= 0;
				int dh				= component.getBoxHeight() / compCount;
				
				// setup the display for the legend
				String rankLegend	= "";
				Rank rank			= ranksMap.get(rk);
				rankLegend			= rank.getCode();
				
				// if a baseline was chosen then setup the display for the legend
				String baselineLegend = "";
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
						gdm.add(i + 2, -gdm.mean(i + 2));
						// gdm.add(2, -dm.getQuick(0, 2));
					}
					
					// set up the legend 
					for (int i = 0; i < legendsCols.length; i++) {
						channelLegendsCols[i] = String.format("%s%s %s %s", channel.getCode(), baselineLegend, rankLegend, legendsCols[i]);
					}
					
					// create an individual matrix renderer for each component selected
					for (int i = 0; i < columnsList.size(); i++) {
						if (selectedCols[i]) {
							MatrixRenderer leftMR	= getLeftMatrixRenderer(channel, gdm, displayCount, dh, i);
							MatrixRenderer rightMR	= getRightMatrixRenderer(channel, gdm, displayCount, dh, i);
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
				plotVelocityMap();
				break;
		}
		
		switch(plotType) {
			case TIME_SERIES:
				v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Time Series");
				break;
			case VELOCITY_MAP:
				v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Velocity Field");
				break;
		}
	}

	/**
	 * Concrete realization of abstract method. 
	 * Generate PNG image to local file.
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception {
		v3Plot		= v3p;
		component	= comp;
		channelsMap	= getChannels(vdxSource, vdxClient);
		ranksMap	= getRanks(vdxSource, vdxClient);
		columnsList	= getColumns(vdxSource, vdxClient);
		getInputs();
		getData();
		
		plotData();
		
		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		plot.writePNG(v3Plot.getLocalFilename());
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
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(client);
		VDXClient cl = pool.checkout();
		List<String> chs = cl.getTextData(params);
		pool.checkin(cl);
		channels = Channel.fromStringsToMap(chs);
		return channels;
	}
	
	/**
	 * Initialize list of ranks for given vdx source
	 * @param source	vdx source name
	 * @param client	vdx name
	 */
	private static Map<Integer, Rank> getRanks(String source, String client) {
		Map<Integer, Rank> ranks;
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", source);
		params.put("action", "ranks");
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(client);
		VDXClient cl = pool.checkout();
		List<String> rks = cl.getTextData(params);
		pool.checkin(cl);
		ranks = Rank.fromStringsToMap(rks);
		return ranks;
	}

	/**
	 * Initialize list of channels for given vdx source
	 * @param source	vdx source name
	 * @param client	vdx name
	 */
	private static List<Column> getColumns(String source, String client) {
		List<Column> columns;	
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", source);
		params.put("action", "columns");
		Pool<VDXClient> pool	= Valve3.getInstance().getDataHandler().getVDXClient(client);
		VDXClient cl			= pool.checkout();
		List<String> cols		= cl.getTextData(params);
		pool.checkin(cl);
		columns					= Column.fromStringsToList(cols);
		return columns;
	}
}