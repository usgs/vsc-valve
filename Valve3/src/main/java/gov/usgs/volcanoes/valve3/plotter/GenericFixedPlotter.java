package gov.usgs.volcanoes.valve3.plotter;

import gov.usgs.volcanoes.core.data.GenericDataMatrix;
import gov.usgs.volcanoes.core.legacy.plot.Plot;
import gov.usgs.volcanoes.core.legacy.plot.PlotException;
import gov.usgs.volcanoes.core.legacy.plot.render.MatrixRenderer;
import gov.usgs.volcanoes.core.legacy.util.Pool;
import gov.usgs.volcanoes.core.math.Butterworth;
import gov.usgs.volcanoes.core.math.Butterworth.FilterType;
import gov.usgs.volcanoes.core.util.StringUtils;
import gov.usgs.volcanoes.valve3.PlotComponent;
import gov.usgs.volcanoes.valve3.Plotter;
import gov.usgs.volcanoes.valve3.Valve3;
import gov.usgs.volcanoes.valve3.Valve3Exception;
import gov.usgs.volcanoes.valve3.result.Valve3Plot;
import gov.usgs.volcanoes.vdx.client.VDXClient;
import gov.usgs.volcanoes.vdx.data.Channel;
import gov.usgs.volcanoes.vdx.data.Column;
import gov.usgs.volcanoes.vdx.data.ExportData;
import gov.usgs.volcanoes.vdx.data.MatrixExporter;
import gov.usgs.volcanoes.vdx.data.Rank;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Generate images for generic data plot to files.
 *
 * @author Dan Cervelli
 * @author Loren Antolik
 * @author Bill Tollett
 */
public class GenericFixedPlotter extends RawDataPlotter {

  private Map<Integer, GenericDataMatrix> channelDataMap;

  private String[] legendsCols;

  /**
   * Default constructor.
   */
  public GenericFixedPlotter() {
    super();
  }

  /**
   * Initialize internal data from PlotComponent component.
   *
   * @param comp data to initialize from
   */
  protected void getInputs(PlotComponent comp) throws Valve3Exception {

    parseCommonParameters(comp);

    rk = comp.getInt("rk");
    columnsCount = columnsList.size();
    legendsCols = new String[columnsCount];
    channelLegendsCols = new String[columnsCount];
    bypassCols = new boolean[columnsCount];
    accumulateCols = new boolean[columnsCount];

    leftLines = 0;
    axisMap = new LinkedHashMap<>();

    validateDataManipOpts(comp);

    // iterate through all the active columns and place them in a map if they are displayed
    for (int i = 0; i < columnsCount; i++) {
      Column column = columnsList.get(i);
      String colArg = comp.get(column.name);
      if (colArg != null) {
        column.checked = StringUtils.stringToBoolean(comp.get(column.name));
      }
      bypassCols[i] = column.bypassmanip;
      accumulateCols[i] = column.accumulate;
      legendsCols[i] = column.description;
      if (column.checked) {
        if (isPlotSeparately()) {
          axisMap.put(i, "L");
          leftUnit = column.unit;
          leftLines++;
        } else {
          if ((leftUnit != null && leftUnit.equals(column.unit))) {
            axisMap.put(i, "L");
            leftLines++;
          } else if (rightUnit != null && rightUnit.equals(column.unit)) {
            axisMap.put(i, "R");
          } else if (leftUnit == null) {
            leftUnit = column.unit;
            axisMap.put(i, "L");
            leftLines++;
          } else if (rightUnit == null) {
            rightUnit = column.unit;
            axisMap.put(i, "R");
          } else {
            throw new Valve3Exception("Too many different units.");
          }
        }
      } else {
        axisMap.put(i, "");
      }
    }

    if (leftUnit == null && rightUnit == null) {
      throw new Valve3Exception("Nothing to plot.");
    }
  }

