package gov.usgs.volcanoes.valve3.result;

import java.io.File;

/**
 * Handles with computed raw data results, 
 * keeps raw data file name
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.1  2006/05/17 21:46:13  tparker
 * Initial commit
 *
 *
 * @author Tom Parker
 */
public class RawData extends Result
{
	protected String url;
	protected String filename;
	
	/**
	 * Constructor
	 * @param u URL to result
	 * @param fn filename with raw data
	 */
	public RawData(String u, String fn)
	{
		url = u;
		filename = fn;
	}
	
	/**
	 * Yield local filename
	 * @return file name for raw data results
	 */
	public String getLocalFilename()
	{
		//return Valve3.getInstance().getApplicationPath() + File.separatorChar + filename;
		return filename;
	}

	/**
	 * Deletes raw data result file
	 */
	public void delete()
	{
		if (new File(getLocalFilename()).delete())
			logger.info("Deleted {}", getLocalFilename());
		else
			logger.info("Couldn't delete {}", getLocalFilename());
	}
	
	/**
	 * Yield XML representation
	 * @return xml representation of RawData
	 */
	public String toXML()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("\t<rawData>\n");
		sb.append("\t\t<url>" + url + "</url>\n");
		sb.append("\t</rawData>\n");
		return toXML("rawData", sb.toString());
	}
}
