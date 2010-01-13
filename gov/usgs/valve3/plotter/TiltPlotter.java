package gov.usgs.valve3.plotter;

import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.EllipseVectorRenderer;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.Renderer;
import gov.usgs.plot.SmartTick;
import gov.usgs.plot.TextRenderer;
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
import gov.usgs.vdx.data.tilt.TiltData;


import gov.usgs.proj.GeoRange;
import gov.usgs.plot.map.GeoLabel;
import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.plot.map.GeoLabelSet;
import gov.usgs.plot.map.MapRenderer;
import gov.usgs.proj.TransverseMercator;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Logger;

import cern.colt.matrix.DoubleMatrix2D;

/**
 * Generate tilt images from raw data got from vdx source
 *
 * @author Dan Cervelli, Loren Antolik
 */
public class TiltPlotter extends Plotter {
	
	private static final char MICRO = (char)0xb5;
	
	private enum PlotType {
		TIME_SERIES, TILT_VECTORS;		
		public static PlotType fromString(String s) {
			if (s.equals("ts")) {
				return TIME_SERIES;
			} else if (s.equals("tv")) {
				return TILT_VECTORS;
			} else {
				return null;
			}
		}
	}
	
	private enum Azimuth {
		NOMINAL, OPTIMAL, USERDEFINED;		
		public static Azimuth fromString(String s) {
			if (s.equals("n")) {
				return NOMINAL;
			} else if (s.equals("o")) {
				return OPTIMAL;
			} else if (s.equals("u")) {
				return USERDEFINED;
			} else {
				return null;
			}
		}
	}
	
	private Valve3Plot v3Plot;
	private PlotComponent component;
	private int compCount;
	
	private Map<Integer, TiltData> channelDataMap;
	private static Map<Integer, Channel> channelsMap;
	private static Map<Integer, Rank> ranksMap;
	private static Map<Integer, Double> azimuthsMap;
	private static List<Column> columnsList;
	private double startTime;
	private double endTime;
	private String ch;
	private int rk;
	
	private String leftUnit;
	private String rightUnit;
	private Map<Integer, String> axisMap;
	private int leftLines;
	private int leftTicks;
	
	private int columnsCount;
	private boolean detrendCols[];
	private String legendsCols[];
	private String channelLegendsCols[];
	
	private PlotType plotType;
	
	private Azimuth azimuth;	
	private double azimuthValue;
	private double azimuthRadial;
	private double azimuthTangential;
	
	public final boolean ranks	= true;
	
	protected Logger logger;
	
	/**
	 * Default constructor
	 */
	public TiltPlotter() {
		logger		= Logger.getLogger("gov.usgs.vdx");		
	}
		
	private void getInputs() throws Valve3Exception {
		
		ch = component.get("ch");
		if (ch == null || ch.length() <= 0)
			throw new Valve3Exception("Illegal channel.");
		
		rk = Util.stringToInt(component.get("rk"));
		if (rk < 0) {
			throw new Valve3Exception("Illegal rank.");
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
		
		switch(plotType) {
		case TIME_SERIES:
		
			azimuth				= Azimuth.fromString(component.get("az"));
		
			columnsCount		= columnsList.size();
			detrendCols			= new boolean [columnsCount];
			legendsCols			= new String  [columnsCount];
			channelLegendsCols	= new String  [columnsCount];
			
			leftLines		= 0;
			axisMap			= new LinkedHashMap<Integer, String>();
			
			// iterate through all the active columns and place them in a map if they are displayed
			for (int i = 0; i < columnsList.size(); i++) {
				Column column	= columnsList.get(i);
				boolean display	= Util.stringToBoolean(component.get(column.name));
				detrendCols[i]	= Util.stringToBoolean(component.get("d_" + column.name));
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
				} else {
					axisMap.put(i, "");
				}
			}
			
			if (leftUnit == null && rightUnit == null)
				throw new Valve3Exception("Nothing to plot.");
			
			break;
			
		case TILT_VECTORS:
			break;
		}
	}