  /**
   * Gets binary data from VDX server.
   *
   * @param comp to get data for
   */
  protected void getData(PlotComponent comp) throws Valve3Exception {

    // initialize variables
    boolean exceptionThrown = false;
    String exceptionMsg = "";
    channelDataMap = new LinkedHashMap<>();
    String[] channels = ch.split(",");

    // create a map of all the input parameters
    Map<String, String> params = new LinkedHashMap<>();
    params.put("source", vdxSource);
    params.put("action", "data");
    params.put("st", Double.toString(startTime));
    params.put("et", Double.toString(endTime));
    params.put("rk", Integer.toString(rk));
    addDownsamplingInfo(params);

    // checkout a connection to the database
    Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
    if (pool != null) {
      VDXClient client = pool.checkout();

      // iterate through each of the selected channels and place the data in the map
      for (String channel : channels) {
        params.put("ch", channel);
        GenericDataMatrix data;
        try {
          data = (GenericDataMatrix) client.getBinaryData(params);
        } catch (Exception e) {
          exceptionThrown = true;
          exceptionMsg = e.getMessage();
          logger.debug(exceptionMsg);
          break;
        }

        // if data was collected
        if (data != null && data.rows() > 0) {
          logger.debug("Data not null.");
          data.adjustTime(timeOffset);
        } else {
          logger.debug("Data null or rows = 0.");
        }
        channelDataMap.put(Integer.valueOf(channel), data);
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
   * If v3Plot is null, prepare data for exporting Otherwise, initialize MatrixRenderers for left
   * and right axis, adds them to plot.
   *
   * @param v3p Valve3Plot
   * @param comp PlotComponent
   */
  private void plotData(Valve3Plot v3p, PlotComponent comp, Rank rank) throws Valve3Exception {

    // setup the legend for the rank
    String rankLegend = rank.getName();

    // calculate the number of plot components that will be displayed per channel
    int channelCompCount = 0;
    if (isPlotSeparately()) {
      for (Column col : columnsList) {
        if (col.checked || (forExport && exportAll)) {
          channelCompCount++;
        }
      }
    } else {
      channelCompCount = 1;
    }

    // total components is components per channel * number of channels
    compCount = channelCompCount * channelDataMap.size();

    // setting up variables to decide where to plot this component
    int currentComp = 1;
    int compBoxHeight = comp.getBoxHeight();

    for (Entry<Integer, GenericDataMatrix> entry : channelDataMap.entrySet()) {

      // get the relevant information for this channel
      Channel channel = channelsMap.get(entry.getKey());
      GenericDataMatrix gdm = entry.getValue();

      // if there is no data for this channel, then resize the plot window
      if (gdm == null || gdm.rows() == 0) {
        v3p.setHeight(v3p.getHeight() - channelCompCount * compBoxHeight);
        Plot plot = v3p.getPlot();
        plot.setSize(plot.getWidth(), plot.getHeight() - channelCompCount * compBoxHeight);
        compCount = compCount - channelCompCount;
        continue;
      }

      // detrend and normalize the data that the user requested to be detrended
      for (int i = 0; i < columnsCount; i++) {
        if (accumulateCols[i]) {
          gdm.accumulate(i + 2);
        }
        if (bypassCols[i]) {
          continue;
        }
        if (doDespike) {
          gdm.despike(i + 2, despikePeriod);
        }
        if (doDetrend) {
          gdm.detrend(i + 2);
        }
        if (filterPick != 0) {
          switch (filterPick) {
            case 1: // Bandpass
              Butterworth bw = new Butterworth();
              FilterType ft = FilterType.BANDPASS;
              Double singleBand = 0.0;
              if (!Double.isNaN(filterMax)) {
                if (filterMax <= 0) {
                  throw new Valve3Exception("Illegal max period value.");
                }
              } else {
                ft = FilterType.LOWPASS;
                singleBand = filterMin;
              }
              if (!Double.isNaN(filterMin)) {
                if (filterMin <= 0) {
                  throw new Valve3Exception("Illegal min period value.");
                }
              } else {
                ft = FilterType.HIGHPASS;
                singleBand = filterMax;
              }
              if (ft == FilterType.BANDPASS) {
                bw.set(ft, 4, Math.pow(filterPeriod, -1), Math.pow(filterMax, -1),
                    Math.pow(filterMin, -1));
              } else {
                bw.set(ft, 4, Math.pow(filterPeriod, -1), Math.pow(singleBand, -1), 0);
              }
              gdm.filter(bw, i + 2, true);
              break;
            case 2: // Running median
              gdm.set2median(i + 2, filterPeriod);
              break;
            case 3: // Running mean
              gdm.set2mean(i + 2, filterPeriod);
              break;
            default:
              break;
          }
        }
        if (doArithmetic) {
          gdm.doArithmetic(i + 2, arithmeticType, arithmeticValue);
        }
        if (debiasPick != 0) {
          double bias = 0.0;
          switch (debiasPick) {
            case 1: // remove mean
              bias = gdm.mean(i + 2);
              break;
            case 2: // remove initial value
              bias = gdm.first(i + 2);
              break;
            case 3: // remove user value
              bias = debiasValue;
              break;
            default:
              break;
          }
          gdm.add(i + 2, -bias);
        }
      }

      if (forExport) {
        // Add column headers to csvHdrs
        int i = 0;
        for (Column col : columnsList) {
          if ((forExport && exportAll) || !axisMap.get(i).equals("")) {
            String[] newHdr = {null, null, channel.getCode(), col.name};
            csvHdrs.add(newHdr);
          }
          i++;
        }
        // Initialize data for export; add to set for CSV
        ExportData ed = new ExportData(csvIndex, new MatrixExporter(gdm.getData(), ranks, axisMap));
        csvData.add(ed);
        csvIndex++;
      } else {
        // set up the legend
        for (int i = 0; i < legendsCols.length; i++) {
          if (useChNames) {
            channelLegendsCols[i] = String.format("%s %s %s",
                                                  channel.getName(),
                                                  rankLegend,
                                                  legendsCols[i]);
          } else {
            channelLegendsCols[i] = String.format("%s %s %s",
                                                  channel.getCode(),
                                                  rankLegend,
                                                  legendsCols[i]);
          }
        }

        // create an individual matrix renderer for each component selected
        if (isPlotSeparately()) {
          for (int i = 0; i < columnsList.size(); i++) {
            Column col = columnsList.get(i);
            if (col.checked) {
              MatrixRenderer leftMR = getLeftMatrixRenderer(comp, channel, gdm, currentComp,
                  compBoxHeight, i, col.unit);
              MatrixRenderer rightMR = getRightMatrixRenderer(comp, channel, gdm, currentComp,
                  compBoxHeight, i, leftMR.getLegendRenderer());
              if (rightMR != null) {
                v3p.getPlot().addRenderer(rightMR);
              }
              v3p.getPlot().addRenderer(leftMR);
              comp.setTranslation(leftMR.getDefaultTranslation(v3p.getPlot().getHeight()));
              comp.setTranslationType("ty");
              v3p.addComponent(comp);
              currentComp++;
            }
          }

          // create a single matrix renderer for each component selected
        } else {
          MatrixRenderer leftMR = getLeftMatrixRenderer(comp, channel, gdm, currentComp,
              compBoxHeight, -1, leftUnit);
          MatrixRenderer rightMR = getRightMatrixRenderer(comp, channel, gdm, currentComp,
              compBoxHeight, -1, leftMR.getLegendRenderer());
          if (rightMR != null) {
            v3p.getPlot().addRenderer(rightMR);
          }
          v3p.getPlot().addRenderer(leftMR);
          comp.setTranslation(leftMR.getDefaultTranslation(v3p.getPlot().getHeight()));
          comp.setTranslationType("ty");
          v3p.addComponent(comp);
          currentComp++;
        }
      }
    }
    if (!forExport) {
      addSuppData(vdxSource, vdxClient, v3p, comp);
      addMetaData(vdxSource, vdxClient, v3p, comp);
      if (channelDataMap.size() != 1) {
        v3p.setCombineable(false);
      } else {
        v3p.setCombineable(true);
      }
      v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Time Series");
    }
  }

  /**
   * Concrete realization of abstract method. Initialize MatrixRenderers for left and right axis
   * (plot may have 2 different value axis) Generate PNG image to file with random file name if v3p
   * isn't null. If v3p is null, prepare data for export -- assumes csvData, csvData & csvIndex
   * initialized
   *
   * @param comp PlotComponent
   * @see Plotter
   */
  public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception, PlotException {

    forExport = (v3p == null);
    channelsMap = getChannels(vdxSource, vdxClient);
    ranksMap = getRanks(vdxSource, vdxClient);
    columnsList = getColumns(vdxSource, vdxClient);
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
}
