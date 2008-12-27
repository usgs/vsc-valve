package gov.usgs.valve3.plotter;

import gov.usgs.plot.Data;
import gov.usgs.plot.DataRenderer;
import gov.usgs.plot.EllipseVectorRenderer2;
import gov.usgs.plot.Plot;
import gov.usgs.plot.Renderer;
import gov.usgs.plot.TextRenderer;
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
import gov.usgs.vdx.data.gps.Benchmark;
import gov.usgs.vdx.data.gps.DataPoint;
import gov.usgs.vdx.data.gps.GPS;
import gov.usgs.vdx.data.gps.GPSData;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.colt.matrix.linalg.EigenvalueDecomposition;

/**
 * TODO: un-hardcode stid 
 * TODO: check map sizes against client max height.
 * 
 * Generate images of coordinate time series and velocity maps from vdx source
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.9  2006/08/29 21:03:22  dcervelli
 * Changed GeoRange import.
 *
 * Revision 1.8  2006/04/09 21:24:51  dcervelli
 * GPS time series plot titles include stations.
 *
 * Revision 1.7  2006/04/09 18:19:36  dcervelli
 * VDX type safety changes.
 *
 * Revision 1.6  2005/10/13 20:35:39  dcervelli
 * Now gets stid from plotterConfig.
 *
 * Revision 1.5  2005/10/07 16:46:10  dcervelli
 * Added lon/lat labels.
 *
 * Revision 1.4  2005/09/06 20:09:45  dcervelli
 * Added some data integrity checks.
 *
 * Revision 1.3  2005/09/05 00:40:46  dcervelli
 * Fixed benchmarks map so more than one GPSPlotter could exist.
 *
 * Revision 1.2  2005/09/03 19:02:58  dcervelli
 * Interim checkin for CurrentTime.
 *
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
public class GPSPlotter extends Plotter
{
	private enum PlotType
	{
		TIME_SERIES, VELOCITY_MAP, DISPLACEMENT_MAP;
		
		public static PlotType fromString(String s)
		{
			if (s == null)
				return null;
			
			if (s.equals("ts"))
				return TIME_SERIES;
			else if (s.equals("vel"))
				return VELOCITY_MAP;
			else if (s.equals("dis"))
				return DISPLACEMENT_MAP;
			else 
				return null;
		}
	}
	
	private static final String[] LEGENDS = new String[] {"East", "North", "Up", "Length"};
	private static Map<String, Map<String, Benchmark>> benchmarksMap = new HashMap<String, Map<String, Benchmark>>();
	
	private Valve3Plot v3Plot;
	private PlotComponent component;
	private double startTime;
	private double endTime;
	private PlotType plotType;
	private boolean scaleErrors;
	private String benchmarkIDs;
	private String baselineID;
	private GPSData baselineData;
	private String solutionTypeID;
	private Map<String, GPSData> stationDataMap;

	/**
	 * Default constructor
	 */
	public GPSPlotter()
	{}
	
	/**
	 * Initialize internal data from PlotComponent component
	 * @throws Valve3Exception
	 */
	public void getInputs() throws Valve3Exception
	{
		endTime = component.getEndTime();
		if (Double.isNaN(endTime))
			throw new Valve3Exception("Illegal end time.");
		startTime = component.getStartTime(endTime);
		if (Double.isNaN(startTime))
			throw new Valve3Exception("Illegal start time.");
		
		benchmarkIDs = component.get("bm");
		if (benchmarkIDs == null)
			throw new Valve3Exception("Illegal benchmarks.");
		
		baselineID = component.get("bl");
		if (baselineID.equals("[none]"))
			baselineID = null;
		
		solutionTypeID = component.get("stid");
		if (solutionTypeID == null)
			throw new Valve3Exception("Illegal solution type.");		
		
		plotType = PlotType.fromString(component.get("type"));
		if (plotType == null)
			throw new Valve3Exception("Illegal plot type.");
		
		String se = component.get("se");
		scaleErrors = (se != null && se.equals("T"));
	}
	
	/**
	 * Gets binary data from VDX
	 * @throws Valve3Exception
	 */
	public void getData() throws Valve3Exception
	{
		stationDataMap = new HashMap<String, GPSData>();
		String[] bms = benchmarkIDs.split(",");
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("stid", solutionTypeID);
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		boolean gotData = false;
		for (String bm : bms) {
			params.put("bm", bm);
			GPSData data = (GPSData)client.getBinaryData(params);
			if (data != null && data.observations() > 0) {
				gotData = true;
				stationDataMap.put(bm, data);
			}
		}
		if (!gotData)
			throw new Valve3Exception("No data for any stations.");
		
		if (baselineID != null)
		{
			params.put("bm", baselineID);
			baselineData = (GPSData)client.getBinaryData(params);
			if (baselineData == null || baselineData.observations() == 0)
				throw new Valve3Exception("No baseline data.");
		}
		pool.checkin(client);
	}
	
	/**
	 * Initialize DataRenderer to plot time series and adds renderer to plot
	 */
	private void plotTimeSeries()
	{			
		boolean ce = component.get("east").equals("T");
		boolean cn = component.get("north").equals("T");
		boolean cu = component.get("up").equals("T");
		boolean cl = component.get("len").equals("T");
		boolean[] comps = new boolean[] { ce, cn, cu, cl };
		
		Set<String> keys = stationDataMap.keySet();
		String id = keys.iterator().next();
		Map<String, Benchmark> benchmarks = benchmarksMap.get(vdxSource);
		Benchmark bm = benchmarks.get(id);
		GPSData data = stationDataMap.get(id);
		
		double[][] dd = data.toTimeSeries(baselineData);
		Data d = new Data(dd);
		
		DataPoint dp = data.getFirstObservation();
		d.add(1, -dp.x);
		d.add(2, -dp.y);
		d.add(3, -dp.z);
		d.add(4, -dp.len);	
		
		String bs = "";
		if (baselineData != null)
			bs = "-" + benchmarks.get(baselineID).getCode();

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
//				double dy = Math.max(0.02, (maxY - minY) * 1.2);
				double dy = Math.max(0.02, Math.abs((maxY - minY) * 1.8));
				dr.setExtents(startTime, endTime, minY - dy, maxY + dy);
//				dr.setExtents(st, et, -0.1, 0.1);
				dr.createDefaultAxis(8, 4, false, false);
				dr.createDefaultPointRenderers();
				dr.createDefaultLegendRenderer(new String[] { String.format("%s%s ", bm.getCode(), bs) + LEGENDS[i] });
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
		
		v3Plot.setTitle(String.format("GPS: %s%s", bm.getCode(), bs));
	}

	/**
	 * Initialize MapRenderer to plot map and list of EllipseVectorRenderer2 
	 * elements which plot a station's movement
	 */
	private void plotVelocityMap()
	{
//		Map<String, Point2D.Double> locs = new HashMap<String, Point2D.Double>();
		List<Point2D.Double> locs = new ArrayList<Point2D.Double>();
		Map<String, Benchmark> benchmarks = benchmarksMap.get(vdxSource);
		for (String key : stationDataMap.keySet())
		{
			GPSData data = stationDataMap.get(key);
			if (data != null)
			{
				if (baselineData != null)
					data.applyBaseline(baselineData);
				locs.add(benchmarks.get(key).getLonLat());
			}
		}
		
		GeoRange range = GeoRange.getBoundingBox(locs);
		System.out.println(range);
		
		Plot plot = v3Plot.getPlot();
		
		TransverseMercator proj = new TransverseMercator();
		Point2D.Double origin = range.getCenter();
		proj.setup(origin, 0, 0);

		MapRenderer mr = new MapRenderer(range, proj);
//		mr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth());
		mr.setLocationByMaxBounds(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), Integer.parseInt(component.get("mh")));
		
		GeoLabelSet labels = Valve3.getInstance().getGeoLabelSet();
		labels = labels.getSubset(range);
		mr.setGeoLabelSet(labels);
		
		GeoImageSet images = Valve3.getInstance().getGeoImageSet();
		RenderedImage ri = images.getMapBackground(proj, range, component.getBoxWidth());
		
		mr.setMapImage(ri);
		mr.createBox(8);
		mr.createGraticule(8, true);
		mr.createScaleRenderer();
		plot.setSize(plot.getWidth(), mr.getGraphHeight() + 60);
		double[] trans = mr.getDefaultTranslation(plot.getHeight());
		trans[4] = range.getWest();
		trans[5] = range.getEast();
		trans[6] = range.getSouth();
		trans[7] = range.getNorth();
		component.setTranslation(trans);
		component.setTranslationType("map");
		v3Plot.addComponent(component);
		mr.createEmptyAxis();
		mr.getAxis().setBottomLabelAsText("Longitude");
		mr.getAxis().setLeftLabelAsText("Latitude");
		plot.addRenderer(mr);

