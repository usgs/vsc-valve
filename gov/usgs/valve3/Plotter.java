package gov.usgs.valve3;

import gov.usgs.util.ConfigFile;
import gov.usgs.valve3.result.Valve3Plot;

/**
 * $Log: not supported by cvs2svn $
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
	
	protected ConfigFile plotterConfig;
	
	public void setVDXClient(String c)
	{
		vdxClient = c;
	}
	
	public void setVDXSource(String s)
	{
		vdxSource = s;
	}
	
	public void setPlotterConfig(ConfigFile cf)
	{
		plotterConfig = cf;
	}
	
	abstract public void plot(Valve3Plot plot, PlotComponent component) throws Valve3Exception;
}
