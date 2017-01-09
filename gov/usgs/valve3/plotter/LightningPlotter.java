package gov.usgs.valve3.plotter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cern.colt.matrix.DoubleMatrix2D;
import gov.usgs.plot.PlotException;
import gov.usgs.plot.decorate.SmartTick;
import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.plot.map.GeoLabelSet;
import gov.usgs.plot.map.MapRenderer;
import gov.usgs.plot.render.AxisRenderer;
import gov.usgs.plot.render.BasicFrameRenderer;
import gov.usgs.plot.render.InvertedFrameRenderer;
import gov.usgs.plot.render.Renderer;
import gov.usgs.plot.render.ShapeRenderer;
import gov.usgs.plot.transform.ArbDepthCalculator;
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
import gov.usgs.vdx.data.ExportData;
import gov.usgs.vdx.data.HistogramExporter;
import gov.usgs.vdx.data.MatrixExporter;
import gov.usgs.vdx.data.Rank;
import gov.usgs.vdx.data.lightning.LightningExporter;
import gov.usgs.vdx.data.lightning.LightningRenderer;
import gov.usgs.vdx.data.lightning.LightningRenderer.AxesOption;
import gov.usgs.vdx.data.lightning.LightningRenderer.ColorOption;
import gov.usgs.vdx.data.lightning.Stroke;
import gov.usgs.vdx.data.lightning.StrokeList;
import gov.usgs.vdx.data.lightning.StrokeList.BinSize;

/**
 * A class for making lightning map plots and histograms.
 * 
 * Modeled after HypocenterPlotter.
 * 
 * @author Tom Parker
 */
public class LightningPlotter extends RawDataPlotter {

	private enum PlotType {
		MAP, STROKES;
		public static PlotType fromString(String s) {
			if (s.equals("map")) {
				return MAP;
			} else if (s.equals("cnts")) {
				return STROKES;
			} else {
				return null;
			}
		}
	}

	private enum RightAxis {
		NONE(""), CUM_STROKES("Cumulative Strikes");

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
			case 'S':
				return CUM_STROKES;
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
	private boolean doLog;

	private AxesOption axesOption;
	private ColorOption colorOption;
	private PlotType plotType;
	private BinSize bin;
	private RightAxis rightAxis;
	private StrokeList strokes;

	/**
	 * Default constructor
	 */
	public LightningPlotter() {
		super();
		logger = Log.getLogger("gov.usgs.valve3");
	}

