package gov.usgs.volcanoes.valve3.plotter;

import gov.usgs.plot.PlotException;
import gov.usgs.plot.data.GenericDataMatrix;
import gov.usgs.plot.data.RSAMData;
import gov.usgs.plot.render.MatrixRenderer;
import gov.usgs.util.Pool;
import gov.usgs.util.UtilException;
import gov.usgs.volcanoes.valve3.PlotComponent;
import gov.usgs.volcanoes.valve3.Plotter;
import gov.usgs.volcanoes.valve3.Valve3;
import gov.usgs.volcanoes.valve3.Valve3Exception;
import gov.usgs.volcanoes.valve3.result.Valve3Plot;
import gov.usgs.volcanoes.vdx.client.VDXClient;
import gov.usgs.volcanoes.vdx.data.Channel;
import gov.usgs.volcanoes.vdx.data.ExportData;
import gov.usgs.volcanoes.vdx.data.MatrixExporter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Get RSAM information from vdx server and generate images of RSAM values and RSAM event count
 * histograms in files with random names.
 *
 * @author Dan Cervelli
 * @author Loren Antolik
 */
public class RatSamPlotter extends RawDataPlotter {

  private enum PlotType {
    VALUES, COUNTS;

    public static PlotType fromString(String s) {
      if (s == null) {
        return null;
      } else if (s.equals("values")) {
        return VALUES;
      } else if (s.equals("cnts")) {
        return COUNTS;
      } else {
        return null;
      }
    }
  }

  private PlotType plotType;
  private static Map<Integer, Channel> channelsMap;
  RSAMData data;

  /**
   * Default constructor.
   */
  public RatSamPlotter() {
    super();
    ranks = false;
  }

  /**
   * Initialize internal data from PlotComponent.
   *
   * @param comp PlotComponent
   */
  protected void getInputs(PlotComponent comp) throws Valve3Exception {

    parseCommonParameters(comp);

    channelLegendsCols = new String[1];

    String pt = comp.get("plotType");
    if (pt == null) {
      plotType = PlotType.VALUES;
    } else {
      plotType = PlotType.fromString(pt);
      if (plotType == null) {
        throw new Valve3Exception("Illegal plot type: " + pt);
      }
    }

    switch (plotType) {

      case VALUES:
        leftLines = 0;
        axisMap = new LinkedHashMap<Integer, String>();
        // validateDataManipOpts(component);
        axisMap.put(0, "L");
        leftUnit = "RatSAM";
        leftLines++;
        break;

      case COUNTS:
        break;
      default:
        break;
    }
  }

  /**
   * Gets binary data from VDX.
   *
   * @param comp PlotComponent
   */
  protected void getData(PlotComponent comp) throws Valve3Exception {

    // initialize variables
    boolean exceptionThrown = false;
    String exceptionMsg = "";
    VDXClient client = null;

    // create a map of all the input parameters
    Map<String, String> params = new LinkedHashMap<String, String>();
    params.put("source", vdxSource);
    params.put("action", "ratdata");
    params.put("ch", ch);
    params.put("st", Double.toString(startTime));
    params.put("et", Double.toString(endTime));
    params.put("plotType", plotType.toString());
    addDownsamplingInfo(params);

    // checkout a connection to the database
    Pool<VDXClient> pool = null;
    pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
    if (pool != null) {
      client = pool.checkout();
      try {
        data = (RSAMData) client.getBinaryData(params);
      } catch (UtilException e) {
        exceptionThrown = true;
        exceptionMsg = e.getMessage();
      } catch (Exception e) {
        exceptionThrown = true;
        exceptionMsg = e.getMessage();
      }

      // if data was collected
      if (data != null && data.rows() > 0) {
        data.adjustTime(timeOffset);
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
   * Initialize DataRenderer, add it to plot, remove mean from rsam data if needed and render rsam
   * values to PNG image in local file.
   *
   * @param v3p Valve3Plot
   * @param comp PlotComponent
   * @param channel1 Channel
   * @param channel2 Channel
   * @param rd RSAMData
   * @param currentComp int
   * @param compBoxHeight int
   */
  protected void plotValues(Valve3Plot v3p, PlotComponent comp, Channel channel1, Channel channel2,
      RSAMData rd, int currentComp, int compBoxHeight) throws Valve3Exception {

    String channelCode1 = channel1.getCode().replace('$', ' ').replace('_', ' ').replace(',', '/');
    String channelCode2 = channel2.getCode().replace('$', ' ').replace('_', ' ').replace(',', '/');
    channel1.setCode(channelCode1 + "-" + channelCode2);
    GenericDataMatrix gdm = new GenericDataMatrix(rd.getData());
    channelLegendsCols[0] = String.format("%s %s", channel1.getCode(), leftUnit);

    if (forExport) {

      // Add column header to csvHdrs
      String[] hdr = {null, null, channel1.getCode(), leftUnit};
      csvHdrs.add(hdr);

      // Initialize data for export; add to set for CSV
      ExportData ed = new ExportData(csvIndex, new MatrixExporter(gdm.getData(), ranks, axisMap));
      csvData.add(ed);

    } else {
      try {
        MatrixRenderer leftMR = getLeftMatrixRenderer(comp, channel1, gdm, currentComp,
            compBoxHeight, 0, leftUnit);
        v3p.getPlot().addRenderer(leftMR);
        comp.setTranslation(leftMR.getDefaultTranslation(v3p.getPlot().getHeight()));
        comp.setTranslationType("ty");
        v3p.addComponent(comp);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Loop through the list of channels and create plots.
   *
   * @param v3p Valve3Plot
   * @param comp PlotComponent
   */
  public void plotData(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception {

    String[] channels = ch.split(",");
    Channel channel1 = channelsMap.get(Integer.valueOf(channels[0]));
    Channel channel2 = channelsMap.get(Integer.valueOf(channels[1]));

    // calculate the number of plot components that will be displayed per channel
    int channelCompCount = 1;

    // total components is components per channel * number of channels
    compCount = channelCompCount;

    // setting up variables to decide where to plot this component
    int currentComp = 1;
    int compBoxHeight = comp.getBoxHeight();

    switch (plotType) {
      case VALUES:
        plotValues(v3p, comp, channel1, channel2, data, currentComp, compBoxHeight);
        if (!forExport) {
          v3p.setCombineable(true);
          v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Values");
        }
        break;
      case COUNTS:
        if (!forExport) {
          v3p.setCombineable(false);
        }
        break;
      default:
        break;
    }
  }

  /**
   * Concrete realization of abstract method. Generate PNG images for values or event count
   * histograms (depends from plot type) to file with random name.
   *
   * @param v3p Valve3Plot
   * @param comp PlotComponent
   * @see Plotter
   */
  public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception, PlotException {

    forExport = (v3p == null);
    channelsMap = getChannels(vdxSource, vdxClient);
    comp.setPlotter(this.getClass().getName());
    getInputs(comp);

    // plot configuration
    if (!forExport) {
      v3p.setExportable(true);
    }

    // this is a legitimate request so lookup the data from the database and plot it
    getData(comp);
    plotData(v3p, comp);

    if (!forExport) {
      writeFile(v3p);
    }
  }
}
