package gov.usgs.valve3;

import gov.usgs.util.Log;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.util.UtilException;
import gov.usgs.valve3.data.DataHandler;
import gov.usgs.valve3.data.DataSourceDescriptor;
import gov.usgs.valve3.plotter.ChannelMapPlotter;
import gov.usgs.valve3.result.ErrorMessage;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.ExportConfig;

import java.io.File;
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
	public static final int MAX_PLOT_WIDTH = 6000;
	public static final int MAX_PLOT_HEIGHT = 6000;
	public static final int DEFAULT_WIDTH_COMPONENT = 610;
	public static final int DEFAULT_HEIGHT_COMPONENT = 140;
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
//		if (tz == null)
//			throw new Valve3Exception("Can't find 'tz' parameter");
		if ( tz==null || tz.equals("") )
			tz = "UTC";
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
	 * Process HttpServletRequest and generate list of {@link PlotComponent}s
	 * @param request request to process
	 * @return list of generated PlotComponents
	 * @throws Valve3Exception
	 */
	protected List<PlotComponent> parseRequest(HttpServletRequest request) throws Valve3Exception
	{
		int n = Util.stringToInt(request.getParameter("n"), -1);
		if (n == -1){
			n = Util.stringToInt(Valve3.getInstance().getDefaults().getString("parameter.n"), -1);
			logger.info("Parameter n was set to default value");
		}
		if(n == -1){
			n=1;
			logger.info("Parameter n was set to default value");
		}
		ArrayList<PlotComponent> list = new ArrayList<PlotComponent>(n);
		
		for (int i = 0; i < n; i++)
		{
			PlotComponent component = createComponent(request, i);
			if (component == null)
				continue;
			
			String source = request.getParameter("src." + i);
			Map<String, String> params = new LinkedHashMap<String, String>();
			params.put("source", source);
			params.put("action", "exportinfo");
			Valve3 v3 = Valve3.getInstance();
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
				}
				pool.checkin(cl);
				ec = new ExportConfig( ecs );
				v3.putExportConfig(source, ec);
			}
			int x = Util.stringToInt(request.getParameter("x." + i), 0);
			int y = Util.stringToInt(request.getParameter("y." + i), 0);
			int w = Util.stringToInt(request.getParameter("w." + i), -1);
			if(w==-1){
				w=Util.stringToInt(Valve3.getInstance().getDefaults().getString("parameter.component.w"), -1);
				logger.info("Parameter w." + i + " was set to default value");
			}
			if(w==-1){
				w=DEFAULT_WIDTH_COMPONENT;
				logger.info("Illegal w." + i + " parameter value, was set to default");
			}
			if (w <= 0 || w > MAX_PLOT_WIDTH)
				throw new Valve3Exception("Illegal w." + i + ".");
			
			int h = Util.stringToInt(request.getParameter("h." + i), -1);
			if(h==-1){
				w=Util.stringToInt(Valve3.getInstance().getDefaults().getString("parameter.component.h"), -1);
				logger.info("Parameter h." + i + " was set to default value");
			}
			if(h==-1){
				h=DEFAULT_WIDTH_COMPONENT;
				logger.info("Illegal h." + i + " parameter value, was set to default");
			}
			if (h <= 0 || h > MAX_PLOT_HEIGHT)
				throw new Valve3Exception("Illegal h." + i + ".");
			component.setBoxX(x);
			component.setBoxY(y);
			component.setBoxWidth(w);
			component.setBoxHeight(h);
			component.setExportable( ec.isExportable() );
			list.add(component);
		}
		return list;
	}
	
	/**
	 * Check if component's boundaries compatible to this plot
	 * @param components List of PlotComponents to check
	 * @param plot Valve3Plot to check against
	 * @throws Valve3Exception raised if incompatible
	 */
	public void checkComponents(List<PlotComponent> components, Valve3Plot plot) throws Valve3Exception{
		int i=0;
		for(PlotComponent component: components){
			int x = component.getBoxX();
			if (x < 0 || x > plot.getWidth()){
				throw new Valve3Exception("Illegal x." + i + " value.");
			}
			int y = component.getBoxY();
			if (y < 0 || y > plot.getHeight())
				throw new Valve3Exception("Illegal y." + i + " value.");
			i++;
		}
	}
	
	/**
	 * Handle the given http request and generate a plot. 
	 * @see HttpHandler#handle 
	 */
	public Object handle(HttpServletRequest request)
	{
		try
		{
			List<PlotComponent> components = parseRequest(request);
			if (components == null || components.size() <= 0)
				return null;
			Valve3Plot plot = new Valve3Plot(request, components.size());
			checkComponents(components, plot);
			boolean exportable = false;
			for (PlotComponent component : components)
			{
				String source = component.getSource();
				Plotter plotter = null;
				if ( component.getExportable() )
					plot.setExportable( true );
				if (source.equals("channel_map"))
				{
					plotter = new ChannelMapPlotter();
					DataSourceDescriptor dsd = dataHandler.getDataSourceDescriptor(component.get("subsrc"));
					if (dsd != null)
					{
						plotter.setVDXClient(dsd.getVDXClientName());
						plotter.setVDXSource(dsd.getVDXSource());
					}
				}
				else
				{
					plotter = dataHandler.getDataSourceDescriptor(component.getSource()).getPlotter();
				}
				if (plotter != null)
					plotter.plot(plot, component);
			}
			Valve3.getInstance().getResultDeleter().addResult(plot);
			return plot;
		}
		catch (Valve3Exception e)
		{
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
