package gov.usgs.valve3.result;

import gov.usgs.plot.Plot;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.PlotHandler;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.CombinedPlot;
import gov.usgs.util.Log;
import gov.usgs.util.Util;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

/**
 * Result which contains plot and information how
 * (in which format, where, size) store it in the file system
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.3  2005/09/03 21:51:07  dcervelli
 * Removed new line.
 *
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
	
	public static final int DEFAULT_WIDTH_PLOT = 1000;
	public static final int DEFAULT_HEIGHT_PLOT = 250;
	
	protected Plot plot;
	protected String filename;
	private String title;
	protected OutputType outputType;
	
	protected int width;
	protected int height;
	//filled during initiating, from http request, not during plotting like "components"
	private int componentCount = 0;

	protected String url;
	
	protected List<PlotComponent> components;
	private Logger logger;	
	
	protected boolean exportable;
	private boolean isCombined = false;
	
	/**
	 * Constructor
	 * @param request http servlet request which keeps height, width and output type parameters
	 * @throws Valve3Exception
	 */
	public Valve3Plot(HttpServletRequest request, int componentCount) throws Valve3Exception
	{
		logger = Log.getLogger("gov.usgs.valve3");
		String w = request.getParameter("w");
		String h = request.getParameter("h");
		
		width = -1;
		if (w == null){
			w= Valve3.getInstance().getDefaults().getString("parameter.plot.w");
			logger.info("Parameter plot.w was set to default value");
		}
		width = Util.stringToInt(w, -1);
		if (width <= 0 || width > PlotHandler.MAX_PLOT_WIDTH){
			width = DEFAULT_WIDTH_PLOT;
			logger.info("Illegal plot.w parameter value, was set to default");
	}
		height = -1;
		if (h == null){
			h= Valve3.getInstance().getDefaults().getString("parameter.plot.h");
			logger.info("Parameter plot.h was set to default value");
		}
		height = Util.stringToInt(h, -1);
		if (height <= 0 || height > PlotHandler.MAX_PLOT_HEIGHT){
			height = DEFAULT_HEIGHT_PLOT;
			logger.info("Illegal plot.h parameter value, was set to default");
		}
		
		String o = request.getParameter("o");
		if(o==null){
			o = Valve3.getInstance().getDefaults().getString("parameter.plot.o");
			if(o==null){
				o="png";
			}
			logger.info("Parameter plot.o was set to default value");
		}
		outputType = OutputType.fromString(o);
		if (outputType == null)
			throw new Valve3Exception("Illegal output type.");

		title = "Valve Plot";
		url = request.getQueryString();
		components = new ArrayList<PlotComponent>(2);
		String combined = request.getParameter("combine");
		if(combined !=null){
			isCombined = combined.toLowerCase().equals("true");
		}
		if(isCombined){
			plot = new CombinedPlot(width, height, componentCount);
		} else {
			plot = new Plot(width, height);
		}
		exportable = false;
	}

	/***
	 * 
	 * @return plot width
	 */
	public int getWidth()
	{
		return width;
	}
	
	/**
	 * Setter for plot width
	 * @param w
	 */
	public void setWidth(int w)
	{
		width = w;
	}
	
	/**
	 * 
	 * @return plot height
	 */
	public int getHeight()
	{
		return height;
	}
	
	/**
	 * Setter for plot height
	 * @param h
	 */
	public void setHeight(int h)
	{
		height = h;
	}
	
	/**
	 * 
	 * @return output type to generate plot content
	 */
	public OutputType getOutputType()
	{
		return outputType;
	}
	
	/**
	 * Setter for file name to generated plot image
	 */
	public void setFilename(String fn)
	{
		filename = fn;
	}
	
	/**
	 * 
	 * @return full file name to generate plot image. If not set return random file name.
	 */
	public String getLocalFilename()
	{
		if (filename == null)
			filename = PlotHandler.getRandomFilename();
		
		return Valve3.getInstance().getApplicationPath() + File.separatorChar + filename;
	}
	
	/**
	 * 
	 * @return short file name to generate plot image.
	 */
	public String getFilename()
	{
		return filename;
	}
	
	/**
	 * 
	 * @return short file name to generate plot image as URL
	 */
	public String getURLFilename()
	{
		return filename.replace(File.separatorChar, '/');
	}
	
	/**
	 * Does nothing
	 * @param rgb
	 * @return
	 */
	public Color getRGB(String rgb)
	{
		return null;
	}
	
	/**
	 * 
	 * Getter for plot
	 */
	public Plot getPlot()
	{
		return plot;
	}
	
	/**
	 * Add PlotComponent. (For what? It is never used)
	 */
	public void addComponent(PlotComponent comp)
	{
		components.add(comp);
	}

	/**
	 * Getter for plot title
	 */
	public String getTitle()
	{
		return title;
	}

	/**
	 * Setter for plot title
	 */
	public void setTitle(String t)
	{
		if(isCombined){
			if(title.equals("Valve Plot")){
				title = t;
			} else {
				title = title + "+" + t;
			}
		} else {
			title = t;
		}
	}

	/**
	 * Getter for plot exportable flag
	 * @returns "plot is exportable"
	 */
	public boolean getExportable()
	{
		return exportable;
	}

	/**
	 * Setter for plot exportable flag
	 * @param e boolean: "plot is exportable"
	 */
	public void setExportable(boolean e)
	{
		exportable = e;
	}

	/**
	 * Delete file with generated plot image from file system
	 */
	public void delete()
	{
		new File(getLocalFilename()).delete();
	}
	
	/**
	 * @return XML representation of object
	 */
	public String toXML()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("\t<plot>\n");
		sb.append("\t\t<url><![CDATA[" + url + "]]></url>\n");
		sb.append("\t\t<file>" + getURLFilename() + "</file>\n");
		sb.append("\t\t<title>" + title + "</title>\n");
		sb.append("\t\t<width>" + width + "</width>\n");
		sb.append("\t\t<height>" + height + "</height>\n");
		sb.append("\t\t<exportable>" + exportable + "</exportable>\n");
//		for (Iterator it = components.iterator(); it.hasNext(); )
//			sb.append(((PlotComponent)it.next()).toXML());
		for (PlotComponent pc : components)
			sb.append(pc.toXML());
		sb.append("\t</plot>");
		return toXML("plot", sb.toString());
	}
}
