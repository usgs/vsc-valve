package gov.usgs.volcanoes.valve3.plotter;

import gov.usgs.volcanoes.core.math.DownsamplingType;
import gov.usgs.volcanoes.core.legacy.plot.PlotException;
import gov.usgs.volcanoes.core.data.GenericDataMatrix;
import gov.usgs.volcanoes.core.legacy.plot.decorate.DefaultFrameDecorator;
import gov.usgs.volcanoes.core.legacy.plot.decorate.DefaultFrameDecorator.Location;
import gov.usgs.volcanoes.core.legacy.plot.decorate.SmartTick;
import gov.usgs.volcanoes.core.legacy.plot.render.AxisRenderer;
import gov.usgs.volcanoes.core.legacy.plot.render.LegendRenderer;
import gov.usgs.volcanoes.core.legacy.plot.render.MatrixRenderer;
import gov.usgs.volcanoes.core.legacy.util.Pool;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.core.time.Time;
import gov.usgs.volcanoes.core.util.StringUtils;
import gov.usgs.volcanoes.valve3.PlotComponent;
import gov.usgs.volcanoes.valve3.Plotter;
import gov.usgs.volcanoes.valve3.Valve3;
import gov.usgs.volcanoes.valve3.Valve3Exception;
import gov.usgs.volcanoes.valve3.result.Valve3Plot;
import gov.usgs.volcanoes.vdx.ExportConfig;
import gov.usgs.volcanoes.vdx.client.VDXClient;
import gov.usgs.volcanoes.vdx.data.Channel;
import gov.usgs.volcanoes.vdx.data.Column;
import gov.usgs.volcanoes.vdx.data.ExportData;
import gov.usgs.volcanoes.vdx.data.MetaDatum;
import gov.usgs.volcanoes.vdx.data.Rank;
import gov.usgs.volcanoes.vdx.data.SuppDatum;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class which keeps general functionality for all plotters based on MatrixRenderer.
 *
 * @author Max Kokoulin
 * @author Bill Tollett
 */

public abstract class RawDataPlotter extends Plotter {

  protected double startTime;
  protected double endTime;
  protected String ch;
  protected boolean isDrawLegend;
  protected int rk;
  public boolean ranks = true;
  protected boolean tickMarksX = true;
  protected boolean tickValuesX = true;
  protected boolean unitsX = true;
  protected boolean labelX = false;
  protected boolean tickMarksY = true;
  protected boolean tickValuesY = true;
  protected boolean unitsY = true;
  protected boolean labelY = false;

  protected int downsamplingInterval = 0;
  protected DownsamplingType downsamplingType = DownsamplingType.NONE;

  //count of left ticks
  protected int leftTicks = 0;

  //starting line color
  protected int leftLines;
  protected String leftUnit;
  protected String rightUnit;
  protected int compCount;
  protected String shape = "l";

  protected int columnsCount;
  protected List<Column> columnsList;
  protected String[] channelLegendsCols;

  protected Map<Integer, String> axisMap;
  protected Map<Integer, Channel> channelsMap;
  protected Map<Integer, Rank> ranksMap;

  protected TreeSet<ExportData> csvData;
  protected StringBuffer csvText;
  protected Map<String, String> csvCmtBits;
  protected Vector<String[]> csvHdrs;
  protected int csvIndex = 0;

  protected boolean[] bypassCols;
  protected boolean[] accumulateCols;
  protected boolean doAccumulate;
  protected boolean doDespike;
  protected double despikePeriod;
  protected boolean doDetrend;
  protected int filterPick;
  protected double filterMin;
  protected double filterMax;
  protected double filterPeriod;
  protected int debiasPick;
  protected double debiasValue;
  protected boolean exportAll = false;
  protected boolean doArithmetic;
  protected String arithmeticType;
  protected double arithmeticValue;

  protected String outputType;
  protected boolean inclTime;
  protected SuppDatum[] sdData;
  protected String[] scnl;
  protected double samplingRate = 0.0;
  protected String dataType = null;

  protected double timeOffset;
  protected String timeZoneID;
  protected String dateFormatString;

  /**
   * Default constructor.
   */
  public RawDataPlotter() {
    csvCmtBits = new LinkedHashMap<String, String>();
    csvHdrs = new Vector<String[]>();
    dateFormatString = "yyyy-MM-dd HH:mm:ss";
  }

