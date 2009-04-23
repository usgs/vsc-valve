package gov.usgs.valve3.plotter;

import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.EllipseVectorRenderer;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.Renderer;
import gov.usgs.plot.ShapeRenderer;
import gov.usgs.plot.SmartTick;
import gov.usgs.plot.TextRenderer;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.util.Time;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.GenericDataMatrix;
import gov.usgs.vdx.data.tilt.TiltStationData;
import gov.usgs.vdx.data.tilt.Station;


import gov.usgs.proj.GeoRange;
import gov.usgs.plot.map.GeoLabel;
import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.plot.map.GeoLabelSet;
import gov.usgs.plot.map.MapRenderer;
import gov.usgs.proj.TransverseMercator;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import cern.colt.matrix.DoubleMatrix2D;

/**
 * Generate tilt images 
 * from raw data got from vdx source
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.2  2005/09/24 17:57:22  dcervelli
 * Working on implementing various tilt features.
 *
 * Revision 1.1  2005/09/06 20:10:24  dcervelli
 * Initial commit.
 *
 * @author Dan Cervelli
 */
public class TiltPlotter extends Plotter {
	
	private static final char MICRO = (char)0xb5;
	private static final char DEGREES = (char)0xb0;
	
	private Valve3Plot v3Plot;
	private PlotComponent component;
	
	private Map<String, TiltStationData> stationDataMap;
	private static Map<String, Map<String, Station>> stationsMap = new HashMap<String, Map<String, Station>>();
	
	private TiltStationData data;
	private double TZOffset;
	
	private String channels;
	private double startTime;
	private double endTime;
	
	private PlotType plotType;
	private Azimuth azimuth;
	private RightAxis rightAxis;
	
	private boolean showEast = false;
	private boolean showNorth = false;
	private boolean showRadial = false;
	private boolean showTangential = false;
	private boolean showMagnitude = false;
	private boolean showAzimuth = false;
	
	private boolean detrendEast = false;
	private boolean detrendNorth = false;
	private boolean detrendRadial = false;
	private boolean detrendTangential = false;
	
	private double azimuthValue;
	
	private enum PlotType {
		TIME_SERIES, TILT_VECTORS;
		
		public static PlotType fromString(String s) {
			if (s.equals("ts"))
				return TIME_SERIES;
			else if (s.equals("tv"))
				return TILT_VECTORS;
			else 
				return null;
		}
	}
	
	private enum RightAxis {
		NONE, HOLETEMPERATURE, BOXTEMPERATURE, INSTVOLTAGE, GNDVOLTAGE, RAINFALL;
		
		public static RightAxis fromString(String s) {
			if (s.equals("r_h"))
				return HOLETEMPERATURE;
			else if (s.equals("r_b"))
				return BOXTEMPERATURE;
			else if (s.equals("r_i"))
				return INSTVOLTAGE;
			else if (s.equals("r_g"))
				return GNDVOLTAGE;
			else if (s.equals("r_r"))
				return RAINFALL;
			else
				return NONE;
		}
	}
	
	private enum Azimuth {
		NOMINAL, OPTIMAL, USERDEFINED;
		
		public static Azimuth fromString(String s) {
			if (s.equals("n"))
				return NOMINAL;
			else if (s.equals("o"))
				return OPTIMAL;
			else if (s.equals("u"))
				return USERDEFINED;
			else
				return null;
		}
	}
	
	/**
	 * Default constructor
	 */
	public TiltPlotter() {}
		
	private void getInputs() throws Valve3Exception {
		
		channels	= component.get("ch");
		if (channels == null || channels.length() <= 0)
			throw new Valve3Exception("Illegal channel.");
		
		endTime		= component.getEndTime();
		if (Double.isNaN(endTime))
			throw new Valve3Exception("Illegal end time.");
		
		startTime	= component.getStartTime(endTime);
		if (Double.isNaN(startTime))
			throw new Valve3Exception("Illegal start time.");
		
		showEast			= Util.stringToBoolean(component.get("l_e"));
		showNorth			= Util.stringToBoolean(component.get("l_n"));
		showRadial			= Util.stringToBoolean(component.get("l_r"));
		showTangential		= Util.stringToBoolean(component.get("l_t"));
		showMagnitude		= Util.stringToBoolean(component.get("l_m"));
		showAzimuth			= Util.stringToBoolean(component.get("l_a"));
		
		detrendEast			= Util.stringToBoolean(component.get("d_e"));
		detrendNorth		= Util.stringToBoolean(component.get("d_n"));
		detrendRadial		= Util.stringToBoolean(component.get("d_r"));
		detrendTangential	= Util.stringToBoolean(component.get("d_t"));
		
		azimuth				= Azimuth.fromString(component.get("az"));
		
		rightAxis			= RightAxis.fromString(component.get("right"));	
		
		plotType			= PlotType.fromString(component.get("type"));
		if (plotType == null)
			throw new Valve3Exception("Illegal plot type.");
	}

