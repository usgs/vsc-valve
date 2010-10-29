package gov.usgs.valve3;

import gov.usgs.util.Log;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.util.UtilException;
import gov.usgs.valve3.data.DataHandler;
import gov.usgs.valve3.data.DataSourceDescriptor;
import gov.usgs.valve3.plotter.ChannelMapPlotter;
import gov.usgs.valve3.result.ErrorMessage;
import gov.usgs.valve3.result.RawData;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.vdx.client.VDXClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.TimeZone;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import gov.usgs.vdx.data.Rank;

/**
 * Generates raw data from http request.
 * A request represents exactly one image plot.
 *
 * $Log: not supported by cvs2svn $
 * Revision 1.3  2006/07/24 17:02:09  tparker
 * cleanup old data files
 *
 * Revision 1.2  2006/07/19 16:12:55  tparker
 * Set filename for non-seismic data streams
 *
 * Revision 1.1  2006/05/17 21:56:11  tparker
 * initial commit
 *
 * @author Tom Parker
 */
public class RawDataHandler implements HttpHandler
{
	public static final int MAX_PLOT_WIDTH = 6000;
	public static final int MAX_PLOT_HEIGHT = 6000;
	public static final int DEFAULT_WIDTH_COMPONENT = 610;
	public static final int DEFAULT_HEIGHT_COMPONENT = 140;
	private DataHandler dataHandler;
	private Logger logger;	
	
	/**
	 * Constructor
	 * @param dh data handler for this raw data handler
	 */
	public RawDataHandler(DataHandler dh)
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
		int n = Util.stringToInt(request.getParameter("n"), -1);
		if (n == -1){
			n = Util.stringToInt(Valve3.getInstance().getDefaults().getString("parameter.n"), -1);
			logger.info("Parameter n was set to default value");
		}
		if(n == -1){
			n=1;
			logger.info("Parameter n was set to default value");
		}
		
		String rkNameArg = request.getParameter( "rkName" );
		String outputType = request.getParameter( "o" );
		if ( outputType == null || outputType.equals("") )
			outputType = "csv";
		else if (!(outputType.equals("csv") || outputType.equals("csvnots") || outputType.equals("seed") ) )
			throw new Valve3Exception("Illegal output type");
		else if ( outputType.equals("seed") )
			throw new Valve3Exception("Miniseed unimplemented");
		
		ArrayList<PlotComponent> list = new ArrayList<PlotComponent>(n);
		
