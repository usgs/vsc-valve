package gov.usgs.volcanoes.valve3.result;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private final static Logger logger = LoggerFactory.getLogger(Result.class);
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
	 * @param u to set
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
		logger.debug("Result.delete()");
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
