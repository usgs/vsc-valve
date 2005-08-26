package gov.usgs.valve3.plotter;

import gov.usgs.plot.Plot;
import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.plot.map.GeoRange;
import gov.usgs.plot.map.MapRenderer;
import gov.usgs.proj.TransverseMercator;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.PlotHandler;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.result.Valve3Plot;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.io.File;

/**
 * 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class MapPlotter extends Plotter
{
	private GeoImageSet images;
	
	public MapPlotter()
	{
		images = new GeoImageSet();
	}
	
	public void plot(Valve3Plot v3Plot, PlotComponent component)
	{
		v3Plot.setFilename(PlotHandler.getRandomFilename());
		
		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
	
		double w = Double.parseDouble(component.get("west"));
		double e = Double.parseDouble(component.get("east"));
		double s = Double.parseDouble(component.get("south"));
		double n = Double.parseDouble(component.get("north"));
		GeoRange range = new GeoRange(w, e, s, n);
		
		TransverseMercator proj = new TransverseMercator();
		Point2D.Double origin = range.getCenter();
		proj.setup(origin, 0, 0);
		
		MapRenderer mr = new MapRenderer(range, proj);
		mr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth());
		
		RenderedImage ri = images.getMapBackground(proj, range, component.getBoxWidth());
		mr.setMapImage(ri);
		plot.addRenderer(mr);
		//RenderedImageDataRenderer idr = new RenderedImageDataRenderer(ri);
		
		//plot.setSize(plot.getWidth(), ri.getHeight() + 40);
//		idr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
//		idr.setLocation(component.getBoxX(), component.getBoxY(), ri.getWidth(), ri.getHeight());
//		double[] extents = range.getProjectedExtents(proj);
//		idr.setExtents(extents[0], extents[1], extents[2], extents[3]);
//		idr.setDataExtents(extents[0], extents[1], extents[2], extents[3]);
//		idr.createDefaultAxis(8, 8, false, false);
//		idr.getAxis().setLeftLabelAsText("Meters");
//		idr.getAxis().setBottomLabelAsText("Meters");
//		plot.addRenderer(idr);
		
		/*
		LineData grid = LineData.createLonLatGrid(w, e, s, n, 10);
		grid.applyProjection(proj);
		LineDataRenderer ldr = new LineDataRenderer(grid);
		ldr.setLocation(component.getBoxX(), component.getBoxY(), ri.getWidth(), ri.getHeight());
		ldr.setExtents(extents[0], extents[1], extents[2], extents[3]);
		
		plot.addRenderer(ldr);
		*/
		
		plot.writePNG(Valve3.getInstance().getApplicationPath() + File.separatorChar + v3Plot.getFilename());
		
		v3Plot.addComponent(component);
		v3Plot.setTitle("Map");
		
	}
}
