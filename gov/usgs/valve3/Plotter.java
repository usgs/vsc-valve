package gov.usgs.valve3;

import gov.usgs.valve3.result.Valve3Plot;

/**
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
abstract public class Plotter
{
//	protected DataSource dataSource;
//	
//	public void setDataSource(DataSource ds)
//	{
//		dataSource = ds;
//	}
	
	protected String vdxClient;
	protected String vdxSource;
	
	public void setVDXClient(String c)
	{
		vdxClient = c;
	}
	
	public void setVDXSource(String s)
	{
		vdxSource = s;
	}
	
	abstract public void plot(Valve3Plot plot, PlotComponent component);
}
