package gov.usgs.valve3.result;

/**
 * 
 * $Log: not supported by cvs2svn $
 *
 * @author Tom Parker
 */
public class RawData extends Result
{
	protected String url;
	
	public RawData(String u)
	{
		url = u;
	}
	
	public String toXML()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("\t<rawData>\n");
		sb.append("\t\t<url>" + url + "</url>\n");
		sb.append("\t</rawData>\n");
		return toXML("rawData", sb.toString());
	}
}