package gov.usgs.valve3;

import gov.usgs.util.ConfigFile;
import gov.usgs.valve3.result.Valve3Plot;

/**
 * Abstract base class for plotter, all concrete plotters
 * should extend this class
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.3  2005/10/13 20:34:32  dcervelli
 * Added plotterConfig.
 *
 * Revision 1.2  2005/08/28 19:00:46  dcervelli
 * plot() now throws exceptions.
 *
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
abstract public class Plotter
{
	protected String vdxClient;
	protected String vdxSource;
	protected int maxrows = 0;
	
	protected ConfigFile plotterConfig;
	
	/**
	 * Setter for vdx name
	 * @param c vdx name ("....vdx" config file parameter)
	 */
	public void setVDXClient(String c)
	{
		vdxClient = c;
	}
	
	/**
	 * Setter for vdx source name
	 * @param s vdx source name ("....vdx.source" parameter)
	 */
	public void setVDXSource(String s)
	{
		vdxSource = s;
	}
	
	/**
	 * Setter for plotter configuration
	 * @param cf configuration subset for plotter
	 */
	public void setPlotterConfig(ConfigFile cf)
	{
		plotterConfig = cf;
	}
	
	/**
	 * Setter for max rows count
	 * @param c vdx name ("....vdx" config file parameter)
	 */
	public void setMaxRows(int maxrows)
	{
		this.maxrows = maxrows;
	}
	
	/**
	 * Exports PlotComponent to CSV format
	 * @param comp 
	 * @return string with csv data
	 * @throws Valve3Exception
	 */
	public String toCSV(PlotComponent comp) throws Valve3Exception
	{
		throw new Valve3Exception("Data export not available for this data source.");
	}
	
	
	/**
	 * Returns flag if plotter output several components separately or as one plot
	 * @return boolean flag
	 */
	public boolean isPlotComponentsSeparately(){
		String value = plotterConfig.getString("plotComponentsSeparately");
		if(value==null || !value.equals("true")){
			return false;
		} else {
			return true;
		}
	}

	/**
	 * renders PlotComponent in given plot
	 */
	abstract public void plot(Valve3Plot plot, PlotComponent component) throws Valve3Exception;
}
