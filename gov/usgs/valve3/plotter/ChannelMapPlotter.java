package gov.usgs.valve3.plotter;

import gov.usgs.plot.Plot;
import gov.usgs.plot.PlotException;
import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.plot.map.GeoLabel;
import gov.usgs.plot.map.GeoLabelSet;
import gov.usgs.plot.map.MapRenderer;
import gov.usgs.proj.GeoRange;
import gov.usgs.proj.TransverseMercator;
import gov.usgs.util.Pool;
import gov.usgs.util.UtilException;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.Channel;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Generate PNG map image to local file from vdx source data.
 *
 * @author Dan Cervelli
 */
public class ChannelMapPlotter extends Plotter
{
	private PlotComponent component;
	private Valve3Plot v3Plot;
	private GeoRange range;
	private GeoLabelSet labels;
	private boolean forExport;
	
	protected boolean xTickMarks = true;
    protected boolean xTickValues = true;
    protected boolean xUnits = true;
    protected boolean xLabel = false;
    protected boolean yTickMarks = true;
    protected boolean yTickValues = true;
    protected boolean yUnits = true;
    protected boolean yLabel = false;
	
	protected Logger logger;
	
	/**
	 * Default constructor
	 */
	public ChannelMapPlotter() {
		logger		= Logger.getLogger("gov.usgs.vdx");		
	}

	/**
	 * Initialize internal data from PlotComponent 
	 * @throws Valve3Exception
	 */
	private void getInputs() throws Valve3Exception
	{
		double w = component.getDouble("west");
		if (w > 360 || w < -360)
			throw new Valve3Exception("Illegal area of interest: w=" +w);
		double e = component.getDouble("east");
		if (e > 360 || e < -360)
			throw new Valve3Exception("Illegal area of interest: e=" +e);
		double s = component.getDouble("south");
		if (s < -90)
			throw new Valve3Exception("Illegal area of interest: s=" +s);
		double n = component.getDouble("north");
		if (n > 90)
			throw new Valve3Exception("Illegal area of interest: n=" +n);
		if(s>=n){
			throw new Valve3Exception("Illegal area of interest: s=" + s + ", n=" + n);
		}
		try{
			xTickMarks = component.getBoolean("xTickMarks");
		} catch(Valve3Exception ex){
			xTickMarks=true;
		}
		try{
			xTickValues = component.getBoolean("xTickValues");
		} catch(Valve3Exception ex){
			xTickValues=true;
		}
		try{
			xUnits = component.getBoolean("xUnits");
		} catch(Valve3Exception ex){
			xUnits=true;
		}
		try{
			xLabel = component.getBoolean("xLabel");
		} catch(Valve3Exception ex){
			xLabel=false;
		}
		try{
			yTickMarks = component.getBoolean("yTickMarks");
		} catch(Valve3Exception ex){
			yTickMarks=true;
		}
		try{
			yTickValues = component.getBoolean("yTickValues");
		} catch(Valve3Exception ex){
			yTickValues=true;
		}
		try{
			yUnits = component.getBoolean("yUnits");
		} catch(Valve3Exception ex){
			yUnits=true;
		}
		try{
			yLabel = component.getBoolean("yLabel");
		} catch(Valve3Exception ex){
			yLabel=false;
		}
		range = new GeoRange(w, e, s, n);
	}
	
	/**
	 * Loads GeoLabelSet labels from VDX
	 * @throws Valve3Exception
	 */
	private void getData() throws Valve3Exception {
		
		// initialize variables
		List<String> stringList = null;
		Pool<VDXClient> pool	= null;
		VDXClient client		= null;
		labels 					= new GeoLabelSet();
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "channels");
		
		// checkout a connection to the database, the vdxClient could be null or invalid
		pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		if (pool != null) {
			client = pool.checkout();
			try {
				stringList = client.getTextData(params);
			} catch (UtilException e) {
				stringList = null;
			} finally {
				pool.checkin(client);
			}
		}
		
		// if data was collected, iterate through the list of channels and add a geo label
		if (stringList != null) {
			Set<String> used	= new HashSet<String>();
			for (String ch : stringList) {
				Channel channel		= new Channel(ch);
				String channelCode	= channel.getCode();
				String channelCode0 = channelCode.split(" ")[0];
				if (!used.contains(channelCode0)) {
					GeoLabel gl = new GeoLabel(channelCode0, channel.getLon(), channel.getLat());
					labels.add(gl);
					used.add(channelCode0);
				}
			}
		}
	}
	
	/**
	 * Initialize MapRenderer and add it to plot. 
	 * Generate PNG map image to local file.
	 * @throws Valve3Exception
	 */
	private void plotMap() throws Valve3Exception, PlotException
	{
		Plot plot = v3Plot.getPlot();
		TransverseMercator proj = new TransverseMercator();
		Point2D.Double origin = range.getCenter();
		proj.setup(origin, 0, 0);

		MapRenderer mr = new MapRenderer(range, proj);
		mr.setLocationByMaxBounds(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getInt("mh"));
		
		if (labels != null)
			mr.setGeoLabelSet(labels.getSubset(range));
		
		GeoImageSet images = Valve3.getInstance().getGeoImageSet();
		RenderedImage ri = images.getMapBackground(proj, range, component.getBoxWidth());
		
		mr.setMapImage(ri);
		mr.createBox(8);
		mr.createGraticule(8, xTickMarks, yTickMarks, xTickValues, yTickValues, Color.BLACK);
		mr.createScaleRenderer();
		plot.setSize(plot.getWidth(), mr.getGraphHeight() + 60);
		v3Plot.setHeight(mr.getGraphHeight() + 60);
		double[] trans = mr.getDefaultTranslation(plot.getHeight());
		trans[4] = 0;
		trans[5] = 0;
		trans[6] = origin.x;
		trans[7] = origin.y;
		component.setTranslation(trans);
		component.setTranslationType("map");
		
		mr.createEmptyAxis();
		if(xUnits){
			mr.getAxis().setBottomLabelAsText("Longitude");
		}
		if(yUnits){
			mr.getAxis().setLeftLabelAsText("Latitude");
		}
		plot.addRenderer(mr);
		plot.writePNG(v3Plot.getLocalFilename());
		
		v3Plot.addComponent(component);
		v3Plot.setTitle("Map");
		if (vdxSource != null)
		{
			String n = Valve3.getInstance().getMenuHandler().getItem(vdxSource).name;
			v3Plot.setTitle("Map: " + n);
		}
	}

	/**
	 * Concrete realization of abstract method. 
	 * Generate PNG map image to local file.
	 * @see Plotter
	 */
	public void plot(Valve3Plot plot, PlotComponent comp) throws Valve3Exception, PlotException
	{
		forExport = false;
		v3Plot = plot;
		component = comp;
		getInputs();
		getData();
		plotMap();
	}
}
