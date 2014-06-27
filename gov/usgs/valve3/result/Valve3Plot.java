package gov.usgs.valve3.result;

import gov.usgs.plot.Plot;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.PlotHandler;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.CombinedPlot;
import gov.usgs.vdx.data.MetaDatum;
import gov.usgs.vdx.data.SuppDatum;
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
 * 
 * @author Dan Cervelli
 */
public class Valve3Plot extends Result
{
	public enum OutputType 
	{ 
		XML("xml", "application/xml"), 
		PNG("png", "image/png"), 
		HTML("html", "text/html"), 
		PS("ps", "application/postscript");
		
		public final String extension;
		public final String mimeType;
		
		private OutputType(String extension, String mimeType) {
			this.extension = extension;
			this.mimeType = mimeType;
		}
		
		public static OutputType fromString(String s)
		{
			if (s == null)
				return null;
			
			if (s.equals(XML.extension))
				return XML;
			else if (s.equals(PNG.extension))
				return PNG;
			else if (s.equals(HTML.extension))
				return HTML;
			else if (s.equals(PS.extension))
				return PS;
			else
				return null;
		}
	}
	
	// Medium.  please refer to STANDARD_SIZES as defined in plot.js
	public static final int DEFAULT_PLOT_WIDTH	= 900;
	public static final int DEFAULT_PLOT_HEIGHT	= 300;
	
	protected Plot plot;
	protected String filename;
	private String title;
	protected OutputType outputType;
	protected OutputType plotFormat;
	
	protected int width;
	protected int height;

	protected String url;
	
	protected List<PlotComponent> components;
	private Logger logger;	
	
	protected boolean isExportable	= false;
	protected boolean isCombineable	= false;
	private boolean isCombined		= false;
	protected boolean isWaveform	= false;
	
	protected List<SuppDatum> suppdata;
	protected List<MetaDatum> metadata;
	
	/**
	 * Constructor
	 * @param request http servlet request which keeps height, width and output type parameters
	 * @param componentCount number of components
	 * @throws Valve3Exception
	 */
	public Valve3Plot(HttpServletRequest request, int componentCount) throws Valve3Exception
	{
		logger = Log.getLogger("gov.usgs.valve3");
		
		width = Util.stringToInt(request.getParameter("w"), DEFAULT_PLOT_WIDTH);
		if (width <= 0 || width > PlotHandler.MAX_PLOT_WIDTH) {
			width = DEFAULT_PLOT_WIDTH;
			logger.info("Illegal w parameter.  Was set to default value of " + DEFAULT_PLOT_WIDTH);
		}
		
		height = Util.stringToInt(request.getParameter("h"), DEFAULT_PLOT_HEIGHT);
		if (height <= 0 || height > PlotHandler.MAX_PLOT_HEIGHT) {
			height = DEFAULT_PLOT_HEIGHT;
			logger.info("Illegal h parameter.  Was set to default value of " + DEFAULT_PLOT_HEIGHT);
		}

		outputType = OutputType.fromString(Util.stringToString(request.getParameter("o"), "png"));
		if (outputType == null) {
			throw new Valve3Exception("Illegal output type.");
		}

		if (outputType == OutputType.XML || outputType == OutputType.HTML)
			// XML and HTML are wrappers around a .png image
			plotFormat = OutputType.PNG;
		else
			plotFormat = outputType;
		
		title		= "Valve Plot";
		url			= request.getQueryString();
		components	= new ArrayList<PlotComponent>(2);
		
		isCombined	= Util.stringToBoolean(request.getParameter("combine"), false);
		if(isCombined){
			plot = new CombinedPlot(width, height, componentCount);
			setCombineable(true);
		} else {
			plot = new Plot(width, height);
		}
		
		// is this necessary ??
		// exportable = false;
		
		suppdata = new ArrayList<SuppDatum>();
		metadata = new ArrayList<MetaDatum>();
	}

