package gov.usgs.valve3.plotter;

import gov.usgs.plot.ArbDepthCalculator;
import gov.usgs.plot.ArbDepthFrameRenderer;
import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.BasicFrameRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.PlotException;
import gov.usgs.plot.Renderer;
import gov.usgs.plot.ShapeRenderer;
import gov.usgs.plot.SmartTick;
import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.plot.map.GeoLabelSet;
import gov.usgs.plot.map.MapRenderer;
import gov.usgs.proj.GeoRange;
import gov.usgs.proj.TransverseMercator;
import gov.usgs.util.Log;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.util.UtilException;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.Rank;
import gov.usgs.vdx.data.ExportData;
import gov.usgs.vdx.data.HistogramExporter;
import gov.usgs.vdx.data.hypo.Hypocenter;
import gov.usgs.vdx.data.hypo.HypocenterList;
import gov.usgs.vdx.data.hypo.HypocenterList.BinSize;
import gov.usgs.vdx.data.hypo.plot.HypocenterRenderer;
import gov.usgs.vdx.data.hypo.plot.HypocenterRenderer.AxesOption;
import gov.usgs.vdx.data.hypo.plot.HypocenterRenderer.ColorOption;
import gov.usgs.vdx.data.HypocenterExporter;
import gov.usgs.vdx.data.MatrixExporter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cern.colt.matrix.DoubleMatrix2D;

/**
 * A class for making hypocenter map plots and histograms. 
 * 
 * TODO: implement triple view. 
 * 
 * @author Dan Cervelli
 */
public class HypocenterPlotter extends RawDataPlotter {
	
	private enum PlotType {
		MAP, COUNTS;
		public static PlotType fromString(String s) {
			if (s.equals("map")) {
				return MAP;
			} else if (s.equals("cnts")) {
				return COUNTS;
			} else {
				return null;
			}
		}
	}

	private enum RightAxis {
		NONE(""), CUM_COUNTS("Cumulative Counts"), CUM_MAGNITUDE("Cumulative Magnitude"), CUM_MOMENT("Cumulative Moment");

		private String description;

		private RightAxis(String s) {
			description = s;
		}

		public String toString() {
			return description;
		}

