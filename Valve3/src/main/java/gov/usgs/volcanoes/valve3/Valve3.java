package gov.usgs.volcanoes.valve3;

import gov.usgs.volcanoes.core.configfile.ConfigFile;
import gov.usgs.volcanoes.core.legacy.plot.map.GeoImageSet;
import gov.usgs.volcanoes.core.legacy.plot.map.GeoLabelSet;
import gov.usgs.volcanoes.valve3.data.DataHandler;
import gov.usgs.volcanoes.vdx.ExportConfig;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Program startup class, it is configured in the deployment
 * descriptor of the web application. Receives notifications about
 * changes to the web application's servlet context.
 * Keeps data about application state.
 *
 * @author Dan Cervelli
 * @author Tom Parker
 * @author Bill Tollett
 */
public class Valve3 implements ServletContextListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(Valve3.class);
  private static final String CONFIG_PATH = File.separator + "WEB-INF"
                                            + File.separator + "config" + File.separator;
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
  private String openDataUrl = "";

  private GeoImageSet imageSet;
  private GeoLabelSet labelSet;
  private ConfigFile defaults;

  private ResultDeleter resultDeleter;

  private HashMap<String, ExportConfig> exportConfigs;

  /**
   * Default constructor.
   */
  public Valve3() {
    instance = this;
    org.apache.log4j.Logger.getLogger("gov.usgs.volcanoes.core.util").setLevel(Level.INFO);
    org.apache.log4j.Logger.getLogger("gov.usgs.volcanoes.core.legacy.net").setLevel(Level.ERROR);
    resultDeleter = new ResultDeleter();
    resultDeleter.start();
    exportConfigs = new HashMap<String, ExportConfig>();
  }

  /**
   * Process configuration files and performs initialization.
   */
  public void processConfigFile() {
    ConfigFile config = new ConfigFile(applicationPath + File.separator + CONFIG_PATH
                                        + File.separator + CONFIG_FILE);
    administrator = config.getString("admin.name");
    LOGGER.info("admin.name: {}", administrator);
    administratorEmail = config.getString("admin.email");
    LOGGER.info("admin.email: {}", administratorEmail);
    installationTitle = config.getString("title");
    LOGGER.info("title: {}", installationTitle);
    timeZoneAbbr = config.getString("timeZoneAbbr");
    if (timeZoneAbbr == null) {
      timeZoneAbbr = "UTC";
    }
    LOGGER.info("timeZoneAbbr: {}", timeZoneAbbr);

    ExportConfig ec = new ExportConfig("", config);
    exportConfigs.put("", ec);
    openDataUrl = config.getString("openDataURL");
    if (openDataUrl == null) {
      openDataUrl = "";
    }
    LOGGER.info("openDataURL: {}", openDataUrl);

    imageSet = new GeoImageSet(config.getString("imageIndex"));
    String ics = config.getString("imageCacheSize");
    if (ics != null) {
      imageSet.setMaxLoadedImagesSize(Integer.parseInt(ics));
    }
    labelSet = new GeoLabelSet(config.getString("labelIndex"));
    defaults = config.getSubConfig("defaults");
  }

  /**
   * Getter for result deleter.
   *
   * @return result deleter
   */
  public ResultDeleter getResultDeleter() {
    return resultDeleter;
  }

  /**
   * Implementation of Singleton pattern.
   *
   * @return Valve3
   */
  public static Valve3 getInstance() {
    return instance;
  }

  /**
   * Getter for menu handler.
   *
   * @return menu handler
   */
  public MenuHandler getMenuHandler() {
    if (menuHandler == null) {
      menuHandler = new MenuHandler(getDataHandler());
    }

    return menuHandler;
  }

  /**
   * Getter for default values configuration.
   *
   * @return config file
   */
  public ConfigFile getDefaults() {
    return defaults;
  }

  /**
   * Getter for data handler.
   *
   * @return data handler
   */
  public DataHandler getDataHandler() {
    if (dataHandler == null) {
      dataHandler = new DataHandler();
    }

    return dataHandler;
  }

  /**
   * Getter for action handler.
   *
   * @return action handler
   */
  public ActionHandler getActionHandler() {
    if (actionHandler == null) {
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
   * Getter for config path.
   *
   * @return full directory name for application's configuration files
   */
  public String getConfigPath() {
    return applicationPath + File.separator + CONFIG_PATH;
  }

  /**
   * Getter for application path.
   *
   * @return full real path to deployed application
   */
  public String getApplicationPath() {
    return applicationPath;
  }

  /**
   * Getter for administrator's name.
   *
   * @return application's administrator name
   */
  public String getAdministrator() {
    return administrator;
  }

  /**
   * Getter for admin email.
   *
   * @return application's administrator email
   */
  public String getAdministratorEmail() {
    return administratorEmail;
  }

  /**
   * Getter for installation title.
   *
   * @return Title of installation displayed on start page
   */
  public String getInstallationTitle() {
    return installationTitle;
  }

  /**
   * Getter for time zone abbreviation.
   *
   * @return Abbreviated name of default time zone
   */
  public String getTimeZoneAbbr() {
    return timeZoneAbbr;
  }

  /**
   * Yield time zone offset for date.
   *
   * @param date Time moment to compute offset
   * @return offset between current time zone and UTC in seconds, on given time moment
   */
  public double getTimeZoneOffset(Date date) {
    TimeZone timeZone = TimeZone.getTimeZone(getTimeZoneAbbr());
    return timeZone.getOffset(date.getTime()) / 1000.0;
  }

  /**
   * Getter for geo image set.
   *
   * @return geo image set
   */
  public GeoImageSet getGeoImageSet() {
    return imageSet;
  }

  /**
   * Getter for geo labels set.
   *
   * @return geo label set
   */
  public GeoLabelSet getGeoLabelSet() {
    return labelSet;
  }

  /**
   * Getter for open data server address.
   */
  public String getOpenDataUrl() {
    return openDataUrl;
  }

  /**
   * Getter for export config for data source.
   *
   * @param source data source name
   * @return export config
   */
  public ExportConfig getExportConfig(String source) {
    return exportConfigs.get(source);
  }

  /**
   * Setter for export config.
   *
   * @param source data source name
   * @param ec     export config
   */
  public void putExportConfig(String source, ExportConfig ec) {
    exportConfigs.put(source, ec);
  }

  /**
   * Initialize.
   *
   * @see ServletContextListener#contextInitialized
   */
  public void contextInitialized(ServletContextEvent sce) {
    LOGGER.info("Valve {} initialization", Version.VERSION_STRING);
    applicationPath = sce.getServletContext().getRealPath("");
    processConfigFile();
  }

  /**
   * Kill.
   *
   * @see ServletContextListener#contextDestroyed
   */
  public void contextDestroyed(ServletContextEvent sce) {
    resultDeleter.kill();
    resultDeleter.deleteResults(true);
  }
}