//		String vs = component.get("vert");
//		boolean vertical = (vs != null && vs.equals("T"));
//		String hs = component.get("horiz");
//		boolean horizontal = (hs != null && hs.equals("T"));
		
		double maxMag = -1E300;
		List<Renderer> vrs = new ArrayList<Renderer>();
		
		//for (int i = 0; i < allData.length; i++)
		for (String key : stationDataMap.keySet())
		{
			Benchmark bm = benchmarks.get(key);
//			Point2D.Double loc = locs.get(key);
//			if ()
			labels.add(new GeoLabel(bm.getCode(), bm.getLon(), bm.getLat()));
			GPSData stn = stationDataMap.get(key);
//			stn.output();
			if (stn == null || stn.observations() <= 1)
				continue;
			
			DoubleMatrix2D g = null;
			g = stn.createVelocityKernel();
			DoubleMatrix2D m = GPS.solveWeightedLeastSquares(g, stn.getXYZ(), stn.getCovariance());
			DoubleMatrix2D t = GPS.createENUTransform(bm.getLon(), bm.getLat());
			DoubleMatrix2D e = GPS.getErrorParameters(g, stn.getCovariance());
			DoubleMatrix2D t2 = GPS.createFullENUTransform(bm.getLon(), bm.getLat(), 2);
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
			System.out.printf("w: %f h: %f\n", w, h);
			EllipseVectorRenderer2 vr = new EllipseVectorRenderer2();
			vr.frameRenderer = mr;
			Point2D.Double ppt = proj.forward(bm.getLonLat());
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

	/**
	 * Initialize list of benchmarks for given vdx source
	 * @param source vdx source name
	 * @param client vdx name
	 */
	private static void getBenchmarks(String source, String client)
	{
		synchronized (benchmarksMap)
		{
			Map<String, Benchmark> benchmarks = benchmarksMap.get(source);
			
			if (benchmarks == null)
			{
				Map<String, String> params = new HashMap<String, String>();
				params.put("source", source);
				params.put("action", "bms");
				Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(client);
				VDXClient cl = pool.checkout();
				List<String> bms = cl.getTextData(params);
				pool.checkin(cl);
				benchmarks = Benchmark.fromStringsToMap(bms);
				benchmarksMap.put(source, benchmarks);
			}
		}
	}

	/**
	 * Concrete realization of abstract method. 
	 * Generate PNG image to local file.
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception
	{
		v3Plot = v3p;
		component = comp;
		getBenchmarks(vdxSource, vdxClient);
		getInputs();
		getData();
		
		switch (plotType)
		{
			case TIME_SERIES:
				plotTimeSeries();
				break;
			case VELOCITY_MAP:
				plotVelocityMap();
				break;
		}
		
		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		plot.writePNG(v3Plot.getLocalFilename());
	}
}