	/***
	 * Getter for plot width
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
	 * Getter for plot height
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
	 * Getter for output type
	 * @return output type to generate plot content
	 */
	public OutputType getOutputType()
	{
		return outputType;
	}
	
	public String getMimeType() 
	{
		return outputType.mimeType;
	}
	
	/**
	 * Setter for file name to generated plot image
	 */
	public void setFilename(String fn)
	{
		filename = fn;
	}
	
	/**
	 * Getter for local file name
	 * @return full file name to generate plot image. If not set return random file name.
	 */
	public String getLocalFilename()
	{
		if (filename == null)
			filename = PlotHandler.getRandomFilename(plotFormat.extension);
		
		return Valve3.getInstance().getApplicationPath() + File.separatorChar + filename;
	}
	
	/**
	 * Getter for file name
	 * @return short file name to generate plot image.
	 */
	public String getFilename()
	{
		return filename;
	}
	
	/**
	 * Getter for short file name
	 * @return short file name to generate plot image as URL
	 */
	public String getURLFilename()
	{
		return filename.replace(File.separatorChar, '/');
	}
	
	/**
	 * Does nothing
	 * @param rgb
	 * @return null
	 */
	public Color getRGB(String rgb)
	{
		return null;
	}
	
	/**
	 * Getter for plot
	 * @return plot
	 */
	public Plot getPlot()
	{
		return plot;
	}
	
	/**
	 * Add PlotComponent.
	 * @param comp PlotComponent
	 */
	public void addComponent(PlotComponent comp)
	{
		components.add(comp);
	}

	/**
	 * Getter for plot title
	 * @return title
	 */
	public String getTitle()
	{
		return title;
	}

	/**
	 * Setter for plot title
	 * @param t title
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
	 * @return "plot is exportable"
	 */
	public boolean getExportable()
	{
		return isExportable;
	}

	/**
	 * Setter for plot exportable flag
	 * @param e boolean: "plot is exportable"
	 */
	public void setExportable(boolean e)
	{
		isExportable = e;
	}
	
	/**
	 * Setter for plot combineable flag
	 * @param e boolean: "plot is combineable"
	 */
	public void setCombineable(boolean e)
	{
		isCombineable = e;
	}

	/**
	 * Getter for waveform plot flag
	 * @return "waveform plot"
	 */
	public boolean getWaveform()
	{
		return isWaveform;
	}

	/**
	 * Setter for waveform plot flag
	 * @param w boolean: "waveform plot"
	 */
	public void setWaveform(boolean w)
	{
		isWaveform = w;
	}

	/**
	 * Getter for plot combined flag
	 * @return "plot is combined"
	 */
	public boolean getCombined()
	{
		return isCombined;
	}
	
	/**
	 * Delete file with generated plot image from file system
	 */
	public void delete()
	{
		new File(getLocalFilename()).delete();
	}
	
	/**
	 * Yield XML representation
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
		sb.append("\t\t<exportable>" + isExportable + "</exportable>\n");
		sb.append("\t\t<combined>" + isCombined + "</combined>\n");
		sb.append("\t\t<combineable>" + isCombineable + "</combineable>\n");
		sb.append("\t\t<waveform>" + isWaveform + "</waveform>\n");
//		for (Iterator it = components.iterator(); it.hasNext(); )
//			sb.append(((PlotComponent)it.next()).toXML());
		for (PlotComponent pc : components)
			sb.append(pc.toXML());
		for (SuppDatum sd : suppdata)
			sb.append(sd.toXML( true ));
		for (MetaDatum md : metadata) {
			sb.append(md.toXML());
		}
		sb.append("\t</plot>");
		return toXML("plot", sb.toString());
	}

	/**
	 * Add SuppDatum.
	 * @param sd SuppDatum SuppDatum to add
	 */
	public void addSuppDatum(SuppDatum sd)
	{
		suppdata.add(sd);
	}
	
	/**
	 * Add MetaDatum
	 * @param md MetaDatum MetaDatum to add
	 */
	public void addMetaDatum(MetaDatum md)
	{
		metadata.add(md);
	}

}
