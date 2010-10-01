package gov.usgs.valve3.plotter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Logger;
import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.DefaultFrameDecorator;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.SmartTick;
import gov.usgs.plot.DefaultFrameDecorator.Location;
import gov.usgs.util.Pool;
import gov.usgs.util.UtilException;
import gov.usgs.util.Util;
import gov.usgs.vdx.ExportConfig;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.client.VDXClient.DownsamplingType;
import gov.usgs.vdx.data.Channel;
import gov.usgs.vdx.data.Column;
import gov.usgs.vdx.data.GenericDataMatrix;
import gov.usgs.vdx.data.Rank;
import gov.usgs.vdx.data.ExportData;

/**
 * Abstract class which keeps general functionality for all plotters based on MatrixRenderer.
 *
 * @author Max Kokoulin
 */

public abstract class RawDataPlotter extends Plotter {
	
	
	protected double startTime;
	protected double endTime;
	protected String ch;
	protected boolean isDrawLegend;
	protected int rk;
	public boolean ranks = true;
	protected DownsamplingType downsamplingType = DownsamplingType.NONE;
	protected int downsamplingInterval = 0;
	protected boolean xTickMarks = true;
    protected boolean xTickValues = true;
    protected boolean xUnits = true;
    protected boolean xLabel = false;
    protected boolean yTickMarks = true;
    protected boolean yTickValues = true;
    protected boolean yUnits = true;
    protected boolean yLabel = false;
	
	protected int leftTicks; //count of left ticks
	protected int leftLines; //starting line color
	protected String leftUnit;
	protected String rightUnit;
	protected int compCount;
	protected String shape="l";
	
	protected int columnsCount;
	protected List<Column> columnsList;
	protected String channelLegendsCols[];
	
	protected Map<Integer, String> axisMap;
	protected Map<Integer, Channel> channelsMap;
	protected Map<Integer, Rank> ranksMap;
	
	protected Logger logger;
	
	protected TreeSet<ExportData> csvData;
	protected StringBuffer csvHdrs, csvCmts, csvText;
	protected int csvIndex = 0;
	
	protected boolean removeBias;
	
	protected boolean bypassManipCols[];
	protected boolean doDespike;
	protected double despikePeriod;
	protected boolean doDetrend;
	protected int filterPick;
	protected double filterMin, filterMax, filterPeriod;
	protected int debiasPick;
	protected double debiasValue;
	
	protected boolean forExport;
	
	
	/**
	 * Default constructor
	 */
	public RawDataPlotter() {
		logger		= Logger.getLogger("gov.usgs.vdx");		
		csvCmts = new StringBuffer();
		csvHdrs = new StringBuffer();
	}
	
