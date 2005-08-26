package gov.usgs.valve3.result;

import gov.usgs.plot.Plot;
import gov.usgs.util.Util;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.PlotHandler;
import gov.usgs.valve3.Valve3;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

/**
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class Valve3Plot extends Result
{
	public static final int OUTPUT_XML = 1;
	public static final int OUTPUT_PNG = 2;
	
	public static final int DEFAULT_WIDTH = 1000;
	public static final int DEFAULT_HEIGHT = 250;
	
	protected Plot plot;
	protected String filename;
	protected String title;
	protected int outputType;
	
	protected int width;
	protected int height;

	protected String url;
	
	protected List<PlotComponent> components;
	
	public Valve3Plot(HttpServletRequest request)
	{
		title = "Valve Plot";
		url = request.getQueryString();
		components = new ArrayList<PlotComponent>(2);
		plot = new Plot();
		
		width = Util.stringToInt(request.getParameter("w"), DEFAULT_WIDTH);
		height = Util.stringToInt(request.getParameter("h"), DEFAULT_HEIGHT);
		
		String ot = request.getParameter("o");
		outputType = OUTPUT_XML;
		if (ot != null && ot.equals("png"))
			outputType = OUTPUT_PNG;
		
		plot.setSize(width, height);
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
	
	public int getOutputType()
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
		sb.append("\t</plot>\n");
		return toXML("plot", sb.toString());
	}
}