  /**
   * Fill those component parameters which are common for all plotters.
   *
   * @param comp plot component
   */
  protected void parseCommonParameters(PlotComponent comp) throws Valve3Exception {

    // declare variables
    String nameArg = null;

    // Check for named ranks, outputAll
    if (forExport) {
      exportAll = comp.getBoolean("outputAll");
      outputType = comp.get("o");
      inclTime = outputType.equals("csv") || outputType.equals("xml") || outputType.equals("json");

      nameArg = comp.get("rkName");
      if (nameArg != null) {
        boolean found = false;
        for (Rank r : ranksMap.values()) {
          if (nameArg.equals(r.getName())) {
            comp.put("rk", "" + r.getId());
            found = true;
            break;
          }
        }
        if (!found) {
          throw new Valve3Exception("Unknown rank name :" + nameArg);
        }
      }
    }

    nameArg = comp.get("chNames");
    if (nameArg != null) {
      String[] names = nameArg.split(",");
      int left = names.length;
      ch = null;
      for (Channel c : channelsMap.values()) {
        String cname = c.getCode();
        for (int i = 0; i < left; i++) {
          if (cname.equals(names[i])) {
            names[i] = names[left - 1];
            if (ch == null) {
              ch = "" + c.getCId();
            } else {
              ch = ch + "," + c.getCId();
            }
            left--;
            break;
          }
        }
        if (left == 0) {
          break;
        }
      }
    } else {
      ch = comp.getString("ch");
    }

    // column parameters
    boolean useColDefaults = true;
    int j = 0;
    if (columnsList != null) {
      boolean[] alreadySet = new boolean[columnsList.size()];
      for (Column c : columnsList) {
        String valC = comp.get(c.name);
        if ((forExport && exportAll) || valC != null) {
          if (forExport && exportAll) {
            c.checked = true;
          } else {
            c.checked = comp.getBoolean(c.name);
          }
          alreadySet[j] = true;
          useColDefaults = false;
        }
        j++;
      }
      j = 0;
      for (Column c : columnsList) {
        if (!alreadySet[j] && !useColDefaults) {
          c.checked = false;
        }
        j++;
      }
    }

    // end time
    endTime = comp.getEndTime();
    if (Double.isNaN(endTime)) {
      if (forExport) {
        endTime = Double.MAX_VALUE;
      } else {
        throw new Valve3Exception("Illegal end time.");
      }
    }

    // start time
    startTime = comp.getStartTime(endTime);
    if (Double.isNaN(startTime)) {
      throw new Valve3Exception("Illegal start time.");
    }

    // DST and time zone parameters
    timeOffset = comp.getOffset(startTime);
    timeZoneID = comp.getTimeZone().getID();

    try {
      downsamplingType = DownsamplingType.fromString(comp.getString("ds"));
      downsamplingInterval = comp.getInt("dsInt");
    } catch (Valve3Exception e) {
      //Do nothing, default values without downsampling
    }

    // plot related parameters
    if (!forExport) {
      try {
        isDrawLegend = comp.getBoolean("lg");
      } catch (Valve3Exception e) {
        isDrawLegend = true;
      }
      try {
        shape = comp.getString("linetype");
      } catch (Valve3Exception e) {
        shape = "l";
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
        labelX = false;
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
    }
  }

  /**
   * Used during request of data for this plotter, adds downsampling information to request's
   * parameters.
   *
   * @param params parameters to add to
   */
  protected void addDownsamplingInfo(Map<String, String> params) {
    params.put("ds", downsamplingType.toString());
    params.put("dsInt", Integer.toString(downsamplingInterval));
  }

  /**
   * Initialize list of columns for given vdx source.
   *
   * @param vdxSource vdx source name
   * @param vdxClient vdx client name
   * @return list of columns
   */
  protected static List<Column> getColumns(String vdxSource, String vdxClient)
      throws Valve3Exception {

    // initialize variables
    List<String> stringList = null;
    List<Column> columnList = null;
    Pool<VDXClient> pool = null;
    VDXClient client = null;

    // create a map of all the input parameters
    Map<String, String> params = new LinkedHashMap<String, String>();
    params.put("source", vdxSource);
    params.put("action", "columns");

    // checkout a connection to the database
    pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
    if (pool != null) {
      client = pool.checkout();
      try {
        stringList = client.getTextData(params);
      } catch (Exception e) {
        stringList = null;
      } finally {
        pool.checkin(client);
      }

      // if data was collected
      if (stringList != null) {
        columnList = Column.fromStringsToList(stringList);
      }
    }

    return columnList;
  }

  /**
   * Initialize list of channels for given vdx source.
   *
   * @param vdxSource vdx source name
   * @param vdxClient vdx client name
   * @return map of ids to channels
   */
  protected static Map<Integer, Channel> getChannels(String vdxSource, String vdxClient)
      throws Valve3Exception {

    // initialize variables
    List<String> stringList = null;
    Map<Integer, Channel> channelMap = null;
    Pool<VDXClient> pool = null;
    VDXClient client = null;

    // create a map of all the input parameters
    Map<String, String> params = new LinkedHashMap<String, String>();
    params.put("source", vdxSource);
    params.put("action", "channels");

    // checkout a connection to the database
    pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
    if (pool != null) {
      client = pool.checkout();
      try {
        stringList = client.getTextData(params);
      } catch (Exception e) {
        stringList = null;
      } finally {
        pool.checkin(client);
      }

      // if data was collected
      if (stringList != null) {
        channelMap = Channel.fromStringsToMap(stringList);
      }
    }

    return channelMap;
  }

  /**
   * Initialize list of ranks for given vdx source.
   *
   * @param vdxSource vdx source name
   * @param vdxClient vdx client name
   * @return map of ids to ranks
   */
  public static Map<Integer, Rank> getRanks(String vdxSource, String vdxClient)
      throws Valve3Exception {

    // initialize variables
    List<String> stringList = null;
    Map<Integer, Rank> rankMap = null;
    Pool<VDXClient> pool = null;
    VDXClient client = null;

    // create a map of all the input parameters
    Map<String, String> params = new LinkedHashMap<String, String>();
    params.put("source", vdxSource);
    params.put("action", "ranks");

    // checkout a connection to the database
    pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
    if (pool != null) {
      client = pool.checkout();
      try {
        stringList = client.getTextData(params);
      } catch (Exception e) {
        stringList = null;
      } finally {
        pool.checkin(client);
      }

      // if data was collected
      if (stringList != null) {
        rankMap = Rank.fromStringsToMap(stringList);
      }
    }

    return rankMap;
  }

  /**
   * Initialize list of azimuths for given vdx source.
   *
   * @param vdxSource vdx source name
   * @param vdxClient vdx client name
   * @return map of ids to azimuths
   */
  protected static Map<Integer, Double> getAzimuths(String vdxSource, String vdxClient)
      throws Valve3Exception {

    // initialize variables
    List<String> stringList = null;
    Map<Integer, Double> azimuthMap = null;
    Pool<VDXClient> pool = null;
    VDXClient client = null;

    // create a map of all the input parameters
    Map<String, String> params = new LinkedHashMap<String, String>();
    params.put("source", vdxSource);
    params.put("action", "azimuths");

    // checkout a connection to the database
    pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
    if (pool != null) {
      client = pool.checkout();
      try {
        stringList = client.getTextData(params);
      } catch (Exception e) {
        stringList = null;
      } finally {
        pool.checkin(client);
      }

      // if data was collected
      if (stringList != null) {
        azimuthMap = new LinkedHashMap<Integer, Double>();
        for (int i = 0; i < stringList.size(); i++) {
          String[] temp = stringList.get(i).split(":");
          azimuthMap.put(Integer.valueOf(temp[0]), Double.valueOf(temp[1]));
        }
      }
    }

    return azimuthMap;
  }

  /**
   * Initialize MatrixRenderer for left plot axis.
   *
   * @param comp plot component
   * @param channel Channel
   * @param gdm data matrix
   * @param index number of column to plot inside renderer. -1 value means we need to render all
   *              columns from gdm matrix.
   * @param unit axis label
   * @return renderer
   */
  protected MatrixRenderer getLeftMatrixRenderer(PlotComponent comp, Channel channel,
      GenericDataMatrix gdm,
      int currentComp, int compBoxHeight, int index, String unit) throws Valve3Exception {

    // setup the matrix renderer with this data
    MatrixRenderer mr = new MatrixRenderer(gdm.getData(), ranks);
    mr.setLocation(comp.getBoxX(), comp.getBoxY() + (currentComp - 1) * compBoxHeight,
        comp.getBoxWidth(), compBoxHeight - 16);
    mr.setAllVisible(false);

    // define the axis and the extents
    AxisParameters ap = new AxisParameters("L", axisMap, gdm, index, comp, mr);
    mr.setExtents(startTime + timeOffset, endTime + timeOffset, ap.minY, ap.maxY);

    // x axis decorations
    if (currentComp == compCount) {
      mr.createDefaultAxis(8, 8, tickMarksX, tickMarksY, false, ap.allowExpand, tickValuesX,
          tickValuesY);
      mr.setXAxisToTime(8, tickMarksX, tickValuesX);
      if (unitsX) {
        mr.getAxis().setBottomLabelAsText(
            timeZoneID + " Time (" + J2kSec.toDateString(startTime + timeOffset)
                + " to " + J2kSec.toDateString(endTime + timeOffset) + ")");
      }

      // don't display xTickValues for top and middle components, only for bottom component
    } else {
      mr.createDefaultAxis(8, 8, tickMarksX, tickMarksY, false, ap.allowExpand, false, tickValuesY);
      mr.setXAxisToTime(8, tickMarksX, false);
    }

    // y axis decorations
    if (unitsY) {
      mr.getAxis().setLeftLabelAsText(unit);
    }
    if (labelY) {
      DefaultFrameDecorator.addLabel(mr, channel.getCode(), Location.LEFT);
    }
    if (mr.getAxis().leftTicks != null) {
      leftTicks = mr.getAxis().leftTicks.length;
    }

    // data decorations
    if (shape == null) {
      mr.createDefaultPointRenderers(comp.getColor());
    } else {
      if (shape.equals("l")) {
        mr.createDefaultLineRenderers(comp.getColor());
      } else {
        mr.createDefaultPointRenderers(shape.charAt(0), comp.getColor());
      }
    }

    // legend decorations
    if (isDrawLegend) {
      mr.createDefaultLegendRenderer(channelLegendsCols);
    }

    return mr;
  }

  /**
   * Initialize MatrixRenderer for right plot axis.
   *
   * @param comp plot component
   * @param channel Channel
   * @param gdm data matrix
   * @param index number of column to plot inside renderer. -1 value means we need to render all
   *              columns from gdm matrix.
   * @return renderer
   */
  protected MatrixRenderer getRightMatrixRenderer(PlotComponent comp, Channel channel,
      GenericDataMatrix gdm, int currentComp, int compBoxHeight, int index,
      LegendRenderer leftLegendRenderer) throws Valve3Exception {

    if (rightUnit == null) {
      return null;
    }
    MatrixRenderer mr = new MatrixRenderer(gdm.getData(), ranks);
    mr.setLocation(comp.getBoxX(), comp.getBoxY() + (currentComp - 1) * compBoxHeight,
        comp.getBoxWidth(), compBoxHeight - 16);
    mr.setAllVisible(false);
    AxisParameters ap = new AxisParameters("R", axisMap, gdm, index, comp, mr);
    mr.setExtents(startTime + timeOffset, endTime + timeOffset, ap.minY, ap.maxY);
    AxisRenderer ar = new AxisRenderer(mr);
    if (tickValuesY) {
      ar.createRightTickLabels(SmartTick.autoTick(mr.getMinY(), mr.getMaxY(), leftTicks, false),
          null);
    }
    mr.setAxis(ar);
    if (unitsY) {
      mr.getAxis().setRightLabelAsText(rightUnit);
    }
    if (shape == null) {
      mr.createDefaultPointRenderers(leftLines, comp.getColor());
    } else {
      if (shape.equals("l")) {
        mr.createDefaultLineRenderers(leftLines, comp.getColor());
      } else {
        mr.createDefaultPointRenderers(leftLines, shape.charAt(0), comp.getColor());
      }
    }
    if (isDrawLegend) {
      mr.addToLegendRenderer(leftLegendRenderer, channelLegendsCols, leftLines);
    }
    return mr;
  }

  /**
   * This function should be overridden in each concrete plotter. Configure plotter according
   * component parameters.
   *
   * @param comp plot component
   */
  abstract void getInputs(PlotComponent comp) throws Valve3Exception;

  /**
   * This function should be overridden in each concrete plotter. Request the data from vdx server.
   *
   * @param comp plot component
   */
  abstract void getData(PlotComponent comp) throws Valve3Exception;

  /**
   * Does column contain single character strings.
   *
   * @return "column i contains single-character strings"
   */
  boolean isCharColumn(int i) {
    return false;
  }

  /**
   * Format time and data using decFmt (and nullField for empty fields); append to csvText.
   *
   * @param data data for line
   * @param time time for data
   * @param decFmt how to format numbers
   * @param nullField what to use for missing fields
   */
  private void addCSVline(Double[][] data, Double time, String decFmt, String nullField) {
    String line;
    String firstDecFmt;
    String nextDecFmt;
    if (inclTime) {
      line = String.format("%14.3f,", Time.j2kToEw(time)) + J2kSec.toDateString(time);
      firstDecFmt = "," + decFmt;
    } else {
      line = "";
      firstDecFmt = decFmt;
    }
    nextDecFmt = "," + decFmt;
    for (Double[] group : data) {
      for (int i = 1; i < group.length; i++) {
        Double v = group[i];
        if (v == null) {
          line += nullField;
        } else if (isCharColumn(i)) {
          if (v == Double.NaN || v > 255) {
            line += ", ";
          } else {
            line += "," + new Character((char) (v.intValue()));
          }
        } else {
          line += String.format(i == 1 ? firstDecFmt : nextDecFmt, v);
        }
      }
    }
    csvText.append(line);
    csvText.append("\n");
  }

  /**
   * Format time and data using decFmt (and nullField for empty fields); append to csvText (in XML
   * format).
   *
   * @param data data for line
   * @param time time for data
   * @param decFmt how to format numbers
   * @param pos line number
   * @param timeZone name of time zone
   * @param rank Default rank
   */
  private void addXMLline(Double[][] data, Double time, String decFmt, int pos, String timeZone,
      String rank) {
    boolean hasChannels = (csvHdrs.get(1)[2] != null);  // Export has channel information
    String line;                    // Export line being added

    /* If first line, add header tag */
    if (pos == 1) {
      line = "\t<DATA>\n";
    } else {
      line = "";
    }
    /* Tag for a row of data */
    line += String.format("\t\t<ROW pos=\"%d\">\n", pos);
    if (inclTime) {
      line += String.format("\t\t\t<EPOCH>%1.3f</EPOCH>\n\t\t\t<TIMESTAMP>%s</TIMESTAMP>\n",
          Time.j2kToEw(time), J2kSec.toDateString(time));
      line += "\t\t\t<TIMEZONE>" + timeZone + "</TIMEZONE>\n";
    }
    String channel = "";    // Channel name
    String tab = "\t\t\t";    // Indent for contents of a row
    int hdrIdx = 1;      // Current column of export
    boolean hasRank = (!rank.equals(""));    // Export has rank information

    for (Double[] group : data) {
      for (int i = 1; i < group.length; i++) {
        hdrIdx++;
        String[] hdr = csvHdrs.get(hdrIdx);  // Info about data value
        String tag = hdr[3];          // Tag for data value
        boolean showRank = false;        // Row has rank info to show
        if (hasChannels) {
          if (!channel.equals(hdr[2])) {
            if (i > 1) {
              line += "\t\t\t</CHANNEL>\n";
            }
            channel = hdr[2];
            line += "\t\t\t<CHANNEL>\n\t\t\t\t<code>" + channel + "</code>\n";
            tab = "\t\t\t\t";
            showRank = true;
          }
        } else {
          showRank = (i == 1);
        }
        if (showRank) {
          if (hdr[1] != null) {
            line += tab + "<rank>" + hdr[1] + "</rank>\n";
          } else if (hasRank) {
            line += tab + "<rank>" + rank + "</rank>\n";
          }
        }

        Double v = group[i];    // Actual exported data value
        if (v != null) {
          line += tab + "<" + tag + ">";
          if (isCharColumn(i)) {
            if (v == Double.NaN || v > 255) {
              ;
            } else {
              line += new Character((char) (v.intValue()));
            }
          } else {
            line += String.format(decFmt, v);
          }
          line += "</" + tag + ">\n";
        }
      }
    }
    if (hasChannels) {
      line += "\t\t\t</CHANNEL>\n";
    }
    csvText.append(line);
    csvText.append("\t\t</ROW>\n");
  }

  /**
   * Format time and data using decFmt (and nullField for empty fields); append to csvText (in JSON
   * format).
   *
   * @param data data for line
   * @param time time for data
   * @param decFmt how to format numbers
   * @param pos line number
   * @param timeZone name of time zone
   * @param rank Default rank
   */
  private void addJsonLine(Double[][] data, Double time, String decFmt, int pos, String timeZone,
      String rank) {
    boolean hasChannels = (csvHdrs.get(1)[2] != null);  // Export has channel information
    String line;                    // Export line being added

    /* If first line, add header tag */
    if (pos == 1) {
      line = "\t\"data\":[\n";
    } else {
      line = ",\n";
    }
    line += "\t\t{";
    if (inclTime) {
      line += String.format("\"EPOCH\":%1.3f,\"TIMESTAMP\":\"%s\",", Time.j2kToEw(time),
          J2kSec.toDateString(time));
      line += "\"TIMEZONE\":\"" + timeZone + "\"" + (hasChannels ? ",\n" : "");
    }

    if (hasChannels) {
      line += "\t\t\"CHANNELS\":[\n";
    }
    String channel = "";
    int hdrIdx = 1;      // Current column of export
    boolean hasRank = (!rank.equals(""));    // Export has rank information
    for (Double[] group : data) {
      if (hdrIdx != 1 && hasChannels) {
        line += ",\n";
      }
      for (int i = 1; i < group.length; i++) {
        hdrIdx++;
        String[] hdr = csvHdrs.get(hdrIdx);  // Info about data value
        String tag = hdr[3];          // Tag for data value
        boolean showRank = false;        // Row has rank info to show
        if (hasChannels) {
          if (!channel.equals(hdr[2])) {
            channel = hdr[2];
            line += "\t\t\t{\"code\":\"" + channel + "\"";
            showRank = true;
          }
        } else {
          showRank = (i == 1);
        }
        if (showRank) {
          if (hdr[1] != null) {
            line += ",\n\t\t\t\"rank\":\"" + hdr[1] + "\"";
          } else if (hasRank) {
            line += ",\n\t\t\t\"rank\":\"" + rank + "\"";
          }
        }

        Double v = group[i];
        if (v != null) {
          line += String.format(",\n\t\t\t\"%s\":", tag);
          if (isCharColumn(i)) {
            if (v == Double.NaN || v > 255) {
              line += "\"\"";
            } else {
              line += "\"" + new Character((char) (v.intValue())) + "\"";
            }
          } else {
            line += String.format(decFmt, v);
          }
        }
      }
      line += "}";
    }
    csvText.append(line);
    if (hasChannels) {
      csvText.append("\n\t\t]}");
    }
  }

  private int vaxOrder = 0;
  //private int enc_fmt = 1; // 16-bit ints
  private int encFmt = 3; // 32-bit ints
  //private int enc_fmt = 5; // IEEE double
  private int drlAdd = encFmt == 3 ? 2 : (encFmt == 1 ? 1 : 3);

  private void writeShort(OutputStream out, int val) throws IOException {
    out.write(val % 256);
    out.write(val / 256);
  }

  private void writeDouble(OutputStream out, double val) throws IOException {
    int i;
    if (encFmt == 5) {
      long lval = Double.doubleToRawLongBits(val);
      if (vaxOrder == 0) {
        for (i = 0; i < 8; i++) {
          out.write((int) ((lval >> (8 * i)) & 0x00ffL));
        }
      } else {
        for (i = 7; i >= 0; i--) {
          out.write((int) ((lval >> (8 * i)) & 0x00ffL));
        }
      }
    } else {
      Double valD = new Double(val);
      int ival = (encFmt == 1 ? valD.shortValue() : valD.intValue());
      int hi = (encFmt == 1 ? 2 : 4);
      if (vaxOrder == 0) {
        for (i = 0; i < hi; i++) {
          out.write((ival >> (8 * i)) & 0x00ff);
        }
      } else {
        for (i = hi - 1; i >= 0; i--) {
          out.write((ival >> (8 * i)) & 0x00ff);
        }
      }
    }
  }

  /**
   * Yield contents in an export format.
   *
   * @param comp plot component
   * @param cmtBits comment info to add after configured comments
   * @param seedOut stream to write seed data to
   * @return export of binary data described by given PlotComponent
   */
  public String toExport(PlotComponent comp, Map<String, String> cmtBits, OutputStream seedOut)
      throws Valve3Exception {

    // Get export configuration parameters
    ExportConfig ec = getExportConfig(vdxSource, vdxClient);
    outputType = comp.get("o");
    boolean outToCsv = outputType.equals("csv");
    boolean outToXml = outputType.equals("xml");
    boolean outToJson = outputType.equals("json");
    Vector<String> cmtLines = new Vector<String>();
    inclTime = outToCsv || outToXml || outToJson;

    if (!(Valve3.getInstance().getOpenDataURL().equalsIgnoreCase(comp.get("requestserver"))) && !ec
        .isExportable()) {
      throw new Valve3Exception("Requested export not allowed");
    }

    // Get opening comment line(s)
    String[] comments = ec.getComments();
    if (comments == null) {
      comments = new String[]{};
    }

    // Add the common column headers
    String timeZone = comp.getTimeZone().getID();
    if (inclTime) {
      String[] h1 = {null, null, null, "Epoch"};
      String[] h2 = {null, null, null, "Date"};
      csvHdrs.add(h1);
      csvHdrs.add(h2);
    }

    // Fill csvData with data to be exported; also completes csvText
    csvData = new TreeSet<ExportData>();
    csvIndex = 0;
    try {
      plot(null, comp);
    } catch (PlotException e) {
      logger.error("{}", e.getMessage());
    }
    String rank = "";
    String rowTimeZone = "";
    if (cmtBits != null) {
      cmtLines.add("reqtime=" + cmtBits.get("reqtime"));
      cmtLines.add("URL=" + cmtBits.get("URL"));
      cmtLines.add("source=" + cmtBits.get("source"));
      cmtLines.add("st=" + cmtBits.get("st") + ", et="
                   + cmtBits.get("et") + ", chCnt=" + cmtBits.get("chCnt"));
      rank = cmtBits.get("rank");
      rowTimeZone = cmtBits.get("timezone");
    }
    if (csvCmtBits.containsKey("sr")) {
      cmtLines.add("sr=" + csvCmtBits.get("sr"));
    }
    if (csvCmtBits.containsKey("datatype")) {
      cmtLines.add("datatype=" + csvCmtBits.get("datatype"));
    }
    csvText = new StringBuffer();
    Vector<String> myCmtLines = cmtLines;

    if (outToCsv) {
      for (String comment : comments) {
        csvText.append("#" + comment + "\n");
      }
      for (String comment : myCmtLines) {
        csvText.append("#" + comment + "\n");
      }
      StringBuffer hdrLine = new StringBuffer();
      boolean first = true;
      for (String[] s : csvHdrs) {
        String hdr;
        if (s[2] != null) {
          hdr = s[2] + "_" + s[3];
        } else {
          hdr = s[3];
        }
        if (first) {
          hdrLine.append(hdr);
          first = false;
        } else {
          hdrLine.append("," + hdr);
        }
      }
      csvText.append(hdrLine);
      csvHdrs = new Vector<String[]>();
      csvText.append("\n");
    }
    if (outToXml) {
      csvText.append("<VALVE_XML>\n\t<COMMENTS>\n");
      int i = 1;
      for (String comment : comments) {
        csvText.append("\t\t<COMMENTLINE pos=\"" + i + "\">" + comment.replaceAll("&", "&amp;")
            + "</COMMENTLINE>\n");
        i++;
      }
      for (String comment : myCmtLines) {
        csvText.append("\t\t<COMMENTLINE pos=\"" + i + "\">" + comment.replaceAll("&", "&amp;")
            + "</COMMENTLINE>\n");
        i++;
      }
      csvText.append("\t</COMMENTS>\n");
    }
    if (outToJson) {
      csvText.append("{\"valve-json\":\n\t{\"comments\":");
      String sep = "[\n\t\t\"";
      for (String comment : comments) {
        csvText.append(sep + comment);
        sep = "\",\n\t\t\"";
      }
      for (String comment : myCmtLines) {
        csvText.append(sep + comment);
        sep = "\",\n\t\t\"";
      }
      if (sep.charAt(0) == '[') {
        csvText.append("[],\n");
      } else {
        csvText.append("\"],\n");
      }
    }
    csvCmtBits = new LinkedHashMap<String, String>();

    // currLine is an array of the current row of data from each source, indexed by that source's ID
    Double[][] currLine = new Double[csvData.size()][];
    String decFmt = "%" + ec.getFixedWidth()[0] + "." + ec.getFixedWidth()[1] + "f";
    String jxDecFmt = "%1." + ec.getFixedWidth()[1] + "f";
    String nullField = String.format(",%" + ec.getFixedWidth()[0] + "s", "");

    if (seedOut != null) {
      try {
        // We're writing data to a miniseed file
        ExportData cd = csvData.first();

        int sampleCount = cd.count();
        int valsLeft = sampleCount; //samples
        int valsInBlockette = 4096;
        int drl = 12;
        int blocketteCount = 0;
        while (valsLeft > 0) {
          while (valsLeft < valsInBlockette) {
            drl--;
            valsInBlockette /= 2;
          }
          valsLeft -= valsInBlockette;
          blocketteCount++;
        }
        seedOut.write("000000".getBytes()); // seq nbr
        seedOut.write("D".getBytes()); // QC
        seedOut.write(32); //reserved

        String scnlOut;
        // These have to be extracted from source
        if (scnl != null) {
          scnlOut = String.format("%-5s  %-3s%-2s", scnl[0], scnl[1], scnl[2]);
        } else {
          scnlOut = "            ";
        }
        seedOut.write(scnlOut.getBytes());

        // These get extracted from start time
        Double[] datum = cd.currExportDatum();
        Date jd = J2kSec.asDate(datum[0]);
        Calendar cal = new GregorianCalendar();
        cal.setTimeZone(TimeZone.getTimeZone(timeZone));
        cal.setTime(jd);

        writeShort(seedOut, cal.get(Calendar.YEAR)); // year
        writeShort(seedOut, cal.get(Calendar.DAY_OF_YEAR)); // julian day
        valsLeft = cal.get(Calendar.HOUR_OF_DAY);
        seedOut.write(valsLeft); // hour
        valsLeft = cal.get(Calendar.MINUTE);
        seedOut.write(valsLeft); // minute
        valsLeft = cal.get(Calendar.SECOND);
        seedOut.write(valsLeft); // second
        seedOut.write(0); // unused
        valsLeft = cal.get(Calendar.MILLISECOND);
        writeShort(seedOut, valsLeft); // fraction

        // These get extracted from source
        writeShort(seedOut, sampleCount); // # samples
        valsLeft = (int) (Math.floor(samplingRate));
        writeShort(seedOut, valsLeft); // sampling rate factor
        writeShort(seedOut, 1); // sampling rate multiplier

        seedOut.write(0); // accuracy? flag
        seedOut.write(0); // IO clock? flag
        seedOut.write(0); // data quality flag
        seedOut.write(blocketteCount); // num blockettes
        seedOut.write(0); // time correction
        seedOut.write(0);
        seedOut.write(0);
        seedOut.write(0);
        writeShort(seedOut, 48 + 8 * blocketteCount); // start of data
        writeShort(seedOut, 48); // first blockette

        int offset = 48;
        drl = 12;
        valsInBlockette = 4096;
        valsLeft = sampleCount;
        while (valsLeft > 0) {
          while (valsLeft < valsInBlockette) {
            drl--;
            valsInBlockette /= 2;
          }
          valsLeft -= valsInBlockette;

          // Write header for this blockette
          // Blockette type 1000
          writeShort(seedOut, 1000);
          // Next/last blockette
          offset += 8;
          writeShort(seedOut, valsLeft > 0 ? offset : 0);

          // Encoding format
          seedOut.write(encFmt);
          // VAX order?
          seedOut.write(vaxOrder);
          // Data Record Length
          seedOut.write(drl + drlAdd);
          // Reserved
          seedOut.write(0);

        }
        // And now, the actual data
        double min = datum[1];
        double max = datum[1];
        valsLeft = sampleCount;
        while (datum != null) {
          valsLeft--;
          if (min > datum[1]) {
            min = datum[1];
          }
          if (max < datum[1]) {
            max = datum[1];
          }
          writeDouble(seedOut, datum[1]);
          datum = cd.nextExportDatum();
        }
      } catch (IOException e) {
        throw new Valve3Exception("Error writing mseed file: " + e.getMessage());
      }
    } else if (currLine.length == 1) {
      // Since there's only 1 source, we can just loop through it
      ExportData cd = csvData.first();
      Double[] datum = cd.currExportDatum();
      if (outToCsv) {
        while (datum != null) {
          currLine[0] = datum;
          addCSVline(currLine, datum[0], decFmt, nullField);
          datum = cd.nextExportDatum();
        }
      }
      cd = csvData.first();
      datum = cd.currExportDatum();
      if (outToXml) {
        int pos = 0;
        while (datum != null) {
          pos++;
          currLine[0] = datum;
          addXMLline(currLine, datum[0], jxDecFmt, pos, rowTimeZone, rank);
          datum = cd.nextExportDatum();
        }
      }
      cd = csvData.first();
      datum = cd.currExportDatum();
      if (outToJson) {
        int pos = 0;
        while (datum != null) {
          pos++;
          currLine[0] = datum;
          addJsonLine(currLine, datum[0], jxDecFmt, pos, rowTimeZone, rank);
          datum = cd.nextExportDatum();
        }
      }
    } else {
      // An array of our data sources, indexed by ID
      ExportData[] sources = new ExportData[csvData.size()];
      for (ExportData cd : csvData) {
        sources[cd.exportDataId()] = cd;
        currLine[cd.exportDataId()] = cd.dummyExportDatum();
      }

      // prevTime is the time of the last row formatted into csvText
      Double prevTime = null;
      int pos = 0;
      while (true) {
        ExportData loED;
        try {
          // Grab the ExportData whose next datum has the earliest time
          loED = csvData.first();
        } catch (Exception e) {
          loED = null;
        }

        if (prevTime != null) {
          int cmp = -1;
          if (loED != null) {
            Double[] l = loED.currExportDatum();
            Double l0 = l[0];
            cmp = prevTime.compareTo(l0);
          }
          if (cmp < 0) {
            pos++;
            // Add the current line to csvText
            if (outToCsv) {
              addCSVline(currLine, prevTime, decFmt, nullField);
            }
            if (outToXml) {
              addXMLline(currLine, prevTime, decFmt, pos, rowTimeZone, rank);
            }
            if (outToJson) {
              addJsonLine(currLine, prevTime, decFmt, pos, rowTimeZone, rank);
            }
            if (loED == null) {
              // No new data; we're done!
              break;
            }
            // "Erase" the current line
            for (ExportData cd : sources) {
              currLine[cd.exportDataId()] = cd.dummyExportDatum();
            }
            prevTime = loED.currExportDatum()[0];
          }
        } else if (loED != null) {
          // This is our first item
          prevTime = loED.currExportDatum()[0];
        } else {
          throw new Valve3Exception("No data to export");
        }
        // Add current item to current line
        currLine[loED.exportDataId()] = loED.currExportDatum();

        // Remove & add our ExportData back so that it gets placed based on its new data
        csvData.remove(loED);
        if (loED.nextExportDatum() != null) {
          csvData.add(loED);
        }
      }
    }
    if (outToXml) {
      csvText.append("\t</DATA>\n</VALVE_XML>\n");
    }
    if (outToJson) {
      csvText.append("]}}\n");
    }
    String result = csvText.toString();
    csvText = null;
    return result;
  }

  class AxisParameters {

    double minY = 1E300;
    double maxY = -1E300;
    boolean allowExpand = true;

    public AxisParameters(String axisType, Map<Integer, String> axisMap, GenericDataMatrix gdm,
        int index, PlotComponent comp, MatrixRenderer mr) throws Valve3Exception {
      if (!(axisType.equals("L") || axisType.equals("R"))) {
        throw new Valve3Exception("Illegal axis type: " + axisType);
      }
      if (index == -1) {
        for (int i = 0; i < axisMap.size(); i++) {
          setParameters(axisType, axisMap.get(i), gdm, i, comp, mr);
        }
      } else {
        setParameters(axisType, axisMap.get(index), gdm, index, comp, mr);
      }
    }

    private void setParameters(String axisType, String mapAxisType, GenericDataMatrix gdm,
        int index, PlotComponent comp, MatrixRenderer mr) throws Valve3Exception {

      int offset;
      String ysMin;
      String ysMax = "";
      boolean minAutoY = false;
      boolean maxAutoY = false;
      boolean minMeanY = false;
      boolean maxMeanY = false;

      if (!ranks) {
        offset = 1;
      } else {
        offset = 2;
      }

      if (!(mapAxisType.equals("L") || mapAxisType.equals("R") || mapAxisType.equals(""))) {
        throw new Valve3Exception("Illegal axis type in axis map: " + mapAxisType);
      }

      if (mapAxisType.equals(axisType) || isPlotSeparately()) {
        if (mapAxisType.equals(axisType)) {
          mr.setVisible(index, true);
        }

        try {
          ysMin = comp.get("ys" + mapAxisType + "Min").toLowerCase();
        } catch (NullPointerException e) {
          ysMin = "";
        }
        try {
          ysMax = comp.get("ys" + mapAxisType + "Max").toLowerCase();
        } catch (NullPointerException e) {
          ysMin = "";
        }

        // if not defined or empty, default to auto scaling
        if (ysMin.startsWith("a") || ysMin == null || ysMin.trim().isEmpty()) {
          minAutoY = true;
        } else if (ysMin.startsWith("m")) {
          minMeanY = true;
        }
        if (ysMax.startsWith("a") || ysMax == null || ysMax.trim().isEmpty()) {
          maxAutoY = true;
        } else if (ysMax.startsWith("m")) {
          maxMeanY = true;
        }

        // calculate min auto scale
        if (minAutoY || minMeanY) {
          minY = Math.min(minY, gdm.min(index + offset));

          // calculate min user defined scale
        } else {
          minY = StringUtils.stringToDouble(ysMin, Math.min(minY, gdm.min(index + offset)));
          allowExpand = false;
        }

        // calculate max auto scale
        if (maxAutoY) {
          maxY = Math.max(maxY, gdm.max(index + offset));

          // calculate max mean scale
        } else if (maxMeanY) {
          maxY = gdm.mean(index + offset) + (2 * Math.abs(gdm.mean(index + offset)));

          // calculate max user defined scale
        } else {
          maxY = StringUtils.stringToDouble(ysMax, Math.max(maxY, gdm.max(index + offset)));
          allowExpand = false;
        }

        if (minY > maxY) {
          throw new Valve3Exception("Illegal " + mapAxisType + " axis values");
        }

        double buffer = 0.05;
        if (minY == maxY && minY != 0) {
          buffer = Math.abs(minY * 0.05);
        } else {
          buffer = (maxY - minY) * 0.05;
        }
        if (allowExpand) {
          minY = minY - buffer;
          maxY = maxY + buffer;
        }
      }
    }
  }

  /**
   * Fills plotter's data manipulation configuration according component parameters.
   *
   * @param comp plot component
   */
  protected void validateDataManipOpts(PlotComponent comp) throws Valve3Exception {
    doDespike = StringUtils.stringToBoolean(comp.get("despike"));
    if (doDespike) {
      despikePeriod = comp.getDouble("despike_period");
      if (Double.isNaN(despikePeriod)) {
        throw new Valve3Exception("Illegal/missing period for despike");
      }
    }
    doDetrend = StringUtils.stringToBoolean(comp.get("detrend"));
    try {
      filterPick = comp.getInt("dmo_fl");
    } catch (Valve3Exception e) {
      filterPick = 0;
    }
    if (filterPick != 0) {
      if (filterPick != 1) {
        filterPeriod = comp.getDouble("filter_arg1");
        if (Double.isNaN(filterPeriod)) {
          throw new Valve3Exception("Illegal/missing period for filter");
        }
      } else {
        filterMin = comp.getDouble("filter_arg1");
        filterMax = comp.getDouble("filter_arg2");
        if (Double.isNaN(filterMin) && Double.isNaN(filterMax)) {
          throw new Valve3Exception("Illegal/missing bound(s) for bandpass");
        }
        // if (filterMax <= filterMin)
        // throw new Valve3Exception("Min Period must be less than Max Period");
        try {
          // this will throw exception for menus that don't collect the filter period (waveforms)
          filterPeriod = comp.getDouble("filter_arg3");
          if (Double.isNaN(filterPeriod)) {
            throw new Valve3Exception("Missing Samp Rate for bandpass");
          }
        } catch (Valve3Exception e) {
          filterPeriod = Double.NaN;
        }
      }
    }
    try {
      debiasPick = comp.getInt("dmo_db");
    } catch (Valve3Exception e) {
      debiasPick = 0;
    }
    if (debiasPick == 3) {
      debiasValue = comp.getDouble("debias_period");
      if (Double.isNaN(debiasValue)) {
        throw new Valve3Exception("Illegal/missing value for bias removal");
      }
    }
    try {
      doArithmetic = !comp.getString("dmo_arithmetic").equalsIgnoreCase("None");
    } catch (Valve3Exception e) {
      doArithmetic = false;
    }
    if (doArithmetic) {
      arithmeticType = comp.getString("dmo_arithmetic");
      arithmeticValue = comp.getDouble("dmo_arithmetic_value");
      if (Double.isNaN(arithmeticValue)) {
        throw new Valve3Exception("Illegal/missing value for arithmetic");
      }
    }
  }

  protected void addMetaData(String vdxSource, String vdxClient, Valve3Plot v3p, PlotComponent comp)
      throws Valve3Exception {
    // MetaData is associated with a channel, column, and rank combination.
    List<String> stringList = null;
    VDXClient client = null;

    Map<String, String> params = new LinkedHashMap<String, String>();
    params.put("source", vdxSource);
    params.put("action", "metadata");
    params.put("rk", Integer.toString(rk));
    params.put("ch", ch);
    params.put("byID", "true");

    // calculate the columns parameters
    String cols = null;
    if (columnsList == null) {
      cols = "";
    } else {
      for (int i = 0; i < columnsList.size(); i++) {
        Column column = columnsList.get(i);
        if (column.checked) {
          if (cols == null) {
            cols = "" + (i + 1);
          } else {
            cols += "," + (i + 1);
          }
        }
      }
    }
    params.put("col", cols);
    Pool<VDXClient> pool = null;
    pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
    if (pool != null) {
      client = pool.checkout();
      try {
        stringList = client.getTextData(params);
        for (String s : stringList) {
          System.out.println("metadatum: " + s);
          MetaDatum md = new MetaDatum(s);
          v3p.addMetaDatum(md);
        }
      } catch (Exception e) {
        throw new Valve3Exception(e.getMessage());
      } finally {
        pool.checkin(client);
      }
    }
  }

  /**
   * Add to plotter's supplemental data.
   *
   * @param vdxSource data source
   * @param vdxClient name of VDX client
   * @param v3p Valve3Plot
   * @param comp plot component
   */
  protected void addSuppData(String vdxSource, String vdxClient, Valve3Plot v3p, PlotComponent comp)
      throws Valve3Exception {

    // initialize variables
    List<String> stringList = null;
    VDXClient client = null;
    String sdTypes;

    try {
      sdTypes = comp.getString("sdt");
    } catch (Valve3Exception e) {
      sdTypes = "";
    }

    Map<String, String> params = new LinkedHashMap<String, String>();
    params.put("source", vdxSource);
    params.put("action", "suppdata");
    params.put("st", J2kSec.format(Time.STANDARD_TIME_FORMAT_MS, startTime).replaceAll("\\D", ""));
    params.put("et", J2kSec.format(Time.STANDARD_TIME_FORMAT_MS, endTime).replaceAll("\\D", ""));
    params.put("rk", Integer.toString(rk));
    params.put("byID", "true");
    params.put("ch", ch);
    params.put("type", sdTypes);
    params.put("dl", "10");

    // calculate the columns parameters
    String cols = null;
    if (columnsList == null) {
      cols = "";
    } else {
      for (int i = 0; i < columnsList.size(); i++) {
        Column column = columnsList.get(i);
        if (column.checked) {
          if (cols == null) {
            cols = "" + (i + 1);
          } else {
            cols = cols + "," + (i + 1);
          }
        }
      }
    }
    params.put("col", cols);

    // create a column map
    Map<Integer, Integer> colMap = new LinkedHashMap<Integer, Integer>();
    int i = 0;
    if (cols.length() > 0) {
      for (String c : cols.split(",")) {
        colMap.put(Integer.parseInt(c), i);
        i++;
      }
    }

    // create a channel map
    Map<Integer, Integer> chMap = new LinkedHashMap<Integer, Integer>();
    i = 0;
    if (ch.length() > 0) {
      for (String c : ch.split(",")) {
        chMap.put(Integer.parseInt(c), i);
        i++;
      }
    }

    // define the box height
    int compBoxHeight = comp.getBoxHeight();
    Pool<VDXClient> pool = null;
    pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
    if (pool != null) {
      client = pool.checkout();
      try {
        stringList = client.getTextData(params);
        for (String sd : stringList) {
          SuppDatum sdo = new SuppDatum(sd);
          int offset;
          if (isPlotSeparately()) {
            offset = (Integer) chMap.get(sdo.cid) * colMap.size() + (Integer) colMap.get(sdo.colid);
          } else {
            offset = (Integer) chMap.get(sdo.cid);
          }
          sdo.frameY = comp.getBoxY() + (offset * compBoxHeight) + 8;
          sdo.frameH = compBoxHeight - 16;
          sdo.adjustTime(timeOffset);
          v3p.addSuppDatum(sdo);
        }
      } catch (Exception e) {
        throw new Valve3Exception(e.getMessage());
      } finally {
        pool.checkin(client);
      }
    }
  }
}
