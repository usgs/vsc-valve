package gov.usgs.valve3.result;

import java.util.ArrayList;

/**
 * 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class GenericMenu extends Result
{
	public java.util.List<String> columns;
	public String title = "Generic Data";
	public String timeShortcuts = "-1h,-1d,-1w,-1m";
	public String selectorString = "Channels";
	public String description = "";
	
	public GenericMenu(java.util.List<String> src)
	{
		title = src.get(0);
		description = src.get(1);
		selectorString = src.get(2);
		timeShortcuts = src.get(3);
		int cnt = Integer.parseInt(src.get(4));
		columns = new ArrayList<String>(cnt);
		for (int i = 5; i < cnt + 5; i++)
			columns.add(src.get(i));
	}
	
	public String toXML()
	{
		StringBuilder sb = new StringBuilder(256);
		sb.append("\t<genericMenu>\n");
		sb.append("\t\t<title>" + title + "</title>\n");
		sb.append("\t\t<selectorString>");
		sb.append(selectorString);
		sb.append("</selectorString>\n");
		sb.append("\t\t<timeShortcuts>");
		sb.append(timeShortcuts);
		sb.append("</timeShortcuts>\n");
		sb.append("\t\t<description>");
		sb.append(description);
		sb.append("</description>\n");
		sb.append("\t\t<columns>\n");
		for (String col : columns)
			sb.append("\t\t\t<column>" + col + "</column>\n");
		sb.append("\t\t</columns>\n");
		sb.append("\t</genericMenu>\n");
		return toXML("genericMenu", sb.toString());
	}
}
