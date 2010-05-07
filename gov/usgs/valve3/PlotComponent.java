package gov.usgs.valve3;

import gov.usgs.util.Util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Keeps set of parameters to generate valve plot
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
	
	private int boxX = 0;
	private int boxY = 0;
	private int boxHeight = 0;
	private int boxWidth = 0;
	private SimpleDateFormat df = null;

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
	 */
	public String getSource()
	{
		return source;
	}

	/**
	 * Setter for source
	 */
	public void setSource(String s)
	{
		source = s;
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
	 * 
	 * @param key parameter name
	 * @return value of given parameter
	 */
	public String get(String key)
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
			throw new Valve3Exception("Illegal " + key + ":" + value==null?"null":value);
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
			throw new Valve3Exception("Illegal " + key + ":" + value==null?"null":value);
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
			throw new Valve3Exception("Illegal " + key + ":" + value==null?"null":value);
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
	 * 
	 * @param pre prefix to query
	 * @return flag if auto scaling allowed for given prefix
	 */
	public boolean isAutoScale(String pre)
	{
		String ysMin = params.get(pre + "Min");
		String ysMax = params.get(pre + "Max");
		return ysMin == null || ysMax == null || ysMin.toLowerCase().startsWith("a") || ysMax.toLowerCase().startsWith("a");
	}
	
	/**
	 * Compute max and min values for given prefix from PlotComponent's parameters
	 * @param pre prefix to query
	 * @param min default min value 
	 * @param max default max value
	 * @return array of 2 doubles, first is initialized min value, second is initialized max value
	 */
	public double[] getYScale(String pre, double min, double max) {
		String ysMin, ysMax;
		double[] d = new double[2];
		
		ysMin	= params.get(pre + "Min");
		ysMax	= params.get(pre + "Max");
		
		if (ysMin == null) {
			d[0] = min;
		} else if (ysMin.toLowerCase().equals("min") || ysMin.toLowerCase().equals("auto")) {
			d[0] = min;
		} else {
			d[0] = Util.stringToDouble(ysMin, min);
		}		
		
		if (ysMax == null) {
			d[1] = max;
		} else if (ysMax.toLowerCase().equals("max") || ysMax.toLowerCase().equals("auto")) {
			d[1] = max;
		} else {
			d[1] = Util.stringToDouble(ysMax, max);
		}

		return d;
	}

	/**
	 * Setter for translation type (map, heli, ty, xy)
	 */
	public void setTranslationType(String t)
	{
		translationType = t;
	}

	/**
	 * Setter for translation
	 */
	public void setTranslation(double[] t)
	{
		translation = t;
	}
	
	/**
	 * @return X coord of top left corner of graph's box
	 */
	public int getBoxX()
	{
		return boxX;
	}

	/**
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
	 * @return graph's box width
	 */
	public int getBoxWidth()
	{
		return boxWidth;
	}

	/**
	 * @return graph's box height
	 */
	public int getBoxHeight()
	{
		return boxHeight;
	}
	
	/**
	 * Setter for graph's box width
	 */
	public void setBoxWidth(int w)
	{
		boxWidth = w;
	}

	/**
	 * Setter for graph's box height
	 */	
	public void setBoxHeight(int h)
	{
		boxHeight = h;
	}
	
	/**
	 * Compute start time from PlotComponent's parameters
	 * @param end reference end time for relative parameter values (for example, "-1h")
	 * @return start time in seconds, UTC
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
	 * @return TimeZone which was used for initial time input by user
	 */
	public TimeZone getTimeZone(){
		return df.getTimeZone();
	}
	
	/**
	 * @param time Time moment in j2Ksec
	 * @return offset between current time zone and UTC in seconds, on given time moment
	 */
	public double getOffset(double time){
		return df.getTimeZone().getOffset(Util.j2KToDate(time).getTime())/1000.0;
	}

	/**
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
		sb.append("\t\t</component>\n");
		return sb.toString();
	}
}