		for (int i = 0; i < n; i++)
		{
			PlotComponent component = createComponent(request, i);
			if (component == null)
				continue;
			
			int x = Util.stringToInt(request.getParameter("x." + i), 0);
			if (x < 0 || x > MAX_PLOT_WIDTH){
				throw new Valve3Exception("Illegal x." + i + " value.");
			}
			int y = Util.stringToInt(request.getParameter("y." + i), 0);
			if (y < 0 || y > MAX_PLOT_HEIGHT)
				throw new Valve3Exception("Illegal y." + i + " value.");
			
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

			if ( rkNameArg != null ) 
				component.put( "rkName", rkNameArg );
			component.put( "o", outputType );

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
	 * Get map of available ranks
	 * @param source source name
	 * @param client vdx name
	 * @return mapping from ids to ranks
	 * @throws Valve3Exception
	 */
	protected static Map<Integer, Rank> getRanks(String source, String client) throws Valve3Exception {
		Map<Integer, Rank> ranks;
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", source);
		params.put("action", "ranks");
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(client);
		VDXClient cl = pool.checkout();
		List<String> rks = null;
		try{
			rks = cl.getTextData(params);
		}
		catch(UtilException e){
			throw new Valve3Exception(e.getMessage()); 
		}
		pool.checkin(cl);
		ranks = Rank.fromStringsToMap(rks);
		return ranks;
	}
	
	/**
	 * Handle the given http request and generate raw data type result. 
	 * @see HttpHandler#handle 
	 */
	public Object handle(HttpServletRequest request)
	{
		try
		{
			List<PlotComponent> components = parseRequest(request);
			if (components == null || components.size() <= 0)
				return null;
			
			SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
			df.setTimeZone(TimeZone.getTimeZone("GMT"));
			StringBuffer sb = new StringBuffer();
			String fn_source = null, fn_rank = "";
			int fn_rankID = -1;
			String timeZone = null;
			SimpleDateFormat dfc = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
			Date now = new Date();
			String cmtDate = "";
			String cmtURL = "#URL=" + request.getRequestURL().toString() + "?" + request.getQueryString() + "\n";
			String cmtTimes = "";
			String cmtDataType = null;
			double cmtSampleRate = 0.0;
			Map<Integer, Rank> ranksMap = null;

			for (PlotComponent component : components)
			{
				String source = component.getSource();
				if ( fn_source == null )
					fn_source = source;
				else if ( !fn_source.equals(source) )
					throw new Valve3Exception( "Multi-source export not supported" );
				Plotter plotter = null;
				DataSourceDescriptor dsd = null;
				if (source.equals("channel_map"))
				{
					dsd = dataHandler.getDataSourceDescriptor(component.get("subsrc"));
					plotter = new ChannelMapPlotter();
					if (dsd != null) {
						plotter.setVDXClient(dsd.getVDXClientName());
						plotter.setVDXSource(dsd.getVDXSource());
					}
				}
				else
				{
					dsd = dataHandler.getDataSourceDescriptor(component.getSource());
					plotter = dsd.getPlotter();
				}
				if (plotter != null) {
					if ( cmtDataType == null ) {
						cmtSampleRate = plotter.getSampleRate();
						cmtDataType = plotter.getDataType();
					}
				}
				String rk = component.get( "rk" );
				String ss = component.get( "selectedStation" );
				if ( rk == null && ss == null ) {
					ranksMap = getRanks(dsd.getVDXSource(), dsd.getVDXClientName());
					for (Map.Entry<Integer, Rank> me: ranksMap.entrySet() ) {
						Rank r = me.getValue();
						if ( r.getUserDefault() == 1 ) {
							rk = "" + me.getKey();
							component.put( "rk", rk );
							break;
						}
					}
					logger.info("Ranks acquired");
				}
				if ( rk != null ) {
					int rankID = component.getInt( "rk" );
					if ( rankID != fn_rankID )
						if ( fn_rankID == -1 ) {
							if ( rankID == 0 )
								throw new Valve3Exception( "Mixed-rank export not supported" );
							fn_rankID = rankID;
						} else
							throw new Valve3Exception( "Multi-rank export not supported" );
				}
				if ( fn_rankID != -1 && fn_rank.equals("") ) {
					if ( dsd == null ) {
						fn_rank = "RankNbr" + fn_rankID;
					} else {
						if ( ranksMap == null ) {
							ranksMap = getRanks(dsd.getVDXSource(), dsd.getVDXClientName());
						}
						fn_rank = ranksMap.get(fn_rankID).getName();
					}
				}
				timeZone = component.getTimeZone().getID();
				dfc.setTimeZone(TimeZone.getTimeZone(timeZone));
				cmtDate = dfc.format(now);
				cmtDate = "#reqtime=" + String.format( "%14.3f,%s,%s\n", (now.getTime()*0.001), cmtDate, timeZone);
				double endtime = component.getEndTime();
				cmtTimes = String.format( "#st=%14.3f, et=%14.3f\n", component.getStartTime(endtime), endtime );
				StringBuffer cmt = new StringBuffer(cmtDate + cmtURL + "#source=" + fn_source + "\n" + cmtTimes );
				String outputType = component.get( "o" );
				if (plotter != null) {
					sb.append(plotter.toCSV(component, cmt.toString()));
				} else
					sb.append( cmt.toString() );
			}
			
			
			String fn = df.format(now) + "_" 
				+ fn_source.replaceAll( "-", "_")
				+ (fn_rank==null ? "_NoRank" : fn_rank.replaceAll("-","_")) 
				+ ".csv";
			String filePath = Valve3.getInstance().getApplicationPath() + File.separatorChar + "data" + File.separatorChar + fn;
			try
			{
		        FileOutputStream out = new FileOutputStream(filePath);
		        out.write(sb.toString().getBytes());
		        out.close();
			}
			catch (IOException e)
			{
				logger.info("RawDataHandler file error" );
				throw new Valve3Exception(e.getMessage());
			}

			Pattern p = Pattern.compile(request.getContextPath());
			String fileURL = p.split(request.getRequestURL().toString())[0] + request.getContextPath() + "/data/" + fn;
			RawData rd = new RawData(fileURL, filePath);
			
			Valve3.getInstance().getResultDeleter().addResult(rd);
			return rd;
		}
		catch (Valve3Exception e)
		{
			logger.info("RawDataHandler error " + e.getMessage());
			return new ErrorMessage(e.getMessage());
		}
	}
}
