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
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.hypo.Hypocenter;
import gov.usgs.vdx.data.hypo.HypocenterList;
import gov.usgs.vdx.data.hypo.HypocenterRenderer;
import hep.aida.IAxis;
import hep.aida.ref.FixedAxis;
import hep.aida.ref.Histogram1D;
import hep.aida.ref.VariableAxis;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class HypocenterPlotter extends Plotter
{
	private static final int MAX_BINS = 1000;
	
	public HypocenterPlotter()
	{}
	
	public void plotMap(Valve3Plot v3Plot, PlotComponent component, GeoRange range, double minDepth, double maxDepth, double st, double et, HypocenterList eqs)
	{
		Plot plot = v3Plot.getPlot();
		BasicFrameRenderer base = null;
		String axes = component.get("axes");
		String color = component.get("color");
		if (axes.equals("M")) // map view
		{
			TransverseMercator proj = new TransverseMercator();
			Point2D.Double origin = range.getCenter();
			proj.setup(origin, 0, 0);

//			for (int i = 0; i < eqs.length; i++)
			for (Hypocenter hc : eqs.getHypocenters())
				hc.project(proj);
			
			MapRenderer mr = new MapRenderer(range, proj);
			//mr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth());
			mr.setLocationByMaxBounds(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), Integer.parseInt(component.get("mh")));
			
			GeoLabelSet labels = Valve3.getInstance().getGeoLabelSet();
			mr.setGeoLabelSet(labels.getSubset(range));
			
			GeoImageSet images = Valve3.getInstance().getGeoImageSet();
			RenderedImage ri = images.getMapBackground(proj, range, component.getBoxWidth());
			
			mr.setMapImage(ri);
			mr.createBox(8);
			mr.createGraticule(8, true);
			plot.setSize(plot.getWidth(), mr.getGraphHeight() + 50);
			base = mr;
			double[] trans = base.getDefaultTranslation(plot.getHeight());
			trans[4] = origin.x;
			trans[5] = origin.y;
//			trans[4] = range.getWest();
//			trans[5] = range.getEast();
//			trans[6] = range.getSouth();
//			trans[7] = range.getNorth();
			trans[6] = 0;
			trans[7] = 0;
			component.setTranslation(trans);
			component.setTranslationType("map");
		}
		else if (axes.equals("E") || axes.equals("N") || axes.equals("D"))
		{
			base = new BasicFrameRenderer();
			base.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
			if (axes.equals("E"))
			{
				base.setExtents(range.getWest(), range.getEast(), -maxDepth, -minDepth);
				base.createDefaultAxis();
				component.setTranslation(base.getDefaultTranslation(plot.getHeight()));
				component.setTranslationType("xy");
			}
			else if (axes.equals("N"))
			{
				base.setExtents(range.getSouth(), range.getNorth(), -maxDepth, -minDepth);
				base.createDefaultAxis();
				component.setTranslation(base.getDefaultTranslation(plot.getHeight()));
				component.setTranslationType("xy");
			}
			else if (axes.equals("D"))
			{
				base.setExtents(st, et, -maxDepth, -minDepth);
				base.createDefaultAxis();
				base.setXAxisToTime(8);
				component.setTranslation(base.getDefaultTranslation(plot.getHeight()));
				component.setTranslationType("ty");
			}
		}
		
		HypocenterRenderer hr = new HypocenterRenderer(eqs, base, axes);
		if (color.equals("M"))
			hr.setMonochrome();
		else if (color.equals("D"))
			hr.setColorDepth();
		else if (color.equals("T"))
			hr.setColorTime(st, et);
		else if (color.equals("A"))
		{
			if (axes.equals("M"))
				hr.setColorDepth();
			else if (axes.equals("D"))
				hr.setMonochrome();
			else
				hr.setColorTime(st, et);
		}
		
		plot.addRenderer(base);
		plot.addRenderer(hr);
		plot.writePNG(v3Plot.getLocalFilename());
		
		v3Plot.addComponent(component);
		v3Plot.setTitle("Earthquake Map");
	}
	
	private IAxis getHistogramAxis(String bin, double ts, double te)
	{
		int bins = 1;
		IAxis axis = null;
		if (bin.equals("minute"))
		{
			ts -= (ts - 43200) % 60;
			te -= (te - 43200) % 60 - 60;
			bins = (int)(te - ts) / 60;
			if (bins > MAX_BINS)
				bin = "hour";
			else
				axis = new FixedAxis(bins, ts, te);
		}
		if (bin.equals("hour"))
		{
			ts -= (ts - 43200) % 3600;
			te -= (te - 43200) % 3600 - 3600;
			bins = (int)(te - ts) / 3600;
			if (bins > MAX_BINS)
				bin = "day";
			else
				axis = new FixedAxis(bins, ts, te);
		}
		if (bin.equals("day"))
		{
			ts -= (ts - 43200) % 86400;
			te -= (te - 43200) % 86400 - 86400;
			bins = (int)(te - ts) / 86400;
			if (bins > MAX_BINS)
				bin = "week";
			else
				axis = new FixedAxis(bins, ts, te);
		}
		if (bin.equals("week"))
		{
			ts -= (ts - 43200) % 604800;
			te -= (te - 43200) % 604800 - 604800;
			bins = (int)(te - ts) / 604800;
			if (bins > MAX_BINS)
				bin = "month";
			else
				axis = new FixedAxis(bins, ts, te);
		}
		if (bin.equals("month"))
		{
			Date ds = Util.j2KToDate(ts);
			Date de = Util.j2KToDate(te);
			bins = Util.getMonthsBetween(ds, de) + 1;
			if (bins <= MAX_BINS)
			{
				Calendar cal = Calendar.getInstance();
				cal.setTime(ds);
				cal.set(Calendar.DAY_OF_MONTH, 1);
				cal.set(Calendar.HOUR_OF_DAY, 0);
				cal.set(Calendar.MINUTE, 0);
				cal.set(Calendar.SECOND, 0);
				cal.set(Calendar.MILLISECOND, 0);
				double[] edges = new double[bins + 1];
				for (int i = 0; i < bins + 1; i++)
				{
					edges[i] = Util.dateToJ2K(cal.getTime());
					cal.add(Calendar.MONTH, 1);
				}
				axis = new VariableAxis(edges);
			}
			else
				bin = "year";
		}
		if (bin.equals("year"))
		{
			Date ds = Util.j2KToDate(ts);  
			Date de = Util.j2KToDate(te);
			bins = Util.getYear(de) - Util.getYear(ds) + 1;
			double edges[] = new double[bins + 1];
			Calendar cal = Calendar.getInstance();
			cal.setTime(ds);
			cal.set(Calendar.MONTH, 1);
			cal.set(Calendar.DAY_OF_MONTH, 1);
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			for (int i = 0; i < bins + 1; i++)
			{
				edges[i] = Util.dateToJ2K(cal.getTime());
				cal.add(Calendar.YEAR, 1);
			}
			axis = new VariableAxis(edges);
		}   
		return axis;
	}
	
	public void plotCounts(Valve3Plot v3Plot, PlotComponent component, double st, double et, HypocenterList eqs)
	{
		Plot plot = v3Plot.getPlot();
		
		String bin = component.get("cntsBin");
		String rplot = component.get("cntsAxis");
		IAxis axis = getHistogramAxis(bin, st, et);
	   
		List<Hypocenter> hcs = eqs.getHypocenters();
		double[][] count = new double[hcs.size()][2];
		Histogram1D hist = new Histogram1D("", axis);
		for (int i = 0; i < hcs.size(); i++)
		{
			Hypocenter hc = hcs.get(i);
			hist.fill(hc.getTime());
			count[i][0] = hc.getTime();
			if (rplot.charAt(0) == 'C')
				count[i][1] = i;
			else if (rplot.charAt(0) == 'M' || rplot.charAt(0) == 'T')
			{
				double mo = Math.pow(10, (321 / 20 + 3 * hc.getMag() / 2));
				if (i == 0)
					count[i][1] = mo;
				else
					count[i][1] = count[i - 1][1] + mo;
			}
		}
		
		if (rplot.charAt(0) == 'M')
		{
			for (int i = 0; i < hcs.size(); i++)
				count[i][1] = (Math.log(count[i][1]) / Data.LOG10) / 1.5 - 10.7;
		}
		
		Data countData = new Data(count);
		DataRenderer dr = new DataRenderer(countData);
		dr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		dr.createDefaultLineRenderers();
		
		Renderer[] r = dr.getLineRenderers();
		((ShapeRenderer)r[0]).color = Color.red;
		((ShapeRenderer)r[0]).stroke = new BasicStroke(2.0f);
		double cmin = countData.getData()[0][1];
		double cmax = countData.getData()[count.length - 1][1];
		dr.setExtents(st, et, cmin, cmax);
		HistogramRenderer hr = new HistogramRenderer(hist);
		hr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		hr.setUnit("counts per time");
		hr.setDefaultExtents();
		hr.setMinX(st);
		hr.setMaxX(et);
		hr.createDefaultAxis(8, 8, false, true);
		hr.setXAxisToTime(8);
		AxisRenderer ar = new AxisRenderer(dr);
		ar.createRightTickLabels(SmartTick.autoTick(cmin, cmax, 8, false), null);
		dr.setAxis(ar);
		hr.addRenderer(dr);
		plot.addRenderer(hr);
		
		hr.getAxis().setLeftLabelAsText("Earthquakes per " + bin);
		hr.getAxis().setBottomLabelAsText("Time");
		String rLabel = "";
		if (rplot.charAt(0) == 'C')
			rLabel = "Cumulative Counts";
		else if (rplot.charAt(0) == 'M')
			rLabel = "Cumulative Magnitude";
		else if (rplot.charAt(0) == 'T')
			rLabel = "Cumulative Moment";
		hr.getAxis().setRightLabelAsText(rLabel);
		
		plot.writePNG(v3Plot.getLocalFilename());
		component.setTranslation(hr.getDefaultTranslation(plot.getHeight()));
		component.setTranslationType("ty");
		v3Plot.addComponent(component);
		v3Plot.setTitle("Earthquake Counts");
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
		double minDepth = Double.parseDouble(component.get("minDepth"));
		double maxDepth = Double.parseDouble(component.get("maxDepth"));
		double minMag = Double.parseDouble(component.get("minMag"));
		double maxMag  = Double.parseDouble(component.get("maxMag"));
		GeoRange range = new GeoRange(w, e, s, n);
		
		double end = component.getEndTime();
		double start = component.getStartTime(end);
		if (Double.isNaN(start) || Double.isNaN(end))
		{
			// return an error
			return;
		}
		
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", vdxSource);
		params.put("st", Double.toString(start));
		params.put("et", Double.toString(end));
		params.put("west", Double.toString(w));
		params.put("east", Double.toString(e));
		params.put("south", Double.toString(s));
		params.put("north", Double.toString(n));
		params.put("minDepth", Double.toString(-maxDepth));
		params.put("maxDepth", Double.toString(-minDepth));
		params.put("minMag", Double.toString(minMag));
		params.put("maxMag", Double.toString(maxMag));
		
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		if (client == null)
			return;
		HypocenterList eqs = (HypocenterList)client.getData(params);
		pool.checkin(client);
		if (eqs == null)
			eqs = new HypocenterList(new ArrayList<Hypocenter>());
		
		String type = component.get("type");
		if (type.equals("map"))
			plotMap(v3Plot, component, range, minDepth, maxDepth, start, end, eqs);
		else if (type.equals("cnts"))
			plotCounts(v3Plot, component, start, end, eqs);
	}
	
}
