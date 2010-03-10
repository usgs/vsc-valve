package gov.usgs.valve3.plotter;

import gov.usgs.plot.Plot;
import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.plot.map.GeoLabel;
import gov.usgs.plot.map.GeoLabelSet;
import gov.usgs.plot.map.MapRenderer;
import gov.usgs.proj.GeoRange;
import gov.usgs.proj.TransverseMercator;
import gov.usgs.util.Pool;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.Channel;

import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	/**
	 * Initialize internal data from PlotComponent 
	 * @throws Valve3Exception
	 */
	private void getInputs() throws Valve3Exception
	{
		try
		{
			double w = Double.parseDouble(component.get("west"));
			double e = Double.parseDouble(component.get("east"));
			double s = Double.parseDouble(component.get("south"));
			double n = Double.parseDouble(component.get("north"));
			if (s >= n || s < -90 || n > 90 || w > 360 || w < -360 || e > 360 || e < -360)
				throw new Valve3Exception("Illegal area of interest.");
			range = new GeoRange(w, e, s, n);
		}
		catch (Exception e)
		{
			throw new Valve3Exception("Illegal filter settings.");
		}
	}
	
	/**
	 * Loads GeoLabelSet labels from VDX
	 * @throws Valve3Exception
	 */
	private void getData()
	{
		if (vdxSource == null || vdxClient == null)
			return; 
		
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "channels");

		Pool<VDXClient> pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client		= pool.checkout();
		List<String> channels	= client.getTextData(params);
		Set<String> used		= new HashSet<String>();
		labels					= new GeoLabelSet();
		
		// iterate through the list of channels and add a geo label
		for (String ch : channels) {
			Channel channel		= new Channel(ch);
			String channelCode	= channel.getCode();
			String channelCode0 = channelCode.split(" ")[0];
			if (!used.contains(channelCode0)) {
				GeoLabel gl = new GeoLabel(channelCode0, channel.getLon(), channel.getLat());
				labels.add(gl);
				used.add(channelCode0);
			}
		}
		pool.checkin(client);
	}
	
	/**
	 * Initialize MapRenderer and add it to plot. 
	 * Generate PNG map image to local file.
	 * @throws Valve3Exception
	 */
	private void plotMap()
	{
		Plot plot = v3Plot.getPlot();
		TransverseMercator proj = new TransverseMercator();
		Point2D.Double origin = range.getCenter();
		proj.setup(origin, 0, 0);

		MapRenderer mr = new MapRenderer(range, proj);
		mr.setLocationByMaxBounds(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), Integer.parseInt(component.get("mh")));
		
		if (labels != null)
			mr.setGeoLabelSet(labels.getSubset(range));
		
		GeoImageSet images = Valve3.getInstance().getGeoImageSet();
		RenderedImage ri = images.getMapBackground(proj, range, component.getBoxWidth());
		
		mr.setMapImage(ri);
		mr.createBox(8);
		mr.createGraticule(8, true);
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
		mr.getAxis().setBottomLabelAsText("Longitude");
		mr.getAxis().setLeftLabelAsText("Latitude");
		
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
	public void plot(Valve3Plot plot, PlotComponent comp) throws Valve3Exception
	{
		v3Plot = plot;
		component = comp;
		getInputs();
		getData();
		plotMap();
	}
}
