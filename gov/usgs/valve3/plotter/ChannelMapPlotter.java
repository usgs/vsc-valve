package gov.usgs.valve3.plotter;

import gov.usgs.plot.Plot;
import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.plot.map.GeoLabel;
import gov.usgs.plot.map.GeoLabelSet;
import gov.usgs.plot.map.GeoRange;
import gov.usgs.plot.map.MapRenderer;
import gov.usgs.proj.TransverseMercator;
import gov.usgs.util.Pool;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;

import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.HashMap;
import java.util.List;

/**
 * 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class ChannelMapPlotter extends Plotter
{
	private PlotComponent component;
	private Valve3Plot v3Plot;
	private GeoRange range;
	private GeoLabelSet labels;
	
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
	
	private void getData()
	{
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "selectors");

		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		List<String> selectors = (List<String>)client.getData(params);
		labels = new GeoLabelSet();
		for (String sel : selectors)
		{
			String[] ss = sel.split(":");
			GeoLabel gl = new GeoLabel(ss[3], Double.parseDouble(ss[1]), Double.parseDouble(ss[2]));
			labels.add(gl);
		}
		pool.checkin(client);
	}
	
	private void plotMap()
	{
		Plot plot = v3Plot.getPlot();
		TransverseMercator proj = new TransverseMercator();
		Point2D.Double origin = range.getCenter();
		proj.setup(origin, 0, 0);

		MapRenderer mr = new MapRenderer(range, proj);
		mr.setLocationByMaxBounds(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), Integer.parseInt(component.get("mh")));
		
		mr.setGeoLabelSet(labels.getSubset(range));
		
		GeoImageSet images = Valve3.getInstance().getGeoImageSet();
		RenderedImage ri = images.getMapBackground(proj, range, component.getBoxWidth());
		
		mr.setMapImage(ri);
		mr.createBox(8);
		mr.createGraticule(8, true);
		plot.setSize(plot.getWidth(), mr.getGraphHeight() + 50);
		double[] trans = mr.getDefaultTranslation(plot.getHeight());
		trans[4] = 0;
		trans[5] = 0;
		trans[6] = origin.x;
		trans[7] = origin.y;
		component.setTranslation(trans);
		component.setTranslationType("map");
		
		plot.addRenderer(mr);
		plot.writePNG(v3Plot.getLocalFilename());
		
		v3Plot.addComponent(component);
		v3Plot.setTitle("Channel Map");
	}

	public void plot(Valve3Plot plot, PlotComponent comp) throws Valve3Exception
	{
		System.out.println("Channel Map Plotter");
		v3Plot = plot;
		component = comp;
		getInputs();
		getData();
		plotMap();
	}
}
