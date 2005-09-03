package gov.usgs.valve3.result;

import gov.usgs.plot.Plot;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.PlotHandler;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

/**
 * $Log: not supported by cvs2svn $
 * Revision 1.2  2005/08/29 22:54:28  dcervelli
 * Refactored for enums.
 *
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
public class Valve3Plot extends Result
{
	public enum OutputType 
	{ 
		XML, PNG, HTML;
		
		public static OutputType fromString(String s)
		{
			if (s == null)
				return null;
			
			if (s.equals("xml"))
				return XML;
			else if (s.equals("png"))
				return PNG;
			else if (s.equals("html"))
				return HTML;
			else
				return null;
		}
	}
	
	public static final int DEFAULT_WIDTH = 1000;
	public static final int DEFAULT_HEIGHT = 250;
	
	protected Plot plot;
	protected String filename;
	protected String title;
	protected OutputType outputType;
	
	protected int width;
	protected int height;

	protected String url;
	
	protected List<PlotComponent> components;
	
	public Valve3Plot(HttpServletRequest request) throws Valve3Exception
	{
		String w = request.getParameter("w");
		String h = request.getParameter("h");
		
		width = -1;
		if (w == null)
			width = DEFAULT_WIDTH;
		else
			try { width = Integer.parseInt(w); } catch (Exception e) {}
		if (width <= 0 || width > PlotHandler.MAX_PLOT_WIDTH)
			throw new Valve3Exception("Illegal width.");

		height = -1;
		if (h == null)
			height = DEFAULT_HEIGHT;
		else
			try { height = Integer.parseInt(h); } catch (Exception e) {}
		if (height <= 0 || height > PlotHandler.MAX_PLOT_HEIGHT)
			throw new Valve3Exception("Illegal height.");
		
		outputType = OutputType.fromString(request.getParameter("o"));
		if (outputType == null)
			throw new Valve3Exception("Illegal output type.");

		title = "Valve Plot";
		url = request.getQueryString();
		components = new ArrayList<PlotComponent>(2);
		
		plot = new Plot(width, height);
	}
	
	public int getWidth()
	{
		return width;
	}
	
	public void setWidth(int w)
	{
		width = w;
	}
	
	public int getHeight()
	{
		return height;
	}
	
	public void setHeight(int h)
	{
		height = h;
	}
	
	public OutputType getOutputType()
	{
		return outputType;
	}
	
	public void setFilename(String fn)
	{
		filename = fn;
	}
	
	public String getLocalFilename()
	{
		if (filename == null)
			filename = PlotHandler.getRandomFilename();
		
		return Valve3.getInstance().getApplicationPath() + File.separatorChar + filename;
	}
	
	public String getFilename()
	{
		return filename;
	}
	
	public String getURLFilename()
	{
		return filename.replace(File.separatorChar, '/');
	}
	
	public Color getRGB(String rgb)
	{
		return null;
	}
	
	public Plot getPlot()
	{
		return plot;
	}
	
	public void addComponent(PlotComponent comp)
	{
		components.add(comp);
	}
	
	public void setTitle(String t)
	{
		title = t;
	}

	public void delete()
	{
		new File(getLocalFilename()).delete();
	}
	
	public String toXML()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("\t<plot>\n");
		sb.append("\t\t<url><![CDATA[" + url + "]]></url>\n");
		sb.append("\t\t<file>" + getURLFilename() + "</file>\n");
		sb.append("\t\t<title>" + title + "</title>\n");
		sb.append("\t\t<width>" + width + "</width>\n");
		sb.append("\t\t<height>" + height + "</height>\n");
//		for (Iterator it = components.iterator(); it.hasNext(); )
//			sb.append(((PlotComponent)it.next()).toXML());
		for (PlotComponent pc : components)
			sb.append(pc.toXML());
		sb.append("\t</plot>");
		return toXML("plot", sb.toString());
	}
}
