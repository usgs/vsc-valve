package gov.usgs.valve3.result;

import gov.usgs.valve3.PlotHandler;
import gov.usgs.valve3.Valve3;

import java.io.File;

/**
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
	
	public RawData(String u, String fn)
	{
		url = u;
		filename = fn;
	}
	
	
	public String getLocalFilename()
	{
		//return Valve3.getInstance().getApplicationPath() + File.separatorChar + filename;
		return filename;
	}

	public void delete()
	{
		if (new File(getLocalFilename()).delete())
			System.out.println("Deleted " + getLocalFilename());
		else
			System.out.println("Couldn't delete " + getLocalFilename());
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