package gov.usgs.volcanoes.valve3.plotter;

import cern.colt.matrix.DoubleMatrix2D;

import gov.usgs.volcanoes.core.legacy.plot.PlotException;
import gov.usgs.volcanoes.core.legacy.plot.decorate.SmartTick;
import gov.usgs.volcanoes.core.legacy.plot.map.GeoImageSet;
import gov.usgs.volcanoes.core.legacy.plot.map.GeoLabelSet;
import gov.usgs.volcanoes.core.legacy.plot.map.MapRenderer;
import gov.usgs.volcanoes.core.legacy.plot.render.ArbDepthFrameRenderer;
import gov.usgs.volcanoes.core.legacy.plot.render.AxisRenderer;
import gov.usgs.volcanoes.core.legacy.plot.render.BasicFrameRenderer;
import gov.usgs.volcanoes.core.legacy.plot.render.Histogram2DRenderer;
import gov.usgs.volcanoes.core.legacy.plot.render.InvertedFrameRenderer;
import gov.usgs.volcanoes.core.legacy.plot.render.Renderer;
import gov.usgs.volcanoes.core.legacy.plot.render.ShapeRenderer;
import gov.usgs.volcanoes.core.legacy.plot.transform.ArbDepthCalculator;
import gov.usgs.volcanoes.core.legacy.util.Pool;
import gov.usgs.volcanoes.core.math.proj.GeoRange;
import gov.usgs.volcanoes.core.math.proj.TransverseMercator;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.core.util.StringUtils;
import gov.usgs.volcanoes.core.util.UtilException;
import gov.usgs.volcanoes.valve3.PlotComponent;
import gov.usgs.volcanoes.valve3.Plotter;
import gov.usgs.volcanoes.valve3.Valve3;
import gov.usgs.volcanoes.valve3.Valve3Exception;
import gov.usgs.volcanoes.valve3.result.Valve3Plot;
import gov.usgs.volcanoes.vdx.client.VDXClient;
import gov.usgs.volcanoes.vdx.data.ExportData;
import gov.usgs.volcanoes.vdx.data.HistogramExporter;
import gov.usgs.volcanoes.vdx.data.HypocenterExporter;
import gov.usgs.volcanoes.vdx.data.MatrixExporter;
import gov.usgs.volcanoes.vdx.data.Rank;
import gov.usgs.volcanoes.vdx.data.hypo.Hypocenter;
import gov.usgs.volcanoes.vdx.data.hypo.HypocenterList;
import gov.usgs.volcanoes.vdx.data.hypo.HypocenterList.BinSize;
import gov.usgs.volcanoes.vdx.data.hypo.plot.HypocenterRenderer;
import gov.usgs.volcanoes.vdx.data.hypo.plot.HypocenterRenderer.AxesOption;
import gov.usgs.volcanoes.vdx.data.hypo.plot.HypocenterRenderer.ColorOption;

