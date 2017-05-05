package gov.usgs.volcanoes.valve3;

import gov.usgs.util.Util;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Keeps set of parameters to generate valve plot component - 
 * separated area of plot with set of axis and one or several graphs.
 * Plot can contain several components.
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.4  2005/11/03 19:40:27  tparker
 * Correct time adj for bug #68
 *
 * Revision 1.3  2005/11/03 18:46:22  tparker
 * Convert input times for bug #68
 *
 * Revision 1.2  2005/09/05 00:39:31  dcervelli
 * Got rid of unnecessary tabs.
 *
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
public class PlotComponent
{
	private String source;
	private Map<String, String> params;
	
	protected String translationType = "none";
	protected double[] translation;
	
	private int boxX			= 0;
	private int boxY			= 0;
	private int boxHeight		= 0;
	private int boxWidth		= 0;
	private int boxMapHeight	= 0;
	
	private SimpleDateFormat df	= null;
	private String plotter		= null;

	private boolean exportable	= false;

	/**
	 * Constructor
	 * @param s source name
	 */
	public PlotComponent(String s, TimeZone timeZone)
	{
		source = s;
		params = new HashMap<String, String>();
		df = new SimpleDateFormat("yyyyMMddHHmmssSSS");
		df.setTimeZone(timeZone);
	}
	
	/**
	 * Getter for source
	 * @return source
	 */
	public String getSource()
	{
		return source;
	}

	/**
	 * Setter for source
	 * @param s source
	 */
	public void setSource(String s)
	{
		source = s;
	}
	
	public void setPlotter(String plotterName){
		this.plotter = plotterName;
	}

	/**
	 * Adds parameter
	 * @param key parameter name
	 * @param val parameter value
	 */
	public void put(String key, String val)
	{
		params.put(key, val);
	}

	/**
	 * Get parameter by key
	 * @param key parameter name
	 * @return value of given parameter
	 */
	public String get(String key) throws NullPointerException
	{
		return params.get(key);
	}
	
	/**
	 * Get parameter value as integer
	 * @param key parameter name
	 * @return value of given parameter
	 * @throws Valve3Exception if parameter absent or can't be parsed
	 */
	public int getInt(String key) throws Valve3Exception {
		String value = get(key);
		int pv = Util.stringToInt(value, Integer.MIN_VALUE);
		if (pv == Integer.MIN_VALUE) {
			throw new Valve3Exception("Illegal " + key + ":" + (value==null?"null":value));
		}
		return pv;
	}
	
	/**
	 * Get parameter value as double
	 * @param key parameter name
	 * @return value of given parameter
	 * @throws Valve3Exception if parameter absent or can't be parsed
	 */
	public double getDouble(String key) throws Valve3Exception {
		String value = get(key);
		double pv = Util.stringToDouble(value, Double.NaN);
		if (pv == Double.NaN) {
			throw new Valve3Exception("Illegal " + key + ":" + (value==null?"null":value));
		}
		return pv;
	}
	
	/**
	 * Get parameter value as string
	 * @param key parameter name
	 * @return value of given parameter
	 * @throws Valve3Exception if parameter absent
	 */
	public String getString(String key) throws Valve3Exception {
		String value = get(key);
		if (value == null || value.length()==0) {
			throw new Valve3Exception("Illegal " + key + ":" + (value==null?"null":value));
		}
		return value;
	}
	
	/**
	 * Get parameter value as boolean
	 * @param key parameter name
	 * @return value of given parameter
	 * @throws Valve3Exception if parameter absent or can't be parsed.
	 */
	public boolean getBoolean(String key) throws Valve3Exception{
		String value = get(key);
		if(value==null){
			throw new Valve3Exception("Illegal " + key + ":null");
		}
		if ((!value.toLowerCase().equals("true") && value.toLowerCase().equals("t") && !value.toLowerCase().equals("false") && value.toLowerCase().equals("f") && !value.equals("1") && !value.equals("0"))) {
			throw new Valve3Exception("Illegal " + key + ":" + value);
		}
		boolean pv = Util.stringToBoolean(value);
		return pv;
	}
	
	/**
	 * Get whether or not vector autoscaling is allowed
	 * @param key key to query 
	 * @return flag if auto scaling allowed for given key
	 */
	public boolean isVectorAutoScale(String key)
	{
		String vs = params.get(key);
		return vs == null || vs.trim().isEmpty() || vs.toLowerCase().startsWith("a");
	}

	/**
	 * Setter for translation type (map, heli, ty, xy)
	 * @param t translation type
	 */
	public void setTranslationType(String t)
	{
		translationType = t;
	}

	/**
	 * Setter for translation
	 * @param t translation
	 */
	public void setTranslation(double[] t)
	{
		translation = t;
	}
	
	/**
	 * Getter for box's x coordinate
	 * @return X coord of top left corner of graph's box
	 */
	public int getBoxX()
	{
		return boxX;
	}

	/**
	 * Getter for box's y coordinate
	 * @return Y coord of top left corner of graph's box
	 */
	public int getBoxY()
	{
		return boxY;
	}
	
	/**
	 * Setter for boxX
	 * @param x X coord of top left corner of graph's box
	 */
	public void setBoxX(int x)
	{
		boxX = x;
	}
	
	/**
	 * Setter for boxY
	 * @param y Y coord of top left corner of graph's box
	 */
	public void setBoxY(int y)
	{
		boxY = y;
	}
	
