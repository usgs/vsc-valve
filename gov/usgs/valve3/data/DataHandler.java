package gov.usgs.valve3.data;

import gov.usgs.util.ConfigFile;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.valve3.HttpHandler;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.result.GenericMenu;
import gov.usgs.vdx.client.VDXClient;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.2  2005/10/13 20:35:13  dcervelli
 * Changes for plotterConfig.
 *
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
public class DataHandler implements HttpHandler
{
	private static final String CONFIG_FILE = "data.config";
	
	protected Map<String, DataSourceDescriptor> dataSources;
	protected Map<String, Pool<VDXClient>> vdxClients;
	protected ConfigFile config;
	
	public DataHandler()
	{
		dataSources = new HashMap<String, DataSourceDescriptor>();
		vdxClients = new HashMap<String, Pool<VDXClient>>();
		processConfigFile();
	}
	
	public void processConfigFile()
	{
		config = new ConfigFile(Valve3.getInstance().getConfigPath() + File.separator + CONFIG_FILE);
		
		List<String> vdxs = config.getList("vdx");
		for (String vdx : vdxs)
		{
			System.out.println("VDX: " + vdx);
			ConfigFile sub = config.getSubConfig(vdx);
			int num = Util.stringToInt(sub.getString("clients"), 4);
			Pool<VDXClient> pool = new Pool<VDXClient>();
			for (int i = 0; i < num; i++)
			{
				VDXClient client = new VDXClient(sub.getString("host"), Integer.parseInt(sub.getString("port")));
				pool.checkin(client);
			}
			vdxClients.put(vdx, pool);
		}
		
		List<String> sources = config.getList("source");
		for (String source : sources)
		{
			System.out.println("Data source: " + source);
			ConfigFile sub = config.getSubConfig(source);
			ConfigFile plotterSub = sub.getSubConfig("plotter");
			DataSourceDescriptor dsd = new DataSourceDescriptor(source, sub.getString("vdx"), sub.getString("vdx.source"), sub.getString("plotter"), sub.getConfig(), plotterSub);
			dataSources.put(source, dsd);
		}
	}

	public ConfigFile getConfig()
	{
		return config;
	}
	
	public Pool<VDXClient> getVDXClient(String key)
	{
		return vdxClients.get(key);
	}
	
	public DataSourceDescriptor getDataSourceDescriptor(String key)
	{
		return dataSources.get(key);
	}
	
	public List<DataSourceDescriptor> getDataSources()
	{
		List<DataSourceDescriptor> result = new ArrayList<DataSourceDescriptor>();
		result.addAll(dataSources.values());
		return result;
	}
	
	public Object handle(HttpServletRequest request)
	{
		System.out.println("DataHandler");
		String source = request.getParameter("src");
		System.out.println(source);
		DataSourceDescriptor dsd = dataSources.get(source);
		if (dsd == null)
			return null;  // TODO: throw Valve3Exception
		
		String action = request.getParameter("da"); // da == data action
		if (action == null)
			return null;  // TODO: throw Valve3Exception
		
		// TODO: refactor out cut/paste job
		if (action.equals("genericMenu"))
		{
			Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(dsd.getVDXClientName());
			Map<String, String> params = new HashMap<String, String>();
			params.put("source", dsd.getVDXSource());
			params.put("action", "genericMenu");
			
			VDXClient client = pool.checkout();
			GenericMenu result = null;
			if (client != null)
			{
				List<String> ls = (List<String>)client.getData(params);
				if (ls != null)
					result = new GenericMenu(ls);
			}
			pool.checkin(client);
			return result;
		}
		else if (action.equals("selectors"))
		{
			Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(dsd.getVDXClientName());
			Map<String, String> params = new HashMap<String, String>();
			params.put("source", dsd.getVDXSource());
			params.put("action", "selectors");
			
			// selectors: <unique id>:lon:lat:code:name
			// TODO: get Results back instead of Lists
			VDXClient client = pool.checkout();
			gov.usgs.valve3.result.List result = null;
			if (client != null)
			{
				List<String> ls = (List<String>)client.getData(params);
				result = new gov.usgs.valve3.result.List(ls);
			}
			pool.checkin(client);
			return result;
		}
		
		return null;
	}
}
