package gov.usgs.valve3;

import gov.usgs.util.Log;
import gov.usgs.util.Pool;
import gov.usgs.util.Time;
import gov.usgs.util.Util;
import gov.usgs.util.UtilException;
import gov.usgs.valve3.data.DataHandler;
import gov.usgs.valve3.data.DataSourceDescriptor;
import gov.usgs.valve3.plotter.ChannelMapPlotter;
import gov.usgs.valve3.result.ErrorMessage;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.valve3.Valve3;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.ExportConfig;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

/**
 * Generates valve plot from http request.
 * A request represents exactly one image plot.
 *
 * $Log: not supported by cvs2svn $
 * Revision 1.5  2005/09/13 17:54:44  dcervelli
 * Fixed bugs from x > w and y > h.
 *
 * Revision 1.4  2005/09/02 22:37:54  dcervelli
 * Support for ChannelMapPlotter.
 *
 * Revision 1.3  2005/08/29 22:52:54  dcervelli
 * Input validation.
 *
 * Revision 1.2  2005/08/28 19:00:20  dcervelli
 * Eliminated warning.  Added support for plotting exceptions and notifying clients about them.
 *
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
public class PlotHandler implements HttpHandler
{
	
	// Medium.  please refer to STANDARD_SIZES as defined in plot.js
	public static final int DEFAULT_COMPONENT_WIDTH		= 750;
	public static final int DEFAULT_COMPONENT_HEIGHT	= 240;
	public static final int DEFAULT_COMPONENT_TOP		= 20;
	public static final int DEFAULT_COMPONENT_LEFT		= 75;
	public static final int DEFAULT_COMPONENT_MAPHEIGHT	= 900;	
	public static final int MAX_PLOT_WIDTH				= 6000;
	public static final int MAX_PLOT_HEIGHT				= 50000;
	
	private DataHandler dataHandler;
	private Logger logger;	

	/**
	 * Constructor
	 * @param dh data handler for this plot handler
	 */
	public PlotHandler(DataHandler dh)
	{
		logger = Log.getLogger("gov.usgs.valve3");
		dataHandler = dh;
	}
	
	/**
	 * Process HttpServletRequest and generate list of {@link PlotComponent}s
	 * @param request request to process
	 * @return list of generated PlotComponents
	 * @throws Valve3Exception
	 */
	protected List<PlotComponent> parseRequest(HttpServletRequest request) throws Valve3Exception
	{
		int n = Util.stringToInt(request.getParameter("n"), 1);
		ArrayList<PlotComponent> list = new ArrayList<PlotComponent>(n);
		
		for (int i = 0; i < n; i++)
		{
			PlotComponent component = createComponent(request, i);
			if (component == null)
				continue;
			
			String source = request.getParameter("src." + i);
			if ( source.equals( "channel_map" ) ) {
				String subsrc = request.getParameter("subsrc." + i);
				component.put( "subsrc", subsrc );
			} else {
				Valve3 v3 = Valve3.getInstance();
				Map<String, String> params = new LinkedHashMap<String, String>();
				params.put("source", source);
				params.put("action", "exportinfo");
				ExportConfig ec = v3.getExportConfig(source);
				if ( ec == null ) {
					DataSourceDescriptor dsd = dataHandler.getDataSourceDescriptor(source);
					if (dsd == null)
						throw new Valve3Exception("Missing data source for " + source);
					ec = v3.getExportConfig("");
					ec.parameterize( params );
					Pool<VDXClient> pool = v3.getDataHandler().getVDXClient(dsd.getVDXClientName());
					VDXClient cl = pool.checkout();
					java.util.List<String> ecs = null;
					try {
						ecs = cl.getTextData(params);
					} catch (UtilException e){
						ecs = new ArrayList<String>();
					} finally {
						pool.checkin(cl);
					}
					ec = new ExportConfig( ecs );
					v3.putExportConfig(source, ec);
				}
				component.setExportable( ec.isExportable() );
				
				String sSt = request.getParameter("st.0");
				double dSt = Double.parseDouble(sSt);
				if (dSt > 0) {
					// Not relative value, convert to j2k and compare against et
					dSt = Time.parse("yyyyMMddHHmmssSSS", sSt);
					String sEt = request.getParameter("et.0");
					double dEt;
					if (sEt.equalsIgnoreCase("N"))
						dEt = Util.nowJ2K();
					else
						dEt = Time.parse("yyyyMMddHHmmssSSS", sEt);
					
					if (dEt < dSt)
						throw new Valve3Exception("Start time must be prior to end time. St: " + dSt + "; Et: " + dEt);
				}
			}
			
			int w = Util.stringToInt(request.getParameter("w." + i), DEFAULT_COMPONENT_WIDTH);
			if (w <= 0 || w > MAX_PLOT_WIDTH) {
				throw new Valve3Exception("Illegal w." + i + " parameter.  Must be between 0 and " + MAX_PLOT_WIDTH);
			}
			
			int h = Util.stringToInt(request.getParameter("h." + i), DEFAULT_COMPONENT_HEIGHT);
			if (h <= 0 || h > MAX_PLOT_HEIGHT) {
				throw new Valve3Exception("Illegal h." + i + " parameter.  Must be between 0 and " + MAX_PLOT_HEIGHT);
			}
			
			int mh = Util.stringToInt(request.getParameter("mh." + i), DEFAULT_COMPONENT_MAPHEIGHT);
			if (mh < 0){
				throw new Valve3Exception("Illegal mh." + i + " parameter.  Must be greater than 0");
			}
			
			int x = Util.stringToInt(request.getParameter("x." + i), DEFAULT_COMPONENT_LEFT);
			if (x < 0 || x > w){
				throw new Valve3Exception("Illegal x." + i + " parameter.  Must be between 0 and " + w);
			}
			
			int y = Util.stringToInt(request.getParameter("y." + i), DEFAULT_COMPONENT_TOP);
			if (y < 0 || y > h){
				throw new Valve3Exception("Illegal y." + i + " parameter.  Must be between 0 and " + h);
			}

			component.setBoxWidth(w);
			component.setBoxHeight(h);
			component.setBoxMapHeight(mh);
			component.setBoxX(x);
			component.setBoxY(y);
			
			list.add(component);
		}
		return list;
	}
	
	/**
	 * Process HttpServletRequest and generate one {@link PlotComponent}
	 * @param request request to process
	 * @param i serial number of source in the request 
	 * @return generated PlotComponent
	 * @throws Valve3Exception
	 */
	protected PlotComponent createComponent(HttpServletRequest request, int i) throws Valve3Exception
	{
		String source = request.getParameter("src." + i);
		if (source == null || source.length()==0)
			throw new Valve3Exception("Illegal src." + i + " value.");
		String tz = request.getParameter("tz");
		if ( tz==null || tz.equals("") ) {
			tz = Valve3.getInstance().getTimeZoneAbbr();
			logger.info( "Illegal/missing tz parameter; using default value" );
		}	
		TimeZone timeZone = TimeZone.getTimeZone(tz);
		PlotComponent component = new PlotComponent(source, timeZone);

		// Not using generics because HttpServletRequest is Java 1.4
		Map parameters = request.getParameterMap();
		for (Object k : parameters.keySet())
		{
			String key = (String)k;
			if (key.endsWith("." + i))
			{
				String[] values = (String[])parameters.get(key);
				if (values == null || values.length <= 0)
					continue;
				String value = values[0];
				key = key.substring(0, key.indexOf('.'));
				component.put(key, value);
			}
		}
		return component;
	}
	
	/**
	 * Handle the given http request and generate a plot. 
	 * @see HttpHandler#handle 
	 */
	public Object handle(HttpServletRequest request) {
		try {
			List<PlotComponent> components = parseRequest(request);
			if (components == null || components.size() <= 0)
				return null;
			
			Valve3Plot plot = new Valve3Plot(request, components.size());
			for (PlotComponent component : components) {
				String source				= component.getSource();
				Plotter plotter				= null;
				DataSourceDescriptor dsd	= null;
				if (component.getExportable())
					plot.setExportable( true );
				if (source.equals("channel_map")) {
					plotter	= new ChannelMapPlotter();
					dsd		= dataHandler.getDataSourceDescriptor(component.get("subsrc"));
					if (dsd != null) {
						plotter.setVDXClient(dsd.getVDXClientName());
						plotter.setVDXSource(dsd.getVDXSource());
					}
				} else {
					plotter = dataHandler.getDataSourceDescriptor(component.getSource()).getPlotter();
				}
				if (plotter != null)
					try {
						plotter.plot(plot, component);
					} catch (Exception e) {
						throw new Valve3Exception(e.getMessage());
					}
			}
			Valve3.getInstance().getResultDeleter().addResult(plot);
			return plot;
		} catch (Valve3Exception e) {
			logger.severe(e.getMessage());
			return new ErrorMessage(e.getMessage());
		}
	}
	
	/**
	 * Yield a random file name
	 * @return random file name in the img/ directory with .png extension
	 */
	public static String getRandomFilename()
	{
		return "img" + File.separator + "tmp" + Math.round(Math.random() * 100000) + ".png"; 
	}
}
