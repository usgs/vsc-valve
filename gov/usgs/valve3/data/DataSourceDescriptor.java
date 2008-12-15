package gov.usgs.valve3.data;

import gov.usgs.util.ConfigFile;
import gov.usgs.valve3.Plotter;

/**
 * Keeps configuration parameters for vdx data source
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
	
	/**
	 * Constructor
	 * @param n name of valve data source ("source" config file parameter)
	 * @param c vdx name ("vdx" config file parameter)
	 * @param s vdx data source name ("vdx.source" config file parameter)
	 * @param pc plotter name ("plotter" config file parameter)
	 * @param cf subconfiguration for this data source
	 */
	public DataSourceDescriptor(String n, String c, String s, String pc, ConfigFile cf)
	{
		name = n;
		vdxClientName = c;
		vdxSource = s;
		plotterClassName = pc;
		config = cf;
	}
	
	/**
	 * 
	 * @return name of this data source
	 */
	public String getName()
	{
		return name;
	}
	
	/**
	 * 
	 * @return vdx name for valve data source
	 */
	public String getVDXClientName()
	{
		return vdxClientName;
	}
	
	/**
	 * 
	 * @return vdx data source name
	 */
	public String getVDXSource()
	{
		return vdxSource;
	}
	
	/**
	 * 
	 * @return subconfiguration for valve data source
	 */
	public ConfigFile getConfig()
	{
		return config;
	}
	
	/**
	 * 
	 * @return initialized plotter for valve data source
	 */
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
