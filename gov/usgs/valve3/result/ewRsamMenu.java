package gov.usgs.valve3.result;

import gov.usgs.util.Log;

import java.util.logging.Logger;

/**
 * 
 * Determine if both events and counts tables are present and inform the GUI
 *  
 * @author Tom Parker
 */
public class ewRsamMenu extends Result
{
	public String title = "EWRsam Data";
	private Logger logger;
	String dataTypes;
	
	/**
	 * Constructor
	 * @param src list of data types
	 */
	public ewRsamMenu(java.util.List<String> src)
	{
		logger = Log.getLogger("gov.usgs.vdx");
		logger.info("ewRsamMenu() src = " + src.toString());

		dataTypes = src.toString();
	}

	/**
	 * @return XML representation of this ewRsamMenu
	 */
	public String toXML()
	{
		StringBuilder sb = new StringBuilder(256);
		sb.append("\t<ewRsamMenu>\n");
		sb.append("\t\t<dataTypes>" + dataTypes + "</dataTypes>\n");
		sb.append("\t</ewRsamMenu>\n");
		return toXML("ewRsamMenu", sb.toString());
	}
}
