package gov.usgs.valve3.plotter;

import gov.usgs.math.Butterworth;
import gov.usgs.math.Butterworth.FilterType;
import gov.usgs.plot.EllipseVectorRenderer;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.PlotException;
import gov.usgs.plot.Renderer;
import gov.usgs.plot.TextRenderer;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.util.UtilException;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.Channel;
import gov.usgs.vdx.data.Column;
import gov.usgs.vdx.data.ExportData;
import gov.usgs.vdx.data.GenericDataMatrix;
import gov.usgs.vdx.data.MatrixExporter;
import gov.usgs.vdx.data.Rank;
import gov.usgs.vdx.data.tensorstrain.TensorstrainData;
import gov.usgs.vdx.data.tilt.TiltData;

import gov.usgs.proj.GeoRange;
import gov.usgs.plot.map.GeoLabel;
import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.plot.map.GeoLabelSet;
import gov.usgs.plot.map.MapRenderer;
import gov.usgs.proj.TransverseMercator;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix2D;

/**
 * Generate tensorstrain images from raw data got from vdx source
 * 
 * @author Max Kokoulin
 */
public class TensorstrainPlotter extends RawDataPlotter {

	private enum Azimuth {
		NATURAL, USERDEFINED;
		public static Azimuth fromString(String s) {
			if (s.equals("n")) {
				return NATURAL;
			} else if (s.equals("u")) {
				return USERDEFINED;
			} else {
				return null;
			}
		}
	}

	private Map<Integer, TensorstrainData> channelDataMap;
	private static Map<Integer, Double> azimuthsMap;

	private String legendsCols[];

	private Azimuth azimuth;
	private double azimuthValue;

	/**
	 * Default constructor
	 */
	public TensorstrainPlotter() {
		super();
	}

