package gov.usgs.valve3.plotter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.SmartTick;
import gov.usgs.util.Pool;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.Channel;
import gov.usgs.vdx.data.Column;
import gov.usgs.vdx.data.GenericDataMatrix;
import gov.usgs.vdx.data.Rank;

public abstract class RawDataPlotter extends Plotter {
	
	protected double startTime;
	protected double endTime;
	protected String ch;
	protected int rk;
	public boolean ranks = true;
	
	protected int leftTicks;
	protected int leftLines;
	protected String leftUnit;
	protected String rightUnit;
	protected int compCount;
	protected String shape="l";
	
	protected String channelLegendsCols[];
	
	protected Map<Integer, String> axisMap;
	protected Map<Integer, Channel> channelsMap;
	protected Map<Integer, Rank> ranksMap;
	protected List<Column> columnsList;
	
	protected Logger logger;
	
	/**
	 * Default constructor
	 */
	public RawDataPlotter() {
		logger		= Logger.getLogger("gov.usgs.vdx");		
	}
	
	protected void parseCommonParameters(PlotComponent component) throws Valve3Exception {
		
		ch = component.getString("ch");

		endTime = component.getEndTime();
		if (Double.isNaN(endTime))
			throw new Valve3Exception("Illegal end time.");
		
		startTime = component.getStartTime(endTime);
		if (Double.isNaN(startTime))
			throw new Valve3Exception("Illegal start time.");
		
	}
	
	/**
	 * Initialize list of channels for given vdx source
	 * @param source	vdx source name
	 * @param client	vdx name
	 */
	protected static List<Column> getColumns(String source, String client) {
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
	
	/**
	 * Initialize list of channels for given vdx source
	 * @param source	vdx source name
	 * @param client	vdx name
	 */
	protected static Map<Integer, Channel> getChannels(String source, String client) {
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
	protected static Map<Integer, Rank> getRanks(String source, String client) {
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
	 * Initialize MatrixRenderer for left plot axis
	 * @param index number of column to plot inside renderer. -1 value means we need to render all columns from gdm matrix.
	 * @throws Valve3Exception
	 */
	protected MatrixRenderer getLeftMatrixRenderer(PlotComponent component, Channel channel, GenericDataMatrix gdm, int displayCount, int dh, int index, String unit) throws Valve3Exception {	
		double timeOffset = component.getOffset(startTime);
		MatrixRenderer mr = new MatrixRenderer(gdm.getData(), ranks);
		mr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
		mr.setAllVisible(false);
		AxisParameters ap = new AxisParameters("L", axisMap, gdm, index, component, mr);
		mr.setExtents(startTime+timeOffset, endTime+timeOffset, ap.yMin, ap.yMax);	
		mr.createDefaultAxis(8, 8, false, ap.allowExpand);
		mr.setXAxisToTime(8);
		mr.getAxis().setLeftLabelAsText(unit);
		if(shape==null){
			mr.createDefaultPointRenderers();
		} else {
			if (shape.equals("l")) {
				mr.createDefaultLineRenderers();
			} else {
				mr.createDefaultPointRenderers(shape.charAt(0));
			}
		}
		mr.createDefaultLegendRenderer(channelLegendsCols);
		leftTicks = mr.getAxis().leftTicks.length;
		
		if (displayCount + 1 == compCount) {
			mr.getAxis().setBottomLabelAsText("Time (" + component.getTimeZone().getID()+ ")");	
		}
		return mr;
	}

	/**
	 * Initialize MatrixRenderer for right plot axis
	 * @param index number of column to plot inside renderer. -1 value means we need to render all columns from gdm matrix.
	 * @throws Valve3Exception
	 */
	protected MatrixRenderer getRightMatrixRenderer(PlotComponent component, Channel channel, GenericDataMatrix gdm, int displayCount, int dh, int index) throws Valve3Exception {
		
		if (rightUnit == null)
			return null;
		double timeOffset = component.getOffset(startTime);
		MatrixRenderer mr = new MatrixRenderer(gdm.getData(), ranks);
		mr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
		mr.setAllVisible(false);
		AxisParameters ap = new AxisParameters("R", axisMap, gdm, index, component, mr);
		mr.setExtents(startTime+timeOffset, endTime+timeOffset, ap.yMin, ap.yMax);
		AxisRenderer ar = new AxisRenderer(mr);
		ar.createRightTickLabels(SmartTick.autoTick(ap.yMin, ap.yMax, leftTicks, false), null);
		// ar.createRightTickLabels(SmartTick.autoTick(yMin, yMax, 8, allowExpand), null);
		mr.setAxis(ar);
		mr.getAxis().setRightLabelAsText(rightUnit);
		if(shape==null){
			mr.createDefaultPointRenderers(leftLines);
		} else{
			if (shape.equals("l")) {
				mr.createDefaultLineRenderers(leftLines);
			} else {
				mr.createDefaultPointRenderers(leftLines, shape.charAt(0));
			}
		}
		mr.createDefaultLegendRenderer(channelLegendsCols, leftLines);
		return mr;
	}
	
	abstract void getInputs(PlotComponent component) throws Valve3Exception;
	abstract void getData(PlotComponent component) throws Valve3Exception;
	
	/**
	 * @return CSV dump of binary data described by given PlotComponent
	 */
	public String toCSV(PlotComponent comp) throws Valve3Exception {
	
		getInputs(comp);
		getData(comp);
		
		// return data.toCSV();
		return "NOT IMPLEMENTED YET FOR MULTIPLE STATIONS";
	}	
	
	class AxisParameters {
		double yMin = 1E300;
		double yMax = -1E300;
		double buff;
		boolean allowExpand = true;
		double[] ys=null;
		
		public AxisParameters(String axisType, Map<Integer, String> axisMap, GenericDataMatrix gdm, int index, PlotComponent component, MatrixRenderer mr) throws Valve3Exception{
			if (!(axisType.equals("L") || axisType.equals("R"))) 
				throw new Valve3Exception("Illegal axis type: " + axisType);
			if(index==-1){
				for (int i = 0; i < axisMap.size(); i++) {
					setParameters(axisType, axisMap.get(i), gdm, i, component, mr);
				}
			} else {
				setParameters(axisType, axisMap.get(index), gdm, index, component, mr);
			}
		}
		
		private void setParameters(String axisType, String mapAxisType, GenericDataMatrix gdm, int index, PlotComponent component, MatrixRenderer mr) throws Valve3Exception {
			if (!(mapAxisType.equals("L") || mapAxisType.equals("R") || mapAxisType.equals(""))) 
				throw new Valve3Exception("Illegal axis type in axis map: " + mapAxisType);
			if (mapAxisType.equals(axisType) || isPlotComponentsSeparately()){
				if (mapAxisType.equals(axisType)){
					mr.setVisible(index, true);
				}
				if (component.isAutoScale("ys"+axisType)) {
					yMin	= Math.min(yMin, gdm.min(index + 2));
					yMax	= Math.max(yMax, gdm.max(index + 2));
					buff	= (yMax - yMin) * 0.05;
					yMin	= yMin - buff;
					yMax	= yMax + buff;
				} else {
					ys = component.getYScale("ys"+axisType, yMin, yMax);
					yMin = ys[0];
					yMax = ys[1];
					allowExpand = false;
					if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
						throw new Valve3Exception("Illegal axis values.");
				}
			}
		}
	}
}
