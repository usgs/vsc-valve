package gov.usgs.valve3;

import gov.usgs.util.Util;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * $Log: not supported by cvs2svn $
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
	
	public PlotComponent()
	{}
	
	public PlotComponent(String s)
	{
		source = s;
		params = new HashMap<String, String>();
	}
	
	public String getSource()
	{
		return source;
	}
	
	public void setSource(String s)
	{
		source = s;
	}
	
	public void put(String key, String val)
	{
		params.put(key, val);
	}
	
	public String get(String key)
	{
		return params.get(key);
	}
	
	public void setTranslationType(String t)
	{
		translationType = t;
	}
	
	public void setTranslation(double[] t)
	{
		translation = t;
	}
	
	public int getBoxX()
	{
		return boxX;
	}
	
	public int getBoxY()
	{
		return boxY;
	}
	
	public void setBoxX(int x)
	{
		boxX = x;
	}
	
	public void setBoxY(int y)
	{
		boxY = y;
	}
	
	public int getBoxWidth()
	{
		return boxWidth;
	}
	
	public int getBoxHeight()
	{
		return boxHeight;
	}
	
	public void setBoxWidth(int w)
	{
		boxWidth = w;
	}
	
	public void setBoxHeight(int h)
	{
		boxHeight = h;
	}
	
	public double getStartTime(double end)
	{
		String st = params.get("st");
		if (st == null)
			return Double.NaN;
		else 
			return parseTime(st, end);
	}
	
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
				return Util.dateToJ2K(dateIn.parse(t));// - Valve3.getTimeZoneAdj();
		}
		catch (Exception e)
		{}
		return Double.NaN;
	}
	
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
		sb.append("\t\t\t</translation>\n");
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
