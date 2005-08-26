
package gov.usgs.valve3;

import gov.usgs.plot.map.GeoLabelSet;
import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.util.ConfigFile;
import gov.usgs.valve3.data.DataHandler;

import java.io.File;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class Valve3 implements ServletContextListener
{
	public static final String VERSION = "3.0.0-a0";
	public static final String BUILD_DATE = "2005-08-25";
	
	public static final String CONFIG_PATH = File.separator + "WEB-INF" + File.separator + "config" + File.separator;
	private static final String CONFIG_FILE = "valve3.config";
	private static Valve3 instance;
	
	private ActionHandler actionHandler;
	private DataHandler dataHandler;
	private String applicationPath;
	private String administrator = "Administrator";
	private String administratorEmail = "admin@usgs.gov";
	private String installationTitle = "Valve Installation";
	
	private GeoImageSet imageSet;
	private GeoLabelSet labelSet;
	
	private ResultDeleter resultDeleter;
	
	public Valve3()
	{
		instance = this;
		resultDeleter = new ResultDeleter();
		resultDeleter.start();
	}
	
	public void processConfigFile()
	{
		ConfigFile config = new ConfigFile(applicationPath + File.separator + CONFIG_PATH + File.separator + CONFIG_FILE);
		administrator = config.getString("admin.name");
		administratorEmail = config.getString("admin.email");
		installationTitle = config.getString("title");
		
		imageSet = new GeoImageSet(config.getString("imageIndex"));
		String ics = config.getString("imageCacheSize");
		if (ics != null)
			imageSet.setMaxLoadedImagesSize(Integer.parseInt(ics));
		labelSet = new GeoLabelSet(config.getString("labelIndex"));
	}
	
	public ResultDeleter getResultDeleter()
	{
		return resultDeleter;
	}
	
	public static Valve3 getInstance()
	{
		return instance;
	}
	
	public DataHandler getDataHandler()
	{
		if (dataHandler == null)
			dataHandler = new DataHandler();
		
		return dataHandler;
	}
	
	public ActionHandler getActionHandler()
	{
		if (actionHandler == null)
		{
			actionHandler = new ActionHandler("a");
			DataHandler dh = getDataHandler();
			actionHandler.getHandlers().put("data", dh);
			actionHandler.getHandlers().put("plot", new PlotHandler(dh));
			actionHandler.getHandlers().put("menu", new MenuHandler(dh));
		}
		
		return actionHandler;
	}

	public String getConfigPath()
	{
		return applicationPath + File.separator + CONFIG_PATH;
	}
	
	public String getApplicationPath()
	{
		return applicationPath;
	}

	public String getAdministrator()
	{
		return administrator;
	}
	
	public String getAdministratorEmail()
	{
		return administratorEmail;
	}
	
	public String getInstallationTitle()
	{
		return installationTitle;
	}
	
	public GeoImageSet getGeoImageSet()
	{
		return imageSet;
	}
	
	public GeoLabelSet getGeoLabelSet()
	{
		return labelSet;
	}
	
	public void contextInitialized(ServletContextEvent sce)
	{
		System.out.println("Valve " + VERSION + ", " + BUILD_DATE + " initialization");
		applicationPath = sce.getServletContext().getRealPath("");
		System.out.println("Application path: " + applicationPath);
		processConfigFile();
	}

	public void contextDestroyed(ServletContextEvent sce)
	{
		resultDeleter.kill();
		resultDeleter.deleteResults(true);
	}
}