	private void getData() throws Valve3Exception {
		
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
		channelDataMap		= new LinkedHashMap<Integer, TiltData>();
		String[] channels	= ch.split(",");
		
		// iterate through each of the selected channels and get the data from the db=
		for (String channel : channels) {
			params.put("ch", channel);		
			TiltData data = (TiltData)client.getBinaryData(params);
			if (data != null) {
				gotData = true;	
				data.adjustTime(TZOffset);
				channelDataMap.put(Integer.valueOf(channel), data);
			}
		}
		
		if (!gotData) {
			throw new Valve3Exception("No data for any stations.");
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
	private MatrixRenderer getLeftMatrixRenderer(Channel channel, GenericDataMatrix gdm, int displayCount, int dh) throws Valve3Exception {	
		
		MatrixRenderer mr = new MatrixRenderer(gdm.getData(), ranks);
		mr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);

		double yMin = 1E300;
		double yMax = -1E300;
		boolean allowExpand = true;
			
		mr.setAllVisible(false);

		for (int i = 0; i < axisMap.size(); i++) {
			if (axisMap.get(i).equals("L")) {
				mr.setVisible(i, true);
				if (component.isAutoScale("ysL")) {
					double buff;
					yMin	= Math.min(yMin, gdm.min(i + 2));
					yMax	= Math.max(yMax, gdm.max(i + 2));
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
			}
		}
		
		mr.setExtents(startTime, endTime, yMin, yMax);	
		mr.createDefaultAxis(8, 8, false, allowExpand);
		mr.setXAxisToTime(8);
		mr.getAxis().setLeftLabelAsText(leftUnit);
		mr.createDefaultLineRenderers();
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
	private MatrixRenderer getRightMatrixRenderer(Channel channel, GenericDataMatrix gdm, int displayCount, int dh) throws Valve3Exception {
		
		if (rightUnit == null)
			return null;
		
		MatrixRenderer mr = new MatrixRenderer(gdm.getData(), ranks);
		mr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
		
		double yMin = 1E300;
		double yMax = -1E300;
		boolean allowExpand = true;
		
		mr.setAllVisible(false);

		for (int i = 0; i < axisMap.size(); i++) {
			if (axisMap.get(i).equals("R")) {
				mr.setVisible(i, true);
				if (component.isAutoScale("ysR")) {
					double buff;
					yMin	= Math.min(yMin, gdm.min(i + 2));
					yMax	= Math.max(yMax, gdm.max(i + 2));
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
			}
		}
		
		mr.setExtents(startTime, endTime, yMin, yMax);
		
		AxisRenderer ar = new AxisRenderer(mr);
		ar.createRightTickLabels(SmartTick.autoTick(yMin, yMax, leftTicks, false), null);
		// ar.createRightTickLabels(SmartTick.autoTick(yMin, yMax, 8, allowExpand), null);
		mr.setAxis(ar);
		mr.getAxis().setRightLabelAsText(rightUnit);
		mr.createDefaultLineRenderers(leftLines);
		mr.createDefaultLegendRenderer(channelLegendsCols, leftLines);
		
		return mr;
	}
	
	public void plotTiltVectors() {
		
		List<Point2D.Double> locs = new ArrayList<Point2D.Double>();
		
		// add a location for each channel that is being plotted
		for (int cid : channelDataMap.keySet()) {
			TiltData data = channelDataMap.get(cid);
			if (data != null) {
				locs.add(channelsMap.get(cid).getLonLat());
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
		
		for (int cid : channelDataMap.keySet()) {
			Channel channel = channelsMap.get(cid);
			TiltData data = channelDataMap.get(cid);
			
			if (data == null) {
				continue;
			}
			
			labels.add(new GeoLabel(channel.getCode(), channel.getLon(), channel.getLat()));
			
			DoubleMatrix2D dm = data.getAllData(0.0);
			
			double et1	= dm.getQuick(0, 2);
			double et2	= dm.getQuick(dm.rows() - 1, 2);
			double nt1	= dm.getQuick(0, 3);
			double nt2	= dm.getQuick(dm.rows() - 1, 3);
			double e	= et2 - et1;
			double n	= nt2 - nt1;
			
			EllipseVectorRenderer evr = new EllipseVectorRenderer();
			evr.frameRenderer = mr;
			Point2D.Double ppt = proj.forward(channel.getLonLat());
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
	}

	/**
	 * Initialize MatrixRenderers for left and right axis, adds them to plot
	 * @throws Valve3Exception
	 */
	public void plotData() throws Valve3Exception {
		
		switch (plotType) {
			case TIME_SERIES:
				
				/// calculate how many graphs we are going to build (number of channels)
				compCount			= channelDataMap.size();
				
				// setting up variables to decide where to plot this component
				int displayCount	= 0;
				int dh				= component.getBoxHeight() / compCount;
				
				// get the rank information for this plot
				String rankLegend	= "";
				Rank rank			= ranksMap.get(rk);
				rankLegend			= rank.getCode() + " (" + rank.getRank() + ")";
				
				for (int cid : channelDataMap.keySet()) {
					
					// get the relevant information for this channel
					Channel channel	= channelsMap.get(cid);
					TiltData data	= channelDataMap.get(cid);
					
					// verify their is something to plot
					if (data == null || data.rows() == 0) {
						continue;
					}
					
					// instantiate the azimuth and tangential values based on the user selection
					switch (azimuth) {
					case NOMINAL:
						azimuthValue = azimuthsMap.get(channel.getId());
						break;
					case OPTIMAL:
						azimuthValue = data.getOptimalAzimuth();
						break;
					case USERDEFINED:
						azimuthValue = Util.stringToDouble(component.get("azval"));
						break;
					default:
						azimuthValue = 0.0;
						break;
					}
					azimuthValue 	   -= 90.0;
					azimuthRadial		= (azimuthValue + 90.0) % 360.0;
					azimuthTangential	= (azimuthValue + 180.0) % 360.0;
					
					// System.out.println("tilt plotter:" + channel.getCode() + "/azimuthValue:" + azimuthValue + "/azimuthRadial:" + azimuthRadial + "/azimuthTangential:" + azimuthTangential);
					
					// subtract the mean from the data to get it on a zero based scale (for east, north, radial and tangential
					data.add(2, -data.mean(2));
					data.add(3, -data.mean(3));
					// data.add(4, -data.mean(4));
					// data.add(5, -data.mean(5));
					
					GenericDataMatrix gdm	= new GenericDataMatrix(data.getAllData(azimuthValue));
					
					// detrend the data that the user requested to be detrended					
					for (int i = 0; i < columnsCount; i++) {
						if (detrendCols[i]) { data.detrend(i + 2); }
					}
					
					// set up the legend 
					String tiltLegend;
					for (int i = 0; i < legendsCols.length; i++) {
						if (legendsCols[i].equals("Radial")) {
							tiltLegend	= String.valueOf(azimuthRadial);
						} else if (legendsCols[i].equals("Tangential")) {
							tiltLegend	= String.valueOf(azimuthTangential);
						} else {
							tiltLegend	= legendsCols[i];
						}
						channelLegendsCols[i] = String.format("%s %s %s", channel.getCode(), rankLegend, tiltLegend);
					}
					
					MatrixRenderer leftMR	= getLeftMatrixRenderer(channel, gdm, displayCount, dh);
					MatrixRenderer rightMR	= getRightMatrixRenderer(channel, gdm, displayCount, dh);
					v3Plot.getPlot().addRenderer(leftMR);
					if (rightMR != null)
						v3Plot.getPlot().addRenderer(rightMR);
					component.setTranslation(leftMR.getDefaultTranslation(v3Plot.getPlot().getHeight()));
					component.setTranslationType("ty");
					v3Plot.addComponent(component);
					displayCount++;
				}
				break;
				
			case TILT_VECTORS:
				plotTiltVectors();
				break;
		}
		
		switch(plotType) {
			case TIME_SERIES:
				v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Time Series");
				break;
			case TILT_VECTORS:
				v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Vectors");
				break;
		}
	}
	
	/**
	 * Concrete realization of abstract method. 
	 * Generate tilt PNG image to file with random name.
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception	{		
		v3Plot		= v3p;
		component	= comp;
		channelsMap	= getChannels(vdxSource, vdxClient);
		ranksMap	= getRanks(vdxSource, vdxClient);
		azimuthsMap	= getAzimuths(vdxSource, vdxClient);
		columnsList	= getColumns(vdxSource, vdxClient);
		getInputs();
		getData();
		
		plotData();
				
		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		plot.writePNG(v3Plot.getLocalFilename());
	}	

	/**
	 * @return CSV dump of binary data described by given PlotComponent
	 */
	public String toCSV(PlotComponent comp) throws Valve3Exception {
		component	= comp;
		
		getInputs();
		getData();
		
		// return data.toCSV();
		return "NOT IMPLEMENTED YET FOR MULTIPLE STATIONS";
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
	private static Map<Integer, Double> getAzimuths(String source, String client) {
		Map<Integer, Double> azimuths = new LinkedHashMap<Integer, Double>();	
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", source);
		params.put("action", "azimuths");
		Pool<VDXClient> pool	= Valve3.getInstance().getDataHandler().getVDXClient(client);
		VDXClient cl			= pool.checkout();
		List<String> chs		= cl.getTextData(params);
		pool.checkin(cl);
		for (int i = 0; i < chs.size(); i++) {
			String[] temp = chs.get(i).split(":");
			azimuths.put(Integer.valueOf(temp[0]), Double.valueOf(temp[1]));
		}
		return azimuths;
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
