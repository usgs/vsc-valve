package gov.usgs.volcanoes.valve3;

import gov.usgs.util.Log;
import gov.usgs.util.Util;
import gov.usgs.volcanoes.valve3.data.DataHandler;
import gov.usgs.volcanoes.valve3.data.DataSourceDescriptor;
import gov.usgs.volcanoes.valve3.plotter.ChannelMapPlotter;
import gov.usgs.volcanoes.valve3.plotter.RawDataPlotter;
import gov.usgs.volcanoes.valve3.result.ErrorMessage;
import gov.usgs.volcanoes.valve3.result.RawData;
import gov.usgs.volcanoes.valve3.Valve3Exception;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Logger;
import java.util.Date;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import javax.servlet.http.HttpServletRequest;
import gov.usgs.volcanoes.vdx.data.Rank;

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
	 * @param dh data handler for this raw data handler
	 */
	public RawDataHandler(DataHandler dh)
	{
		logger = Log.getLogger("gov.usgs.volcanoes.valve3");
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
		
		String rkNameArg	= request.getParameter( "rkName" );
		String outputAllArg = Util.stringToString(request.getParameter("outputAll"), "f");
		String outputType	= Util.stringToString(request.getParameter("o"), "csv");
		if (!(outputType.equals("csv") || outputType.equals("csvnots") || outputType.equals("seed")
				|| outputType.equals("xml")|| outputType.equals("json")))
			outputType	= "csv";
		
		for (int i = 0; i < n; i++)
		{
			PlotComponent component = createComponent(request, i);
			if (component == null)
				continue;			

			component.put( "o", outputType );
			component.put( "outputAll", outputAllArg );

			if ( rkNameArg != null ) 
				component.put( "rkName", rkNameArg );
			
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
		
		// RequestServer -- used when checking for openDataServer option
		component.put("requestserver", request.getServerName());
		return component;
	}

	/**
	 * Handle the given http request and generate raw data type result. 
	 * @see HttpHandler#handle 
	 */
	public Object handle(HttpServletRequest request) {
		String ext = "";
		try {
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
			Map<String,String> cmtBits = new LinkedHashMap<String,String>();
			String cmtTimes = "";
			String cmtDataType = null;
			double cmtSampleRate = 0.0;
			Map<Integer, Rank> ranksMap = null;
			boolean miniseed = false;
			String fn = null, filePath = null, outFileName = null, outFilePath = null;
			
			cmtBits.put( "URL", request.getRequestURL().toString() + "?" + request.getQueryString() );

			for (PlotComponent component : components) {
				String source				= component.getSource();
				Plotter plotter				= null;
				DataSourceDescriptor dsd	= null;
				if ( fn_source == null )
					fn_source = source;
				else if ( !fn_source.equals(source) )
					throw new Valve3Exception( "Multi-source export not supported" );
				if (source.equals("channel_map")) {
					plotter	= new ChannelMapPlotter();
					dsd		= dataHandler.getDataSourceDescriptor(component.get("subsrc"));
					if (dsd != null) {
						plotter.setVDXClient(dsd.getVDXClientName());
						plotter.setVDXSource(dsd.getVDXSource());
					}
				} else {
					plotter = dataHandler.getDataSourceDescriptor(component.getSource()).getPlotter();
					dsd		= dataHandler.getDataSourceDescriptor(component.get("src"));
				}
				if (plotter != null) {
					if ( cmtDataType == null ) {
						cmtSampleRate = plotter.getSampleRate();
						cmtDataType = plotter.getDataType();
					}
				}
				String rk = component.get( "rk" );
				if (rk == null) 
					try {
						ranksMap = RawDataPlotter.getRanks(dsd.getVDXSource(), dsd.getVDXClientName());
						for (Map.Entry<Integer, Rank> me: ranksMap.entrySet() ) {
							Rank r = me.getValue();
							if ( r.getUserDefault() == 1 ) {
								rk = "" + me.getKey();
								component.put( "rk", rk );
								break;
							}
						}
						logger.info("Ranks acquired");
					} catch (Exception e) {}
				if (rk != null) {
					int rankID = component.getInt( "rk" );
					if ( rankID != fn_rankID )
						if ( fn_rankID == -1 ) {
							// if ( rankID == 0 )
								// throw new Valve3Exception( "Mixed-rank export not supported" );
							fn_rankID = rankID;
						} else
							throw new Valve3Exception( "Multi-rank export not supported" );
				}
				if ( fn_rankID != -1 && fn_rank.equals("") ) {
					if ( dsd == null ) {
						fn_rank = "RankNbr" + fn_rankID;
					} else {
						if (fn_rankID == 0) {
							fn_rank = "Best Available Rank";
						} else {
							if ( ranksMap == null ) {
								ranksMap = RawDataPlotter.getRanks(dsd.getVDXSource(), dsd.getVDXClientName());
							}
							fn_rank = ranksMap.get(fn_rankID).getName();
						}
					}
				}
				fn_rank = fn_rank.replaceAll("\\s", "");
				timeZone = component.getTimeZone().getID();
				dfc.setTimeZone(TimeZone.getTimeZone(timeZone));
				cmtBits.put( "timezone", timeZone);
				cmtBits.put( "rank", fn_rank );
				cmtBits.put( "reqtime", String.format( "%14.3f,%s,%s", (now.getTime()*0.001), dfc.format(now), timeZone) );
				double endtime = component.getEndTime();
				cmtTimes = String.format( "#st=%14.3f, et=%14.3f\n", component.getStartTime(endtime), endtime );
				cmtBits.put( "st", String.format( "%14.3f", component.getStartTime(endtime) ) );
				cmtBits.put( "et", String.format( "%14.3f", endtime ) );
				cmtBits.put( "source", fn_source );
				String outputType = component.get( "o" );
				fn = df.format(now) + "_" 
					+ fn_source.replaceAll( "-", "_") + "_"
					+ (fn_rank==null ? "_NoRank" : fn_rank.replaceAll("-","_"));
				filePath = Valve3.getInstance().getApplicationPath() + File.separatorChar + "data" + File.separatorChar + fn;
				if ( !miniseed && outputType.equals("seed") )
					miniseed = true;
				else
					ext = outputType;
				if (plotter != null) {
					if ( miniseed ) {
						try {
							outFilePath = filePath + ".zip";
							outFileName = fn  + ".zip";
							FileOutputStream zipdest = new FileOutputStream(outFilePath);
							ZipOutputStream zipout = new ZipOutputStream(new BufferedOutputStream(zipdest));
							ZipEntry zipentry = new ZipEntry(fn + ".msi");
							zipout.putNextEntry(zipentry);
							sb.append(plotter.toExport(component, cmtBits, zipout));
							zipentry = new ZipEntry(fn + ".mst");
							zipout.putNextEntry(zipentry);
							zipout.write(sb.toString().getBytes());
							zipout.close();
						}
						catch (ZipException ez)
						{
							logger.info("RawDataHandler zipfile error" );
							throw new Valve3Exception(ez.getMessage());
						}	
						catch (IOException eio)
						{
							logger.info("RawDataHandler file error" );
							throw new Valve3Exception(eio.getMessage());
						}
					} else 
						sb.append(plotter.toExport(component, cmtBits, null));
				} 
			}
			
			if ( outFilePath == null ) {
				try
				{
					outFilePath = filePath + "." + ext;
					outFileName = fn + "." + ext;
					FileOutputStream out = new FileOutputStream(outFilePath);
					out.write(sb.toString().getBytes());
					out.close();
				}
				catch (IOException e)
				{
					logger.info("RawDataHandler file error" );
					throw new Valve3Exception(e.getMessage());
				}
			}
			String fileURL = "data/" + outFileName;
			RawData rd = new RawData(fileURL, outFilePath);
			
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
