package gov.usgs.valve3.data;

import gov.usgs.util.ConfigFile;
import gov.usgs.valve3.Plotter;

/**
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.3  2005/10/13 20:35:22  dcervelli
 * Changes for plotterConfig.
 *
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
	
	private ConfigFile config;
	
	public DataSourceDescriptor(String n, String c, String s, String pc, ConfigFile cf)
	{
		name = n;
		vdxClientName = c;
		vdxSource = s;
		plotterClassName = pc;
		config = cf;
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
	
	public ConfigFile getConfig()
	{
		return config;
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
			plotter.setPlotterConfig(config.getSubConfig("plotter"));
			return plotter;
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		
		return null;
	}
}
