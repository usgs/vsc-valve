package gov.usgs.valve3;

import gov.usgs.util.Util;
import gov.usgs.valve3.data.DataHandler;
import gov.usgs.valve3.data.DataSourceDescriptor;
import gov.usgs.valve3.plotter.ChannelMapPlotter;
import gov.usgs.valve3.result.ErrorMessage;
import gov.usgs.valve3.result.RawData;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;

/**
 * A request represents exactly one image plot.

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
	private DataHandler dataHandler;
	
	public RawDataHandler(DataHandler dh)
	{
		dataHandler = dh;
	}
	
	protected PlotComponent createComponent(HttpServletRequest request, int i) throws Valve3Exception
	{
		String source = request.getParameter("src." + i);
		if (source == null)
			throw new Valve3Exception("Illegal src value.");
		
		PlotComponent component = new PlotComponent(source);

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
	
	protected List<PlotComponent> parseRequest(HttpServletRequest request) throws Valve3Exception
	{
		int n = Util.stringToInt(request.getParameter("n"), -1);
		if (n == -1)
			throw new Valve3Exception("Illegal n value.");
		
		ArrayList<PlotComponent> list = new ArrayList<PlotComponent>(n);
		
		for (int i = 0; i < n; i++)
		{
			PlotComponent component = createComponent(request, i);
			if (component == null)
				continue;
			int x = Util.stringToInt(request.getParameter("x." + i), 75);
			int y = Util.stringToInt(request.getParameter("y." + i), 19);
			int w = Util.stringToInt(request.getParameter("w." + i), 610);
			int h = Util.stringToInt(request.getParameter("h." + i), 140);
			if (x < 0)
				throw new Valve3Exception("Illegal x value.");
			if (y < 0)
				throw new Valve3Exception("Illegal y value.");
			if (w <= 0 || w > MAX_PLOT_WIDTH)
				throw new Valve3Exception("Illegal width.");
			if (h <= 0 || h > MAX_PLOT_HEIGHT)
				throw new Valve3Exception("Illegal height.");
			component.setBoxX(x);
			component.setBoxY(y);
			component.setBoxWidth(w);
			component.setBoxHeight(h);
			list.add(component);
		}
		return list;
	}
	
	public Object handle(HttpServletRequest request)
	{

		try
		{
			List<PlotComponent> components = parseRequest(request);
			if (components == null || components.size() <= 0)
				return null;
			
			String fn = "";
			StringBuffer sb = new StringBuffer();
			for (PlotComponent component : components)
			{
				String source = component.getSource();
				Plotter plotter = null;
				if (source.equals("channel_map"))
				{
					DataSourceDescriptor dsd = dataHandler.getDataSourceDescriptor(component.get("subsrc"));
					if (dsd == null)
						throw new Valve3Exception("Unknown data source.");
					
					plotter = new ChannelMapPlotter();
					plotter.setVDXClient(dsd.getVDXClientName());
					plotter.setVDXSource(dsd.getVDXSource());
				}
				else
				{
					plotter = dataHandler.getDataSourceDescriptor(component.getSource()).getPlotter();
				}
				if (plotter != null)
					sb.append(plotter.toCSV(component));
				
				SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
				df.setTimeZone(TimeZone.getTimeZone("GMT"));
				
				if (fn.length() > 0)
					fn += "-";
				
				if (component.get("ch") != null)
					fn = component.get("ch").replace('$','_');
				else
					fn = component.get("src");
					
				fn += "-" + df.format(Util.j2KToDate(component.getStartTime(component.getEndTime()) ));
				fn += "-" + df.format(Util.j2KToDate(component.getEndTime()));
			}
			
			fn += ".csv";
			String filePath = Valve3.getInstance().getApplicationPath() + File.separatorChar + "data" + File.separatorChar + fn;
			try
			{
		        FileOutputStream out = new FileOutputStream(filePath);
		        out.write(sb.toString().getBytes());
		        out.close();
			}
			catch (IOException e)
			{
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
			return new ErrorMessage(e.getMessage());
		}
		
		
	}
}
