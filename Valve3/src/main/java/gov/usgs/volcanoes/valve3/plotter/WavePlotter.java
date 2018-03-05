package gov.usgs.volcanoes.valve3.plotter;

import gov.usgs.volcanoes.core.math.Butterworth;
import gov.usgs.volcanoes.core.math.Butterworth.FilterType;
import gov.usgs.volcanoes.core.legacy.plot.Plot;
import gov.usgs.volcanoes.core.legacy.plot.PlotException;
import gov.usgs.volcanoes.core.data.SliceWave;
import gov.usgs.volcanoes.core.data.Wave;
import gov.usgs.volcanoes.core.legacy.plot.render.wave.SpectraRenderer;
import gov.usgs.volcanoes.core.legacy.plot.render.wave.SpectrogramRenderer;
import gov.usgs.volcanoes.core.legacy.util.Pool;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.core.util.StringUtils;
import gov.usgs.volcanoes.core.util.UtilException;
import gov.usgs.volcanoes.valve3.PlotComponent;
import gov.usgs.volcanoes.valve3.Plotter;
import gov.usgs.volcanoes.valve3.Valve3;
import gov.usgs.volcanoes.valve3.Valve3Exception;
import gov.usgs.volcanoes.valve3.result.Valve3Plot;
import gov.usgs.volcanoes.vdx.client.VDXClient;
import gov.usgs.volcanoes.vdx.data.Channel;
import gov.usgs.volcanoes.vdx.data.ExportData;
import gov.usgs.volcanoes.vdx.data.wave.SliceWaveExporter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generate images of waveforms, spectras, and spectrograms from raw wave data from vdx source.
 *
 * @author Dan Cervelli
 */
public class WavePlotter extends RawDataPlotter {

  private enum PlotType {
    WAVEFORM, SPECTRA, SPECTROGRAM;

    public static PlotType fromString(String s) {
      if (s.equals("wf")) {
        return WAVEFORM;
      } else if (s.equals("sp")) {
        return SPECTRA;
      } else if (s.equals("sg")) {
        return SPECTROGRAM;
      } else {
        return null;
      }
    }
  }

  private PlotType plotType;
  private FilterType filterType;
  private SliceWave wave;
  private int nfft;
  private int binSize;
  private double overlap;
  private int minPower;
  private int maxPower;
  private double minFreq;
  private double maxFreq;
  private boolean logPower;
  private boolean logFreq;
  private Map<Integer, SliceWave> channelDataMap;

  private static final double MAX_DATA_REQUEST = 86400;

  /**
   * Default constructor.
   */
  public WavePlotter() {
    super();
  }

  /**
   * Initialize internal data from PlotComponent.
   *
   * @param comp PlotComponent
   */
  protected void getInputs(PlotComponent comp) throws Valve3Exception {

    parseCommonParameters(comp);
    if (endTime - startTime > MAX_DATA_REQUEST) {
      throw new Valve3Exception("Maximum waveform request is 24 hours.");
    }

    String pt = comp.get("plotType");
    if (pt == null) {
      plotType = PlotType.WAVEFORM;
    } else {
      plotType = PlotType.fromString(pt);
      if (plotType == null) {
        throw new Valve3Exception("Illegal plot type: " + pt);
      }
    }

    validateDataManipOpts(comp);

    String ft = comp.get("ftype");
    if (ft != null) {
      if (!(ft.equals("L") || ft.equals("H") || ft.equals("B") || ft.equals("N"))) {
        throw new Valve3Exception("Illegal filter type: " + ft);
      }
      filterType = FilterType.fromString(ft);
    }

    try {
      logPower = comp.getBoolean("splp");
    } catch (Valve3Exception ex) {
      logPower = true;
    }

    try {
      logFreq = comp.getBoolean("splf");
    } catch (Valve3Exception ex) {
      logFreq = false;
    }

    if (plotType == PlotType.SPECTRA || plotType == PlotType.SPECTROGRAM) {
      try {
        nfft = comp.getInt("nfft");
      } catch (Valve3Exception ex) {
        nfft = 0;
      }

      try {
        binSize = comp.getInt("binSize");
      } catch (Valve3Exception ex) {
        binSize = 256;
      }

      try {
        overlap = comp.getDouble("overlap");
      } catch (Valve3Exception ex) {
        overlap = 0.859375;
      }

      if (overlap < 0.0 || overlap > 1.0) {
        throw new Valve3Exception("Illegal overlap: " + overlap + " must be between 0 and 1");
      }

      try {
        minPower = comp.getInt("minPower");
      } catch (Valve3Exception ex) {
        minPower = 20;
      }

      try {
        maxPower = comp.getInt("maxPower");
      } catch (Valve3Exception ex) {
        maxPower = 120;
      }

      if (minPower >= maxPower) {
        throw new Valve3Exception(
            "Illegal minimum/maximum power: " + minPower + " and " + maxPower);
      }

      try {
        minFreq = comp.getDouble("spminf");
      } catch (Valve3Exception ex) {
        minFreq = 0.0;
      }

      try {
        maxFreq = comp.getDouble("spmaxf");
      } catch (Valve3Exception ex) {
        maxFreq = 20.0;
      }

      if (minFreq < 0 || maxFreq <= 0 || minFreq >= maxFreq) {
        throw new Valve3Exception(
            "Illegal minimum/maximum frequencies: " + minFreq + " and " + maxFreq);
      }
    }
  }