	/**
	 * Initialize internal data from PlotComponent component
	 * 
	 * @param component
	 *            PlotComponent
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

		timeOffset = comp.getOffset(startTime);
		timeZoneID = comp.getTimeZone().getID();

		String pt = comp.get("plotType");
		if (pt == null)
			plotType = PlotType.MAP;
		else {
			plotType = PlotType.fromString(pt);
			if (plotType == null) {
				throw new Valve3Exception("Illegal plot type: " + pt);
			}
		}
		try {
			xTickMarks = comp.getBoolean("xTickMarks");
		} catch (Valve3Exception e) {
			xTickMarks = true;
		}
		try {
			xTickValues = comp.getBoolean("xTickValues");
		} catch (Valve3Exception e) {
			xTickValues = true;
		}
		try {
			xUnits = comp.getBoolean("xUnits");
		} catch (Valve3Exception e) {
			xUnits = true;
		}
		try {
			xLabel = comp.getBoolean("xLabel");
		} catch (Valve3Exception e) {
			xLabel = true;
		}
		try {
			yTickMarks = comp.getBoolean("yTickMarks");
		} catch (Valve3Exception e) {
			yTickMarks = true;
		}
		try {
			yTickValues = comp.getBoolean("yTickValues");
		} catch (Valve3Exception e) {
			yTickValues = true;
		}
		try {
			yUnits = comp.getBoolean("yUnits");
		} catch (Valve3Exception e) {
			yUnits = true;
		}
		try {
			yLabel = comp.getBoolean("yLabel");
		} catch (Valve3Exception e) {
			yLabel = false;
		}
		try {
			isDrawLegend = comp.getBoolean("lg");
		} catch (Valve3Exception e) {
			isDrawLegend = true;
		}

		double w = comp.getDouble("west");
		if (w > 360 || w < -360)
			throw new Valve3Exception("Illegal area of interest: w=" + w);
		double e = comp.getDouble("east");
		if (e > 360 || e < -360)
			throw new Valve3Exception("Illegal area of interest: e=" + e);
		double s = comp.getDouble("south");
		if (s < -90)
			throw new Valve3Exception("Illegal area of interest: s=" + s);
		double n = comp.getDouble("north");
		if (n > 90)
			throw new Valve3Exception("Illegal area of interest: n=" + n);

		this.startLoc = new Point2D.Double(w, n);
		this.endLoc = new Point2D.Double(e, s);

		if (s >= n) {
			double t = s;
			s = n;
			n = t;
		}

		range = new GeoRange(w, e, s, n);

		hypowidth = Util.stringToDouble(comp.get("hypowidth"), DEFAULT_WIDTH);

		switch (plotType) {

		case MAP:

			// axes defaults to Map View
			axesOption = AxesOption.fromString(Util.stringToString(comp.get("axesOption"), "M"));
			if (axesOption == null)
				throw new Valve3Exception("Illegal axes type.");

			// color defaults to Auto
			String c = Util.stringToString(comp.get("colorOption"), "A");
			if (c.equals("A"))
				colorOption = ColorOption.chooseAuto(axesOption);
			else
				colorOption = ColorOption.fromString(c);
			if (colorOption == null)
				throw new Valve3Exception("Illegal color option.");

			break;

		case STROKES:

			// bin size defalts to day
			bin = BinSize.fromString(Util.stringToString(comp.get("cntsBin"), "day"));
			if (bin == null)
				throw new Valve3Exception("Illegal bin size option.");

			if ((endTime - startTime) / bin.toSeconds() > 10000)
				throw new Valve3Exception("Bin size too small.");

			// right axis default to cumulative counts
			rightAxis = RightAxis.fromString(Util.stringToString(comp.get("cntsAxis"), "S"));
			if (rightAxis == null)
				throw new Valve3Exception("Illegal counts axis option. (" + comp.get("cntsAxis") + ")");

			break;
		}
		if (comp.get("outputAll") != null)
			exportAll = comp.getBoolean("outputAll");
		else
			exportAll = false;
	}

	/**
	 * Gets hypocenter list binary data from VDX
	 * 
	 * @param component
	 *            PlotComponent
	 * @throws Valve3Exception
	 */
	protected void getData(PlotComponent comp) throws Valve3Exception {

		// initialize variables
		boolean exceptionThrown = false;
		String exceptionMsg = "";
		Pool<VDXClient> pool = null;
		VDXClient client = null;

		double twest = range.getWest();
		double teast = range.getEast();
		double tsouth = range.getSouth();
		double tnorth = range.getNorth();

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
		params.put("outputAll", Boolean.toString(exportAll));

		// checkout a connection to the database
		pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		if (pool != null) {
			client = pool.checkout();

			// get the data, if nothing is returned then create an empty list
			try {
				strokes = (StrokeList) client.getBinaryData(params);
			} catch (UtilException e) {
				exceptionThrown = true;
				exceptionMsg = e.getMessage();
			} catch (Exception e) {
				strokes = null;
			}

			// we return an empty list if there is no data, because it is valid
			// to have no hypocenters for a time period
			if (strokes != null) {
				strokes.adjustTime(timeOffset);
			} else {
				strokes = new StrokeList();
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
	 * @param plot
	 *            Valve3Plot
	 * @param component
	 *            PlotComponent
	 * @throws Valve3Exception
	 */
	private BasicFrameRenderer plotMapView(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception {

		// TODO: make projection variable
		TransverseMercator proj = new TransverseMercator();
		Point2D.Double origin = range.getCenter();
		proj.setup(origin, 0, 0);

		strokes.project(proj);

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
		trans[4] = startTime + timeOffset;
		trans[5] = endTime + timeOffset;
		trans[6] = origin.x;
		trans[7] = origin.y;
		comp.setTranslation(trans);
		comp.setTranslationType("map");
		return mr;
	}

	/**
	 * Initialize BasicFrameRenderer (init mode depends from axes type) and add
	 * it to plot. Generate PNG image to local file.
	 * 
	 * @param v3Plot
	 *            Valve3Plot
	 * @param component
	 *            PlotComponent
	 * @param rank
	 *            Rank
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
		List<Stroke> mystrokes;
		BasicFrameRenderer base = new InvertedFrameRenderer();
		base.setLocation(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(), comp.getBoxHeight() - 16);
		v3p.getPlot().setSize(v3p.getPlot().getWidth(), v3p.getPlot().getHeight() + 115);

		switch (axesOption) {

		case MAP_VIEW:
			base = plotMapView(v3p, comp);
			base.createEmptyAxis();
			if (xUnits)
				base.getAxis().setBottomLabelAsText("Longitude");
			if (yUnits)
				base.getAxis().setLeftLabelAsText("Latitude");
			((MapRenderer) base).createScaleRenderer();
			break;

		}

		// set the label at the top of the plot.
		if (xLabel) {
			base.getAxis().setTopLabelAsText(subCount + getTopLabel(rank));
		}

		// add this plot to the valve plot
		v3p.getPlot().addRenderer(base);

		// Create density overlay if desired
		// create the scale renderer
		LightningRenderer hr = new LightningRenderer(strokes, base, axesOption);
		hr.setColorOption(colorOption);
		if (colorOption == ColorOption.TIME)
			hr.setColorTime(startTime + timeOffset, endTime + timeOffset);
		if (xLabel) {
			hr.createColorScaleRenderer(base.getGraphX() + base.getGraphWidth() / 2 + 150,
					base.getGraphY() + base.getGraphHeight() + 150);
			hr.createStrokeScaleRenderer(base.getGraphX() + base.getGraphWidth() / 2 - 150,
					base.getGraphY() + base.getGraphHeight() + 150);
		}
		v3p.getPlot().addRenderer(hr);
		v3p.addComponent(comp);
	}

	/**
	 * If v3Plot is null, prepare data for exporting Otherwise, initialize
	 * HistogramRenderer and add it to plot. Generate PNG image to local file.
	 * 
	 * @param v3Plot
	 *            Valve3Plot
	 * @param component
	 *            PlotComponent
	 * @param rank
	 *            Rank
	 * @throws Valve3Exception
	 */
	private void plotCounts(Valve3Plot v3p, PlotComponent comp, Rank rank) throws Valve3Exception {

		int leftLabels = 0;
		HistogramExporter hr = new HistogramExporter(strokes.getCountsHistogram(bin));
		hr.setLocation(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(), comp.getBoxHeight() - 16);
		hr.setDefaultExtents();
		hr.setMinX(startTime + timeOffset);
		hr.setMaxX(endTime + timeOffset);
		hr.createDefaultAxis(8, 8, xTickMarks, yTickMarks, false, true, xTickValues, yTickValues);
		hr.setXAxisToTime(8, xTickMarks, xTickValues);
		if (yUnits)
			hr.getAxis().setLeftLabelAsText("Strokes per " + bin);
		if (xUnits)
			hr.getAxis().setBottomLabelAsText(
					timeZoneID + " Time (" + Util.j2KToDateString(startTime + timeOffset, dateFormatString) + " to "
							+ Util.j2KToDateString(endTime + timeOffset, dateFormatString) + ")");
		if (xLabel)
			hr.getAxis().setTopLabelAsText(getTopLabel(rank));
		if (hr.getAxis().getLeftLabels() != null) {
			leftLabels = hr.getAxis().getLeftLabels().length;
		}
		if (forExport) {
			// Add column headers to csvHdrs (second one incomplete)
			String[] hdr = { null, null, null, String.format("%s_StrokesPer%s", rank.getName(), bin) };
			csvHdrs.add(hdr);
			csvData.add(new ExportData(csvIndex, hr));
			csvIndex++;
		}
		DoubleMatrix2D data = null;
		String headerName = "";
		switch (rightAxis) {
		case CUM_STROKES:
			data = strokes.getCumulativeCounts();
			if (forExport)
				// Add specialized part of column header to csvText
				headerName = "CumulativeStrokes";
			break;
		}
		if (data != null && data.rows() > 0) {
			double cmin = data.get(0, 1);
			double cmax = data.get(data.rows() - 1, 1);

			// TODO: utilize ranks for counts plots
			MatrixExporter mr = new MatrixExporter(data, false, null);
			mr.setAllVisible(true);
			mr.setLocation(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(), comp.getBoxHeight() - 16);
			mr.setExtents(startTime + timeOffset, endTime + timeOffset, cmin, cmax * 1.05);
			mr.createDefaultLineRenderers(comp.getColor());

			if (forExport) {
				// Add column to header; add Exporter to set for CSV
				String[] hdr = { null, rank.getName(), null, headerName };
				csvHdrs.add(hdr);
				csvData.add(new ExportData(csvIndex, mr));
				csvIndex++;
			} else {
				Renderer[] r = mr.getLineRenderers();
				((ShapeRenderer) r[0]).color = Color.red;
				((ShapeRenderer) r[0]).stroke = new BasicStroke(2.0f);
				AxisRenderer ar = new AxisRenderer(mr);
				if (yTickValues)
					ar.createRightTickLabels(SmartTick.autoTick(cmin, cmax, leftLabels, false), null);
				if (yUnits)
					hr.getAxis().setRightLabelAsText(rightAxis.toString());
				mr.setAxis(ar);
				hr.addRenderer(mr);
			}
		}
		if (isDrawLegend)
			hr.createDefaultLegendRenderer(new String[] { rank.getName() + " Strokes" });

		if (!forExport) {
			comp.setTranslation(hr.getDefaultTranslation(v3p.getPlot().getHeight()));
			comp.setTranslationType("ty");
			addMetaData(vdxSource, vdxClient, v3p, comp);
			v3p.getPlot().addRenderer(hr);
			v3p.addComponent(comp);
		}
	}

	/**
	 * Compute rank, calls appropriate function to init renderers
	 * 
	 * @param v3Plot
	 *            Valve3Plot
	 * @param component
	 *            PlotComponent
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3p, PlotComponent comp, Rank rank) throws Valve3Exception {

		switch (plotType) {
		case MAP:
			if (forExport) {
				// Add column headers to csvHdrs
				String rankName = rank.getName();
				String[] hdr1 = { null, rankName, null, "Lat" };
				csvHdrs.add(hdr1);
				String[] hdr2 = { null, rankName, null, "Lon" };
				csvHdrs.add(hdr2);
				String[] hdr3 = { null, rankName, null, "Stations Detected" };
				csvHdrs.add(hdr3);
				String[] hdr4 = { null, rankName, null, "Residual" };
				csvHdrs.add(hdr4);
				// Initialize data for export; add to set for CSV
				ExportData ed = new ExportData(csvIndex, new LightningExporter(strokes, true));
				csvData.add(ed);
				csvIndex++;
			} else {
				plotMap(v3p, comp, rank);
				v3p.setCombineable(false);
				addMetaData(vdxSource, vdxClient, v3p, comp);
				v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Map");
			}
			break;

		case STROKES:
			plotCounts(v3p, comp, rank);
			if (!forExport) {
				v3p.setCombineable(true);
				addMetaData(vdxSource, vdxClient, v3p, comp);
				v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Counts");
			}
			break;
		}
	}

	/**
	 * Concrete realization of abstract method. Generate PNG image (hypocenters
	 * map or histogram, depends on plot type) to file with random name. If v3p
	 * is null, prepare data for export -- assumes csvData, csvData & csvIndex
	 * initialized
	 * 
	 * @param v3p
	 *            Valve3Plot
	 * @param comp
	 *            PlotComponent
	 * @throws Valve3Exception
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception, PlotException {

		forExport = (v3p == null);
		ranksMap = getRanks(vdxSource, vdxClient);
		comp.setPlotter(this.getClass().getName());
		getInputs(comp);

		// get the rank object for this request
		Rank rank = new Rank();
		if (rk == 0) {
			rank = rank.bestAvailable();
		} else {
			rank = ranksMap.get(rk);
		}

		// plot configuration
		if (!forExport) {
			v3p.setExportable(true);
		}

		/*
		 * if (!forExport) { if (rk == 0) { v3p.setExportable(false); } else {
		 * v3p.setExportable(true); }
		 * 
		 * // export configuration } else { if (rk == 0) { throw new
		 * Valve3Exception(
		 * "Data Export Not Available for Best Available Rank"); } }
		 */

		// this is a legitimate request so lookup the data from the database and
		// plot it
		getData(comp);
		plotData(v3p, comp, rank);

		if (!forExport)
			writeFile(v3p);
	}

	/**
	 * 
	 * @return plot top label text
	 */
	private String getTopLabel(Rank rank) {

		StringBuilder top = new StringBuilder(100);
		top.append(strokes.size() + " " + rank.getName());

		// data coming from the stroke list have already been adjusted for
		// the time offset
		if (strokes.size() == 1) {
			top.append(" stroke on ");
			top.append(Util.j2KToDateString(strokes.getStrokes().get(0).j2ksec, dateFormatString));
		} else {
			top.append(" strokes between ");
			if (strokes.size() == 0) {
				top.append(Util.j2KToDateString(startTime + timeOffset, dateFormatString));
				top.append(" and ");
				top.append(Util.j2KToDateString(endTime + timeOffset, dateFormatString));
			} else if (strokes.size() > 1) {
				top.append(Util.j2KToDateString(strokes.getStrokes().get(0).j2ksec, dateFormatString));
				top.append(" and ");
				top.append(Util.j2KToDateString(strokes.getStrokes().get(strokes.size() - 1).j2ksec, dateFormatString));
			}
		}
		top.append(" " + timeZoneID + " Time");
		return top.toString();
	}

	/**
	 * 
	 * @return "column i contains single-character strings"
	 */
	boolean isCharColumn(int i) {
		return (i > 13);
	}

}
