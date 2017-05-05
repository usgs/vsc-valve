package gov.usgs.volcanoes.valve3.result;

/**
 * Generic menu to render on valve GUI

 * @author Dan Cervelli
 */
public class GenericMenu extends Result
{
	public String title			= "Generic Data";
	public String timeShortcuts	= "-1h,-1d,-1w,-1m";
	public String channelString	= "Channels";
	public String description	= "";
	
	/**
	 * Constructor
	 * @param src list of strings: title, description, channel string, time shortcuts
	 */
	public GenericMenu(java.util.List<String> src)
	{
		title			= src.get(0);
		description		= src.get(1);
		channelString	= src.get(2);
		timeShortcuts	= src.get(3);
	}
	
	/**
	 * Yield XML representation
	 * @return XML representation of this GenericMenu
	 */
	public String toXML()
	{
		StringBuilder sb = new StringBuilder(256);
		sb.append("\t<genericMenu>\n");
		sb.append("\t\t<title>" + title + "</title>\n");
		sb.append("\t\t<channelString>");
		sb.append(channelString);
		sb.append("</channelString>\n");
		sb.append("\t\t<timeShortcuts>");
		sb.append(timeShortcuts);
		sb.append("</timeShortcuts>\n");
		sb.append("\t\t<description>");
		sb.append(description);
		sb.append("</description>\n");
		sb.append("\t</genericMenu>\n");
		return toXML("genericMenu", sb.toString());
	}
}
