package gov.usgs.valve3.data;

import gov.usgs.util.ConfigFile;
import gov.usgs.util.Log;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.valve3.HttpHandler;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.result.GenericMenu;
import gov.usgs.valve3.result.ewRsamMenu;
import gov.usgs.vdx.client.VDXClient;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

/**
 * 
 *
 * @author Dan Cervelli
 */
public class DataHandler implements HttpHandler
{
	private static final String CONFIG_FILE = "data.config";
	private static final int DEFAULT_VDX_CLIENT_TIMEOUT = 60000;
	
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
				int timeout = Util.stringToInt(sub.getString("timeout"), DEFAULT_VDX_CLIENT_TIMEOUT);
				client.setTimeout(timeout);
				pool.checkin(client);
			}
			vdxClients.put(vdx, pool);
		}
		
		List<String> sources = config.getList("source");
		for (String source : sources)
		{
			System.out.println("Data source: " + source);
			ConfigFile sub = config.getSubConfig(source);
			DataSourceDescriptor dsd = new DataSourceDescriptor(source, sub.getString("vdx"), sub.getString("vdx.source"), sub.getString("plotter"), sub);
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
		
		Logger logger = Log.getLogger("gov.usgs.vdx");

		logger.info("entering DataHandler.handle()");
		String source = request.getParameter("src");
		logger.info("src = " + source);
		DataSourceDescriptor dsd = dataSources.get(source);
		if (dsd == null)
			return null;  // TODO: throw Valve3Exception
		
		String action = request.getParameter("da"); // da == data action
		if (action == null)
			return null;  // TODO: throw Valve3Exception

		logger.info("action = " + action);
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
				List<String> ls = client.getTextData(params);
				if (ls != null)
					result = new GenericMenu(ls);
			}
			pool.checkin(client);
			return result;
		}
		else if (action.equals("ewRsamMenu"))
		{
			Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(dsd.getVDXClientName());
			Map<String, String> params = new HashMap<String, String>();
			params.put("source", dsd.getVDXSource());
			params.put("action", "ewRsamMenu");
			
			VDXClient client = pool.checkout();
			ewRsamMenu result = null;
			if (client != null)
			{
				List<String> ls = client.getTextData(params);
				logger.info("params = " + params.toString());
				logger.info("EWRSAMMENU params = " + ls.toString());
				if (ls != null)
					result = new ewRsamMenu(ls);
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
				List<String> ls = client.getTextData(params);
				result = new gov.usgs.valve3.result.List(ls);
			}
			pool.checkin(client);
			return result;
		}
		
		return null;
	}
}
