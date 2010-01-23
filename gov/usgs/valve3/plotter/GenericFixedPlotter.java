package gov.usgs.valve3.plotter;

import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
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
import gov.usgs.vdx.data.Column;
import gov.usgs.vdx.data.GenericDataMatrix;
import gov.usgs.vdx.data.Rank;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Generate images for generic data plot to files
 *
 * @author Dan Cervelli, Loren Antolik
 */
public class GenericFixedPlotter extends Plotter {
	
	private Valve3Plot v3Plot;
	private PlotComponent component;
	int compCount;
	
	private Map<Integer, GenericDataMatrix> channelDataMap;	
	private static Map<Integer, Channel> channelsMap;
	private static Map<Integer, Rank> ranksMap;
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
	private boolean normalzCols[];
	private String legendsCols[];
	private String channelLegendsCols[];
	
	public final boolean ranks	= true;
	
	protected Logger logger;

	/**
	 * Default constructor
	 */
	public GenericFixedPlotter() {
		logger		= Logger.getLogger("gov.usgs.vdx");		
	}

	/**
	 * Initialize internal data from PlotComponent component
	 * @throws Valve3Exception
	 */
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
		
		columnsCount		= columnsList.size();
		detrendCols			= new boolean [columnsCount];
		normalzCols			= new boolean [columnsCount];
		legendsCols			= new String  [columnsCount];
		channelLegendsCols	= new String  [columnsCount];
		
		leftLines		= 0;
		axisMap			= new LinkedHashMap<Integer, String>();
		
		// iterate through all the active columns and place them in a map if they are displayed
		for (int i = 0; i < columnsList.size(); i++) {
			Column column	= columnsList.get(i);
			boolean display	= Util.stringToBoolean(component.get(column.name));
			detrendCols[i]	= Util.stringToBoolean(component.get("d_" + column.name));
			normalzCols[i]	= Util.stringToBoolean(component.get("n_" + column.name));
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
	}

	/**
	 * Gets binary data from VDX server.
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
		params.put("rk", Integer.toString(rk));
		
		// checkout a connection to the database
		Pool<VDXClient> pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client		= pool.checkout();
		if (client == null)
			return;
		
		double TZOffset = Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
		
		// create a map to hold all the channel data
		channelDataMap		= new LinkedHashMap<Integer, GenericDataMatrix>();
		String[] channels	= ch.split(",");
		
		// iterate through each of the selected channeld and place the data in the map
		for (String channel : channels) {
			params.put("ch", channel);
			GenericDataMatrix data = (GenericDataMatrix)client.getBinaryData(params);		
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
	private MatrixRenderer getRightMatrixRenderer(Channel channel, GenericDataMatrix gdm, int displayCount, int dh) throws Valve3Exception {
		
		if (rightUnit == null)
			return null;
		
		MatrixRenderer mr = new MatrixRenderer(gdm.getData(), ranks);
		mr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);

		double yMin = 1E300;
		double yMax = -1E300;
		
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
		mr.createDefaultPointRenderers(leftLines);
		mr.createDefaultLegendRenderer(channelLegendsCols, leftLines);
		
		return mr;
	}

	/**
	 * Initialize MatrixRenderers for left and right axis, adds them to plot
	 * @throws Valve3Exception
	 */
	public void plotData() throws Valve3Exception {
		
		/// calculate how many graphs we are going to build (number of channels)
		compCount			= channelDataMap.size();
		
		// setting up variables to decide where to plot this component
		int displayCount	= 0;
		int dh				= component.getBoxHeight() / compCount;
		
		// get the rank information for this plot
		String rankLegend	= "";
		Rank rank			= ranksMap.get(rk);
		rankLegend			= rank.getCode();
		
		for (int cid : channelDataMap.keySet()) {
			
			// get the relevant information for this channel
			Channel channel			= channelsMap.get(cid);
			GenericDataMatrix data	= channelDataMap.get(cid);
			
			// verify their is something to plot
			if (data == null || data.rows() == 0) {
				continue;
			}
			
			// detrend and normalize the data that the user requested to be detrended		
			for (int i = 0; i < columnsCount; i++) {
				if (detrendCols[i]) { data.detrend(i + 2); }
				if (normalzCols[i]) { data.add(i + 2, -data.mean(i + 2)); }
			}
			
			// set up the legend 
			for (int i = 0; i < legendsCols.length; i++) {
				channelLegendsCols[i] = String.format("%s %s %s", channel.getCode(), rankLegend, legendsCols[i]);
			}
			
			MatrixRenderer leftMR	= getLeftMatrixRenderer(channel, data, displayCount, dh);
			MatrixRenderer rightMR	= getRightMatrixRenderer(channel, data, displayCount, dh);
			v3Plot.getPlot().addRenderer(leftMR);
			if (rightMR != null)
				v3Plot.getPlot().addRenderer(rightMR);
			component.setTranslation(leftMR.getDefaultTranslation(v3Plot.getPlot().getHeight()));
			component.setTranslationType("ty");
			v3Plot.addComponent(component);
			displayCount++;
		}
		
		v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name);
	}
	
	/**
	 * Concrete realization of abstract method. 
	 * Initialize MatrixRenderers for left and right axis
	 * (plot may have 2 different value axis)
	 * Generate PNG image to file with random file name.
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
	 * @return CSV dump of binary data described by given PlotComponent
	 */
	public String toCSV(PlotComponent comp) throws Valve3Exception {
		component	= comp;		
		columnsList	= getColumns(vdxSource, vdxClient);
		
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