	protected void getInputs(PlotComponent component) throws Valve3Exception {
		parseCommonParameters(component);
		rk = component.getInt("rk");
		String az = component.get("az");
		if (az == null)
			az = "n";
		azimuth = Azimuth.fromString(az);
		if (azimuth == null) {
			throw new Valve3Exception("Illegal azimuth: " + az);
		}
		columnsCount = columnsList.size();
		legendsCols = new String[columnsCount];
		channelLegendsCols = new String[columnsCount];
		bypassManipCols = new boolean[columnsList.size()];
		leftLines = 0;
		axisMap = new LinkedHashMap<Integer, String>();
		validateDataManipOpts(component);
		// iterate through all the active columns and place them in a map if
		// they are displayed
		for (int i = 0; i < columnsList.size(); i++) {
			Column column = columnsList.get(i);
			String col_arg = component.get(column.name);
			if (col_arg != null)
				column.checked = Util.stringToBoolean(component
						.get(column.name));
			// detrendCols[i] = Util.stringToBoolean(component.get("d_" +
			// column.name));
			legendsCols[i] = column.description;
			if (column.checked) {
				if (forExport || isPlotComponentsSeparately()) {
					axisMap.put(i, "L");
					leftUnit = column.unit;
					leftLines++;
				} else {
					if (leftUnit != null && leftUnit.equals(column.unit)) {
						axisMap.put(i, "L");
						leftLines++;
					} else if (rightUnit != null
							&& rightUnit.equals(column.unit)) {
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
		if (leftUnit == null && rightUnit == null)
			throw new Valve3Exception("Nothing to plot.");
	}

	/**
	 * Gets binary data from VDX
	 * 
	 * @throws Valve3Exception
	 */
	protected void getData(PlotComponent component) throws Valve3Exception {

		boolean gotData = false;
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("rk", Integer.toString(rk));
		addDownsamplingInfo(params);
		// checkout a connection to the database
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		if (client == null)
			return;

		// create a map to hold all the channel data
		channelDataMap = new LinkedHashMap<Integer, TensorstrainData>();
		String[] channels = ch.split(",");

		// iterate through each of the selected channels and get the data from
		// the db=
		for (String channel : channels) {
			params.put("ch", channel);
			TensorstrainData data = null;
			try {
				data = (TensorstrainData) client.getBinaryData(params);
			} catch (UtilException e) {
				throw new Valve3Exception(e.getMessage());
			}

			if (data != null) {
				gotData = true;
				data.adjustTime(component.getOffset(startTime));
				channelDataMap.put(Integer.valueOf(channel), data);
			}
		}
		if (!gotData) {
			throw new Valve3Exception("No data for any stations.");
		}
		// check back in our connection to the database
		pool.checkin(client);
	}

	/**
	 * If v3Plot is null, prepare data for exporting Otherwise, Initialize
	 * MatrixRenderers for left and right axis, adds them to plot
	 * 
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3Plot, PlotComponent component) throws Valve3Exception {
		boolean forExport = (v3Plot == null);
		int csvIndex = 0;
		int displayCount = 0, dh = 0;
		Rank rank = null;
		String rankLegend = null;
		if (!forExport) {
			// setting up variables to decide where to plot this component
			displayCount = 0;
			dh = component.getBoxHeight();
			// setup the display for the legend
			rank = new Rank();
			if (rk == 0) {
				rank = rank.bestPossible();
				v3Plot.setExportable(false);
			} else {
				rank = ranksMap.get(rk);
			}
			rankLegend = rank.getName();
		}
		for (int cid : channelDataMap.keySet()) {
			// get the relevant information for this channel
			Channel channel = channelsMap.get(cid);
			TensorstrainData data = channelDataMap.get(cid);
			// verify their is something to plot
			if (data == null || data.rows() == 0) {
				continue;
			}
			// instantiate the azimuth and tangential values based on the user
			// selection
			switch (azimuth) {
			case NATURAL:
				azimuthValue = azimuthsMap.get(channel.getCID());
				break;
			case USERDEFINED:
				String azval = component.get("azval");
				if (azval == null)
					azimuthValue = 0.0;
				else
					azimuthValue = component.getDouble("azval");
				break;
			default:
				azimuthValue = 0.0;
				break;
			}
			// subtract the mean from the data to get it on a zero based scale
			// (for east and north)
			data.add(2, -data.mean(2));
			data.add(3, -data.mean(3));

			// set up the legend
			String tensorstrainLegend = null;
			if (!forExport)
				for (int i = 0; i < legendsCols.length; i++) {
					tensorstrainLegend = legendsCols[i];
					channelLegendsCols[i] = String.format("%s %s %s", channel.getCode(), rankLegend, tensorstrainLegend);
				}
			GenericDataMatrix gdm = new GenericDataMatrix(data.getAllData(90-azimuthValue));
			// detrend the data that the user requested to be detrended
			for (int i = 0; i < columnsCount; i++) {
				Column col = columnsList.get(i);
				if (!col.checked) {
					continue;
				}
				if (bypassManipCols[i]) {
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
							if (filterMax <= 0)
								throw new Valve3Exception(
										"Illegal max hertz value.");
						} else {
							ft = FilterType.HIGHPASS;
							singleBand = filterMin;
						}
						if (!Double.isNaN(filterMin)) {
							if (filterMin <= 0)
								throw new Valve3Exception(
										"Illegal min hertz value.");
						} else {
							ft = FilterType.LOWPASS;
							singleBand = filterMax;
						}
						/*
						 * SBH if ( ft == FilterType.BANDPASS ) bw.set(ft, 4,
						 * gdm.getSamplingRate(), filterMin, filterMax); else
						 * bw.set(ft, 4, gdm.getSamplingRate(), singleBand, 0);
						 * gdm.filter(bw, true);
						 */
						break;
					case 2: // Running median
						gdm.set2median(i + 2, filterPeriod);
						break;
					case 3: // Running mean
						gdm.set2mean(i + 2, filterPeriod);
					}
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
					}
					gdm.add(i + 2, -bias);
				}
			}

