package gov.usgs.valve3.plotter;

import gov.usgs.plot.Data;
import gov.usgs.plot.DataRenderer;
import gov.usgs.plot.EllipseVectorRenderer2;
import gov.usgs.plot.Plot;
import gov.usgs.plot.Renderer;
import gov.usgs.plot.TextRenderer;
import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.plot.map.GeoLabelSet;
import gov.usgs.plot.map.GeoRange;
import gov.usgs.plot.map.MapRenderer;
import gov.usgs.proj.TransverseMercator;
import gov.usgs.util.CurrentTime;
import gov.usgs.util.Pool;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.gps.Benchmark;
import gov.usgs.vdx.data.gps.DataPoint;
import gov.usgs.vdx.data.gps.GPS;
import gov.usgs.vdx.data.gps.GPSData;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.colt.matrix.linalg.EigenvalueDecomposition;

/**
 * TODO: un-hardcode stid 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class GPSPlotter extends Plotter
{
	private static final String[] LEGENDS = new String[] {"East", "North", "Up", "Length"};
	private List<Benchmark> benchmarks;
	
	public GPSPlotter()
	{}
	
	private void plotTimeSeries(Valve3Plot v3Plot, PlotComponent component, double st, double et)
	{
		HashMap<String, String> params = new HashMap<String, String>();
		String benchmark = component.get("bm");
		params.put("source", vdxSource);
		params.put("bm", benchmark);
		params.put("action", "data");
		params.put("st", Double.toString(st));
		params.put("et", Double.toString(et));
		params.put("stid", Integer.toString(7));
		
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		if (client == null)
			return;
		GPSData data = (GPSData)client.getData(params);
		
		String baseline = component.get("bl");
		GPSData baselineData = null;
		if (baseline != null && baseline.length() > 0 && !baseline.equals("[none]"))
		{
			params.put("bm", baseline);
			baselineData = (GPSData)client.getData(params);
//			baselineData = (GPSData)dataSource.getData(params);
//			if (baselineData != null)
//				data.applyBaseline(baselineData);
//			else
//				baseline = null;
		}
		pool.checkin(client);
		
		boolean ce = component.get("east").equals("T");
		boolean cn = component.get("north").equals("T");
		boolean cu = component.get("up").equals("T");
		boolean cl = component.get("len").equals("T");
		boolean[] comps = new boolean[] { ce, cn, cu, cl };
		
		double[][] dd = data.toTimeSeries(baselineData);
		Data d = new Data(dd);
		
		DataPoint dp = data.getFirstObservation();
		d.add(1, -dp.x);
		d.add(2, -dp.y);
		d.add(3, -dp.z);
		d.add(4, -dp.len);

		int compCount = 0;
		for (int i = 0; i < comps.length; i++)
			if (comps[i])
				compCount++;
		
		int dh = component.getBoxHeight() / compCount;
		int displayCount = 0;
		for (int i = 0; i < comps.length; i++)
		{
			if (comps[i])
			{
				PlotComponent pc = new PlotComponent();
				pc.setSource(component.getSource());
				pc.setBoxX(component.getBoxX());
				pc.setBoxY(component.getBoxY());
				pc.setBoxWidth(component.getBoxWidth());
				
				int[] col = new int[] {0, i + 1};
				Data nd = d.subset(col);
				DataRenderer dr = new DataRenderer(nd);
				dr.setUnit("meters");
				dr.setLocation(pc.getBoxX(), pc.getBoxY() + displayCount * dh + 8, pc.getBoxWidth(), dh - 16);
				double[] lsq = nd.leastSquares(1);
				double minY = lsq[0] * nd.getMinTime() + lsq[1];
				double maxY = lsq[0] * nd.getMaxTime() + lsq[1];
				double dy = Math.max(0.05, (maxY - minY));
				dr.setExtents(st, et, minY - dy, maxY + dy);
//				dr.setExtents(st, et, -0.1, 0.1);
				dr.createDefaultAxis(8, 4, false, false);
				dr.createDefaultPointRenderers();
				dr.createDefaultLegendRenderer(new String[] { LEGENDS[i] });
				dr.setXAxisToTime(8);
				dr.getAxis().setLeftLabelAsText("Meters");
				dr.getAxis().setBottomLabelAsText("Time");
				
				displayCount++;
				if (displayCount != compCount)
					dr.getAxis().setBottomLabels(null);
				
				pc.setTranslation(dr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
				pc.setTranslationType("ty");
				v3Plot.getPlot().addRenderer(dr);
				v3Plot.addComponent(pc);
			}
		}
		
		v3Plot.setTitle("GPS: " + benchmark + (baseline == null ? "" : "-" + baseline));
	}
	
	private GeoRange getBoundingBox(Point2D.Double[] pts)
	{
		if (pts == null || pts.length <= 0)
			return null;
				
		Rectangle2D.Double rect = null;
		for (int i = 0; i < pts.length; i++)
			if (pts[i] != null)
			{
				if (rect == null)
					rect = new Rectangle2D.Double(pts[i].x, pts[i].y, 0, 0);
				rect.add(pts[i]);
			}

		double nw = rect.width * 1.3;
		double nh = rect.height * 1.3;
		rect.x -= (nw - rect.width) / 2;
		rect.y -= (nh - rect.height) / 2;
		rect.width = nw;
		rect.height = nh;
		
		if (rect.width == 0 || rect.height == 0)
		{
			rect.x -= 0.15;
			rect.y -= 0.15;
			rect.width = 0.3;
			rect.height = 0.3;
		}
		
		GeoRange gr = new GeoRange(rect);
		return gr;
	}
	
	private void plotVelocities(Valve3Plot v3Plot, PlotComponent component, double st, double et)
	{
		String benchmark = component.get("bm");
		String[] bms = benchmark.split(",");
		if (bms.length <= 0)
			return;
		
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("st", Double.toString(st));
		params.put("et", Double.toString(et));
		params.put("stid", Integer.toString(7));
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		if (client == null)
			return;
		
		String baseline = component.get("bl");
		GPSData baselineData = null;
		if (baseline != null)
		{
			params.put("bm", baseline);
//			baselineData = (GPSData)dataSource.getData(params);
			baselineData = (GPSData)client.getData(params);
		}
		pool.checkin(client);
		
		Point2D.Double[] locs = new Point2D.Double[bms.length];
		GPSData[] allData = new GPSData[bms.length];
		for (int i = 0; i < bms.length; i++)
		{
			System.out.println("data: " + bms[i]);
			params.put("bm", bms[i]);
			GPSData data = (GPSData)client.getData(params);
			if (data != null)
			{
				if (baselineData != null)
					data.applyBaseline(baselineData);
				allData[i] = data;
				int j = Collections.binarySearch(benchmarks, bms[i]);
				if (j >= 0)
					locs[i] = benchmarks.get(j).getLonLat();
			}
		}
		
		GeoRange range = getBoundingBox(locs);
		System.out.println(range);
		
		Plot plot = v3Plot.getPlot();
		
		TransverseMercator proj = new TransverseMercator();
		Point2D.Double origin = range.getCenter();
		proj.setup(origin, 0, 0);

		MapRenderer mr = new MapRenderer(range, proj);
//		mr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth());
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
		trans[4] = range.getWest();
		trans[5] = range.getEast();
		trans[6] = range.getSouth();
		trans[7] = range.getNorth();
		component.setTranslation(trans);
		component.setTranslationType("map");
		v3Plot.addComponent(component);
		plot.addRenderer(mr);

		String se = component.get("se");
		boolean scaleErrors = (se != null && se.equals("T"));
//		String vs = component.get("vert");
//		boolean vertical = (vs != null && vs.equals("T"));
//		String hs = component.get("horiz");
//		boolean horizontal = (hs != null && hs.equals("T"));
		System.out.println("scaleErrors: " + scaleErrors);
		
		double maxMag = -1E300;
		List<Renderer> vrs = new ArrayList<Renderer>(allData.length);
		for (int i = 0; i < allData.length; i++)
		{
			if (allData[i] == null || allData[i].observations() == 0)
				continue;
			GPSData stn = allData[i];
			DoubleMatrix2D g = null;
			g = stn.createVelocityKernel();
			DoubleMatrix2D m = GPS.solveWeightedLeastSquares(g, stn.getXYZ(), stn.getCovariance());
			System.out.println("Origin: " + locs[i].x + " " + locs[i].y);
			DoubleMatrix2D t = GPS.createENUTransform(locs[i].x, locs[i].y);
			DoubleMatrix2D e = GPS.getErrorParameters(g, stn.getCovariance());
			DoubleMatrix2D t2 = GPS.createFullENUTransform(locs[i].x, locs[i].y, 2);
			e = Algebra.DEFAULT.mult(Algebra.DEFAULT.mult(t2, e), t2.viewDice());
			DoubleMatrix2D v = m.viewPart(0, 0, 3, 1);
	
			System.out.println("XYZ Velocity: " + v.getQuick(0,0) + " " + v.getQuick(1,0) + " " + v.getQuick(2,0));
			DoubleMatrix2D vt = Algebra.DEFAULT.mult(t, v);
			if (vt.getQuick(0, 0) == 0 && vt.getQuick(1, 0) == 0 && vt.getQuick(2, 0) == 0)
				continue;
			
			if (scaleErrors)
			{
				DoubleMatrix2D gm = Algebra.DEFAULT.mult(g, m);
				DoubleMatrix2D r = stn.getXYZ().copy().assign(gm, cern.jet.math.Functions.minus);
				DoubleMatrix2D sdi = Algebra.DEFAULT.inverse(stn.getCovariance());
				DoubleMatrix2D c2 = Algebra.DEFAULT.mult(Algebra.DEFAULT.mult(r.viewDice(), sdi), r);
				double chi2 = c2.getQuick(0, 0) / (stn.getXYZ().rows() - 6);
				e.assign(cern.jet.math.Mult.mult(chi2));
			}
			
			System.out.println("Velocity: " + vt);
			System.out.println("Error: " + e);
			
			DoubleMatrix2D es = e.viewPart(0, 0, 2, 2);
			EigenvalueDecomposition ese = new EigenvalueDecomposition(es);
			DoubleMatrix1D evals = ese.getRealEigenvalues();
			DoubleMatrix2D evecs = ese.getV();
			System.out.println("evals: " + evals);
			System.out.println("evecs: " + evecs);
			double phi = Math.atan2(evecs.getQuick(0, 0), evecs.getQuick(1, 0));
			double w = Math.sqrt(evals.getQuick(0) * 5.9915);
			double h = Math.sqrt(evals.getQuick(1) * 5.9915);
			
			EllipseVectorRenderer2 vr = new EllipseVectorRenderer2();
			vr.frameRenderer = mr;
			Point2D.Double ppt = proj.forward(locs[i]);
			vr.x = ppt.x;
			vr.y = ppt.y;
			vr.u = vt.getQuick(0, 0);
			vr.v = vt.getQuick(1, 0);
			vr.ellipseOrientation = phi;
			vr.ellipseWidth = Math.max(w, h) * 2;
			vr.ellipseHeight = Math.min(w, h) * 2;
			
			maxMag = Math.max(vr.getMag(), maxMag);
			plot.addRenderer(vr);
			vrs.add(vr);
		}
		
		if (maxMag == -1E300)
		{
			return;
		}
		
		double scale = EllipseVectorRenderer2.getBestScale(maxMag);
		System.out.println("Scale: " + scale);
		double desiredLength = Math.min((mr.getMaxY() - mr.getMinY()), (mr.getMaxX() - mr.getMinX())) / 5;
		System.out.println("desiredLength: " + desiredLength);
		System.out.println("desiredLength/scale: " + desiredLength / scale);
		
		for (int i = 0; i < vrs.size(); i++)
		{
			EllipseVectorRenderer2 vr = (EllipseVectorRenderer2)vrs.get(i);
			vr.setScale(desiredLength / scale);
		}
		
		EllipseVectorRenderer2 svr = new EllipseVectorRenderer2();
		svr.frameRenderer = mr;
		svr.drawEllipse = false;
		svr.x = mr.getMinX();
		svr.y = mr.getMinY() + 17 / mr.getYScale();
		svr.u = desiredLength;
		svr.v = 0;
		 
		TextRenderer tr = new TextRenderer();
		tr.x = mr.getGraphX() + 10;
		tr.y = mr.getGraphY() + mr.getGraphHeight() - 5;
		tr.text = scale + " m/year";
		plot.addRenderer(svr);
		plot.addRenderer(tr);
		
		v3Plot.setTitle("GPS Velocities");
	}
	
	
	public void plot(Valve3Plot v3Plot, PlotComponent component)
	{
		Map<String, String> params = new HashMap<String, String>();
		if (benchmarks == null)
		{
			params.put("source", vdxSource);
			params.put("action", "bms");
			Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
			VDXClient client = pool.checkout();
			List<String> bms = (List<String>)client.getData(params);
			pool.checkin(client);
			benchmarks = Benchmark.fromStringsToList(bms);
		}
		//		HashMap params = new HashMap();
//		if (benchmarks == null)
//		{
//			params.put("type", "bms");
//			benchmarks = (List)dataSource.getData(params);
//		}
//		params.clear();
		double end = component.getEndTime();
		double start = component.getStartTime(end);
		if (Double.isNaN(start) || Double.isNaN(end))
		{
			double now = CurrentTime.nowJ2K();
			start = now - 43200;
			end = now;
		}
		
		String plotType = component.get("type");
		if (plotType.equals("ts"))
			plotTimeSeries(v3Plot, component, start, end);
		else if (plotType.equals("vel"))
			plotVelocities(v3Plot, component, start, end);
		
		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		plot.writePNG(v3Plot.getLocalFilename());
	}
}
