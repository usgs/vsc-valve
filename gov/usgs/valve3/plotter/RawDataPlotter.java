package gov.usgs.valve3.plotter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.Vector;
import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.DefaultFrameDecorator;
import gov.usgs.plot.LegendRenderer;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.PlotException;
import gov.usgs.plot.SmartTick;
import gov.usgs.plot.DefaultFrameDecorator.Location;
import gov.usgs.util.Pool;
import gov.usgs.util.Time;
import gov.usgs.util.Util;
import gov.usgs.vdx.ExportConfig;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.client.VDXClient.DownsamplingType;
import gov.usgs.vdx.data.Channel;
import gov.usgs.vdx.data.Column;
import gov.usgs.vdx.data.GenericDataMatrix;
import gov.usgs.vdx.data.Rank;
import gov.usgs.vdx.data.ExportData;
import gov.usgs.vdx.data.SuppDatum;

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
	public boolean ranks			= true;
	protected boolean xTickMarks	= true;
    protected boolean xTickValues	= true;
    protected boolean xUnits		= true;
    protected boolean xLabel		= false;
    protected boolean yTickMarks	= true;
    protected boolean yTickValues	= true;
    protected boolean yUnits		= true;
    protected boolean yLabel		= false;
    
	protected int downsamplingInterval = 0;
	protected DownsamplingType downsamplingType = DownsamplingType.NONE;
	
	 //count of left ticks
	protected int leftTicks = 0;
	
	 //starting line color
	protected int leftLines;
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
	protected StringBuffer csvText;
	protected Map<String,String> csvCmtBits;
	protected Vector<String[]> csvHdrs;
	protected int csvIndex = 0;
	
	protected boolean bypassCols[];
	protected boolean accumulateCols[];
	protected boolean doAccumulate;
	protected boolean doDespike;
	protected double despikePeriod;
	protected boolean doDetrend;
	protected int filterPick;
	protected double filterMin, filterMax, filterPeriod;
	protected int debiasPick;
	protected double debiasValue;
	protected boolean exportAll = false;
	
	protected String outputType;
	protected boolean inclTime;
	protected SuppDatum sd_data[];
	protected String scnl[];
	protected double samplingRate = 0.0;
	protected String dataType = null;
	
	protected double timeOffset;
	protected String timeZoneID;
	protected String dateFormatString;
		
	/**
	 * Default constructor
	 */
	public RawDataPlotter() {
		logger	= Logger.getLogger("gov.usgs.vdx");		
		csvCmtBits = new LinkedHashMap<String,String>();
		csvHdrs = new Vector<String[]>();
		dateFormatString	= "yyyy-MM-dd HH:mm:ss";
	}
	
	/**
	 * Fill those component parameters which are common for all plotters
     * @param component plot component
     * @throws Valve3Exception
	 */
	protected void parseCommonParameters(PlotComponent comp) throws Valve3Exception {
		
		// declare variables
		String nameArg = null;
		
		// Check for named ranks, outputAll
		if ( forExport ) {
			exportAll = comp.getBoolean("outputAll");
			outputType	= comp.get( "o" );
			inclTime	= outputType.equals( "csv" )||outputType.equals("xml")||outputType.equals("json");
		
			nameArg = comp.get( "rkName" );
			if ( nameArg != null ) {
			    boolean found = false;
				for ( Rank r : ranksMap.values() ) 
					if ( nameArg.equals(r.getName()) ) {
						comp.put( "rk", ""+r.getId() );
						found = true;
						break;
					}
				if ( !found )
				    throw new Valve3Exception( "Unknown rank name :" + nameArg );
			}
		}
		
		nameArg		= comp.get( "chNames" );
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
			ch = comp.getString("ch");
		}
		
		// column parameters
		boolean useColDefaults = true;
		int j = 0;
		if ( columnsList != null ) {
			boolean alreadySet[] = new boolean[columnsList.size()];
			for ( Column c : columnsList ) {
				String cVal = comp.get(c.name);
				if ( (forExport && exportAll) || cVal != null ) {
					if ( forExport && exportAll )
						c.checked = true;
					else
						c.checked = comp.getBoolean(c.name);
					alreadySet[j] = true;
					useColDefaults = false;
				}
				j++;
			}
			j = 0;
			for ( Column c : columnsList ) {
				if ( !alreadySet[j] && !useColDefaults ) {
					c.checked = false;
				}
				j++;
			}
		}

		// end time
		endTime = comp.getEndTime();
		if (Double.isNaN(endTime)) {
			if ( forExport ) {
				endTime = Double.MAX_VALUE;
			} else {
				throw new Valve3Exception("Illegal end time.");
			}
		}
		
		// start time
		startTime = comp.getStartTime(endTime);
		if (Double.isNaN(startTime)) {
			throw new Valve3Exception("Illegal start time.");
		}
		
		// DST and time zone parameters
		timeOffset	= comp.getOffset(startTime);
		timeZoneID	= comp.getTimeZone().getID();
		
		try {
			downsamplingType = DownsamplingType.fromString(comp.getString("ds"));
			downsamplingInterval = comp.getInt("dsInt");
		} catch (Valve3Exception e) {
			//Do nothing, default values without downsampling
		}
		
		// plot related parameters
		if ( !forExport ) {
			try{
				isDrawLegend = comp.getBoolean("lg");
			} catch(Valve3Exception e){
				isDrawLegend=true;
			}
			try{
				shape = comp.getString("linetype");
			} catch(Valve3Exception e){
				shape="l";
			}
			try{
				xTickMarks = comp.getBoolean("xTickMarks");
			} catch(Valve3Exception e){
				xTickMarks=true;
			}
			try{
				xTickValues = comp.getBoolean("xTickValues");
			} catch(Valve3Exception e){
				xTickValues=true;
			}
			try{
				xUnits = comp.getBoolean("xUnits");
			} catch(Valve3Exception e){
				xUnits=true;
			}
			try{
				xLabel = comp.getBoolean("xLabel");
			} catch(Valve3Exception e){
				xLabel=false;
			}
			try{
				yTickMarks = comp.getBoolean("yTickMarks");
			} catch(Valve3Exception e){
				yTickMarks=true;
			}
			try{
				yTickValues = comp.getBoolean("yTickValues");
			} catch(Valve3Exception e){
				yTickValues=true;
			}
			try{
				yUnits = comp.getBoolean("yUnits");
			} catch(Valve3Exception e){
				yUnits=true;
			}
			try{
				yLabel = comp.getBoolean("yLabel");
			} catch(Valve3Exception e){
				yLabel=false;
			}
		}
	}
	
	/**
	 * Used during request of data for this plotter, adds downsampling information to request's parameters
	 * @param params parameters to add to
	 */
	protected void addDownsamplingInfo(Map<String, String> params){
		params.put("ds", downsamplingType.toString());
		params.put("dsInt", Integer.toString(downsamplingInterval));
	}
	
	/**
	 * Initialize list of columns for given vdx source
	 * @param vdxSource	vdx source name
	 * @param vdxClient	vdx client name
	 * @return list of columns
	 * @throws Valve3Exception
	 */
	protected static List<Column> getColumns(String vdxSource, String vdxClient) throws Valve3Exception {
		
		// initialize variables
		List<String> stringList = null;
		List<Column> columnList	= null;
		Pool<VDXClient> pool	= null;
		VDXClient client		= null;
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "columns");
		
		// checkout a connection to the database
		pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		if (pool != null) {
			client	= pool.checkout();		
			try {
				stringList	= client.getTextData(params);
			} catch (Exception e) {
				stringList	= null;
			} finally {
				pool.checkin(client);
			}		
			
			// if data was collected
			if (stringList != null) {
				columnList	= Column.fromStringsToList(stringList);
			}
		}
		
		return columnList;
	}
	
	/**
	 * Initialize list of channels for given vdx source
	 * @param vdxSource	vdx source name
	 * @param vdxClient	vdx client name
	 * @return map of ids to channels
	 * @throws Valve3Exception
	 */
	protected static Map<Integer, Channel> getChannels(String vdxSource, String vdxClient) throws Valve3Exception {
		
		// initialize variables
		List<String> stringList 			= null;
		Map<Integer, Channel> channelMap	= null;
		Pool<VDXClient> pool	= null;
		VDXClient client		= null;
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "channels");
		
		// checkout a connection to the database
		pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		if (pool != null) {
			client	= pool.checkout();		
			try {
				stringList	= client.getTextData(params);
			} catch (Exception e) {
				stringList	= null;
			} finally {
				pool.checkin(client);
			}		
			
			// if data was collected
			if (stringList != null) {
				channelMap	= Channel.fromStringsToMap(stringList);
			}
		}
		
		return channelMap;
	}
	
	/**
	 * Initialize list of ranks for given vdx source
	 * @param vdxSource	vdx source name
	 * @param vdxClient	vdx client name
	 * @return map of ids to ranks
	 * @throws Valve3Exception
	 */
	public static Map<Integer, Rank> getRanks(String vdxSource, String vdxClient) throws Valve3Exception {
		
		// initialize variables
		List<String> stringList 	= null;
		Map<Integer, Rank> rankMap	= null;
		Pool<VDXClient> pool		= null;
		VDXClient client			= null;
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "ranks");
		
		// checkout a connection to the database
		pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		if (pool != null) {
			client	= pool.checkout();
			try {
				stringList	= client.getTextData(params);
			} catch (Exception e) {
				stringList	= null;
			} finally {
				pool.checkin(client);
			}
			
			// if data was collected
			if (stringList != null) {
				rankMap	= Rank.fromStringsToMap(stringList);
			}
		}
		
		return rankMap;
	}
	
	/**
	 * Initialize list of azimuths for given vdx source
	 * @param vdxSource	vdx source name
	 * @param vdxClient	vdx client name
	 * @return map of ids to azimuths
	 * @throws Valve3Exception
	 */
	protected static Map<Integer, Double> getAzimuths(String vdxSource, String vdxClient) throws Valve3Exception {
		
		// initialize variables
		List<String> stringList 		= null;
		Map<Integer, Double> azimuthMap	= null;	
		Pool<VDXClient> pool			= null;
		VDXClient client				= null;
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "azimuths");
		
		// checkout a connection to the database
		pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		if (pool != null) {
			client	= pool.checkout();		
			try {
				stringList	= client.getTextData(params);
			} catch (Exception e) {
				stringList	= null;
			} finally {
				pool.checkin(client);
			}
			
			// if data was collected
			if (stringList != null) {
				azimuthMap	= new LinkedHashMap<Integer, Double>();
				for (int i = 0; i < stringList.size(); i++) {
					String[] temp = stringList.get(i).split(":");
					azimuthMap.put(Integer.valueOf(temp[0]), Double.valueOf(temp[1]));
				}
			}
		}
		
		return azimuthMap;
	}
	
	/**
	 * Initialize MatrixRenderer for left plot axis
     * @param component plot component
     * @param channel Channel
     * @param gdm data matrix
     * @param currentComp 
     * @param compBoxHeight
	 * @param index number of column to plot inside renderer. -1 value means we need to render all columns from gdm matrix.
	 * @param unit axis label
	 * @return renderer
	 * @throws Valve3Exception
	 */
	protected MatrixRenderer getLeftMatrixRenderer(PlotComponent comp, Channel channel, GenericDataMatrix gdm, 
			int currentComp, int compBoxHeight, int index, String unit) throws Valve3Exception {
		
		// setup the matrix renderer with this data
		MatrixRenderer mr = new MatrixRenderer(gdm.getData(), ranks);
		mr.setLocation(comp.getBoxX(), comp.getBoxY() + (currentComp - 1) * compBoxHeight, comp.getBoxWidth(), compBoxHeight - 16);
		mr.setAllVisible(false);
		
		// define the axis and the extents
		AxisParameters ap = new AxisParameters("L", axisMap, gdm, index, comp, mr);
		mr.setExtents(startTime+timeOffset, endTime+timeOffset, ap.yMin, ap.yMax);
		
		// x axis decorations
		if (currentComp == compCount) {	
			mr.createDefaultAxis(8, 8, xTickMarks, yTickMarks, false, ap.allowExpand, xTickValues, yTickValues);
			mr.setXAxisToTime(8, xTickMarks, xTickValues);
			if (xUnits) {
				mr.getAxis().setBottomLabelAsText(timeZoneID + " Time (" + Util.j2KToDateString(startTime+timeOffset, dateFormatString) + " to " + Util.j2KToDateString(endTime+timeOffset, dateFormatString)+ ")");	
			}
			if (xLabel) { }
		
		// don't display xTickValues for top and middle components, only for bottom component
		} else {	
			mr.createDefaultAxis(8, 8, xTickMarks, yTickMarks, false, ap.allowExpand, false, yTickValues);
			mr.setXAxisToTime(8, xTickMarks, false);
		}
		
		// y axis decorations
		if (yUnits) {
			mr.getAxis().setLeftLabelAsText(unit);
		}
		if (yLabel){
			DefaultFrameDecorator.addLabel(mr, channel.getCode(), Location.LEFT);
		}
		if (mr.getAxis().leftTicks != null) {
			leftTicks = mr.getAxis().leftTicks.length;
		}
		
		// data decorations
		if (shape==null) {
			mr.createDefaultPointRenderers(comp.getColor());
		} else {
			if (shape.equals("l")) {
				mr.createDefaultLineRenderers(comp.getColor());
			} else {
				mr.createDefaultPointRenderers(shape.charAt(0), comp.getColor());
			}
		}
		
		// legend decorations
		if (isDrawLegend) {
			mr.createDefaultLegendRenderer(channelLegendsCols);
		}
		
		return mr;
	}

	/**
	 * Initialize MatrixRenderer for right plot axis
     * @param component plot component
     * @param channel Channel
     * @param gdm data matrix
     * @param currentComp 
     * @param compBoxHeight
	 * @param index number of column to plot inside renderer. -1 value means we need to render all columns from gdm matrix.
	 * @return renderer
	 * @throws Valve3Exception
	 */
	protected MatrixRenderer getRightMatrixRenderer(PlotComponent comp, Channel channel, GenericDataMatrix gdm, int currentComp, int compBoxHeight, int index, LegendRenderer leftLegendRenderer) throws Valve3Exception {
		
		if (rightUnit == null)
			return null;
		MatrixRenderer mr = new MatrixRenderer(gdm.getData(), ranks);
		mr.setLocation(comp.getBoxX(), comp.getBoxY() + (currentComp - 1) * compBoxHeight, comp.getBoxWidth(), compBoxHeight - 16);
		mr.setAllVisible(false);
		AxisParameters ap = new AxisParameters("R", axisMap, gdm, index, comp, mr);
		mr.setExtents(startTime+timeOffset, endTime+timeOffset, ap.yMin, ap.yMax);
		AxisRenderer ar = new AxisRenderer(mr);
		if(yTickValues){
			ar.createRightTickLabels(SmartTick.autoTick(mr.getMinY(), mr.getMaxY(), leftTicks, false), null);
		}
		mr.setAxis(ar);
		if(yUnits){
			mr.getAxis().setRightLabelAsText(rightUnit);
		}
		if(shape==null){
			mr.createDefaultPointRenderers(leftLines, comp.getColor());
		} else{
			if (shape.equals("l")) {
				mr.createDefaultLineRenderers(leftLines, comp.getColor());
			} else {
				mr.createDefaultPointRenderers(leftLines, shape.charAt(0), comp.getColor());
			}
		}
		if(isDrawLegend) {
			mr.addToLegendRenderer(leftLegendRenderer, channelLegendsCols, leftLines);
		}
		return mr;
	}
	
	/**
	 * This function should be overridden in each concrete plotter.
	 * Configure plotter according component parameters.
     * @param component plot component
     * @throws Valve3Exception
	 */
	abstract void getInputs(PlotComponent comp) throws Valve3Exception;
	
	/**
	 * This function should be overridden in each concrete plotter.
	 * Request the data from vdx server.
     * @param component plot component
     * @throws Valve3Exception
	 */
	abstract void getData(PlotComponent comp) throws Valve3Exception;
	
	/**
	 * 
	 * @return "column i contains single-character strings"
	 */
	boolean isCharColumn( int i ) {
		return false;
	}
	
	/**
	 * Format time and data using decFmt (and nullField for empty fields); 
	 * append to csvText 
	 * @param data data for line
	 * @param time time for data
	 * @param decFmt how to format numbers
	 * @param nullField what to use for missing fields
	 */
	private void addCSVline( Double[][] data, Double time, String decFmt, String nullField ) {
		String line;
		String firstDecFmt, nextDecFmt;
		if ( inclTime ) {
			line = String.format( "%14.3f,", Util.j2KToEW(time) ) + Util.j2KToDateString(time);	
			firstDecFmt = ","+decFmt;
		} else {
			line = "";
			firstDecFmt = decFmt;
		}
		nextDecFmt = ","+decFmt;
		for ( Double[] group : data )
			for ( int i = 1; i < group.length; i++ ) {
				Double v = group[i];
				if ( v == null )
					line += nullField;
				else if ( isCharColumn(i) )
					if ( v==Double.NaN || v>255 )
						line += ", ";
					else
						line += ","+new Character((char)(v.intValue()));
				else
					line += String.format( i==1 ? firstDecFmt : nextDecFmt, v );
			}
		csvText.append(line);
		csvText.append("\n");	
	}
	
	/**
	 * Format time and data using decFmt (and nullField for empty fields); 
	 * append to csvText (in XML format)
	 * @param data data for line
	 * @param time time for data
	 * @param decFmt how to format numbers
	 * @param pos line number
	 * @param timeZone name of time zone
	 * @param rank Default rank
	 */
	private void addXMLline( Double[][] data, Double time, String decFmt, int pos, String timeZone, String rank ) {
		boolean hasChannels = (csvHdrs.get(1)[2] != null);	// Export has channel information
		String line;										// Export line being added
		
		/* If first line, add header tag */
		if ( pos == 1 ) {								
			line = "\t<DATA>\n";
		} else
			line = "";
		/* Tag for a row of data */
		line += String.format( "\t\t<ROW pos=\"%d\">\n", pos );	
		if ( inclTime ) {
			line += String.format( "\t\t\t<EPOCH>%1.3f</EPOCH>\n\t\t\t<TIMESTAMP>%s</TIMESTAMP>\n", Util.j2KToEW(time), Util.j2KToDateString(time) );
			line += "\t\t\t<TIMEZONE>" + timeZone + "</TIMEZONE>\n";
		}
		String channel = "";		// Channel name
		String tab = "\t\t\t";		// Indent for contents of a row
		int hdr_idx = 1;			// Current column of export
		boolean hasRank = ( !rank.equals("") );		// Export has rank information
		
		for ( Double[] group : data )
			for ( int i = 1; i < group.length; i++ ) {
				Double v = group[i];		// Actual exported data value
				hdr_idx++;
				String[] hdr = csvHdrs.get(hdr_idx);	// Info about data value
				String tag = hdr[3];					// Tag for data value
				boolean showRank = false;				// Row has rank info to show
				if ( hasChannels ) {
					if ( !channel.equals( hdr[2] ) ) {
						if ( i>1 )
							line += "\t\t\t</CHANNEL>\n";
						channel = hdr[2];
						line += "\t\t\t<CHANNEL>\n\t\t\t\t<code>" + channel + "</code>\n";
						tab = "\t\t\t\t";
						showRank = true;
					}
				} else
					showRank = (i==1);
				if ( showRank ) {
					if ( hdr[1] != null )
						line += tab + "<rank>" + hdr[1] + "</rank>\n";
					else if ( hasRank )
						line += tab + "<rank>" + rank + "</rank>\n";
				}
				if ( v != null ) {
					line += tab + "<"+tag+">";
					if ( isCharColumn(i) )
						if ( v==Double.NaN || v>255 )
							;
						else
							line += new Character((char)(v.intValue()));
					else
						line += String.format( decFmt, v );
					line += "</"+tag+">\n";
				}
			}
		if ( hasChannels )
			line += "\t\t\t</CHANNEL>\n";
		csvText.append(line);
		csvText.append("\t\t</ROW>\n");	
	}
	
	/**
	 * Format time and data using decFmt (and nullField for empty fields); 
	 * append to csvText (in JSON format)
	 * @param data data for line
	 * @param time time for data
	 * @param decFmt how to format numbers
	 * @param pos line number
	 * @param timeZone name of time zone
	 * @param rank Default rank
	 */
	private void addJSONline( Double[][] data, Double time, String decFmt, int pos, String timeZone, String rank  ) {
		boolean hasChannels = (csvHdrs.get(1)[2] != null);	// Export has channel information
		String line;										// Export line being added
		
		/* If first line, add header tag */
		if ( pos == 1 ) {
			line = "\t\"data\":[\n";
		} else
			line = ",\n";
		line += "\t\t{";	
		if ( inclTime ) {
			line += String.format( "\"EPOCH\":%1.3f,\"TIMESTAMP\":\"%s\",", Util.j2KToEW(time), Util.j2KToDateString(time) );	
			line += "\"TIMEZONE\":\"" + timeZone + "\"" + (hasChannels ? ",\n" : "");
		} 
		
		if ( hasChannels )
			line += "\t\t\"CHANNELS\":[\n";
		String channel = "";
		int hdr_idx = 1;			// Current column of export
		boolean hasRank = ( !rank.equals("") );		// Export has rank information
		for ( Double[] group : data ) {
			if ( hdr_idx != 1 && hasChannels )
				line += ",\n";
			for ( int i = 1; i < group.length; i++ ) {
				Double v = group[i];
				hdr_idx++;
				String[] hdr = csvHdrs.get(hdr_idx);	// Info about data value
				String tag = hdr[3];					// Tag for data value
				boolean showRank = false;				// Row has rank info to show
				if ( hasChannels ) {
					if ( !channel.equals( hdr[2] ) ) {
						channel = hdr[2];
						line += "\t\t\t{\"code\":\"" + channel + "\"";
						showRank = true;
					}
				} else
					showRank = (i==1);
				if ( showRank )
					if ( hdr[1] != null )
						line += ",\n\t\t\t\"rank\":\"" + hdr[1] + "\"";
					else if ( hasRank )
						line += ",\n\t\t\t\"rank\":\"" + rank + "\"";

				if ( v != null ) {
					line += String.format( ",\n\t\t\t\"%s\":", tag );
					if ( isCharColumn(i) )
						if ( v==Double.NaN || v>255 )
							line += "\"\"";
						else
							line += "\"" + new Character((char)(v.intValue())) + "\"";
					else
						line += String.format( decFmt, v );
				}
			}
			line += "}";
		}
		csvText.append(line);
		if ( hasChannels )
			csvText.append("\n\t\t]}");	
	}
	
	private int vax_order = 0;
	//private int enc_fmt = 1; // 16-bit ints
	private int enc_fmt = 3; // 32-bit ints
	//private int enc_fmt = 5; // IEEE double
	private int drl_add = enc_fmt==3 ? 2 : (enc_fmt == 1 ? 1 : 3);

	private void writeShort( OutputStream out, int val ) throws IOException {
		out.write( val % 256 );
		out.write( val / 256 );
	}
	
	private void writeDouble( OutputStream out, double val ) throws IOException {
		int i;
		if ( enc_fmt == 5 ) {
			long lval = Double.doubleToRawLongBits(val);
			if ( vax_order == 0 )
				for ( i=0; i<8; i++ )
					out.write( (int)((lval >> (8*i)) & 0x00ffL) );
			else
				for ( i=7; i>=0; i-- )
					out.write( (int)((lval >> (8*i)) & 0x00ffL) );
		} else {
			Double dVal = new Double(val);
			int ival = (enc_fmt == 1 ? dVal.shortValue() : dVal.intValue());
			int hi = (enc_fmt == 1 ? 2 : 4);
			if ( vax_order == 0 )
				for ( i=0; i<hi; i++ )
					out.write( (ival >> (8*i)) & 0x00ff );
			else
				for ( i=hi-1; i>=0; i-- )
					out.write( (ival >> (8*i)) & 0x00ff );
		}	
	}

    /**
     * Yield contents in an export format
     * @param comp plot component
     * @param cmtBits  comment info to add after configured comments
     * @param seedOut stream to write seed data to
     * @return export of binary data described by given PlotComponent
     * @throws Valve3Exception
     */
	public String toExport(PlotComponent comp, Map<String,String> cmtBits, OutputStream seedOut) throws Valve3Exception {
		
		// Get export configuration parameters
		ExportConfig ec = getExportConfig(vdxSource, vdxClient);
		outputType = comp.get( "o" );
		boolean outToCSV = outputType.equals( "csv" );
		boolean outToXML = outputType.equals( "xml" );
		boolean outToJSON = outputType.equals( "json" );
		Vector<String> cmtLines = new Vector<String>();
		inclTime = outToCSV || outToXML || outToJSON;
		
		if ( !ec.isExportable() )
			throw new Valve3Exception( "Requested export not allowed" );
	
		// Get opening comment line(s)
		String[] comments = ec.getComments();
		if ( comments == null )
				comments = new String[]{};
		
		// Add the common column headers
		String timeZone = comp.getTimeZone().getID();
		if ( inclTime ) {
			String[] h1 = { null, null, null, "Epoch"};
			String[] h2 = { null, null, null, "Date"};
			csvHdrs.add( h1 );
			csvHdrs.add( h2 );
		}

		// Fill csvData with data to be exported; also completes csvText
		csvData = new TreeSet<ExportData>();
		csvIndex = 0;
		try{
			plot( null, comp );
		} catch (PlotException e){
			logger.severe(e.getMessage());
		}
		String rank = "";
		String rowTimeZone = "";
		if ( cmtBits != null ) {
			cmtLines.add( "reqtime=" + cmtBits.get("reqtime"));
			cmtLines.add( "URL=" + cmtBits.get("URL"));
			cmtLines.add( "source=" + cmtBits.get("source") );
			cmtLines.add( "st=" + cmtBits.get("st") + ", et=" + cmtBits.get("et"));
			rank = cmtBits.get("rank");
			rowTimeZone = cmtBits.get( "timezone" );
		}
		if ( csvCmtBits.containsKey("sr") )
			cmtLines.add( "sr=" + csvCmtBits.get("sr") );
		if ( csvCmtBits.containsKey("datatype"))
			cmtLines.add( "datatype=" + csvCmtBits.get("datatype"));
		csvText = new StringBuffer();
		Vector<String> myCmtLines = cmtLines;
		if ( myCmtLines == null )
			myCmtLines = new Vector<String>();
		
		if ( outToCSV ) {
			for ( String comment: comments )
				csvText.append( "#" + comment + "\n");
			for ( String comment: myCmtLines )
				csvText.append( "#" + comment + "\n" );
			StringBuffer hdrLine = new StringBuffer();
			boolean first = true;
			for ( String[] s: csvHdrs ) {
				String hdr;
				if ( s[2] != null )
					hdr = s[2] + "_" + s[3];
				else
					hdr = s[3];
				if ( first ) {
					hdrLine.append( hdr );
					first = false;
				} else 
					hdrLine.append( "," + hdr );
			}
			csvText.append( hdrLine );
			csvHdrs = new Vector<String[]>();
			csvText.append("\n");
		} 
		if ( outToXML ) {
			csvText.append( "<VALVE_XML>\n\t<COMMENTS>\n" );
			int i = 1;
			for ( String comment: comments ) {
				csvText.append( "\t\t<COMMENTLINE pos=\"" + i + "\">" + comment.replaceAll("&","&amp;") + "</COMMENTLINE>\n");
				i++;
			}
			for ( String comment: myCmtLines ) {
				csvText.append( "\t\t<COMMENTLINE pos=\"" + i + "\">" + comment.replaceAll("&","&amp;") + "</COMMENTLINE>\n");
				i++;
			}
			csvText.append( "\t</COMMENTS>\n" );
		} 
		if ( outToJSON ) {
			csvText.append("{\"valve-json\":\n\t{\"comments\":");
			String sep  = "[\n\t\t\"";
			for ( String comment: comments ) {
				csvText.append( sep + comment );
				sep = "\",\n\t\t\"";
			}
			for ( String comment: myCmtLines ) {
				csvText.append( sep + comment );
				sep = "\",\n\t\t\"";
			}
			if ( sep.charAt(0) == '[' )
				csvText.append( "[],\n" );
			else
				csvText.append( "\"],\n" );
		}
		csvCmtBits = new LinkedHashMap<String,String>();
		
		// currLine is an array of the current row of data from each source, indexed by that source's ID
		Double[][] currLine = new Double[ csvData.size() ][];
		String decFmt = "%" + ec.getFixedWidth()[0] + "." + ec.getFixedWidth()[1] + "f";
		String jxDecFmt = "%1." + ec.getFixedWidth()[1] + "f";
		String nullField = String.format( ",%" + ec.getFixedWidth()[0] + "s", "" );

		if ( seedOut != null ) {
			try {
				// We're writing data to a miniseed file
				ExportData cd = csvData.first();
				Double[] datum = cd.currExportDatum();

				int sample_count = cd.count();
				int valsLeft = sample_count; //samples
				int valsInBlockette = 4096;
				int drl = 12;
				int blockette_count = 0;
				while ( valsLeft > 0 ) {
					while ( valsLeft < valsInBlockette ) {
						drl--;
						valsInBlockette /= 2;
					}
					valsLeft -= valsInBlockette;
					blockette_count++;
				}
				seedOut.write( "000000".getBytes() ); // seq nbr
				seedOut.write( "D".getBytes() ); // QC
				seedOut.write( 32 ); //reserved
				
				String scnl_out;
				// These have to be extracted from source
				if ( scnl != null ) 
					scnl_out = String.format( "%-5s  %-3s%-2s", scnl[0], scnl[1], scnl[2] );
				else 
					scnl_out = "            ";
				seedOut.write( scnl_out.getBytes() );
				
				// These get extracted from start time
				Date jd = Util.j2KToDate( datum[0] );
				Calendar cal = new GregorianCalendar();
				cal.setTimeZone( TimeZone.getTimeZone( timeZone ) );
				cal.setTime( jd );
				
				writeShort( seedOut, cal.get( Calendar.YEAR ) ); // year
				writeShort( seedOut, cal.get( Calendar.DAY_OF_YEAR  ) ); // julian day
				valsLeft = cal.get( Calendar.HOUR_OF_DAY );
				seedOut.write( valsLeft ); // hour
				valsLeft = cal.get( Calendar.MINUTE );
				seedOut.write( valsLeft ); // minute
				valsLeft = cal.get( Calendar.SECOND );
				seedOut.write( valsLeft ); // second
				seedOut.write( 0 ); // unused
				valsLeft = cal.get( Calendar.MILLISECOND );
				writeShort( seedOut, valsLeft ); // fraction
				
				// These get extracted from source
				writeShort( seedOut, sample_count ); // # samples
				valsLeft = (int)(Math.floor(samplingRate));
				writeShort( seedOut, valsLeft ); // sampling rate factor
				writeShort( seedOut, 1 ); // sampling rate multiplier
				
				seedOut.write( 0 ); // accuracy? flag
				seedOut.write( 0 ); // IO clock? flag
				seedOut.write( 0 ); // data quality flag
				seedOut.write( blockette_count ); // num blockettes
				seedOut.write( 0 ); // time correction
				seedOut.write( 0 ); 
				seedOut.write( 0 ); 
				seedOut.write( 0 ); 
				writeShort( seedOut, 48 + 8*blockette_count ); // start of data
				writeShort( seedOut, 48 ); // first blockette
				
				
				int offset = 48;
				drl = 12;
				valsInBlockette = 4096;
				valsLeft = sample_count;
				while ( valsLeft > 0 ) {
					while ( valsLeft < valsInBlockette ) {
						drl--;
						valsInBlockette /= 2;
					}
					valsLeft -= valsInBlockette;
										
					// Write header for this blockette
					// Blockette type 1000
					writeShort( seedOut, 1000 );
					// Next/last blockette
					offset += 8;
					writeShort( seedOut, valsLeft > 0 ? offset : 0 );
					
					// Encoding format
					seedOut.write( enc_fmt );
					// VAX order?
					seedOut.write( vax_order );
					// Data Record Length
					seedOut.write( drl + drl_add );
					// Reserved
					seedOut.write( 0 );

				}
				// And now, the actual data
				double min = datum[1];
				double max = datum[1];
				valsLeft = sample_count;
				while ( datum != null ) {
					valsLeft--;
					if ( min > datum[1] )
						min = datum[1];
					if ( max < datum[1] )
						max = datum[1];
					writeDouble( seedOut, datum[1] );
					datum = cd.nextExportDatum();
				}
				//logger.info( "# Samples = " + sample_count );
				//logger.info( "Left = " + valsLeft );
				//logger.info( "Max = " + max );
				//logger.info( "Min = " + min );
			} catch (IOException e) {
				throw new Valve3Exception( "Error writing mseed file: " + e.getMessage() );
			}
		} else if ( currLine.length == 1 ) {
			// Since there's only 1 source, we can just loop through it
			ExportData cd = csvData.first();
			Double[] datum = cd.currExportDatum();
			if ( outToCSV )
				while ( datum != null ) {
					currLine[0] = datum;
					addCSVline( currLine, datum[0], decFmt, nullField );
					datum = cd.nextExportDatum();
				}
			cd = csvData.first();
			datum = cd.currExportDatum();
			if ( outToXML ) {
				int pos = 0;
				while ( datum != null ) {
					pos++;
					currLine[0] = datum;
					addXMLline( currLine, datum[0], jxDecFmt, pos, rowTimeZone, rank );
					datum = cd.nextExportDatum();
				}
			}
			cd = csvData.first();
			datum = cd.currExportDatum();
			if ( outToJSON ) {
				int pos = 0;
				while ( datum != null ) {
					pos++;
					currLine[0] = datum;
					addJSONline( currLine, datum[0], jxDecFmt, pos, rowTimeZone, rank );
					datum = cd.nextExportDatum();
				}
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
			int pos = 0;
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
						pos++;
						// Add the current line to csvText
						if ( outToCSV )
							addCSVline( currLine, prevTime, decFmt, nullField );
						if ( outToXML )
							addXMLline( currLine, prevTime, decFmt, pos, rowTimeZone, rank );
						if ( outToJSON )
							addJSONline( currLine, prevTime, decFmt, pos, rowTimeZone, rank );
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
		if ( outToXML )
			csvText.append( "\t</DATA>\n</VALVE_XML>\n");
		if ( outToJSON )
			csvText.append( "]}}\n");
		String result = csvText.toString();
		csvText = null;
		return result;
	}	
	
	class AxisParameters {
		double yMin = 1E300;
		double yMax = -1E300;
		boolean allowExpand = true;
		
		public AxisParameters(String axisType, Map<Integer, String> axisMap, GenericDataMatrix gdm, int index, PlotComponent comp, MatrixRenderer mr) throws Valve3Exception{
			if (!(axisType.equals("L") || axisType.equals("R"))) {
				throw new Valve3Exception("Illegal axis type: " + axisType);
			}
			if(index==-1) {
				for (int i = 0; i < axisMap.size(); i++) {
					setParameters(axisType, axisMap.get(i), gdm, i, comp, mr);
				}
			} else {
				setParameters(axisType, axisMap.get(index), gdm, index, comp, mr);
			}
		}
		
		private void setParameters(String axisType, String mapAxisType, GenericDataMatrix gdm, int index, PlotComponent comp, MatrixRenderer mr) throws Valve3Exception {

			int offset;
			String ysMin, ysMax = "";
			boolean yMinAuto = false;
			boolean yMaxAuto = false;
			boolean yMinMean = false;
			boolean yMaxMean = false;
			
			if (!ranks) {
				offset = 1;
			} else {
				offset = 2;
			}
			
			if (!(mapAxisType.equals("L") || mapAxisType.equals("R") || mapAxisType.equals(""))) 
				throw new Valve3Exception("Illegal axis type in axis map: " + mapAxisType);
			
			if (mapAxisType.equals(axisType) || isPlotComponentsSeparately()){
				if (mapAxisType.equals(axisType)){
					mr.setVisible(index, true);
				}
				
				try {
					ysMin	= comp.get("ys" + mapAxisType + "Min").toLowerCase();
				} catch (NullPointerException e) {
					ysMin	= "";
				}
				try {
					ysMax	= comp.get("ys" + mapAxisType + "Max").toLowerCase();
				} catch (NullPointerException e) {
					ysMin	= "";
				}

				// if not defined or empty, default to auto scaling
				if (ysMin.startsWith("a") || ysMin == null || ysMin.trim().isEmpty()) {
					yMinAuto = true;
				} else if (ysMin.startsWith("m")) {
					yMinMean = true;
				}
				if (ysMax.startsWith("a") || ysMax == null || ysMax.trim().isEmpty()) {
					yMaxAuto = true;
				} else if (ysMax.startsWith("m")) {
					yMaxMean = true;
				}
				
				
				// calculate min auto scale
				if (yMinAuto || yMinMean) {
					yMin	= Math.min(yMin, gdm.min(index + offset));
					
				// calculate min user defined scale
				} else {
					yMin	= Util.stringToDouble(ysMin, Math.min(yMin, gdm.min(index + offset)));
					allowExpand	= false;
				}
				
				
				// calculate max auto scale
				if (yMaxAuto) {
					yMax	= Math.max(yMax, gdm.max(index + offset));
					
				// calculate max mean scale
				} else if (yMaxMean) {
					yMax	= gdm.mean(index + offset) + (2 * Math.abs(gdm.mean(index + offset)));
					
				// calculate max user defined scale
				} else {
					yMax	= Util.stringToDouble(ysMax, Math.max(yMax, gdm.max(index + offset)));
					allowExpand	= false;
				}
				
				if (yMin > yMax) throw new Valve3Exception("Illegal " + mapAxisType + " axis values");

				double buffer = 0.05;				
				if (yMin == yMax && yMin != 0) {
					buffer = Math.abs(yMin * 0.05);
				} else {
					buffer = (yMax - yMin) * 0.05;
				}
				if (allowExpand) {
					yMin	= yMin - buffer;
					yMax	= yMax + buffer;
				}
			}
		}
	}
	
	/**
	 * Fills plotter's data manipulation configuration according component parameters
     * @param comp plot component
     * @throws Valve3Exception
	 */
	protected void validateDataManipOpts(PlotComponent comp) throws Valve3Exception {
		doDespike = Util.stringToBoolean(comp.get("despike"));
		if ( doDespike ) {
			despikePeriod = comp.getDouble("despike_period");
			if ( Double.isNaN(despikePeriod) )
				throw new Valve3Exception("Illegal/missing period for despike");
		}
		doDetrend = Util.stringToBoolean(comp.get("detrend"));
		try {
			filterPick = comp.getInt("dmo_fl");
		} catch(Valve3Exception e){
			filterPick = 0;
		}
		if ( filterPick != 0 ) {
			if ( filterPick != 1 ) {
				filterPeriod = comp.getDouble("filter_arg1");
				if ( Double.isNaN(filterPeriod) )
					throw new Valve3Exception("Illegal/missing period for filter");
			} else {
				filterMax = comp.getDouble("filter_arg1");
				filterMin = comp.getDouble("filter_arg2");
				if ( Double.isNaN(filterMax) && Double.isNaN(filterMax) )
					throw new Valve3Exception("Illegal/missing bound(s) for bandpass");
			}
		}
		try {
			debiasPick = comp.getInt("dmo_db");
		} catch(Valve3Exception e){
			debiasPick = 0;
		}
		if ( debiasPick == 3 ) {
			debiasValue = comp.getDouble("debias_period");
			if ( Double.isNaN(debiasValue) )
				throw new Valve3Exception("Illegal/missing value for bias removal");
		}
	}
	
	/**
	 * Add to plotter's supplemental data
     * @param source data source
     * @param client name of VDX client
     * @param v3Plot Valve3Plot
     * @param component plot component
     * @throws Valve3Exception
	 */
	protected void addSuppData (String vdxSource, String vdxClient, Valve3Plot v3p, PlotComponent comp) throws Valve3Exception {
		
		// initialize variables
		List<String> stringList = null;
		Pool<VDXClient> pool	= null;
		VDXClient client		= null;
		String sdTypes;
		
		try {
			sdTypes = comp.getString("sdt");
		} catch (Valve3Exception e) {
			sdTypes = "";
		}
		
		// if ( sdTypes.length() == 0 )
		// return;
		// if(!ch.contains(",")){
		
		Map<String, String> params = new LinkedHashMap<String, String>();		
		params.put("source", vdxSource);
		params.put("action", "suppdata");
		params.put("st", Time.format(Time.STANDARD_TIME_FORMAT_MS,startTime).replaceAll("\\D",""));
		params.put("et", Time.format(Time.STANDARD_TIME_FORMAT_MS,endTime).replaceAll("\\D",""));
		params.put("rk", Integer.toString(rk));
		params.put("byID","true");
		params.put("ch", ch);
		params.put("type", sdTypes);
		params.put("dl", "10");
		
		// calculate the columns parameters
		String cols = null;
		if (columnsList == null) {
			cols = "";
		} else {
			for (int i = 0; i < columnsList.size(); i++) {
				Column column	= columnsList.get(i);
				if (column.checked) {
					if (cols == null) {
						cols = "" + (i+1);
					} else {
						cols = cols + "," + (i+1);
					}
				}
			}
		}
		params.put("col", cols);		

		// create a column map
		Map<Integer,Integer> colMap = new LinkedHashMap<Integer,Integer>();
		int i = 0;
		if (cols.length() > 0) {
			for (String c: cols.split(",")) {
				colMap.put( Integer.parseInt(c), i );
				i++;
			}
		}
		
		// create a channel map
		Map<Integer,Integer> chMap = new LinkedHashMap<Integer,Integer>();
		i = 0;
		if (ch.length() > 0)
			for (String c: ch.split(",")) {
				chMap.put(Integer.parseInt(c), i);
				i++;
			}
		
		// define the box height
		int compBoxHeight = comp.getBoxHeight();

		pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		if (pool != null) {
			client	= pool.checkout();
			try {
				stringList	= client.getTextData(params);
				for (String sd: stringList) {
					SuppDatum sdo = new SuppDatum(sd);
					int offset;
					if (isPlotComponentsSeparately()) {
						offset = (Integer)chMap.get(sdo.cid) * colMap.size() + (Integer)colMap.get(sdo.colid);
					} else {
						offset = (Integer)chMap.get( sdo.cid );
					}
					sdo.frame_y = comp.getBoxY() + (offset * compBoxHeight) + 8;
					sdo.frame_h = compBoxHeight - 16;
					v3p.addSuppDatum( sdo );
				}
			} catch (Exception e) {
				throw new Valve3Exception(e.getMessage()); 
			} finally {
				pool.checkin(client);
			}
		}
	}
}