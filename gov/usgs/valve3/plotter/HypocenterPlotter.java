package gov.usgs.valve3.plotter;

import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.BasicFrameRenderer;
import gov.usgs.plot.Data;
import gov.usgs.plot.DataRenderer;
import gov.usgs.plot.HistogramRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.Renderer;
import gov.usgs.plot.ShapeRenderer;
import gov.usgs.plot.SmartTick;
import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.plot.map.GeoLabelSet;
import gov.usgs.plot.map.GeoRange;
import gov.usgs.plot.map.MapRenderer;
import gov.usgs.proj.TransverseMercator;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.PlotHandler;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.hypo.HypocenterList;
import gov.usgs.vdx.data.hypo.HypocenterList.BinSize;
import gov.usgs.vdx.data.hypo.plot.HypocenterRenderer;
import gov.usgs.vdx.data.hypo.plot.HypocenterRenderer.Axes;
import gov.usgs.vdx.data.hypo.plot.HypocenterRenderer.ColorOption;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.HashMap;

import cern.colt.matrix.DoubleMatrix2D;

/**
 * A class for making hypocenter map plots and histograms.
 * 
 * TODO: display number of hypocenters on plot.
 * TODO: implement triple view.
 * TODO: implement arbitrary cross-sections.
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.2  2005/08/28 19:02:00  dcervelli
 * Totally refactored (now uses JDK1.5 enums, gets histogram data from HypocenterList, etc.).
 *
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
public class HypocenterPlotter extends Plotter
{
	private enum PlotType 
	{
		MAP, COUNTS;
		
		public static PlotType fromString(String s)
		{
			if (s == null)
				return null;
			
			if (s.equals("map"))
				return MAP;
			else if (s.equals("cnts"))
				return COUNTS;
			else 
				return null;
		}
	}
	
	private enum RightAxis
	{
		NONE(""), 
		CUM_COUNTS("Cumulative Counts"), 
		CUM_MAGNITUDE("Cumulative Magnitude"), 
		CUM_MOMENT("Cumulative Moment");
		
		private String description;
		
		private RightAxis(String s)
		{
			description = s;
		}
		
		public String toString()
		{
			return description;
		}
		
		public static RightAxis fromString(String s)
		{
			if (s == null)
				return null;
			
			switch (s.charAt(0))
			{
				case 'N':
					return NONE;
				case 'C':
					return CUM_COUNTS;
				case 'M':
					return CUM_MAGNITUDE;
				case 'T': 
					return CUM_MOMENT;
				default:
					return null;
			}
		}
	}
	
	private Valve3Plot v3Plot;
	private PlotComponent component;
	private double startTime;
	private double endTime;
	private GeoRange range;
	private double minDepth;
	private double maxDepth;
	private double minMag;
	private double maxMag;
	private Axes axes;
	private ColorOption color;
	private PlotType type;
	private BinSize bin;
	private RightAxis rightAxis;
	private HypocenterList hypos;
	
	public HypocenterPlotter()
	{}

	private BasicFrameRenderer plotMapView(Plot plot)
	{
		// TODO: make projection variable
		TransverseMercator proj = new TransverseMercator();
		Point2D.Double origin = range.getCenter();
		proj.setup(origin, 0, 0);

		hypos.project(proj);
		
		MapRenderer mr = new MapRenderer(range, proj);
		mr.setLocationByMaxBounds(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), Integer.parseInt(component.get("mh")));
		
		GeoLabelSet labels = Valve3.getInstance().getGeoLabelSet();
		mr.setGeoLabelSet(labels.getSubset(range));
		
		GeoImageSet images = Valve3.getInstance().getGeoImageSet();
		RenderedImage ri = images.getMapBackground(proj, range, component.getBoxWidth());
		
		mr.setMapImage(ri);
		mr.createBox(8);
		mr.createGraticule(8, true);
		plot.setSize(plot.getWidth(), mr.getGraphHeight() + 50);
		double[] trans = mr.getDefaultTranslation(plot.getHeight());
		trans[4] = startTime;
		trans[5] = endTime;
		trans[6] = origin.x;
		trans[7] = origin.y;
		component.setTranslation(trans);
		component.setTranslationType("map");
		return mr;
	}
	
	private void plotMap()
	{
		Plot plot = v3Plot.getPlot();
		BasicFrameRenderer base = null;
		
		base = new BasicFrameRenderer();
		base.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		switch(axes)
		{
			case MAP_VIEW:
				base = plotMapView(plot);
				break;
			case LON_DEPTH:
				base.setExtents(range.getWest(), range.getEast(), -maxDepth, -minDepth);
				base.createDefaultAxis();
				component.setTranslation(base.getDefaultTranslation(plot.getHeight()));
				component.setTranslationType("xy");
				break;
			case LAT_DEPTH:
				base.setExtents(range.getSouth(), range.getNorth(), -maxDepth, -minDepth);
				base.createDefaultAxis();
				component.setTranslation(base.getDefaultTranslation(plot.getHeight()));
				component.setTranslationType("xy");
				break;
			case DEPTH_TIME:
				base.setExtents(startTime, endTime, -maxDepth, -minDepth);
				base.createDefaultAxis();
				base.setXAxisToTime(8);
				component.setTranslation(base.getDefaultTranslation(plot.getHeight()));
				component.setTranslationType("ty");
				break;
		}
		
		HypocenterRenderer hr = new HypocenterRenderer(hypos, base, axes);
		hr.setColorOption(color);
		if (color == ColorOption.TIME)
			hr.setColorTime(startTime, endTime);
		
		plot.addRenderer(base);
		plot.addRenderer(hr);
		plot.writePNG(v3Plot.getLocalFilename());
		
		v3Plot.addComponent(component);
		v3Plot.setTitle("Earthquake Map");
	}
	
	private void plotCounts()
	{
		Plot plot = v3Plot.getPlot();
		
		HistogramRenderer hr = new HistogramRenderer(hypos.getCountsHistogram(bin));
		hr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		hr.setUnit("counts per time");
		hr.setDefaultExtents();
		hr.setMinX(startTime);
		hr.setMaxX(endTime);
		hr.createDefaultAxis(8, 8, false, true);
		hr.setXAxisToTime(8);
		hr.getAxis().setLeftLabelAsText("Earthquakes per " + bin);
		hr.getAxis().setBottomLabelAsText("Time");
		plot.addRenderer(hr);
		
		DoubleMatrix2D data = null;
		switch(rightAxis)
		{
			case CUM_COUNTS:
				data = hypos.getCumulativeCounts();
				break;
			case CUM_MAGNITUDE:
				data = hypos.getCumulativeMagnitude();
				break;
			case CUM_MOMENT:
				data = hypos.getCumulativeMoment();
				break;
		}
		if (data != null && data.rows() > 0)
		{
			Data countData = new Data(data.toArray());
			DataRenderer dr = new DataRenderer(countData);
			dr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
			dr.createDefaultLineRenderers();
			
			Renderer[] r = dr.getLineRenderers();
			((ShapeRenderer)r[0]).color = Color.red;
			((ShapeRenderer)r[0]).stroke = new BasicStroke(2.0f);
			double cmin = countData.getData()[0][1];
			double cmax = countData.getData()[data.rows() - 1][1];
			dr.setExtents(startTime, endTime, cmin, cmax);
			AxisRenderer ar = new AxisRenderer(dr);
			ar.createRightTickLabels(SmartTick.autoTick(cmin, cmax, 8, false), null);
			dr.setAxis(ar);
			hr.addRenderer(dr);
			hr.getAxis().setRightLabelAsText(rightAxis.toString());
		}
		plot.writePNG(v3Plot.getLocalFilename());
		component.setTranslation(hr.getDefaultTranslation(plot.getHeight()));
		component.setTranslationType("ty");
		v3Plot.addComponent(component);
		v3Plot.setTitle("Earthquake Counts");
	}
	
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
			minDepth = Double.parseDouble(component.get("minDepth"));
			maxDepth = Double.parseDouble(component.get("maxDepth"));
			if (minDepth > maxDepth)
				throw new Valve3Exception("Illegal depth filter.");
			minMag = Double.parseDouble(component.get("minMag"));
			maxMag  = Double.parseDouble(component.get("maxMag"));
			if (minMag > maxMag)
				throw new Valve3Exception("Illegal magnitude filter.");
		}
		catch (Exception e)
		{
			throw new Valve3Exception("Illegal filter settings.");
		}
		
		endTime = component.getEndTime();
		if (Double.isNaN(endTime))
			throw new Valve3Exception("Illegal end time.");
		startTime = component.getStartTime(endTime);
		if (Double.isNaN(startTime))
			throw new Valve3Exception("Illegal start time.");
		
		type = PlotType.fromString(component.get("type"));
		if (type == null)
			throw new Valve3Exception("Illegal plot type.");
		
		switch(type)
		{
			case MAP:
				axes = Axes.fromString(component.get("axes"));
				if (axes == null)
					throw new Valve3Exception("Illegal axes type.");
				
				String c = Util.stringToString(component.get("color"), "A");
				if (c.equals("A"))
					color = ColorOption.chooseAuto(axes);
				else
					color = ColorOption.fromString(c);
				
				if (color == null)
					throw new Valve3Exception("Illegal color option.");
				break;
			case COUNTS:
				String bs = Util.stringToString(component.get("cntsBin"), "day");
				bin = BinSize.fromString(bs);
				if (bin == null)
					throw new Valve3Exception("Illegal bin size option.");
				
				rightAxis = RightAxis.fromString(component.get("cntsAxis"));
				if (rightAxis == null)
					throw new Valve3Exception("Illegal counts axis option.");
				break;
		}
	}
	
	private void getData() throws Valve3Exception
	{
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", vdxSource);
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("west", Double.toString(range.getWest()));
		params.put("east", Double.toString(range.getEast()));
		params.put("south", Double.toString(range.getSouth()));
		params.put("north", Double.toString(range.getNorth()));
		params.put("minDepth", Double.toString(-maxDepth));
		params.put("maxDepth", Double.toString(-minDepth));
		params.put("minMag", Double.toString(minMag));
		params.put("maxMag", Double.toString(maxMag));
		
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		if (client == null)
			return;
		hypos = (HypocenterList)client.getData(params);
		pool.checkin(client);
		// allow empty lists
		if (hypos == null)
			hypos = new HypocenterList();
	}
	
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception
	{
		v3Plot = v3p;
		component = comp;
		getInputs();
		getData();
		
		v3Plot.setFilename(PlotHandler.getRandomFilename());
		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		
		switch(type)
		{
			case MAP:
				plotMap();
				break;
			case COUNTS:
				plotCounts();
				break;
		}
	}
	
}