			if (forExport) {
				// Add the headers to the CSV file
				for (int i = 0; i < columnsList.size(); i++) {
					if (!axisMap.get(i).equals("")) {
						csvHdrs.append(String.format(",%s_%s", channel.getCode(), legendsCols[i]));
					}
				}
				// Initialize data for export; add to set for CSV
				ExportData ed = new ExportData(csvIndex, new MatrixExporter(gdm.getData(), ranks, axisMap));
				csvIndex++;
				csvData.add(ed);
			} else if (isPlotComponentsSeparately()) {
				for (Column col : columnsList) {
					if (col.checked) {
						compCount++;
					}
				}
				// create an individual matrix renderer for each component
				// selected
				for (int i = 0; i < columnsList.size(); i++) {
					Column col = columnsList.get(i);
					if (col.checked) {
						MatrixRenderer leftMR = getLeftMatrixRenderer(component, channel, gdm, displayCount, dh, i,	col.unit);
						MatrixRenderer rightMR = getRightMatrixRenderer(component, channel, gdm, displayCount, dh, i);
						v3Plot.getPlot().addRenderer(leftMR);
						if (rightMR != null)
							v3Plot.getPlot().addRenderer(rightMR);
						component.setTranslation(leftMR.getDefaultTranslation(v3Plot.getPlot().getHeight()));
						component.setTranslationType("ty");
						v3Plot.addComponent(component);
						displayCount++;
					}
				}
			} else {
				compCount = channelDataMap.size();
				MatrixRenderer leftMR = getLeftMatrixRenderer(component, channel, gdm, displayCount, dh, -1, leftUnit);
				MatrixRenderer rightMR = getRightMatrixRenderer(component,	channel, gdm, displayCount, dh, -1);
				v3Plot.getPlot().addRenderer(leftMR);
				if (rightMR != null)
					v3Plot.getPlot().addRenderer(rightMR);
				component.setTranslation(leftMR.getDefaultTranslation(v3Plot.getPlot().getHeight()));
				component.setTranslationType("ty");
				v3Plot.addComponent(component);
				displayCount++;
			}
		}
		if (!forExport) {
			v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Time Series");
			addSuppData(vdxSource, vdxClient, v3Plot, component);
		}
	}

	/**
	 * Concrete realization of abstract method. Generate tilt PNG image to file
	 * with random name. If v3p is null, prepare data for export -- assumes
	 * csvData, csvData & csvIndex initialized
	 * 
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception, PlotException {
		forExport = (v3p == null); // = "prepare data for export"
		channelsMap = getChannels(vdxSource, vdxClient);
		ranksMap = getRanks(vdxSource, vdxClient);
		azimuthsMap = getAzimuths(vdxSource, vdxClient);
		columnsList = getColumns(vdxSource, vdxClient);
		getInputs(comp);
		getData(comp);

		plotData(v3p, comp);

		if (!forExport) {
			Plot plot = v3p.getPlot();
			plot.setBackgroundColor(Color.white);
			plot.writePNG(v3p.getLocalFilename());
		}
	}
	



	/**
	 * Initialize list of channels for given vdx source
	 * 
	 * @param source
	 *            vdx source name
	 * @param client
	 *            vdx name
	 */
	private static Map<Integer, Double> getAzimuths(String source, String client) throws Valve3Exception {
		Map<Integer, Double> azimuths = new LinkedHashMap<Integer, Double>();
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", source);
		params.put("action", "azimuths");
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(client);
		VDXClient cl = pool.checkout();
		List<String> chs = null;
		try {
			chs = cl.getTextData(params);
		} catch (UtilException e) {
			throw new Valve3Exception(e.getMessage());
		}
		pool.checkin(cl);
		for (int i = 0; i < chs.size(); i++) {
			String[] temp = chs.get(i).split(":");
			azimuths.put(Integer.valueOf(temp[0]), Double.valueOf(temp[1]));
		}
		return azimuths;
	}
}
