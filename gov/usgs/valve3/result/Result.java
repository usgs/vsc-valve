package gov.usgs.valve3.result;

import gov.usgs.util.Log;

import java.util.logging.Logger;

/**
 * Keeps URL to generated result
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
abstract public class Result
{
	private final static Logger logger = Log.getLogger("gov.usgs.valve3.result.Result"); 
	protected String url;
	
	/**
	 * @return URL to generated result
	 */
	public String getURL()
	{
		return url;
	}
	
	/**
	 * Setter for URL
	 * @param url to set
	 */
	public void setURL(String u)
	{
		url = u;
	}
	
	/**
	 * Deletes generated result
	 */
	public void delete()
	{
		logger.fine("Result.delete()");
	}
	
	/**
	 * Yield XML representation
	 * @param type result type
	 * @param nested String with xml representation of result's content
	 * @return String with xml representation of generated result
	 */
	public String toXML(String type, String nested)
	{
		StringBuffer sb = new StringBuffer();
		sb.append("<valve3result>\n");
		sb.append("\t<type>" + type + "</type>\n");
		//sb.append("\t<url>" + url + "</url>\n");
		sb.append(nested + "\n");
		sb.append("</valve3result>\n");
		return sb.toString();
	}
	
	abstract public String toXML();
}