	/**
	 * Yield graph's box width
	 * @return graph's box width
	 */
	public int getBoxWidth()
	{
		return boxWidth;
	}

	/**
	 * Yield graph's box height
	 * @return graph's box height
	 */
	public int getBoxHeight()
	{
		return boxHeight;
	}
	
	/**
	 * Yield graph's box map height
	 * @return graph's box map height
	 */
	public int getBoxMapHeight()
	{
		return boxMapHeight;
	}
	
	/**
	 * Setter for graph's box width
	 * @param w width
	 */
	public void setBoxWidth(int w)
	{
		boxWidth = w;
	}

	/**
	 * Setter for graph's box height
	 * @param h box's height
	 */	
	public void setBoxHeight(int h)
	{
		boxHeight = h;
	}
	
	/**
	 * Setter for graph's box map height
	 * @param mh map height
	 */
	public void setBoxMapHeight(int mh)
	{
		boxMapHeight = mh;
	}
	
	/**
	 * Compute start time from PlotComponent's parameters
	 * @param end reference end time for relative parameter values (for example, "-1h")
	 * @return start time in seconds, UTC
	 * @throws Valve3Exception
	 */
	public double getStartTime(double end) throws Valve3Exception
	{
		String st = params.get("st");
		if (st == null)
			return Double.NaN;
		else {
			return parseTime(st, end);
		}
	}
	
	// TODO: does this allow startTime > endTime?
	/**
	 * Compute end time from PlotComponent's parameters
	 * @return end time in seconds, UTC
	 * @throws Valve3Exception
	 */
	public double getEndTime() throws Valve3Exception
	{
		String et = params.get("et");
		if (et == null)
			return Double.NaN;
		else {
			return parseTime(et, Double.NaN);
		}
	}
	
	/** Parses the time.
	 * @param t the string representing the time
	 * @param end the end time (for -[n][units] times)
	 * @return the correct j2ksec, UTC
	 * @throws Valve3Exception
	 */
	public double parseTime(String t, double end) throws Valve3Exception
	{
		try
		{
			if (t.equals("N"))
			{
				// is refreshable
				return Util.nowJ2K();
			}
			else if (t.startsWith("-"))
			{
				long ms = -Long.parseLong(t);
				if (Double.isNaN(end))
					return Util.nowJ2K() - ((double)ms/1000);
				else
					return end - ((double)ms/1000);
			}
			else if (t.length() == 17){
				Date dtIn = df.parse(t);
				return Util.dateToJ2K(dtIn);
			}
			else {
				throw new Valve3Exception("Illegal time string: " + t);
			}
		}
		catch (Exception e)
		{
			throw new Valve3Exception("Illegal time string: " + t);
		}
	}
	
	/**
	 * Yield timezone
	 * @return TimeZone which was used for initial time input by user
	 */
	public TimeZone getTimeZone(){
		return df.getTimeZone();
	}
	
	/**
	 * Get offset between current time zone and UTC in seconds
	 * @param time Time moment in j2Ksec
	 * @return offset between current time zone and UTC in seconds, on given time moment
	 */
	public double getOffset(double time){
		return df.getTimeZone().getOffset(Util.j2KToDate(time).getTime())/1000.0;
	}
	
	/**
	 * If option 'color' was set into request string for the component, this function returns color for it's hex representation
	 * @return null if 'color' option wasn't set
	 * @throws Valve3Exception
	 */
	public Color getColor() throws Valve3Exception {
		String colorString = get("color");
		if(colorString==null){
			return null;
		} else {
			int colorInt = 0;
			try{
				colorInt = Integer.parseInt(colorString, 16);
			}
			catch(Exception e){
				throw new Valve3Exception("Can't convert color string to hex integer: " + colorString);
			}
			if((colorInt > 0xFFFFFF) || (colorInt < 0)){
				throw new Valve3Exception("Wrong color code: " + colorInt);
			}
			return new Color(colorInt);
		}
	}

	/**
	 * Yield PlotComponent's xml representation
	 * @return PlotComponent's xml representation
	 */
	public String toXML()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("\t\t<component>\n");
		sb.append("\t\t\t<translation-type>" + translationType + "</translation-type>\n");
		sb.append("\t\t\t<translation>");
		if (translation == null)
			sb.append("none");
		else
		{
			for (int i = 0; i < translation.length; i++)
			{
				sb.append(Double.toString(translation[i]));
				if (i != translation.length - 1)
					sb.append(",");
			}
		}
		sb.append("</translation>\n");
		if (params != null)
		{
//			for (Iterator it = params.keySet().iterator(); it.hasNext(); )
			for (String key : params.keySet())
			{
//				String key = (String)it.next();
				sb.append("\t\t\t<" + key + ">" + params.get(key) + "</" + key + ">\n");
			}
		}
		sb.append("\t\t\t<plotter>" + (plotter==null?"":plotter) + "</plotter>\n");
		sb.append("\t\t</component>\n");
		return sb.toString();
	}

	/**
	 * Setter for graph's exportability
	 * @return boolean "is exportable"
	 */
	public boolean getExportable()
	{
		return exportable;
	}

	/**
	 * Setter for graph's exportability
	 * @param e "is exportable"
	 */	
	public void setExportable(boolean e)
	{
		exportable = e;
	}
	
}