  /**
   * Gets binary data from VDX, performs filtering if needed.
   *
   * @param comp PlotComponent
   */
  protected void getData(PlotComponent comp) throws Valve3Exception {

    // initialize variables
    boolean exceptionThrown = false;
    String exceptionMsg = "";
    VDXClient client = null;
    channelDataMap = new LinkedHashMap<Integer, SliceWave>();
    String[] channels = ch.split(",");

    // create a map of all the input parameters
    Map<String, String> params = new LinkedHashMap<String, String>();
    params.put("source", vdxSource);
    params.put("action", "data");
    params.put("st", Double.toString(startTime));
    params.put("et", Double.toString(endTime));

    // checkout a connection to the database
    Pool<VDXClient> pool = null;
    pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
    if (pool != null) {
      client = pool.checkout();

      // iterate through each of the selected channels and place the data in the map
      for (String channel : channels) {
        params.put("ch", channel);
        Wave data = null;
        try {
          data = (Wave) client.getBinaryData(params);
        } catch (UtilException e) {
          exceptionThrown = true;
          exceptionMsg = e.getMessage();
          break;
        } catch (Exception e) {
          exceptionThrown = true;
          exceptionMsg = e.getMessage();
          break;
        }

        // if data was collected
        if (data != null) {
          if (forExport) {
            samplingRate = data.getSamplingRate();
            if (inclTime) {
              dataType = data.getDataType();
            } else {
              dataType = "i4";
            }
            csvCmtBits.put("sr", "" + samplingRate);
            csvCmtBits.put("datatype", dataType);
          }
          data.setStartTime(data.getStartTime() + timeOffset);
          data.handleBadData();
          if (doDespike) {
            data.despike(despikePeriod);
          }
          if (doDetrend) {
            data.detrend();
          }
          if (filterPick != 0) {
            switch (filterPick) {
              case 1: // Bandpass
                FilterType ft = FilterType.BANDPASS;
                Double singleBand = 0.0;
                if (!Double.isNaN(filterMax)) {
                  if (filterMax <= 0) {
                    throw new Valve3Exception("Illegal max hertz value.");
                  }
                } else {
                  ft = FilterType.HIGHPASS;
                  singleBand = filterMin;
                }
                if (!Double.isNaN(filterMin)) {
                  if (filterMin <= 0) {
                    throw new Valve3Exception("Illegal min hertz value.");
                  }
                } else {
                  ft = FilterType.LOWPASS;
                  singleBand = filterMax;
                }
                Butterworth bw = new Butterworth();
                if (ft == FilterType.BANDPASS) {
                  bw.set(ft, 4, data.getSamplingRate(), filterMin, filterMax);
                } else {
                  bw.set(ft, 4, data.getSamplingRate(), singleBand, 0);
                }
                data.filter(bw, true);
                break;
              case 2: // Running median
                data.set2median(filterPeriod);
                break;
              case 3: // Running mean
                data.set2mean(filterPeriod);
                break;
              default:
                break;
            }
          }
          if (debiasPick != 0) {
            int bias = 0;
            Double dbias;
            switch (debiasPick) {
              case 1: // remove mean
                dbias = new Double(data.mean());
                bias = dbias.intValue();
                break;
              case 2: // remove initial value
                bias = data.first();
                break;
              case 3: // remove user value
                dbias = new Double(debiasValue);
                bias = dbias.intValue();
                break;
              default:
                break;
            }
            data.subtract(bias);
          }
          wave = new SliceWave(data);
          wave.setSlice(data.getStartTime(), data.getEndTime());
          channelDataMap.put(Integer.valueOf(channel), wave);
        }
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
   * Initialize SliceWaveRenderer and add it to plot.
   *
   * @param v3p Valve3Plot
   * @param comp PlotComponent
   * @param channel Channel
   * @param wave SliceWave
   * @param currentComp int
   * @param compBoxHeight display height
   */
  private void plotWaveform(Valve3Plot v3p, PlotComponent comp, Channel channel, SliceWave wave,
      int currentComp, int compBoxHeight) throws Valve3Exception {

    double minY = 1E300;
    double maxY = -1E300;
    boolean minAutoY = false;
    boolean maxAutoY = false;
    String mapAxisType = "L";

    String ysMin = comp.get("ys" + mapAxisType + "Min").toLowerCase();
    String ysMax = comp.get("ys" + mapAxisType + "Max").toLowerCase();

    // if not defined or empty, default to auto scaling
    if (ysMin.startsWith("a") || ysMin == null || ysMin.trim().isEmpty()) {
      minAutoY = true;
    }
    if (ysMax.startsWith("a") || ysMax == null || ysMax.trim().isEmpty()) {
      maxAutoY = true;
    }

    // calculate min auto scale
    if (minAutoY) {
      minY = Math.min(minY, wave.min());

      // calculate min user defined scale
    } else {
      minY = StringUtils.stringToDouble(ysMin, Math.min(minY, wave.min()));
    }

    // calculate max auto scale
    if (maxAutoY) {
      maxY = Math.max(maxY, wave.max());

      // calculate max user defined scale
    } else {
      maxY = StringUtils.stringToDouble(ysMax, Math.max(maxY, wave.max()));
    }

    if (minY > minY) {
      throw new Valve3Exception("Illegal " + mapAxisType + " axis values");
    }

    double buffer = 0.05;
    if (minY == maxY && minY != 0) {
      buffer = Math.abs(minY * 0.05);
    } else {
      buffer = (maxY - minY) * 0.05;
    }
    if (!minAutoY) {
      minY = minY - buffer;
    }
    if (!maxAutoY) {
      maxY = maxY + buffer;
    }

    SliceWaveExporter wr = new SliceWaveExporter();

    if (currentComp == compCount) {
      wr.xTickMarks = this.tickMarksX;
      wr.xTickValues = this.tickValuesX;
      wr.xUnits = this.unitsX;
      wr.xLabel = this.labelX;
    } else {
      wr.xTickMarks = this.tickMarksX;
      wr.xTickValues = false;
      wr.xUnits = false;
      wr.xLabel = false;
    }
    wr.yTickMarks = this.tickMarksY;
    wr.yTickValues = this.tickValuesY;
    wr.setWave(wave);
    wr.setLocation(comp.getBoxX(), comp.getBoxY() + (currentComp - 1) * compBoxHeight,
        comp.getBoxWidth(), compBoxHeight - 16);
    wr.setViewTimes(startTime + timeOffset, endTime + timeOffset, timeZoneID);
    wr.setMinY(minY);
    wr.setMaxY(maxY);
    wr.setColor(comp.getColor());
    if (labelY) {
      wr.setYLabelText(channel.getName());
    }
    wr.update();
    if (isDrawLegend) {
      channelLegendsCols = new String[1];
      channelLegendsCols[0] =
          channel.getName() + " " + (filterType == null ? "" : "(" + filterType.name() + ")");
      wr.createDefaultLegendRenderer(channelLegendsCols);
    }

    if (forExport) {
      String[] hdr = {null, null, channel.getCode().replace('$', '_').replace(',', '/'), "Count"};
      csvHdrs.add(hdr);
      scnl = channel.getCode().split("[$]");
      ExportData ed = new ExportData(csvIndex, wr);
      csvIndex++;
      csvData.add(ed);

    } else {
      comp.setTranslation(wr.getDefaultTranslation(v3p.getPlot().getHeight()));
      comp.setTranslationType("ty");
      v3p.getPlot().addRenderer(wr);
      v3p.addComponent(comp);
    }
  }

  /**
   * Initialize SpectraRenderer and add it to plot.
   *
   * @param v3p Valve3Plot
   * @param comp PlotComponent
   * @param channel Channel
   * @param wave SliceWave
   * @param currentComp int
   * @param compBoxHeight display height
   */
  private void plotSpectra(Valve3Plot v3p, PlotComponent comp, Channel channel, SliceWave wave,
      int currentComp, int compBoxHeight) throws Valve3Exception {

    SpectraRenderer spectraRenderer = new SpectraRenderer();

    if (currentComp == compCount) {
      spectraRenderer.xTickMarks = this.tickMarksX;
      spectraRenderer.xTickValues = this.tickValuesX;
      spectraRenderer.xUnits = this.unitsX;
      spectraRenderer.xLabel = this.labelX;
    } else {
      spectraRenderer.xTickMarks = this.tickMarksX;
      spectraRenderer.xTickValues = false;
      spectraRenderer.xUnits = false;
      spectraRenderer.xLabel = false;
    }
    spectraRenderer.yTickMarks = this.tickMarksY;
    spectraRenderer.yTickValues = this.tickValuesY;
    spectraRenderer.setWave(wave);
    spectraRenderer.setAutoScale(true);
    spectraRenderer.setLocation(comp.getBoxX(), comp.getBoxY() + (currentComp - 1) * compBoxHeight,
        comp.getBoxWidth(), compBoxHeight - 16);
    spectraRenderer.setLogPower(logPower);
    spectraRenderer.setLogFreq(logFreq);
    spectraRenderer.setMinFreq(minFreq);
    spectraRenderer.setMaxFreq(maxFreq);
    spectraRenderer.setColor(comp.getColor());
    if (labelY) {
      spectraRenderer.setYLabelText(channel.getName());
    }
    if (unitsY) {
      spectraRenderer.setYUnitText("Power");
    }

    spectraRenderer.update();
    spectraRenderer.getAxis().setTopLabelAsText(getTopLabel());
    if (isDrawLegend) {
      channelLegendsCols = new String[2];
      channelLegendsCols[1] =
          channel.getName() + " " + (filterType == null ? "" : "(" + filterType.name() + ")");
      spectraRenderer.createDefaultLegendRenderer(channelLegendsCols);
    }

    comp.setTranslation(spectraRenderer.getDefaultTranslation(v3p.getPlot().getHeight()));
    comp.setTranslationType("xy");
    v3p.getPlot().addRenderer(spectraRenderer);
    v3p.addComponent(comp);
  }

  /**
   * Initialize SpectrogramRenderer and add it to plot.
   *
   * @param v3p Valve3Plot
   * @param comp PlotComponent
   * @param channel Channel
   * @param wave SliceWave
   * @param currentComp int
   * @param compBoxHeight display height
   */
  private void plotSpectrogram(Valve3Plot v3p, PlotComponent comp, Channel channel, SliceWave wave,
      int currentComp, int compBoxHeight) {
    SpectrogramRenderer spectrogramRenderer = new SpectrogramRenderer(wave);
    if (currentComp == compCount) {
      spectrogramRenderer.xTickMarks = this.tickMarksX;
      spectrogramRenderer.xTickValues = this.tickValuesX;
      spectrogramRenderer.xUnits = this.unitsX;
      spectrogramRenderer.xLabel = this.labelX;
    } else {
      spectrogramRenderer.xTickMarks = this.tickMarksX;
      spectrogramRenderer.xTickValues = false;
      spectrogramRenderer.xUnits = false;
      spectrogramRenderer.xLabel = false;
    }
    spectrogramRenderer.yTickMarks = this.tickMarksY;
    spectrogramRenderer.yTickValues = this.tickValuesY;
    spectrogramRenderer
        .setLocation(comp.getBoxX(), comp.getBoxY() + (currentComp - 1) * compBoxHeight,
            comp.getBoxWidth(), compBoxHeight - 16);
    spectrogramRenderer.setOverlap(0);
    spectrogramRenderer.setLogPower(logPower);
    spectrogramRenderer.setViewStartTime(startTime + timeOffset);
    spectrogramRenderer.setViewEndTime(endTime + timeOffset);
    spectrogramRenderer.setTimeZone(timeZoneID);
    spectrogramRenderer.setMinFreq(minFreq);
    spectrogramRenderer.setMaxFreq(maxFreq);
    spectrogramRenderer.setNfft(nfft);
    spectrogramRenderer.setBinSize(binSize);
    spectrogramRenderer.setOverlap(overlap);
    spectrogramRenderer.setMaxPower(maxPower);
    spectrogramRenderer.setMinPower(minPower);

    if (unitsY) {
      spectrogramRenderer.setYUnitText("Frequency (Hz)");
    }
    if (labelY) {
      spectrogramRenderer.setYLabelText(channel.getName());
    }

    if (isDrawLegend) {
      channelLegendsCols = new String[1];
      channelLegendsCols[0] =
          channel.getName() + " " + (filterType == null ? "" : "(" + filterType.name() + ")");
      spectrogramRenderer.createDefaultLegendRenderer(channelLegendsCols);
    }

    spectrogramRenderer.update();

    comp.setTranslation(spectrogramRenderer.getDefaultTranslation(v3p.getPlot().getHeight()));
    comp.setTranslationType("ty");
    v3p.getPlot().addRenderer(spectrogramRenderer);
    v3p.addComponent(comp);
  }

  /**
   * If v3Plot is null, prepare data for exporting Otherwise, Loop through the list of channels and
   * create plots.
   *
   * @param v3p Valve3Plot
   * @param comp PlotComponent
   */
  public void plotData(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception {

    if (forExport) {
      switch (plotType) {
        case WAVEFORM:
          break;
        case SPECTRA:
          throw new Valve3Exception("Spectra cannot be exported");
        case SPECTROGRAM:
          throw new Valve3Exception("Spectrograms cannot be exported");
        default:
          break;
      }
    }

    // calculate the number of plot components that will be displayed per channel
    int channelCompCount = 1;

    // total components is components per channel * number of channels
    compCount = channelCompCount * channelDataMap.size();

    // setting up variables to decide where to plot this component
    int currentComp = 1;
    int compBoxHeight = comp.getBoxHeight();

    for (int cid : channelDataMap.keySet()) {

      // get the relevant information for this channel
      Channel channel = channelsMap.get(cid);
      SliceWave wave = channelDataMap.get(cid);

      // if there is no data for this channel, then resize the plot window
      if (wave == null) {
        v3p.setHeight(v3p.getHeight() - channelCompCount * compBoxHeight);
        Plot plot = v3p.getPlot();
        plot.setSize(plot.getWidth(), plot.getHeight() - channelCompCount * compBoxHeight);
        compCount = compCount - channelCompCount;
        continue;
      }

      switch (plotType) {
        case WAVEFORM:
          plotWaveform(v3p, comp, channel, wave, currentComp, compBoxHeight);
          break;
        case SPECTRA:
          plotSpectra(v3p, comp, channel, wave, currentComp, compBoxHeight);
          break;
        case SPECTROGRAM:
          plotSpectrogram(v3p, comp, channel, wave, currentComp, compBoxHeight);
          break;
        default:
          break;
      }
      currentComp++;
    }
    switch (plotType) {
      case WAVEFORM:
        if (!forExport) {
          addSuppData(vdxSource, vdxClient, v3p, comp);
          if (channelDataMap.size() != 1) {
            v3p.setCombineable(false);
          } else {
            v3p.setCombineable(true);
          }
          v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Waveform");
        }
        break;
      case SPECTRA:
        if (!forExport) {
          v3p.setCombineable(false);
          v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Spectra");
        }
        break;
      case SPECTROGRAM:
        if (!forExport) {
          v3p.setCombineable(false);
          v3p.setTitle(
              Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Spectrogram");
        }
        break;
      default:
        break;
    }
  }

  /**
   * Concrete realization of abstract method. Generate PNG image to file with random name. If v3p is
   * null, prepare data for export -- assumes csvData, csvData & csvIndex initialized.
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

    // set the exportable based on the output and plot type
    switch (plotType) {

      case WAVEFORM:

        // plot configuration
        if (!forExport) {
          v3p.setExportable(true);
          v3p.setWaveform(true);
        }
        break;

      case SPECTRA:

        // plot configuration
        if (!forExport) {
          v3p.setExportable(false);

          // export configuration
        } else {
          throw new Valve3Exception("Data Export Not Available for Spectra");
        }
        break;

      case SPECTROGRAM:

        // plot configuration
        if (!forExport) {
          v3p.setExportable(false);

          // export configuration
        } else {
          throw new Valve3Exception("Data Export Not Available for Spectrogram");
        }
        break;
      default:
        break;
    }

    // this is a legitimate request so lookup the data from the database and plot it
    getData(comp);
    plotData(v3p, comp);

    if (!forExport) {
      writeFile(v3p);
    }

  }

  /**
   * Yield the sample rate.
   *
   * @return sample rate
   */
  public double getSampleRate() {
    return samplingRate;
  }

  /**
   * Yield the data type.
   *
   * @return data type
   */
  public String getDataType() {
    return dataType;
  }

  /**
   * Generate top label.
   *
   * @return plot top label text
   */
  private String getTopLabel() {
    StringBuilder top = new StringBuilder(100);
    top.append("Data between ");
    top.append(J2kSec.toDateString(startTime + timeOffset));
    top.append(" and ");
    top.append(J2kSec.toDateString(endTime + timeOffset));
    top.append(" " + timeZoneID + " Time");
    return top.toString();
  }
}