	private void getData() throws Valve3Exception {
		
		// create a storage mechanism for all the datasets
		stationDataMap = new HashMap<String, TiltStationData>();
		String[] channelArray	= channels.split(",");
		HashMap<String, String> params = new HashMap<String, String>();
		
		// define default parameters for the data request
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));

		// get a connection to the database
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		
		// figure out the time zone offset		
		System.out.println("GMT startTime:" + Time.toDateString(startTime));
		System.out.println("GMT endTime:  " + Time.toDateString(endTime));
		TZOffset = Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
		startTime += TZOffset;
		endTime += TZOffset;		
		System.out.println(Valve3.getInstance().getTimeZoneAbbr() + " startTime:" + Time.toDateString(startTime));
		System.out.println(Valve3.getInstance().getTimeZoneAbbr() + " endTime:  " + Time.toDateString(endTime));
		
		// iterate through each of the selected channels and get the data from the db
		boolean gotData = false;
		for (String channel : channelArray) {
			params.put("cid", channel);		
			data = (TiltStationData)client.getBinaryData(params);
			if (data != null || data.getTiltData().rows() > 0) {
				stationDataMap.put(channel, data);
				gotData = true;	
			}
		}
		
		// if nothing was successful then indicate that to the user
		if (!gotData) {
			throw new Valve3Exception("No data for any stations.");
		}
		
		// return the connection to the database
		pool.checkin(client);
	}
	
	public void plotTimeSeries() throws Valve3Exception {
		
		// variables related to all plots from the request
		Map<String, Station> stations = stationsMap.get(vdxSource);
		int dh = component.getBoxHeight() / stationDataMap.size();
		int displayCount = 0;
		boolean allowExpand	= true;
		
		// iterate through each of the selected stations
		for (String key : stationDataMap.keySet()) {
			
			// instantiate the data structure for this station
			TiltStationData data = stationDataMap.get(key);
			List<String> legends = new ArrayList<String> ();
			
			// adjust the tilt data for the installation time zone
			data.getTiltData().adjustTime(TZOffset);			
			
			// instantiate this plot
			PlotComponent pc = new PlotComponent();
			pc.setSource(component.getSource());
			pc.setBoxX(component.getBoxX());
			pc.setBoxY(component.getBoxY());
			pc.setBoxWidth(component.getBoxWidth());
			
			// instantiate the azimuth and tangential values based on the user selection
			switch (azimuth) {
				case NOMINAL:
					azimuthValue = stations.get(key).getAzimuth();
					break;
				case OPTIMAL:
					azimuthValue = data.getTiltData().getOptimalAzimuth();
					break;
				case USERDEFINED:
					azimuthValue = Util.stringToDouble(component.get("azval"));
					break;
				default:
					azimuthValue = 0.0;
					break;
			}
			azimuthValue -= 90.0;

			// get the set of tilt data using this azimuth value (1=east,2=north,3=radial,4=tangential)
			DoubleMatrix2D mm = data.getTiltData().getAllData(azimuthValue);		
			GenericDataMatrix dm = new GenericDataMatrix(mm);
			
			// subtract the mean from the data to get it on a zero based scale
			dm.add(1, -dm.mean(1));
			dm.add(2, -dm.mean(2));
			dm.add(3, -dm.mean(3));
			dm.add(4, -dm.mean(4));
			
			// detrend the data that the user requested to be detrended
			if (detrendEast) 		{ dm.detrend(1); }
			if (detrendNorth)		{ dm.detrend(2); }
			if (detrendRadial)		{ dm.detrend(3); }
			if (detrendTangential)	{ dm.detrend(4); }
			
			
			MatrixRenderer mr = new MatrixRenderer(dm.getData());
			mr.setVisible(0, showEast);
			mr.setVisible(1, showNorth);
			mr.setVisible(2, showRadial);
			mr.setVisible(3, showTangential);
			mr.setVisible(4, showMagnitude);
			mr.setVisible(5, showAzimuth);
			
			double min	= 0.0;
			double max	= 0.0;
			double yMin	= 0.0;
			double yMax	= 0.0;
			
			// iterate through each of the data components to build up the appropriate plot
			if (showEast) {
				min = Math.min(dm.min(1), min);
				max = Math.max(dm.max(1), max);
				legends.add(stations.get(key).getCode() + " East");
			}
			if (showNorth) {
				min = Math.min(dm.min(2), min);
				max = Math.max(dm.max(2), max);
				legends.add(stations.get(key).getCode() + " North");
			}
			if (showRadial) {
				min = Math.min(dm.min(3), min);
				max = Math.max(dm.max(3), max);
				legends.add(stations.get(key).getCode() + " " + ((azimuthValue + 90.0) % 360.0));
			}
			if (showTangential) {
				min = Math.min(dm.min(4), min);
				max = Math.max(dm.max(4), max);
				legends.add(stations.get(key).getCode() + " " + ((azimuthValue + 180.0) % 360.0));
			}
			if (showMagnitude) {
				min = Math.min(dm.min(5), min);
				max = Math.max(dm.max(5), max);
				legends.add(stations.get(key).getCode() + " Magnitude");
			}
			if (showAzimuth) {
				min = Math.min(dm.min(6), min);
				max = Math.max(dm.max(6), max);
				legends.add(stations.get(key).getCode() + " Azimuth");
			}
			
			if (!component.isAutoScale("ysL")) {
				double[] ys = component.getYScale("ysL", min, max);
				yMin = ys[0];
				yMax = ys[1];
				allowExpand = false;
			}
			
			yMin = min;
			yMax = max;
			if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
				throw new Valve3Exception("Illegal axis values.");		

			mr.setUnit("tilt");
			mr.setLocation(pc.getBoxX(), pc.getBoxY() + displayCount * dh + 8, pc.getBoxWidth(), dh - 16);
			mr.setExtents(startTime, endTime, yMin, yMax);
			mr.createDefaultAxis(8, 8, false, allowExpand);
			mr.createDefaultLineRenderers();
			mr.setXAxisToTime(8);
			mr.getAxis().setLeftLabelAsText("Tilt (" + MICRO + "R)");
			
			pc.setTranslation(mr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
			pc.setTranslationType("ty");
			v3Plot.getPlot().addRenderer(mr);
			v3Plot.addComponent(pc);
			
			// right axis functionality
			if (rightAxis != RightAxis.NONE) {
			
				DoubleMatrix2D rightData = null;
				String label = "";
				String unit = "";
				switch (rightAxis) {
					case HOLETEMPERATURE:
						data.getHoleTempData().adjustTime(TZOffset);
						rightData	= data.getHoleTempData().getData();
						label		= "Temperature (" + DEGREES + "C)";
						unit		= "degrees c";
						legends.add(stations.get(key).getCode() + " Hole Temp");
						break;
					case BOXTEMPERATURE:
						data.getBoxTempData().adjustTime(TZOffset);
						rightData	= data.getBoxTempData().getData();
						label		= "Temperature (" + DEGREES + "C)";
						unit		= "degrees c";
						legends.add(stations.get(key).getCode() + " Box Temp");
						break;
					case INSTVOLTAGE:
						data.getInstVoltData().adjustTime(TZOffset);
						rightData	= data.getInstVoltData().getData();
						label		= "Voltage (V)";
						unit		= "volts";
						legends.add(stations.get(key).getCode() + " Inst Volt");
						break;
					case GNDVOLTAGE:
						data.getGndVoltData().adjustTime(TZOffset);
						rightData	= data.getGndVoltData().getData();
						label		= "Voltage (V)";
						unit		= "volts";
						legends.add(stations.get(key).getCode() + " Gnd Volt");
						break;
					case RAINFALL:
						data.getRainfallData().adjustTime(TZOffset);		
						rightData	= data.getRainfallData().getData();
						label		= "Rainfall (mm)";
						unit		= "millimeters";
						legends.add(stations.get(key).getCode() + " Rainfall");
						break;
				}
			
				dm = new GenericDataMatrix(rightData);

				min = dm.min(1);
				max = dm.max(1);
				if (component.isAutoScale("ysR")) {
					yMin = min;
					yMax = max;
					if (max - min < 0.2) {
						yMax += 0.1;
						yMin -= 0.1;
					}
				} else {
					double[] ys = component.getYScale("ysR", min, max);
					yMin = ys[0];
					yMax = ys[1];
				}
				if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
					throw new Valve3Exception("Illegal axis values.");
			
				MatrixRenderer lmr = new MatrixRenderer(dm.getData());
				lmr.setUnit(unit);
				lmr.setLocation(pc.getBoxX(), pc.getBoxY() + displayCount * dh + 8, pc.getBoxWidth(), dh - 16);
				lmr.setExtents(startTime, endTime, yMin, yMax);
				AxisRenderer ar = new AxisRenderer(lmr);
				ar.createRightTickLabels(SmartTick.autoTick(yMin, yMax, 8, false), null);
				lmr.setAxis(ar);
				lmr.createDefaultLineRenderers();
				Renderer[] r = lmr.getLineRenderers();
				((ShapeRenderer)r[0]).color = Color.black;
				lmr.getAxis().setRightLabelAsText(label);
				
				pc.setTranslation(lmr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
				pc.setTranslationType("ty");
				v3Plot.getPlot().addRenderer(lmr);
				v3Plot.addComponent(pc);
			}
			
			displayCount++;
			if (displayCount == stationDataMap.size()) {
				mr.getAxis().setBottomLabelAsText("Time");
			}
			
			// create the legend
			mr.createDefaultLegendRenderer(legends.toArray(new String[0]));
		}
		
		// set information related to the entire plot		
		v3Plot.setTitle("Tilt: Time Series");
	}
	
	public void plotTiltVectors() {
		
		List<Point2D.Double> locs = new ArrayList<Point2D.Double>();
		Map<String, Station> stations = stationsMap.get(vdxSource);
		
		// build up a list of all the stations that are being plotted
		for (String key : stationDataMap.keySet()) {
			TiltStationData data = stationDataMap.get(key);
			if (data != null) {
				locs.add(stations.get(key).getLonLat());
			}
		}
		
		// create the dimensions of the plot based on these stations
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
		
		for (String key : stationDataMap.keySet()) {
			Station station = stations.get(key);
			labels.add(new GeoLabel(station.getCode(), station.getLon(), station.getLat()));
			TiltStationData stn = stationDataMap.get(key);
			
			if (stn == null) {
				continue;
			}
			
			DoubleMatrix2D dm = stn.getTiltData().getAllData(0.0);
			
			double et1	= dm.getQuick(0, 1);
			double et2	= dm.getQuick(dm.rows() - 1, 1);
			double nt1	= dm.getQuick(0, 2);
			double nt2	= dm.getQuick(dm.rows() - 1, 2);
			double e	= et2 - et1;
			double n	= nt2 - nt1;
			
			EllipseVectorRenderer evr = new EllipseVectorRenderer();
			evr.frameRenderer = mr;
			Point2D.Double ppt = proj.forward(station.getLonLat());
			evr.x = ppt.x;
			evr.y = ppt.y;
			evr.u = e;
			evr.v = n;
			
			maxMag = Math.max(evr.getMag(), maxMag);
			plot.addRenderer(evr);
			vrs.add(evr);
		}
		
		if (maxMag == -1E300) {
			return;
		}
		
		// set the length of the legend vector to 1/5 of the width of the shortest side of the map
		double scale = EllipseVectorRenderer.getBestScale(maxMag);
		double desiredLength = Math.min((mr.getMaxY() - mr.getMinY()), (mr.getMaxX() - mr.getMinX())) / 5;
		
		for (int i = 0; i < vrs.size(); i++) {
			EllipseVectorRenderer evr = (EllipseVectorRenderer)vrs.get(i);
			evr.setScale(desiredLength / scale);
		}
		
		// draw the legend vector
		EllipseVectorRenderer svr = new EllipseVectorRenderer();
		svr.frameRenderer = mr;
		svr.drawEllipse = false;
		svr.x = mr.getMinX();
		svr.y = mr.getMinY() + 17 / mr.getYScale();
		svr.u = desiredLength;
		svr.v = 0;
		plot.addRenderer(svr);
		
		// draw the legend vector units
		TextRenderer tr = new TextRenderer();
		tr.x = mr.getGraphX() + 10;
		tr.y = mr.getGraphY() + mr.getGraphHeight() - 5;
		tr.text = scale + " " + MICRO + "R";
		plot.addRenderer(tr);
		
		v3Plot.setTitle("Tilt: Vectors");		
	}
	
	private static void getStations (String source, String client) {
		synchronized (stationsMap) {
			Map<String, Station> stations = stationsMap.get(source);			
			if (stations == null) {
				Map<String, String> params = new HashMap<String, String>();
				params.put("source", source);
				params.put("action", "selectors");
				Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(client);
				VDXClient cl = pool.checkout();
				List<String> stas = cl.getTextData(params);
				pool.checkin(cl);
				stations = Station.fromStringsToMap(stas);
				stationsMap.put(source, stations);
			}
		}
	}
	
	/**
	 * Concrete realization of abstract method. 
	 * Generate tilt PNG image to file with random name.
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception	{
		
		v3Plot = v3p;
		component = comp;
		getStations(vdxSource, vdxClient);
		getInputs();
		getData();
		
		switch (plotType) {
			case TIME_SERIES:
				plotTimeSeries();
				break;
			case TILT_VECTORS:
				plotTiltVectors();
				break;
		}
		
		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		plot.writePNG(v3Plot.getLocalFilename());
	}	

	public String toCSV(PlotComponent c) throws Valve3Exception
	{
		component = c;
		getInputs();
		getData();

        return data.toCSV();
	}
}
