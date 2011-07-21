
package gov.usgs.valve3;

import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.plot.map.GeoLabelSet;
import gov.usgs.util.ConfigFile;
import gov.usgs.util.Log;
import gov.usgs.vdx.ExportConfig;
import gov.usgs.valve3.data.DataHandler;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Program startup class, it is configured in the deployment
 * descriptor of the web application. Receives notifications about 
 * changes to the web application's servlet context.
 * Keeps data about application state.
 *   
 * $Log: not supported by cvs2svn $
 * Revision 1.10  2006/11/10 01:13:46  tparker
 * version bump
 *
 * Revision 1.9  2006/05/17 21:57:00  tparker
 * Add handler for raw data
 *
 * Revision 1.8  2006/04/09 21:03:54  dcervelli
 * Added hook to MenuHandler.
 *
 * Revision 1.7  2005/10/27 00:16:35  tparker
 * Bug #68
 *
 * Revision 1.6  2005/10/26 18:18:15  tparker
 * Add logging related to Bug #68
 *
 * Revision 1.5  2005/10/26 17:59:15  tparker
 * Add timezone for Bug #68
 *
 * Revision 1.4  2005/10/14 21:08:04  dcervelli
 * Version bump.
 *
 * Revision 1.3  2005/10/13 20:34:56  dcervelli
 * Version bump.
 *
 * Revision 1.2  2005/08/29 22:53:09  dcervelli
 * Logging.
 *
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
public class Valve3 implements ServletContextListener
{
	public static final String VERSION = "3.3.0";
	public static final String BUILD_DATE = "2010-02-25";
	
	public static final String CONFIG_PATH = File.separator + "WEB-INF" + File.separator + "config" + File.separator;
	private static final String CONFIG_FILE = "valve3.config";
	private static Valve3 instance;
	
	private ActionHandler actionHandler;
	private DataHandler dataHandler;
	private MenuHandler menuHandler;
	private String applicationPath;
	private String administrator = "Administrator";
	private String administratorEmail = "admin@usgs.gov";
	private String installationTitle = "Valve Installation";
	private String timeZoneAbbr = "UTC";
	
	private GeoImageSet imageSet;
	private GeoLabelSet labelSet;
	private ConfigFile defaults;
	
	private ResultDeleter resultDeleter;

	private Logger logger;
	
	private HashMap<String,ExportConfig> exportConfigs;
	
	/**
	 * Default constructor
	 */
	public Valve3()
	{
		instance = this;
		logger = Log.getLogger("gov.usgs.valve3");
		Log.getLogger("gov.usgs.util").setLevel(Level.INFO);
		Log.getLogger("gov.usgs.net").setLevel(Level.SEVERE);
		resultDeleter = new ResultDeleter();
		resultDeleter.start();
		exportConfigs = new HashMap<String,ExportConfig>();
	}
	
	/**
	 * Process configuration files and performs initialization
	 */
	public void processConfigFile()
	{
		ConfigFile config = new ConfigFile(applicationPath + File.separator + CONFIG_PATH + File.separator + CONFIG_FILE);
		administrator = config.getString("admin.name");
		logger.config("admin.name: " + administrator);
		administratorEmail = config.getString("admin.email");
		logger.config("admin.email: " + administratorEmail);
		installationTitle = config.getString("title");
		logger.config("title: " + installationTitle);
		timeZoneAbbr = config.getString("timeZoneAbbr");
		if ( timeZoneAbbr == null )
			timeZoneAbbr = "UTC";
		logger.config("timeZoneAbbr: " + timeZoneAbbr);

		ExportConfig ec = new ExportConfig( "", config );
		exportConfigs.put( "", ec );

		imageSet = new GeoImageSet(config.getString("imageIndex"));
		String ics = config.getString("imageCacheSize");
		if (ics != null)
			imageSet.setMaxLoadedImagesSize(Integer.parseInt(ics));
		labelSet = new GeoLabelSet(config.getString("labelIndex"));
		defaults = config.getSubConfig("defaults");
	}
	
	/**
	 * Getter for result deleter
	 * @return result deleter
	 */
	public ResultDeleter getResultDeleter()
	{
		return resultDeleter;
	}
	
