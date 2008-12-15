package gov.usgs.valve3;

import gov.usgs.util.Util;

import java.text.SimpleDateFormat;
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
	private static SimpleDateFormat dateIn;
	
	private String source;
	private Map<String, String> params;
	
	protected String translationType = "none";
	protected double[] translation;
	
	private int boxX = 0;
	private int boxY = 0;
	private int boxHeight = 0;
	private int boxWidth = 0;
	
	static
	{
		dateIn = new SimpleDateFormat("yyyyMMddHHmmssSSS");
		dateIn.setTimeZone(TimeZone.getTimeZone("GMT"));
	}
	
	/**
	 * Default constructor
	 */
	public PlotComponent()
	{}

	/**
	 * Constructor
	 * @param s source name
	 */
	public PlotComponent(String s)
	{
		source = s;
		params = new HashMap<String, String>();
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
	public double[] getYScale(String pre, double min, double max)
	{
		double[] d = new double[2];
		String ysMin = params.get(pre + "Min");
		if (ysMin == null)
			d[0] = min;
		else if (ysMin.toLowerCase().equals("min") || ysMin.toLowerCase().equals("auto"))
			d[0] = min;
		else
			d[0] = Util.stringToDouble(ysMin, Double.NaN);
		
		String ysMax = params.get(pre + "Max");
		if (ysMax == null)
			d[1] = max;
		else if (ysMax.toLowerCase().equals("max") || ysMax.toLowerCase().equals("auto"))
			d[1] = max;
		else
			d[1] = Util.stringToDouble(ysMax, Double.NaN);

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
	 * @return start time in seconds
	 */
	public double getStartTime(double end)
	{
		String st = params.get("st");
		if (st == null)
			return Double.NaN;
		else 
			return parseTime(st, end);
	}
	
	// TODO: does this allow startTime > endTime?
	/**
	 * Compute end time from PlotComponent's parameters
	 * @return end time in seconds
	 */
	public double getEndTime()
	{
		String et = params.get("et");
		if (et == null)
			return Double.NaN;
		else
			return parseTime(et, Double.NaN);
	}
	
	/** Parses the time.
	 * @param t the string representing the time
	 * @param end the end time (for -[n][units] times)
	 * @return the correct j2ksec
	 */
	public static double parseTime(String t, double end)
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
			else if (t.length() == 17)
				return Util.dateToJ2K(dateIn.parse(t)) - (Valve3.getInstance().getTimeZoneOffset() * 60 * 60);
				//return  Util.dateToJ2K(dateIn.parse(t)) - (Valve3.getInstance().getTimeZoneOffset() * 60 * 60);// - Valve3.getTimeZoneAdj();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return Double.NaN;
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
