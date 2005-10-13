package gov.usgs.valve3.data;

import gov.usgs.util.ConfigFile;
import gov.usgs.valve3.Plotter;

import java.util.Map;

/**
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.2  2005/08/28 18:59:25  dcervelli
 * Cleaned up.
 *
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
public class DataSourceDescriptor
{
	private String name;
	private String vdxClientName;
	private String vdxSource;
	private String plotterClassName; 
	
	private Map<String, Object> params;
	private ConfigFile plotterConfig;
	
	public DataSourceDescriptor(String n, String c, String s, String pc, Map<String, Object> p, ConfigFile cf)
	{
		name = n;
		vdxClientName = c;
		vdxSource = s;
		plotterClassName = pc;
		plotterConfig = cf;
		
		params = p;
	}
	
	public String getName()
	{
		return name;
	}
	
	public String getVDXClientName()
	{
		return vdxClientName;
	}
	
	public String getVDXSource()
	{
		return vdxSource;
	}
	
	public Map<String, Object> getParams()
	{
		return params;
	}

	public Plotter getPlotter()
	{
		if (plotterClassName == null)
			return null;
		
		try
		{
			Plotter plotter = (Plotter)Class.forName(plotterClassName).newInstance();
			plotter.setVDXClient(vdxClientName);
			plotter.setVDXSource(vdxSource);
			plotter.setPlotterConfig(plotterConfig);
			return plotter;
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		
		return null;
	}
}