	/**
	 * Implementation of Singleton pattern
	 * @return Valve3
	 */
	public static Valve3 getInstance()
	{
		return instance;
	}
	
	/**
	 * Getter for menu handler
	 * @return menu handler
	 */
	public MenuHandler getMenuHandler()
	{
		if (menuHandler == null)
			menuHandler = new MenuHandler(getDataHandler());
		
		return menuHandler;
	}

	/**
	 * Getter for default values configuration
	 * @return config file
	 */
	public ConfigFile getDefaults(){
		return defaults;
	}
	
	/**
	 * Getter for data handler
	 * @return data handler
	 */
	public DataHandler getDataHandler()
	{
		if (dataHandler == null)
			dataHandler = new DataHandler();
		
		return dataHandler;
	}

	/**
	 * Getter for action handler
	 * @return action handler
	 */
	public ActionHandler getActionHandler()
	{
		if (actionHandler == null)
		{
			actionHandler = new ActionHandler("a");
			DataHandler dh = getDataHandler();
			actionHandler.getHandlers().put("data", dh);
			actionHandler.getHandlers().put("rawData", new RawDataHandler(dh));
			actionHandler.getHandlers().put("plot", new PlotHandler(dh));
			MenuHandler mh = getMenuHandler();
			actionHandler.getHandlers().put("menu", mh);
		}
		
		return actionHandler;
	}

	/** 
	 * Getter for config path
	 * @return full directory name for application's configuration files
	 */
	public String getConfigPath()
	{
		return applicationPath + File.separator + CONFIG_PATH;
	}
	
	/**
	 * Getter for application path
	 * @return full real path to deployed application
	 */
	public String getApplicationPath()
	{
		return applicationPath;
	}

	/**
	 * Getter for administrator's name
	 * @return application's administrator name
	 */
	public String getAdministrator()
	{
		return administrator;
	}

	/**
	 * Getter for admin email
	 * @return application's administrator email
	 */
	public String getAdministratorEmail()
	{
		return administratorEmail;
	}
	
	/**
	 * Getter for installation title
	 * @return Title of installation displayed on start page
	 */
	public String getInstallationTitle()
	{
		return installationTitle;
	}
	
	/**
	 * Getter for time zone abbreviation
	 * @return Abbreviated name of default time zone
	 */
	public String getTimeZoneAbbr()
	{
		return timeZoneAbbr;
	}
	
	/**
	 * Yield time zone offset for date
	 * @param date Time moment to compute offset
	 * @return offset between current time zone and UTC in seconds, on given time moment
	 */
	public double getTimeZoneOffset(Date date) {
		TimeZone timeZone = TimeZone.getTimeZone(getTimeZoneAbbr());
		return timeZone.getOffset(date.getTime())/1000.0;
	}
	
	/**
	 * Getter for geo image set
	 * @return geo image set
	 */
	public GeoImageSet getGeoImageSet()
	{
		return imageSet;
	}

	/**
	 * Getter for geo labels set
	 * @return geo label set
	 */
	public GeoLabelSet getGeoLabelSet()
	{
		return labelSet;
	}

	/**
	 * Getter for export config for data source
	 * @param source data source name
	 * @return export config
	 */
	public ExportConfig getExportConfig( String source ) 
	{
		return exportConfigs.get(source);
	}
	/**
	 * Setter for export config
	 * @param source data source name
	 * @param ec export config
	 */
	public void putExportConfig( String source, ExportConfig ec ) 
	{
		exportConfigs.put(source, ec);
	}

	/**
	 * @see ServletContextListener#contextInitialized
	 */
	public void contextInitialized(ServletContextEvent sce)
	{
		logger.info("Valve " + VERSION + ", " + BUILD_DATE + " initialization");
		applicationPath = sce.getServletContext().getRealPath("");
		processConfigFile();
	}

	/**
	 * @see ServletContextListener#contextDestroyed
	 */
	public void contextDestroyed(ServletContextEvent sce)
	{
		resultDeleter.kill();
		resultDeleter.deleteResults(true);
	}
}