import hep.aida.ref.Histogram2D;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class for making hypocenter map plots and histograms.
 * TODO: implement triple view.
 *
 * @author Dan Cervelli
 * @author Bill Tollett
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
    NONE(""), CUM_COUNTS("Cumulative Counts"), CUM_MAGNITUDE("Cumulative Magnitude"), CUM_MOMENT(
        "Cumulative Moment");

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
  private double minDepth;
  private double maxDepth;
  private double minMag;
  private double maxMag;
  private Integer minNPhases;
  private Integer maxNPhases;
  private double minRms;
  private double maxRms;
  private double minHerr;
  private double maxHerr;
  private double minVerr;
  private double maxVerr;
  private String rmk;
  private double minStDst;
  private double maxStDst;
  private double maxGap;
  private boolean density;
  private double densityBinSize;
  private boolean doLog;
  private double centerLat;
  private double centerLon;
  private double radius;

  private AxesOption axesOption;
  private ColorOption colorOption;
  private PlotType plotType;
  private BinSize bin;
  private RightAxis rightAxis;
  private HypocenterList hypos;


  /**
   * Default constructor.
   */
  public HypocenterPlotter() {
    super();
  }

  /**
   * Initialize internal data from PlotComponent component.
   *
   * @param comp PlotComponent
   */
  protected void getInputs(PlotComponent comp) throws Valve3Exception {

    rk = comp.getInt("rk");

    endTime = comp.getEndTime();
    if (Double.isNaN(endTime)) {
      throw new Valve3Exception("Illegal end time.");
    }

    startTime = comp.getStartTime(endTime);
    if (Double.isNaN(startTime)) {
      throw new Valve3Exception("Illegal start time.");
    }

    timeOffset = comp.getOffset(startTime);
    timeZoneID = comp.getTimeZone().getID();

    String pt = comp.get("plotType");
    if (pt == null) {
      plotType = PlotType.MAP;
    } else {
      plotType = PlotType.fromString(pt);
      if (plotType == null) {
        throw new Valve3Exception("Illegal plot type: " + pt);
      }
    }
    try {
      tickMarksX = comp.getBoolean("xTickMarks");
    } catch (Valve3Exception e) {
      tickMarksX = true;
    }
    try {
      tickValuesX = comp.getBoolean("xTickValues");
    } catch (Valve3Exception e) {
      tickValuesX = true;
    }
    try {
      unitsX = comp.getBoolean("xUnits");
    } catch (Valve3Exception e) {
      unitsX = true;
    }
    try {
      labelX = comp.getBoolean("xLabel");
    } catch (Valve3Exception e) {
      labelX = true;
    }
    try {
      tickMarksY = comp.getBoolean("yTickMarks");
    } catch (Valve3Exception e) {
      tickMarksY = true;
    }
    try {
      tickValuesY = comp.getBoolean("yTickValues");
    } catch (Valve3Exception e) {
      tickValuesY = true;
    }
    try {
      unitsY = comp.getBoolean("yUnits");
    } catch (Valve3Exception e) {
      unitsY = true;
    }
    try {
      labelY = comp.getBoolean("yLabel");
    } catch (Valve3Exception e) {
      labelY = false;
    }
    try {
      isDrawLegend = comp.getBoolean("lg");
    } catch (Valve3Exception e) {
      isDrawLegend = true;
    }

    double w = comp.getDouble("west");
    if (w > 360 || w < -360) {
      throw new Valve3Exception("Illegal area of interest: w=" + w);
    }
    double e = comp.getDouble("east");
    if (e > 360 || e < -360) {
      throw new Valve3Exception("Illegal area of interest: e=" + e);
    }
    double s = comp.getDouble("south");
    if (s < -90) {
      throw new Valve3Exception("Illegal area of interest: s=" + s);
    }
    double n = comp.getDouble("north");
    if (n > 90) {
      throw new Valve3Exception("Illegal area of interest: n=" + n);
    }

    this.startLoc = new Point2D.Double(w, n);
    this.endLoc = new Point2D.Double(e, s);

    if (s >= n) {
      double t = s;
      s = n;
      n = t;
    }

    range = new GeoRange(w, e, s, n);

    hypowidth = StringUtils.stringToDouble(comp.get("hypowidth"), DEFAULT_WIDTH);

    minMag = StringUtils.stringToDouble(comp.get("minMag"), -Double.MAX_VALUE);
    maxMag = StringUtils.stringToDouble(comp.get("maxMag"), Double.MAX_VALUE);

    if (minMag > maxMag) {
      throw new Valve3Exception("Illegal magnitude filter.");
    }

    minDepth = StringUtils.stringToDouble(comp.get("minDepth"), -Double.MAX_VALUE);
    maxDepth = StringUtils.stringToDouble(comp.get("maxDepth"), Double.MAX_VALUE);
    if (minDepth > maxDepth) {
      throw new Valve3Exception("Illegal depth filter.");
    }

    minNPhases = StringUtils.stringToInt(comp.get("minNPhases"), 4);
    maxNPhases = StringUtils.stringToInt(comp.get("maxNPhases"), 500);
    if (minNPhases > maxNPhases) {
      throw new Valve3Exception("Illegal nphases filter.");
    }

    minRms = StringUtils.stringToDouble(comp.get("minRMS"), -Double.MAX_VALUE);
    maxRms = StringUtils.stringToDouble(comp.get("maxRMS"), 2.0);
    if (minRms > maxRms) {
      throw new Valve3Exception("Illegal RMS filter.");
    }

    minHerr = StringUtils.stringToDouble(comp.get("minHerr"), -Double.MAX_VALUE);
    maxHerr = StringUtils.stringToDouble(comp.get("maxHerr"), Double.MAX_VALUE);
    if (minHerr > maxHerr) {
      throw new Valve3Exception("Illegal horizontal error filter.");
    }

    minVerr = StringUtils.stringToDouble(comp.get("minVerr"), -Double.MAX_VALUE);
    maxVerr = StringUtils.stringToDouble(comp.get("maxVerr"), Double.MAX_VALUE);
    if (minVerr > maxVerr) {
      throw new Valve3Exception("Illegal vertical error filter.");
    }

    rmk = StringUtils.stringToString(comp.get("rmk"), "");

    minStDst = StringUtils.stringToDouble(comp.get("minStDst"), 0.0);
    maxStDst = StringUtils.stringToDouble(comp.get("maxStDst"), 1000.0);
    maxGap = StringUtils.stringToDouble(comp.get("maxGap"), 360.0);
    centerLat = StringUtils.stringToDouble(comp.get("centerLat"), 0.0);
    centerLon = StringUtils.stringToDouble(comp.get("centerLon"), 0.0);
    radius = StringUtils.stringToDouble(comp.get("radius"), 0.0);

    switch (plotType) {

      case MAP:

        // axes defaults to Map View
        axesOption = AxesOption.fromString(StringUtils.stringToString(comp.get("axesOption"), "M"));
        if (axesOption == null) {
          throw new Valve3Exception("Illegal axes type.");
        }

        // color defaults to Auto
        String c = StringUtils.stringToString(comp.get("colorOption"), "A");
        if (c.equals("A")) {
          colorOption = ColorOption.chooseAuto(axesOption);
        } else {
          colorOption = ColorOption.fromString(c);
        }
        if (colorOption == null) {
          throw new Valve3Exception("Illegal color option.");
        }

        // Density?
        try {
          density = comp.getBoolean("density");
        } catch (Valve3Exception de) {
          density = false;
          doLog = false;
        }
        if (density) {
          if (axesOption.equals(AxesOption.ARB_DEPTH) || axesOption.equals(AxesOption.ARB_TIME)
              || axesOption.equals(AxesOption.TRIPLE_VIEW)) {
            throw new Valve3Exception("Density Maps are not available for Arb-Depth/Time plots.");
          }

          try {
            doLog = comp.getBoolean("doLog");
          } catch (Valve3Exception dle) {
            doLog = false;
          }
          densityBinSize = StringUtils.stringToDouble(comp.get("densityBinSize"), 5.0);
        }

        break;

      case COUNTS:

        // bin size defalts to day
        bin = BinSize.fromString(StringUtils.stringToString(comp.get("cntsBin"), "day"));
        if (bin == null) {
          throw new Valve3Exception("Illegal bin size option.");
        }

        if ((endTime - startTime) / bin.toSeconds() > 10000) {
          throw new Valve3Exception("Bin size too small.");
        }

        // right axis default to cumulative counts
        rightAxis = RightAxis.fromString(StringUtils.stringToString(comp.get("cntsAxis"), "C"));
        if (rightAxis == null) {
          throw new Valve3Exception("Illegal counts axis option.");
        }

        break;

      default:
        break;
    }
    if (comp.get("outputAll") != null) {
      exportAll = comp.getBoolean("outputAll");
    } else {
      exportAll = false;
    }
  }

  /**
   * Gets hypocenter list binary data from VDX.
   *
   * @param comp PlotComponent
   */
  protected void getData(PlotComponent comp) throws Valve3Exception {

    // initialize variables
    boolean exceptionThrown = false;
    String exceptionMsg = "";
    VDXClient client = null;

    double twest = range.getWest();
    double teast = range.getEast();
    double tsouth = range.getSouth();
    double tnorth = range.getNorth();

    // we need to get extra hypocenters for plotting the width
    if (axesOption == AxesOption.ARB_DEPTH || axesOption == AxesOption.ARB_TIME) {
      double latDiff = ArbDepthCalculator.getLatDiff(hypowidth);
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
    params.put("minRMS", Double.toString(minRms));
    params.put("maxRMS", Double.toString(maxRms));
    params.put("minHerr", Double.toString(minHerr));
    params.put("maxHerr", Double.toString(maxHerr));
    params.put("minVerr", Double.toString(minVerr));
    params.put("maxVerr", Double.toString(maxVerr));
    params.put("rmk", (rmk));
    params.put("minStDst", Double.toString(minStDst));
    params.put("maxStDst", Double.toString(maxStDst));
    params.put("maxGap", Double.toString(maxGap));
    params.put("outputAll", Boolean.toString(exportAll));
    params.put("centerLat", Double.toString(centerLat));
    params.put("centerLon", Double.toString(centerLon));
    params.put("radius", Double.toString(radius));

    // checkout a connection to the database
    Pool<VDXClient> pool = null;
    pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
    if (pool != null) {
      client = pool.checkout();

      // get the data, if nothing is returned then create an empty list
      try {
        hypos = (HypocenterList) client.getBinaryData(params);
      } catch (UtilException e) {
        exceptionThrown = true;
        exceptionMsg = e.getMessage();
      } catch (Exception e) {
        hypos = null;
      }

      // we return an empty list if there is no data, because it is valid to have no hypocenters
      // for a time period
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
   * Initialize MapRenderer and add it to given plot.
   *
   * @param v3p Valve3Plot
   * @param comp PlotComponent
   */
  private BasicFrameRenderer plotMapView(Valve3Plot v3p, PlotComponent comp)
      throws Valve3Exception {

    // TODO: make projection variable
    TransverseMercator proj = new TransverseMercator();
    Point2D.Double origin = range.getCenter();
    proj.setup(origin, 0, 0);

    hypos.project(proj);

    MapRenderer mr = new MapRenderer(range, proj);
    mr.setLocationByMaxBounds(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(),
        comp.getBoxMapHeight());
    if (isDrawLegend) {
      v3p.getPlot().setSize(v3p.getPlot().getWidth(), mr.getGraphHeight() + 190);
    } else {
      v3p.getPlot().setSize(v3p.getPlot().getWidth(), mr.getGraphHeight() + 60);
    }

    GeoLabelSet labels = Valve3.getInstance().getGeoLabelSet();
    labels = labels.getSubset(range);
    mr.setGeoLabelSet(labels);

    GeoImageSet images = Valve3.getInstance().getGeoImageSet();
    RenderedImage ri = images.getMapBackground(proj, range, comp.getBoxWidth());

    mr.setMapImage(ri);
    mr.createBox(8);
    mr.createGraticule(8, tickMarksX, tickMarksY, tickValuesX, tickValuesY, Color.BLACK);

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
   * Initialize BasicFrameRenderer (init mode depends from axes type) and add it to plot. Generate
   * PNG image to local file.
   *
   * @param v3p Valve3Plot
   * @param comp PlotComponent
   * @param rank Rank
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
    BasicFrameRenderer base = new InvertedFrameRenderer();
    base.setLocation(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(), comp.getBoxHeight() - 16);
    if (isDrawLegend) {
      v3p.getPlot().setSize(v3p.getPlot().getWidth(), v3p.getPlot().getHeight() + 115);
    } else {
      v3p.getPlot().setSize(v3p.getPlot().getWidth(), v3p.getPlot().getHeight());
    }

    switch (axesOption) {

      case MAP_VIEW:
        base = plotMapView(v3p, comp);
        base.createEmptyAxis();
        if (unitsX) {
          base.getAxis().setBottomLabelAsText("Longitude");
        }
        if (unitsY) {
          base.getAxis().setLeftLabelAsText("Latitude");
        }
        ((MapRenderer) base).createScaleRenderer();
        break;

      case LON_DEPTH:
        base.setExtents(range.getWest(), range.getEast(), hypos.getMinDepth(minDepth),
            hypos.getMaxDepth(maxDepth));
        base.createDefaultAxis();
        if (unitsX) {
          base.getAxis().setBottomLabelAsText("Longitude");
        }
        if (unitsY) {
          base.getAxis().setLeftLabelAsText("Depth (km)");
        }
        comp.setTranslation(base.getDefaultTranslation(v3p.getPlot().getHeight()));
        comp.setTranslationType("xy");
        break;

      case LAT_DEPTH:
        base.setExtents(range.getSouth(), range.getNorth(), hypos.getMinDepth(minDepth),
            hypos.getMaxDepth(maxDepth));
        base.createDefaultAxis();
        if (unitsX) {
          base.getAxis().setBottomLabelAsText("Latitude");
        }
        if (unitsY) {
          base.getAxis().setLeftLabelAsText("Depth (km)");
        }
        comp.setTranslation(base.getDefaultTranslation(v3p.getPlot().getHeight()));
        comp.setTranslationType("xy");
        break;

      case TIME_DEPTH:
        base.setExtents(startTime + timeOffset, endTime + timeOffset, hypos.getMinDepth(minDepth),
            hypos.getMaxDepth(maxDepth));
        base.createDefaultAxis();
        base.setXAxisToTime(8);
        if (unitsX) {
          base.getAxis().setBottomLabelAsText(timeZoneID + " Time ("
              + J2kSec.toDateString(startTime + timeOffset) + " to "
              + J2kSec.toDateString(endTime + timeOffset) + ")");
        }
        if (unitsY) {
          base.getAxis().setLeftLabelAsText("Depth (km)");
        }
        comp.setTranslation(base.getDefaultTranslation(v3p.getPlot().getHeight()));
        comp.setTranslationType("ty");
        break;

      case ARB_DEPTH:

        // need to set the extents for along the line. km offset?
        base = new ArbDepthFrameRenderer();
        base.setLocation(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(),
            comp.getBoxHeight() - 16);

        lat1 = startLoc.getY();
        lon1 = startLoc.getX();
        lat2 = endLoc.getY();
        lon2 = endLoc.getX();

        adc = new ArbDepthCalculator(lat1, lon1, lat2, lon2, hypowidth);

        ((ArbDepthFrameRenderer) base).setArbDepthCalc(adc);

        base.setExtents(0.0, adc.getMaxDist(), hypos.getMinDepth(minDepth),
            hypos.getMaxDepth(maxDepth));
        base.createDefaultAxis();

        if (unitsX) {
          base.getAxis().setBottomLabelAsText(
              "Distance (km) from (" + lat1 + "," + lon1 + ") to (" + lat2 + "," + lon2
                  + ") - width = " + hypowidth + " km");
        }
        if (unitsY) {
          base.getAxis().setLeftLabelAsText("Depth (km)");
        }

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
        subCount = String.format("%d of ", count);

        break;

      case ARB_TIME:

        // need to set the extents for along the line. km offset?
        base = new ArbDepthFrameRenderer();
        base.setLocation(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(), comp.getBoxWidth());
        if (isDrawLegend) {
          v3p.getPlot().setSize(v3p.getPlot().getWidth(), base.getGraphHeight() + 190);
        } else {
          v3p.getPlot().setSize(v3p.getPlot().getWidth(), base.getGraphHeight() + 60);
        }

        lat1 = startLoc.getY();
        lon1 = startLoc.getX();
        lat2 = endLoc.getY();
        lon2 = endLoc.getX();

        adc = new ArbDepthCalculator(lat1, lon1, lat2, lon2, hypowidth);

        ((ArbDepthFrameRenderer) base).setArbDepthCalc(adc);

        base.setExtents(startTime + timeOffset, endTime + timeOffset, 0.0, adc.getMaxDist());
        base.createDefaultAxis();
        base.setXAxisToTime(8);

        if (unitsX) {
          base.getAxis().setBottomLabelAsText(timeZoneID + " Time ("
              + J2kSec.toDateString(startTime + timeOffset) + " to "
              + J2kSec.toDateString(endTime + timeOffset) + ")");
        }
        if (unitsY) {
          base.getAxis().setLeftLabelAsText(
              "Distance (km) from (" + lat1 + "," + lon1 + ") to (" + lat2 + "," + lon2
                  + ") - width = " + hypowidth + " km");
        }

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
        subCount = String.format("%d of ", count);

        break;
      default:
        break;
    }

    // set the label at the top of the plot.
    if (labelX) {
      base.getAxis().setTopLabelAsText(subCount + getTopLabel(rank));
    }

    // add this plot to the valve plot
    v3p.getPlot().addRenderer(base);

    // Create density overlay if desired
    if (density) {
      // create the density renderer
      double val1 = 0;
      double val2 = 0;

      Histogram2D hist = new Histogram2D("",
          (int) Math.round(base.getGraphWidth() / densityBinSize),
          base.getMinX(), base.getMaxX(), (int) Math.round(base.getGraphHeight() / densityBinSize),
          base.getMinY(), base.getMaxY());

      for (Hypocenter hyp : hypos.getHypocenters()) {
        switch (axesOption) {
          case MAP_VIEW:
            val1 = hyp.lon;
            val2 = hyp.lat;
            break;
          case LAT_DEPTH:
            val1 = hyp.lat;
            val2 = hyp.depth;
            break;
          case LON_DEPTH:
            val1 = hyp.lon;
            val2 = hyp.depth;
            break;
          case TIME_DEPTH:
            val1 = hyp.j2ksec;
            val2 = hyp.depth;
            break;
          default:
            break;
        }

        hist.fill(val1, val2);
      }

      Histogram2DRenderer hir = new Histogram2DRenderer(hist);
      hir.setLocation(base);
      hir.setLog(doLog);
      hir.setExtents(base.getMinX(), base.getMaxX(), base.getMinY(), base.getMaxY());
      hir.addRenderer(hir.getScaleRenderer(v3p.getPlot().getWidth() / 2 - 200,
          base.getGraphY() + base.getGraphHeight() + 70));

      if (axesOption.equals(AxesOption.LAT_DEPTH) || axesOption.equals(AxesOption.LON_DEPTH)
          || axesOption.equals(AxesOption.TIME_DEPTH)) {
        hir.setInverted(true);
      }

      v3p.getPlot().addRenderer(hir);
    } else {
      // create the scale renderer
      HypocenterRenderer hr = new HypocenterRenderer(hypos, base, axesOption);
      hr.setColorOption(colorOption);
      if (colorOption == ColorOption.TIME) {
        hr.setColorTime(startTime + timeOffset, endTime + timeOffset);
      }
      if (isDrawLegend) {
        hr.createColorScaleRenderer(base.getGraphX() + base.getGraphWidth() / 2 + 150,
            base.getGraphY() + base.getGraphHeight() + 150);
        hr.createMagnitudeScaleRenderer(base.getGraphX() + base.getGraphWidth() / 2 - 150,
            base.getGraphY() + base.getGraphHeight() + 150);
      }
      v3p.getPlot().addRenderer(hr);
    }
    v3p.addComponent(comp);
  }

  /**
   * If v3Plot is null, prepare data for exporting Otherwise, initialize HistogramRenderer and add
   * it to plot. Generate PNG image to local file.
   *
   * @param v3p Valve3Plot
   * @param comp PlotComponent
   * @param rank Rank
   */
  private void plotCounts(Valve3Plot v3p, PlotComponent comp, Rank rank) throws Valve3Exception {

    int leftLabels = 0;
    HistogramExporter hr = new HistogramExporter(hypos.getCountsHistogram(bin));
    hr.setLocation(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(), comp.getBoxHeight() - 16);
    hr.setDefaultExtents();
    hr.setMinX(startTime + timeOffset);
    hr.setMaxX(endTime + timeOffset);
    hr.createDefaultAxis(8, 8, tickMarksX, tickMarksY, false, true, tickValuesX, tickValuesY);
    hr.setXAxisToTime(8, tickMarksX, tickValuesX);
    if (unitsY) {
      hr.getAxis().setLeftLabelAsText("Earthquakes per " + bin);
    }
    if (unitsX) {
      hr.getAxis().setBottomLabelAsText(
          timeZoneID + " Time (" + J2kSec.toDateString(startTime + timeOffset)
              + " to " + J2kSec.toDateString(endTime + timeOffset) + ")");
    }
    if (labelX) {
      hr.getAxis().setTopLabelAsText(getTopLabel(rank));
    }
    if (hr.getAxis().getLeftLabels() != null) {
      leftLabels = hr.getAxis().getLeftLabels().length;
    }
    if (forExport) {
      // Add column headers to csvHdrs (second one incomplete)
      String[] hdr = {null, null, null, String.format("%s_EventsPer%s", rank.getName(), bin)};
      csvHdrs.add(hdr);
      csvData.add(new ExportData(csvIndex, hr));
      csvIndex++;
    }
    DoubleMatrix2D data = null;
    String headerName = "";
    switch (rightAxis) {
      case CUM_COUNTS:
        data = hypos.getCumulativeCounts();
        if (forExport) {
          // Add specialized part of column header to csvText
          headerName = "CumulativeCounts";
        }
        break;
      case CUM_MAGNITUDE:
        data = hypos.getCumulativeMagnitude();
        if (forExport) {
          // Add specialized part of column header to csvText
          headerName = "CumulativeMagnitude";
        }
        break;
      case CUM_MOMENT:
        data = hypos.getCumulativeMoment();
        if (forExport) {
          // Add specialized part of column header to csvText
          headerName = "CumulativeMoment";
        }
        break;
      default:
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
        String[] hdr = {null, rank.getName(), null, headerName};
        csvHdrs.add(hdr);
        csvData.add(new ExportData(csvIndex, mr));
        csvIndex++;
      } else {
        Renderer[] r = mr.getLineRenderers();
        ((ShapeRenderer) r[0]).color = Color.red;
        ((ShapeRenderer) r[0]).stroke = new BasicStroke(2.0f);
        AxisRenderer ar = new AxisRenderer(mr);
        if (tickValuesY) {
          ar.createRightTickLabels(SmartTick.autoTick(cmin, cmax, leftLabels, false), null);
        }
        if (unitsY) {
          hr.getAxis().setRightLabelAsText(rightAxis.toString());
        }
        mr.setAxis(ar);
        hr.addRenderer(mr);
      }
    }
    if (isDrawLegend) {
      hr.createDefaultLegendRenderer(new String[]{rank.getName() + " Events"});
    }

    if (!forExport) {
      comp.setTranslation(hr.getDefaultTranslation(v3p.getPlot().getHeight()));
      comp.setTranslationType("ty");
      addMetaData(vdxSource, vdxClient, v3p, comp);
      v3p.getPlot().addRenderer(hr);
      v3p.addComponent(comp);
    }
  }

  /**
   * Compute rank, calls appropriate function to init renderers.
   *
   * @param v3p Valve3Plot
   * @param comp PlotComponent
   */
  public void plotData(Valve3Plot v3p, PlotComponent comp, Rank rank) throws Valve3Exception {

    switch (plotType) {
      case MAP:
        if (forExport) {
          // Add column headers to csvHdrs
          String rankName = rank.getName();
          String[] hdr1 = {null, rankName, null, "Lat"};
          csvHdrs.add(hdr1);
          String[] hdr2 = {null, rankName, null, "Lon"};
          csvHdrs.add(hdr2);
          String[] hdr3 = {null, rankName, null, "Depth"};
          csvHdrs.add(hdr3);
          String[] hdr4 = {null, rankName, null, "PrefMag"};
          csvHdrs.add(hdr4);
          if (exportAll) {
            String[] hdr5 = {null, rankName, null, "AmpMag"};
            csvHdrs.add(hdr5);
            String[] hdr6 = {null, rankName, null, "CodaMag"};
            csvHdrs.add(hdr6);
            String[] hdr7 = {null, rankName, null, "NPhases"};
            csvHdrs.add(hdr7);
            String[] hdr8 = {null, rankName, null, "AzGap"};
            csvHdrs.add(hdr8);
            String[] hdr9 = {null, rankName, null, "DMin"};
            csvHdrs.add(hdr9);
            String[] hdr10 = {null, rankName, null, "RMS"};
            csvHdrs.add(hdr10);
            String[] hdr11 = {null, rankName, null, "NSTimes"};
            csvHdrs.add(hdr11);
            String[] hdr12 = {null, rankName, null, "HErr"};
            csvHdrs.add(hdr12);
            String[] hdr13 = {null, rankName, null, "VErr"};
            csvHdrs.add(hdr13);
            String[] hdr14 = {null, rankName, null, "MagType"};
            csvHdrs.add(hdr14);
            String[] hdr15 = {null, rankName, null, "RMK"};
            csvHdrs.add(hdr15);
          }
          // Initialize data for export; add to set for CSV
          ExportData ed = new ExportData(csvIndex, new HypocenterExporter(hypos, exportAll));
          csvData.add(ed);
          csvIndex++;
        } else {
          plotMap(v3p, comp, rank);
          v3p.setCombineable(false);
          addMetaData(vdxSource, vdxClient, v3p, comp);
          v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Map");
        }
        break;

      case COUNTS:
        plotCounts(v3p, comp, rank);
        if (!forExport) {
          v3p.setCombineable(true);
          addMetaData(vdxSource, vdxClient, v3p, comp);
          v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Counts");
        }
        break;
      default:
        break;
    }
  }

  /**
   * Concrete realization of abstract method. Generate PNG image (hypocenters map or histogram,
   * depends on plot type) to file with random name. If v3p is null, prepare data for export --
   * assumes csvData, csvData & csvIndex initialized
   *
   * @param v3p Valve3Plot
   * @param comp PlotComponent
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

    // this is a legitimate request so lookup the data from the database and plot it
    getData(comp);
    plotData(v3p, comp, rank);

    if (!forExport) {
      writeFile(v3p);
    }
  }

  /**
   * Generate top label.
   *
   * @return plot top label text
   */
  private String getTopLabel(Rank rank) {

    StringBuilder top = new StringBuilder(100);
    top.append(hypos.size()).append(" ").append(rank.getName());

    // data coming from the hypocenters list have already been adjusted for the time offset
    if (hypos.size() == 1) {
      top.append(" earthquake on ");
      top.append(J2kSec.toDateString(hypos.getHypocenters().get(0).j2ksec));
    } else {
      top.append(" earthquakes between ");
      top.append(J2kSec.toDateString(startTime + timeOffset));
      top.append(" and ");
      top.append(J2kSec.toDateString(endTime + timeOffset));
    }
    top.append(" " + timeZoneID + " Time");
    if (density) {
      top.insert(0, "Density Plot (");
      top.append(")");
    }
    return top.toString();
  }

  /**
   * Is this a char column.
   *
   * @return "column i contains single-character strings"
   */
  boolean isCharColumn(int i) {
    return (i > 13);
  }

}
