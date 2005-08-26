package gov.usgs.valve3;

import gov.usgs.util.Util;
import gov.usgs.valve3.data.DataHandler;
import gov.usgs.valve3.result.Valve3Plot;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * A request represents exactly one image plot.

 * $Log: not supported by cvs2svn $
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
		
		Map<String, String[]> parameters = request.getParameterMap();
		//for (Iterator it = parameters.keySet().iterator(); it.hasNext(); )
		for (String key : parameters.keySet())
		{
			//String key = (String)it.next();
			if (key.endsWith("." + i))
			{
				String value = parameters.get(key)[0];
				key = key.substring(0, key.indexOf('.'));
				component.put(key, value);
			}
		}
		return component;
	}
	
	protected List<PlotComponent> parseRequest(HttpServletRequest request)
	{
		int n = Util.stringToInt(request.getParameter("n"), 1);
		ArrayList<PlotComponent> list = new ArrayList<PlotComponent>(n);
		
		for (int i = 0; i < n; i++)
		{
			PlotComponent component = createComponent(request, i);
			int x = Util.stringToInt(request.getParameter("x." + i), 0);
			int y = Util.stringToInt(request.getParameter("y." + i), 0);
			int w = Util.stringToInt(request.getParameter("w." + i), 0);
			int h = Util.stringToInt(request.getParameter("h." + i), 0);
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
		Valve3Plot plot = new Valve3Plot(request);
		List<PlotComponent> components = parseRequest(request);
//		for (Iterator it = components.iterator(); it.hasNext(); )
		for (PlotComponent component : components)
		{
//			PlotComponent component = (PlotComponent)it.next();
			Plotter plotter = dataHandler.getDataSourceDescriptor(component.getSource()).getPlotter();
			if (plotter != null)
				plotter.plot(plot, component);
		}
		Valve3.getInstance().getResultDeleter().addResult(plot);
		return plot;
	}
	
	public static String getRandomFilename()
	{
		return "img" + File.separator + "tmp" + Math.round(Math.random() * 100000) + ".png"; 
	}
}