	/**
	 * Fill those component parameters which are common for all plotters
	 */
	protected void parseCommonParameters(PlotComponent component) throws Valve3Exception {
		// Check for named channels, ranks
		String nameArg = null;
		if ( forExport ) {
			nameArg = component.get( "chNames" );
			if ( nameArg != null ) {
				String[] names = nameArg.split(",");
				int left = names.length;
				ch = null;
				for ( Channel c : channelsMap.values() ) {
					String cname = c.getCode();
					for ( int i=0; i<left; i++ )
						if ( cname.equals(names[i]) ) {
							names[i] = names[left-1];
							if ( ch==null )
								ch = "" + c.getCID();
							else
								ch = ch + "," + c.getCID();
							left--;
							break;
						}
					if ( left == 0 )
						break;
				}
			} else {
				ch = component.getString("ch");
			}
		
			nameArg = component.get( "rkName" );
			if ( nameArg != null ) 
				for ( Rank r : ranksMap.values() ) 
					if ( nameArg.equals(r.getName()) ) {
						component.put( "rk", ""+r.getId() );
						break;
					}
		} else {
			ch = component.getString("ch");
		}
		boolean useColDefaults = true;
		int j = 0;
		if ( columnsList != null ) {
			boolean alreadySet[] = new boolean[columnsList.size()];
			for ( Column c : columnsList ) {
				String cVal = component.get(c.name);
				if ( cVal != null ) {
					c.checked = component.getBoolean(c.name);
					alreadySet[j] = true;
					useColDefaults = false;
				}
				j++;
			}
			j = 0;
			for ( Column c : columnsList ) {
				if ( !alreadySet[j] && !useColDefaults )
					c.checked = false;
				j++;
			}
		}

		endTime = component.getEndTime();
		if (Double.isNaN(endTime))
			if ( forExport )
				endTime = Double.MAX_VALUE;
			else
				throw new Valve3Exception("Illegal end time.");
		
		startTime = component.getStartTime(endTime);
		if (Double.isNaN(startTime))
			throw new Valve3Exception("Illegal start time.");
		try{
			downsamplingType = DownsamplingType.fromString(component.getString("ds"));
			downsamplingInterval = component.getInt("dsInt");
		} catch(Valve3Exception e){
			//Do nothing, default values without downsampling
		}
		if ( !forExport ) {
			try{
				isDrawLegend = component.getBoolean("lg");
			} catch(Valve3Exception e){
				isDrawLegend=true;
			}
			try{
				shape = component.getString("lt");
			} catch(Valve3Exception e){
				shape="l";
			}
			try{
				removeBias = component.getBoolean("rb");
			} catch (Valve3Exception ex){
				removeBias = false;
			}
			try{
				xTickMarks = component.getBoolean("xTickMarks");
			} catch(Valve3Exception e){
				xTickMarks=true;
			}
			try{
				xTickValues = component.getBoolean("xTickValues");
			} catch(Valve3Exception e){
				xTickValues=true;
			}
			try{
				xUnits = component.getBoolean("xUnits");
			} catch(Valve3Exception e){
				xUnits=true;
			}
			try{
				xLabel = component.getBoolean("xLabel");
			} catch(Valve3Exception e){
				xLabel=false;
			}
			try{
				yTickMarks = component.getBoolean("yTickMarks");
			} catch(Valve3Exception e){
				yTickMarks=true;
			}
			try{
				yTickValues = component.getBoolean("yTickValues");
			} catch(Valve3Exception e){
				yTickValues=true;
			}
			try{
				yUnits = component.getBoolean("yUnits");
			} catch(Valve3Exception e){
				yUnits=true;
			}
			try{
				yLabel = component.getBoolean("yLabel");
			} catch(Valve3Exception e){
				yLabel=false;
			}
		}
	}
	
	/**
	 * Used during request of data for this plotter, adds downsampling information to request's parameters
	 */
	protected void addDownsamplingInfo(Map<String, String> params){
		params.put("ds", downsamplingType.toString());
		params.put("dsInt", Integer.toString(downsamplingInterval));
	}
	
	/**
	 * Initialize list of channels for given vdx source
	 * @param source	vdx source name
	 * @param client	vdx name
	 */
	protected static List<Column> getColumns(String source, String client) throws Valve3Exception{
		List<Column> columns;	
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", source);
		params.put("action", "columns");
		Pool<VDXClient> pool	= Valve3.getInstance().getDataHandler().getVDXClient(client);
		VDXClient cl			= pool.checkout();
		List<String> cols = null;
		try{
			cols = cl.getTextData(params);
		}
		catch(UtilException e){
			throw new Valve3Exception(e.getMessage()); 
		}
		pool.checkin(cl);
		columns					= Column.fromStringsToList(cols);
		return columns;
	}
	
	/**
	 * Initialize list of channels for given vdx source
	 * @param source	vdx source name
	 * @param client	vdx name
	 */
	protected static Map<Integer, Channel> getChannels(String source, String client) throws Valve3Exception {
		Map<Integer, Channel> channels;	
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", source);
		params.put("action", "channels");
		Pool<VDXClient> pool	= Valve3.getInstance().getDataHandler().getVDXClient(client);
		VDXClient cl			= pool.checkout();
		List<String> chs		= null;
		try{
			chs	= cl.getTextData(params);
		}
		catch(UtilException e){
			throw new Valve3Exception(e.getMessage()); 
		}
		pool.checkin(cl);
		channels				= Channel.fromStringsToMap(chs);
		return channels;
	}
	
