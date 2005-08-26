package gov.usgs.valve3.data;

import gov.usgs.valve3.Plotter;

import java.util.Map;

/**
 * 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class DataSourceDescriptor
{
	private String name;
	private String vdxClientName;
	private String vdxSource;
	private String plotterClassName; 
	
	private Map<String, Object> params;
	
//	private DataSource dataSource;
	private Plotter plotter;
	
	public DataSourceDescriptor(String n, String c, String s, String pc, Map<String, Object> p)
	{
		name = n;
		vdxClientName = c;
		vdxSource = s;
		plotterClassName = pc;
		
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

	/*
	private void instantiateDataSource()
	{
		try
		{
			dataSource = (DataSource)Class.forName(dataClassName).newInstance();
			Class.forName(dataClassName).getMethod("initialize", new Class[] { HashMap.class }).invoke(dataSource, new Object[] { params });
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}
	public DataSource getDataSource()
	{
		if (dataSource == null && dataClassName != null)
			instantiateDataSource();
		
		return dataSource;
	}
	*/
	
	private void instantiatePlotter()
	{
//		getDataSource();
		
		try
		{
			plotter = (Plotter)Class.forName(plotterClassName).newInstance();
			Class.forName(plotterClassName).getMethod("setVDXClient", new Class[] { String.class }).invoke(plotter, new Object[] { vdxClientName });
			Class.forName(plotterClassName).getMethod("setVDXSource", new Class[] { String.class }).invoke(plotter, new Object[] { vdxSource });
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}
	
	public Plotter getPlotter()
	{
		if (plotter == null && plotterClassName != null)
			instantiatePlotter();
		
		return plotter;
	}
}
