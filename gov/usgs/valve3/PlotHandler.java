package gov.usgs.valve3;

import gov.usgs.util.Util;
import gov.usgs.valve3.data.DataHandler;
import gov.usgs.valve3.result.ErrorMessage;
import gov.usgs.valve3.result.Valve3Plot;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * A request represents exactly one image plot.

 * $Log: not supported by cvs2svn $
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
public class PlotHandler implements HttpHandler
{
	private DataHandler dataHandler;
	
	public PlotHandler(DataHandler dh)
	{
		dataHandler = dh;
	}
	
	protected PlotComponent createComponent(HttpServletRequest request, int i)
	{
		String source = request.getParameter("src." + i);
		if (source == null)
			return null;
		
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
	
	protected List<PlotComponent> parseRequest(HttpServletRequest request)
	{
		int n = Util.stringToInt(request.getParameter("n"), -1);
		if (n == -1)
			return null;
		
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
		List<PlotComponent> components = parseRequest(request);
		if (components == null || components.size() <= 0)
			return null;
		
		Valve3Plot plot = new Valve3Plot(request);
		for (PlotComponent component : components)
		{
			Plotter plotter = dataHandler.getDataSourceDescriptor(component.getSource()).getPlotter();
			if (plotter != null)
			{
				try
				{
					plotter.plot(plot, component);
				}
				catch (Valve3Exception e)
				{
					return new ErrorMessage(e.getMessage());
				}
			}
		}
		Valve3.getInstance().getResultDeleter().addResult(plot);
		return plot;
	}
	
	public static String getRandomFilename()
	{
		return "img" + File.separator + "tmp" + Math.round(Math.random() * 100000) + ".png"; 
	}
}