	/**
	 * Initialize list of ranks for given vdx source
	 * @param source	vdx source name
	 * @param client	vdx name
	 */
	protected static Map<Integer, Rank> getRanks(String source, String client) throws Valve3Exception {
		Map<Integer, Rank> ranks;
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", source);
		params.put("action", "ranks");
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(client);
		VDXClient cl = pool.checkout();
		List<String> rks = null;
		try{
			rks = cl.getTextData(params);
		}
		catch(UtilException e){
			throw new Valve3Exception(e.getMessage()); 
		}
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
		mr.createDefaultAxis(xTickMarks?8:0, yTickMarks?8:0, false, ap.allowExpand, yTickValues);
		mr.setXAxisToTime(xTickMarks?8:0, xTickValues);
		if(yUnits){
			mr.getAxis().setLeftLabelAsText(unit);
		}
		if(shape==null){
			mr.createDefaultPointRenderers(component.getColor());
		} else {
			if (shape.equals("l")) {
				mr.createDefaultLineRenderers(component.getColor());
			} else {
				mr.createDefaultPointRenderers(shape.charAt(0), component.getColor());
			}
		}
		if(isDrawLegend) mr.createDefaultLegendRenderer(channelLegendsCols);
		if(mr.getAxis().leftTicks != null){
			leftTicks = mr.getAxis().leftTicks.length;
		}
		if(yLabel){
			DefaultFrameDecorator.addLabel(mr, channel.getCode(), Location.LEFT);
		}
		if (displayCount + 1 == compCount) {
			if(xUnits){
				mr.getAxis().setBottomLabelAsText(component.getTimeZone().getID() + " Time (" + Util.j2KToDateString(startTime+timeOffset, "yyyy MM dd") + " to " + Util.j2KToDateString(endTime+timeOffset, "yyyy MM dd")+ ")");	
			}
			if(xLabel){
				;
			}
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
		if(yTickMarks && yTickValues){
			ar.createRightTickLabels(SmartTick.autoTick(ap.yMin, ap.yMax, leftTicks, false), null);
		}
		mr.setAxis(ar);
		if(yUnits){
			mr.getAxis().setRightLabelAsText(rightUnit);
		}
		if(shape==null){
			mr.createDefaultPointRenderers(leftLines, component.getColor());
		} else{
			if (shape.equals("l")) {
				mr.createDefaultLineRenderers(leftLines, component.getColor());
			} else {
				mr.createDefaultPointRenderers(leftLines, shape.charAt(0), component.getColor());
			}
		}
		if(isDrawLegend) mr.createDefaultLegendRenderer(channelLegendsCols, leftLines);
		return mr;
	}
	
	/**
	 * This function should be overridden in each concrete plotter.
	 * Configure plotter according component parameters.
	 */
	abstract void getInputs(PlotComponent component) throws Valve3Exception;
	/**
	 * This function should be overridden in each concrete plotter.
	 * Request the data from vdx server.
	 */
	abstract void getData(PlotComponent component) throws Valve3Exception;
	
	/**
	 * Format time and data using decFmt (and nullField for empty fields); 
	 * append to csvText 
	 */
	private void addCSVline( Double[][] data, Double time, String decFmt, String nullField ) {
		String line = String.format( "%14.3f,", time ) + Util.j2KToDateString(time);					
		for ( Double[] group : data )
			for ( int i = 1; i < group.length; i++ ) {
				Double v = group[i];
				if ( v != null )
					line += String.format( decFmt, v );
				else
					line += nullField;
			}
		csvText.append(line);
		csvText.append("\n");	
	}
	
	/**
	 * @return CSV dump of binary data described by given PlotComponent
	 */
	public String toCSV(PlotComponent comp) throws Valve3Exception {
		return toCSV(comp, "");
	}

    /**
     * @param comp plot component
     * @param cmt  comment line to add after configured comments
     * @return CSV dump of binary data described by given PlotComponent
     */
	public String toCSV(PlotComponent comp, String cmt) throws Valve3Exception {
		// Get export configuration parameters
		ExportConfig ec = getExportConfig(vdxSource, vdxClient);
		
		if ( !ec.isExportable() )
			throw new Valve3Exception( "Requested export not allowed" );
	
		// Add opening comment line(s) to text
		String[] comments = ec.getComments();
		if ( comments != null )
			for ( String s: comments ) {
				csvCmts.append("#");
				csvCmts.append(s);
				csvCmts.append("\n");
			}
		
		// Add universal comment lines to text
		csvCmts.append( cmt );
		
		// Add the common column headers
		String timeZone = comp.getTimeZone().getID();
		csvHdrs.append("Seconds_since_1970 (");
		csvHdrs.append(timeZone);
		csvHdrs.append("), Date (");
		csvHdrs.append(timeZone);
		csvHdrs.append(")");

		// Fill csvData with data to be exported; also completes csvText
		csvData = new TreeSet<ExportData>();
		csvIndex = 0;
		plot( null, comp );
		csvText = new StringBuffer();
		csvText.append( csvCmts );
		csvCmts = new StringBuffer();
		csvText.append( csvHdrs );
		csvHdrs = new StringBuffer();
		csvText.append("\n");
		
		// currLine is an array of the current row of data from each source, indexed by that source's ID
		Double[][] currLine = new Double[ csvData.size() ][];
		String decFmt = ",%" + ec.getFixedWidth()[0] + "." + ec.getFixedWidth()[1] + "f";
		String nullField = String.format( ",%" + ec.getFixedWidth()[0] + "s", "" );

		if ( currLine.length == 1 ) {
			// Since there's only 1 source, we can just loop through it
			ExportData cd = csvData.first();
			Double[] datum = cd.currExportDatum();
			while ( datum != null ) {
				currLine[0] = datum;
				addCSVline( currLine, datum[0], decFmt, nullField );
				datum = cd.nextExportDatum();
			}
		} else {

			// An array of our data sources, indexed by ID
			ExportData[] sources = new ExportData[ csvData.size() ];
			for ( ExportData cd: csvData ) {
				sources[ cd.exportDataID() ] = cd;
				currLine[ cd.exportDataID() ] = cd.dummyExportDatum();
			}
			
			// prevTime is the time of the last row formatted into csvText
			Double prevTime = null;
			while ( true ) {
				ExportData loED;
				try {
					// Grab the ExportData whose next datum has the earliest time
					loED = csvData.first();
				} catch (Exception e) {
					loED = null;
				}
				
				if ( prevTime != null ) {
					int cmp = -1;
					if ( loED != null ) {
						Double[] l = loED.currExportDatum();
						Double l0 = l[0];
						cmp = prevTime.compareTo(l0);
					}
					if ( cmp < 0 ) {
						// Add the current line to csvText
						addCSVline( currLine, prevTime, decFmt, nullField );
						
						if ( loED == null )
							// No new data; we're done!
							break;
						// "Erase" the current line
						for ( ExportData cd: sources )
							currLine[ cd.exportDataID() ] = cd.dummyExportDatum();
						prevTime = loED.currExportDatum()[0];
					}
				} else if ( loED != null ) {
					// This is our first item
					prevTime = loED.currExportDatum()[0];
				} else {
					throw new Valve3Exception( "No data to export" );
				}
				// Add current item to current line
				currLine[ loED.exportDataID() ] = loED.currExportDatum();
				
				// Remove & add our ExportData back so that it gets placed based on its new data
				csvData.remove( loED );
				if ( loED.nextExportDatum() != null )
					csvData.add( loED );
			}
		}
		String result = csvText.toString();
		csvText = null;
		return result;
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
	
	/**
	 * Fills plotter's data manipulation configuration according component parameters
	 */
	protected void validateDataManipOpts(PlotComponent component) throws Valve3Exception {
		doDespike = Util.stringToBoolean(component.get("despike"));
		if ( doDespike ) {
			despikePeriod = component.getDouble("despike_period");
			if ( Double.isNaN(despikePeriod) )
				throw new Valve3Exception("Illegal/missing period for despike");
		}
		doDetrend = Util.stringToBoolean(component.get("detrend"));
		try {
			filterPick = component.getInt("dmo_fl");
		} catch(Valve3Exception e){
			filterPick = 0;
		}
		if ( filterPick != 0 ) {
			if ( filterPick != 1 ) {
				filterPeriod = component.getDouble("filter_arg1");
				if ( Double.isNaN(filterPeriod) )
					throw new Valve3Exception("Illegal/missing period for filter");
			} else {
				filterMax = component.getDouble("filter_arg1");
				filterMin = component.getDouble("filter_arg2");
				if ( Double.isNaN(filterMax) && Double.isNaN(filterMax) )
					throw new Valve3Exception("Illegal/missing bound(s) for bandpass");
			}
		}
		try {
			debiasPick = component.getInt("dmo_db");
		} catch(Valve3Exception e){
			debiasPick = 0;
		}
		if ( debiasPick == 3 ) {
			debiasValue = component.getDouble("debias_period");
			if ( Double.isNaN(debiasValue) )
				throw new Valve3Exception("Illegal/missing value for bias removal");
		}
	}
}