		public static RightAxis fromString(String s) {
			switch (s.charAt(0)) {
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

	private static final double DEFAULT_WIDTH = 100.0;

	 // the width is for the Arbitrary line vs depth plot (SBH)
	private double hypowidth;
	private GeoRange range;
	private Point2D startLoc;
	private Point2D endLoc;
	private double minDepth, maxDepth;
	private double minMag, maxMag;
	private Integer minNPhases, maxNPhases;
	private double minRMS, maxRMS;
	private double minHerr, maxHerr;
	private double minVerr, maxVerr;
	private String rmk;
	
	private AxesOption axesOption;
	private ColorOption colorOption;
	private PlotType plotType;
	private BinSize bin;
	private RightAxis rightAxis;
	private HypocenterList hypos;

	
	/**
	 * Default constructor
	 */
	public HypocenterPlotter() {
		super();
		logger = Log.getLogger("gov.usgs.valve3");
	}

	/**
	 * Initialize internal data from PlotComponent component
	 * 
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	protected void getInputs(PlotComponent comp) throws Valve3Exception {
		
		rk = comp.getInt("rk");
	
		endTime = comp.getEndTime();
		if (Double.isNaN(endTime))
			throw new Valve3Exception("Illegal end time.");
		
		startTime = comp.getStartTime(endTime);
		if (Double.isNaN(startTime))
			throw new Valve3Exception("Illegal start time.");
		
		timeOffset	= comp.getOffset(startTime);
		timeZoneID	= comp.getTimeZone().getID();
		
		String pt = comp.get("plotType");
		if ( pt == null )
			plotType = PlotType.MAP;
		else {
			plotType	= PlotType.fromString(pt);
			if (plotType == null) {
				throw new Valve3Exception("Illegal plot type: " + pt);
			}
		}
		try{
			xTickMarks = comp.getBoolean("xTickMarks");
		} catch(Valve3Exception e){
			xTickMarks=true;
		}
		try{
			xTickValues = comp.getBoolean("xTickValues");
		} catch(Valve3Exception e){
			xTickValues=true;
		}
		try{
			xUnits = comp.getBoolean("xUnits");
		} catch(Valve3Exception e){
			xUnits=true;
		}
		try{
			xLabel = comp.getBoolean("xLabel");
		} catch(Valve3Exception e){
			xLabel=true;
		}
		try{
			yTickMarks = comp.getBoolean("yTickMarks");
		} catch(Valve3Exception e){
			yTickMarks=true;
		}
		try{
			yTickValues = comp.getBoolean("yTickValues");
		} catch(Valve3Exception e){
			yTickValues=true;
		}
		try{
			yUnits = comp.getBoolean("yUnits");
		} catch(Valve3Exception e){
			yUnits=true;
		}
		try{
			yLabel = comp.getBoolean("yLabel");
		} catch(Valve3Exception e){
			yLabel=false;
		}
		try{
			isDrawLegend = comp.getBoolean("lg");
		} catch(Valve3Exception e){
			isDrawLegend=true;
		}
		
		double w = comp.getDouble("west");
		if (w > 360 || w < -360)
			throw new Valve3Exception("Illegal area of interest: w=" +w);
		double e = comp.getDouble("east");
		if (e > 360 || e < -360)
			throw new Valve3Exception("Illegal area of interest: e=" +e);
		double s = comp.getDouble("south");
		if (s < -90)
			throw new Valve3Exception("Illegal area of interest: s=" +s);
		double n = comp.getDouble("north");
		if (n > 90)
			throw new Valve3Exception("Illegal area of interest: n=" +n);		

		this.startLoc	= new Point2D.Double(w,n);
		this.endLoc		= new Point2D.Double(e,s);
		
		if(s>=n){
			double t = s;
			s=n;
			n=t;
		}	
		// w>e -> spans date line.
//		if(w>=e){
//			double t = e;
//			e=w;
//			w=t;
//		}	
		range		= new GeoRange(w, e, s, n);
		
		hypowidth	= Util.stringToDouble(comp.get("hypowidth"), DEFAULT_WIDTH);
		
		minMag		= Util.stringToDouble(comp.get("minMag"), -Double.MAX_VALUE);
		maxMag		= Util.stringToDouble(comp.get("maxMag"), Double.MAX_VALUE);
		if (minMag > maxMag)
			throw new Valve3Exception("Illegal magnitude filter.");
		
		minDepth	= Util.stringToDouble(comp.get("minDepth"), Double.MAX_VALUE);
		maxDepth	= Util.stringToDouble(comp.get("maxDepth"), -Double.MAX_VALUE);
		if (minDepth > maxDepth)
			throw new Valve3Exception("Illegal depth filter.");
		
		minNPhases	= Util.stringToInteger(comp.get("minNPhases"), Integer.MIN_VALUE);
		maxNPhases	= Util.stringToInteger(comp.get("maxNPhases"), Integer.MAX_VALUE);
		if (minNPhases > maxNPhases)
			throw new Valve3Exception("Illegal nphases filter.");
		
		minRMS		= Util.stringToDouble(comp.get("minRMS"), -Double.MAX_VALUE);
		maxRMS		= Util.stringToDouble(comp.get("maxRMS"), Double.MAX_VALUE);
		if (minRMS > maxRMS)
			throw new Valve3Exception("Illegal RMS filter.");
		
		minHerr		= Util.stringToDouble(comp.get("minHerr"), -Double.MAX_VALUE);
		maxHerr		= Util.stringToDouble(comp.get("maxHerr"), Double.MAX_VALUE);
		if (minHerr > maxHerr)
			throw new Valve3Exception("Illegal horizontal error filter.");
		
		minVerr		= Util.stringToDouble(comp.get("minVerr"), -Double.MAX_VALUE);
		maxVerr		= Util.stringToDouble(comp.get("maxVerr"), Double.MAX_VALUE);
		if (minVerr > maxVerr)
			throw new Valve3Exception("Illegal vertical error filter.");
		
		rmk			= Util.stringToString(comp.get("rmk"), "");
		
		switch (plotType) {
		
		case MAP:
			
			// axes defaults to Map View
			axesOption	= AxesOption.fromString(Util.stringToString(comp.get("axesOption"), "M"));
			if (axesOption == null)
				throw new Valve3Exception("Illegal axes type.");

			// color defaults to Auto
			String c	= Util.stringToString(comp.get("colorOption"), "A");
			if (c.equals("A"))
				colorOption	= ColorOption.chooseAuto(axesOption);
			else
				colorOption	= ColorOption.fromString(c);
			if (colorOption == null)
				throw new Valve3Exception("Illegal color option.");
			
			break;
		
		case COUNTS:
			
			// bin size defalts to day
			bin		= BinSize.fromString(Util.stringToString(comp.get("cntsBin"), "day"));
			if (bin == null)
				throw new Valve3Exception("Illegal bin size option.");

			if ((endTime - startTime) / bin.toSeconds() > 10000)
				throw new Valve3Exception("Bin size too small.");

			// right axis default to cumulative counts
			rightAxis	= RightAxis.fromString(Util.stringToString(comp.get("cntsAxis"), "C"));
			if (rightAxis == null)
				throw new Valve3Exception("Illegal counts axis option.");
			
			break;
		}
	}

	/**
	 * Gets hypocenter list binary data from VDX
	 * 
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	protected void getData(PlotComponent comp) throws Valve3Exception {
		
		// initialize variables
		boolean exceptionThrown	= false;
		String exceptionMsg		= "";
		Pool<VDXClient> pool	= null;
		VDXClient client		= null;
		
		double twest	= range.getWest();
		double teast	= range.getEast();
		double tsouth	= range.getSouth();
		double tnorth	= range.getNorth();
		
		// we need to get extra hypocenters for plotting the width	
		if (axesOption == AxesOption.ARB_DEPTH || axesOption == AxesOption.ARB_TIME) {		
			double latDiff 		= ArbDepthCalculator.getLatDiff(hypowidth);			
			double lonNorthDiff = ArbDepthCalculator.getLonDiff(hypowidth, tnorth);
			double lonSouthDiff = ArbDepthCalculator.getLonDiff(hypowidth, tsouth);
			
			twest -= lonSouthDiff;
			teast += lonNorthDiff;
			tsouth -= latDiff;
			tnorth += latDiff;
		}
			
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("rk", Integer.toString(rk));
		params.put("west", Double.toString(twest));
		params.put("east", Double.toString(teast));
		params.put("south", Double.toString(tsouth));
		params.put("north", Double.toString(tnorth));
		params.put("minDepth", Double.toString(minDepth));
		params.put("maxDepth", Double.toString(maxDepth));
		params.put("minMag", Double.toString(minMag));
		params.put("maxMag", Double.toString(maxMag));
		params.put("minNPhases", Integer.toString(minNPhases));
		params.put("maxNPhases", Integer.toString(maxNPhases));
		params.put("minRMS", Double.toString(minRMS));
		params.put("maxRMS", Double.toString(maxRMS));
		params.put("minHerr", Double.toString(minHerr));
		params.put("maxHerr", Double.toString(maxHerr));
		params.put("minVerr", Double.toString(minVerr));
		params.put("maxVerr", Double.toString(maxVerr));
		params.put("rmk", (rmk));

		// checkout a connection to the database
		pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		if (pool != null) {
			client	= pool.checkout();

			// get the data, if nothing is returned then create an empty list
			try {
				hypos = (HypocenterList)client.getBinaryData(params);
			} catch (UtilException e) {
				exceptionThrown	= true;
				exceptionMsg	= e.getMessage();
			} catch (Exception e) {
				hypos = null; 
			}
		
			// we return an empty list if there is no data, because it is valid to have no hypocenters for a time period
			if (hypos != null) {
				hypos.adjustTime(timeOffset);
			} else {
				hypos = new HypocenterList();
			}
			
			// check back in our connection to the database
			pool.checkin(client);
		} 
		
		// if a data limit message exists, then throw exception
		if (exceptionThrown) {
			throw new Valve3Exception(exceptionMsg);
		}
	}

	/**
	 * Initialize MapRenderer and add it to given plot 
	 * 
	 * @param plot Valve3Plot
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	private BasicFrameRenderer plotMapView(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception {
		
		// TODO: make projection variable
		TransverseMercator proj = new TransverseMercator();
		Point2D.Double origin = range.getCenter();
		proj.setup(origin, 0, 0);

		hypos.project(proj);

		MapRenderer mr = new MapRenderer(range, proj);
		mr.setLocationByMaxBounds(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(), comp.getBoxMapHeight());
		v3p.getPlot().setSize(v3p.getPlot().getWidth(), mr.getGraphHeight() + 190);

		GeoLabelSet labels = Valve3.getInstance().getGeoLabelSet();
		labels = labels.getSubset(range);
		mr.setGeoLabelSet(labels);

		GeoImageSet images = Valve3.getInstance().getGeoImageSet();
		RenderedImage ri = images.getMapBackground(proj, range, comp.getBoxWidth());

		mr.setMapImage(ri);
		mr.createBox(8);
		mr.createGraticule(8, xTickMarks, yTickMarks, xTickValues, yTickValues, Color.BLACK);
		
		double[] trans = mr.getDefaultTranslation(v3p.getPlot().getHeight());
		trans[4] = startTime+timeOffset;
		trans[5] = endTime+timeOffset;
		trans[6] = origin.x;
		trans[7] = origin.y;
		comp.setTranslation(trans);
		comp.setTranslationType("map");
		return mr;
	}

	/**
	 * Initialize BasicFrameRenderer (init mode depends from axes type) and add it to plot.
	 * Generate PNG image to local file.
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @param rank Rank
	 * @throws Valve3Exception
	 */
	private void plotMap(Valve3Plot v3p, PlotComponent comp, Rank rank) throws Valve3Exception {
		
		// default variables
		ArbDepthCalculator adc = null;	
		String subCount = "";
		double lat1;
		double lon1;
		double lat2;
		double lon2;
		int count;
		List<Hypocenter> myhypos;	
		BasicFrameRenderer base = new BasicFrameRenderer();
		base.setLocation(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(), comp.getBoxHeight() - 16);
		v3p.getPlot().setSize(v3p.getPlot().getWidth(), v3p.getPlot().getHeight() + 115);
		
		switch (axesOption) {
		
		case MAP_VIEW:
			base = plotMapView(v3p, comp);
			base.createEmptyAxis();
			if(xUnits) base.getAxis().setBottomLabelAsText("Longitude");
			if(yUnits) base.getAxis().setLeftLabelAsText("Latitude");
			((MapRenderer)base).createScaleRenderer();
			break;
			
		case LON_DEPTH:
			base.setExtents(range.getWest(), range.getEast(), hypos.getMaxDepth(maxDepth), hypos.getMinDepth(minDepth));
			base.createDefaultAxis();
			if(xUnits) base.getAxis().setBottomLabelAsText("Longitude");
			if(yUnits) base.getAxis().setLeftLabelAsText("Depth (km)");
			comp.setTranslation(base.getDefaultTranslation(v3p.getPlot().getHeight()));
			comp.setTranslationType("xy");
			break;
			
		case LAT_DEPTH:
			base.setExtents(range.getSouth(), range.getNorth(), hypos.getMaxDepth(maxDepth), hypos.getMinDepth(minDepth));
			base.createDefaultAxis();
			if(xUnits) base.getAxis().setBottomLabelAsText("Latitude");
			if(yUnits) base.getAxis().setLeftLabelAsText("Depth (km)");
			comp.setTranslation(base.getDefaultTranslation(v3p.getPlot().getHeight()));
			comp.setTranslationType("xy");
			break;
			
		case TIME_DEPTH:
			base.setExtents(startTime+timeOffset, endTime+timeOffset, hypos.getMaxDepth(maxDepth), hypos.getMinDepth(minDepth));
			base.createDefaultAxis();
			base.setXAxisToTime(8);
			if(xUnits) base.getAxis().setBottomLabelAsText(timeZoneID + " Time (" + Util.j2KToDateString(startTime+timeOffset, dateFormatString) + " to " + Util.j2KToDateString(endTime+timeOffset, dateFormatString)+ ")");
			if(yUnits) base.getAxis().setLeftLabelAsText("Depth (km)");
			comp.setTranslation(base.getDefaultTranslation(v3p.getPlot().getHeight()));
			comp.setTranslationType("ty");
			break;
			
		case ARB_DEPTH:
		
			// need to set the extents for along the line. km offset?
			base = new ArbDepthFrameRenderer();
			base.setLocation(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(), comp.getBoxHeight() - 16);

			lat1 = startLoc.getY();
			lon1 = startLoc.getX();
			lat2 = endLoc.getY();
			lon2 = endLoc.getX();

			adc = new ArbDepthCalculator(lat1, lon1, lat2, lon2, hypowidth);

			((ArbDepthFrameRenderer)base).setArbDepthCalc(adc);

			base.setExtents(0.0, adc.getMaxDist(), hypos.getMaxDepth(maxDepth), hypos.getMinDepth(minDepth));
			base.createDefaultAxis();
			
			if(xUnits) base.getAxis().setBottomLabelAsText("Distance (km) from (" + lat1 + "," + lon1 +") to (" + lat2 + "," + lon2 +") - width = " +hypowidth + " km");
			if(yUnits) base.getAxis().setLeftLabelAsText("Depth (km)");
			
			comp.setTranslation(base.getDefaultTranslation(v3p.getPlot().getHeight()));
			comp.setTranslationType("xy");
		
			count = 0;
			myhypos = hypos.getHypocenters();
			for (int i = 0; i < myhypos.size(); i++) {	
				
				Hypocenter hc = (Hypocenter) myhypos.get(i);
				if (adc.isInsideArea(hc.lat, hc.lon)) {
					count++;
				}
			}
			subCount = new String(count + " of ");
			
			break;
			
		case ARB_TIME:
			
			// need to set the extents for along the line. km offset?
			base = new ArbDepthFrameRenderer();
			base.setLocation(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(), comp.getBoxWidth());			
			v3p.getPlot().setSize(v3p.getPlot().getWidth(), base.getGraphHeight() + 190);
			
			lat1 = startLoc.getY();
			lon1 = startLoc.getX();
			lat2 = endLoc.getY();
			lon2 = endLoc.getX();

			adc = new ArbDepthCalculator(lat1, lon1, lat2, lon2, hypowidth);

			((ArbDepthFrameRenderer)base).setArbDepthCalc(adc);	

			base.setExtents(startTime+timeOffset, endTime+timeOffset, 0.0, adc.getMaxDist());
			base.createDefaultAxis();
			base.setXAxisToTime(8);			
					
			if(xUnits) base.getAxis().setBottomLabelAsText(timeZoneID + " Time (" + Util.j2KToDateString(startTime+timeOffset, dateFormatString) + " to " + Util.j2KToDateString(endTime+timeOffset, dateFormatString)+ ")");
			if(yUnits) base.getAxis().setLeftLabelAsText("Distance (km) from (" + lat1 + "," + lon1 +") to (" + lat2 + "," + lon2 +") - width = " +hypowidth + " km");
						
			comp.setTranslation(base.getDefaultTranslation(v3p.getPlot().getHeight()));
			comp.setTranslationType("ty");	
		
			count = 0;
			myhypos = hypos.getHypocenters();
			for (int i = 0; i < myhypos.size(); i++) {	
				
				Hypocenter hc = (Hypocenter) myhypos.get(i);
				if (adc.isInsideArea(hc.lat, hc.lon)) {
					count++;
				}
			}
			subCount = new String(count + " of ");
			
			break;
		}
		
		// set the label at the top of the plot.
		if (xLabel) {
			base.getAxis().setTopLabelAsText(subCount + getTopLabel(rank));
		}
		
		// add this plot to the valve plot
		v3p.getPlot().addRenderer(base);
		
		// create the scale renderer
		HypocenterRenderer hr = new HypocenterRenderer(hypos, base, axesOption);
		hr.setColorOption(colorOption);
		if (colorOption == ColorOption.TIME)
			hr.setColorTime(startTime+timeOffset, endTime+timeOffset);
		if (xLabel) {
			hr.createColorScaleRenderer(base.getGraphX() + base.getGraphWidth() / 2 + 150, base.getGraphY() + base.getGraphHeight() + 150);
			hr.createMagnitudeScaleRenderer(base.getGraphX() + base.getGraphWidth() / 2 - 150, base.getGraphY() + base.getGraphHeight() + 150);
		}
		v3p.getPlot().addRenderer(hr);
		v3p.addComponent(comp);
	}

	/**
	 * If v3Plot is null, prepare data for exporting
	 * Otherwise, initialize HistogramRenderer and add it to plot.
	 * 		Generate PNG image to local file.
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @param rank Rank
	 * @throws Valve3Exception 
	 */
	private void plotCounts(Valve3Plot v3p, PlotComponent comp, Rank rank) throws Valve3Exception {
		
		int leftLabels = 0;		
		HistogramExporter hr = new HistogramExporter(hypos.getCountsHistogram(bin));
		hr.setLocation(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(), comp.getBoxHeight() - 16);
		hr.setDefaultExtents();
		hr.setMinX(startTime+timeOffset);
		hr.setMaxX(endTime+timeOffset);
		hr.createDefaultAxis(8,8,xTickMarks,yTickMarks, false, true, xTickValues, yTickValues);
		hr.setXAxisToTime(8, xTickMarks, xTickValues);
		if (yUnits) hr.getAxis().setLeftLabelAsText("Earthquakes per " + bin);
		if (xUnits) hr.getAxis().setBottomLabelAsText(timeZoneID + " Time (" + Util.j2KToDateString(startTime+timeOffset, dateFormatString) + " to " + Util.j2KToDateString(endTime+timeOffset, dateFormatString)+ ")");
		if (xLabel) hr.getAxis().setTopLabelAsText(getTopLabel(rank));
		if (hr.getAxis().getLeftLabels() != null) {
			leftLabels = hr.getAxis().getLeftLabels().length;
		}
		if ( forExport ) {
			// Add column headers to csvHdrs (second one incomplete)
			csvHdrs.append(String.format( ",%s_EventsPer%s", rank.getName(), bin ));
			csvData.add( new ExportData( csvIndex, hr ) );
			csvIndex++;
		}
		DoubleMatrix2D data = null;
		String headerFmt = "";
		switch (rightAxis) {
		case CUM_COUNTS:
			data = hypos.getCumulativeCounts();
			if ( forExport )
				// Add specialized part of column header to csvText
				headerFmt = ",%s_CumulativeCounts";
			break;
		case CUM_MAGNITUDE:
			data = hypos.getCumulativeMagnitude();
			if ( forExport )
				// Add specialized part of column header to csvText
				headerFmt = ",%s_CumulativeMagnitude";
			break;
		case CUM_MOMENT:
			data = hypos.getCumulativeMoment();
			if ( forExport )
				// Add specialized part of column header to csvText
				headerFmt = ",%s_CumulativeMoment";
			break;
		}
		if (data != null && data.rows() > 0) {
			double cmin = data.get(0, 1);
			double cmax = data.get(data.rows() - 1, 1);	
			
			// TODO: utilize ranks for counts plots
			MatrixExporter mr = new MatrixExporter(data, false, null);
			mr.setAllVisible(true);
			mr.setLocation(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(), comp.getBoxHeight() - 16);
			mr.setExtents(startTime+timeOffset, endTime+timeOffset, cmin, cmax * 1.05);
			mr.createDefaultLineRenderers(comp.getColor());
			
			if ( forExport ) {
				// Add column to header; add Exporter to set for CSV
				csvHdrs.append(String.format( headerFmt, rank.getName() ));
				csvData.add( new ExportData( csvIndex, mr ) );
				csvIndex++;
			} else {
				Renderer[] r = mr.getLineRenderers();
				((ShapeRenderer)r[0]).color		= Color.red;
				((ShapeRenderer)r[0]).stroke	= new BasicStroke(2.0f);
				AxisRenderer ar = new AxisRenderer(mr);
				if (yTickValues) ar.createRightTickLabels(SmartTick.autoTick(cmin, cmax, leftLabels, false), null);
				if (yUnits) hr.getAxis().setRightLabelAsText(rightAxis.toString());
				mr.setAxis(ar);			
				hr.addRenderer(mr);
			}
		}
		if(isDrawLegend) hr.createDefaultLegendRenderer(new String[] {rank.getName() + " Events"});
		
		if (!forExport) {
			comp.setTranslation(hr.getDefaultTranslation(v3p.getPlot().getHeight()));
			comp.setTranslationType("ty");
			v3p.getPlot().addRenderer(hr);
			v3p.addComponent(comp);	
		}
	}
	
	/**
	 * Compute rank, calls appropriate function to init renderers
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3p, PlotComponent comp, Rank rank) throws Valve3Exception {
		
		switch (plotType) {
		case MAP:
			if (forExport) {
				// Add column headers to csvHdrs
				csvHdrs.append(", Lat, Lon, Depth, PrefMag");
				// Initialize data for export; add to set for CSV
				ExportData ed = new ExportData(csvIndex, new HypocenterExporter(hypos));
				csvData.add(ed);
				csvIndex++;
			} else {
				plotMap(v3p, comp, rank);
				v3p.setCombineable(false);
				v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Map");
			}
			break;
		
		case COUNTS:
			plotCounts(v3p, comp, rank);
			if (!forExport){
				v3p.setCombineable(true);
				v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Counts");
			}
			break;			
		}			
	}

	/**
	 * Concrete realization of abstract method. 
	 * Generate PNG image (hypocenters map or histogram, depends on plot type) to file with random name.
	 * If v3p is null, prepare data for export -- assumes csvData, csvData & csvIndex initialized
	 * @param v3p Valve3Plot
	 * @param comp PlotComponent
	 * @throws Valve3Exception
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception, PlotException {
		
		forExport	= (v3p == null);
		ranksMap	= getRanks(vdxSource, vdxClient);
		comp.setPlotter(this.getClass().getName());		
		getInputs(comp);
		
		// get the rank object for this request
		Rank rank	= new Rank();
		if (rk == 0) {
			rank	= rank.bestPossible();
		} else {
			rank	= ranksMap.get(rk);
		}
		
		// plot configuration
		if (!forExport) {
			if (rk == 0) {
				v3p.setExportable(false);
			} else {
				v3p.setExportable(true);
			}
			
		// export configuration
		} else {
			if (rk == 0) {
				throw new Valve3Exception( "Data Export Not Available for Best Possible Rank");
			}
		}
		
		// this is a legitimate request so lookup the data from the database and plot it
		getData(comp);		
		plotData(v3p, comp, rank);
				
		if (!forExport) {
			Plot plot = v3p.getPlot();
			plot.setBackgroundColor(Color.white);
			plot.writePNG(v3p.getLocalFilename());
		}
	}

	/**
	 * 
	 * @return plot top label text
	 */
	private String getTopLabel(Rank rank) {
		
		StringBuilder top = new StringBuilder(100);		
		top.append(hypos.size() + " " + rank.getName());
		
		// data coming from the hypocenters list have already been adjusted for the time offset
		if (hypos.size() == 1) {
			top.append(" earthquake on ");
			top.append(Util.j2KToDateString(hypos.getHypocenters().get(0).j2ksec, dateFormatString));
		} else {
			top.append(" earthquakes between ");
			if (hypos.size() == 0) {				
				top.append(Util.j2KToDateString(startTime+timeOffset, dateFormatString));
				top.append(" and ");
				top.append(Util.j2KToDateString(endTime+timeOffset, dateFormatString));
			} else if (hypos.size() > 1) {
				top.append(Util.j2KToDateString(hypos.getHypocenters().get(0).j2ksec, dateFormatString));
				top.append(" and ");
				top.append(Util.j2KToDateString(hypos.getHypocenters().get(hypos.size() - 1).j2ksec, dateFormatString));
			}
		}
		top.append(" " + timeZoneID + " Time");
		return top.toString();
	}
}
