package gov.usgs.valve3.plotter;

import gov.usgs.math.Butterworth;
import gov.usgs.math.Butterworth.FilterType;
import gov.usgs.plot.Plot;
import gov.usgs.util.Pool;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.Channel;
import gov.usgs.vdx.data.wave.SliceWave;
import gov.usgs.vdx.data.wave.Wave;
import gov.usgs.vdx.data.wave.plot.SliceWaveRenderer;
import gov.usgs.vdx.data.wave.plot.SpectraRenderer;
import gov.usgs.vdx.data.wave.plot.SpectrogramRenderer;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generate images of waveforms, spectras, and spectrograms 
 * from raw wave data from vdx source
 *
 * @author Dan Cervelli
 */
public class WavePlotter extends Plotter {
	private enum PlotType {
		WAVEFORM, SPECTRA, SPECTROGRAM;
		
		public static PlotType fromString(String s) {
			if (s.equals("wf"))
				return WAVEFORM;
			else if (s.equals("sp"))
				return SPECTRA;
			else if (s.equals("sg"))
				return SPECTROGRAM;
			else 
				return null;
		}
	}
	
	private Valve3Plot v3Plot;
	private PlotComponent component;
	private String ch;
	private double startTime;
	private double endTime;
	private PlotType plotType;
	private boolean removeBias;
	private FilterType filterType;
	private SliceWave wave;
	private double minHz;
	private double maxHz;
	private double minFreq;
	private double maxFreq;
	private boolean logPower;
	private boolean logFreq;
	private int labels;
	private int yLabel;
	private int xLabel;
	private String color;
	private Map<Integer, SliceWave> channelDataMap;	
	private static Map<Integer, Channel> channelsMap;
	int compCount;
	
	private static final double MAX_DATA_REQUEST = 86400;
	
	/**
	 * Default constructor
	 */
	public WavePlotter()
	{}
	
	/**
	 * Initialize internal data from PlotComponent
	 * @throws Valve3Exception
	 */
	public void getInputs() throws Valve3Exception {
		
		ch = component.get("ch");
		if (ch == null || ch.length() <= 0) {
			throw new Valve3Exception("Illegal channel.");
		}
		
		endTime	= component.getEndTime();
		if (Double.isNaN(endTime)) {
			throw new Valve3Exception("Illegal end time.");
		}
		
		startTime = component.getStartTime(endTime);
		if (Double.isNaN(startTime)) {
			throw new Valve3Exception("Illegal start time.");
		}

		if (endTime - startTime > MAX_DATA_REQUEST)
			throw new Valve3Exception("Maximum waveform request is 24 hours.");
		
		plotType	= PlotType.fromString(component.get("plotType"));
		if (plotType == null) {
			throw new Valve3Exception("Illegal plot type.");
		}
		
		removeBias = false;
		String clip = component.get("rb");
		if (clip != null && clip.toUpperCase().equals("T"))
			removeBias = true;
		
		filterType = FilterType.fromString(component.get("ftype"));
		minHz = -1;
		maxHz = -1;
		try {
			minHz = Double.parseDouble(component.get("fminhz"));
			maxHz = Double.parseDouble(component.get("fmaxhz"));
		} catch (Exception e) {}
		
		minFreq = -1;
		maxFreq = -1;
		try {
			minFreq = Double.parseDouble(component.get("spminf"));
			maxFreq = Double.parseDouble(component.get("spmaxf"));
		} catch (Exception e) {}
		
		logPower = false;
		String lp = component.get("splp");
		if (lp != null && lp.toUpperCase().equals("T"))
			logPower = true;
		
		logFreq = false;
		String lf = component.get("splf");
		if (lf != null && lf.toUpperCase().equals("T"))
			logFreq = true;
		
		if (plotType == PlotType.SPECTRA || plotType == PlotType.SPECTROGRAM) {
			if (minFreq < 0 || maxFreq <= 0 || minFreq >= maxFreq)
				throw new Valve3Exception("Illegal minimum/maximum frequencies.");
		}
		
		yLabel = 1;
		try {
			yLabel = Integer.parseInt(component.get("yLabel"));
		} catch (Exception e){}
		
		xLabel = 1;
		try {
			xLabel = Integer.parseInt(component.get("xLabel"));
		} catch (Exception e){}

		labels = 0;
		try {
			labels = Integer.parseInt(component.get("labels"));
		} catch (Exception e){}
		
		color = component.get("color");
		if (color == null)
			color = "A";
	}

	/**
	 * Gets binary data from VDX, performs filtering if needed
	 * @throws Valve3Exception
	 */
	public void getData() throws Valve3Exception {
		
		boolean gotData = false;
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));

		// checkout a connection to the database
		Pool<VDXClient> pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client		= pool.checkout();
		if (client == null)
			return;
		
		double TZOffset = Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
		
		// create a map to hold all the channel data
		channelDataMap			= new LinkedHashMap<Integer, SliceWave>();
		String[] channels		= ch.split(",");
		
		// iterate through each of the selected channels and place the data in the map
		for (String channel : channels) {
			
			params.put("ch", channel);
			Wave data = (Wave)client.getBinaryData(params);
			
			if (data != null) {
				
				gotData = true;
				data.setStartTime(data.getStartTime() + TZOffset);
				
				if (filterType != null) {
					Butterworth bw = new Butterworth();
					switch(filterType) {
						case LOWPASS:
							if (maxHz <= 0)
								throw new Valve3Exception("Illegal max hertz value.");
							bw.set(FilterType.LOWPASS, 4, data.getSamplingRate(), maxHz, 0);
							data.filter(bw, true);
							break;
						case HIGHPASS:
							if (minHz <= 0)
								throw new Valve3Exception("Illegal minimum hertz value.");
							bw.set(FilterType.HIGHPASS, 4, data.getSamplingRate(), minHz, 0);
							data.filter(bw, true);
							break;
						case BANDPASS:
							if (minHz <= 0 || maxHz <= 0 || minHz > maxHz)
								throw new Valve3Exception("Illegal minimum/maximum hertz values.");
							bw.set(FilterType.BANDPASS, 4, data.getSamplingRate(), minHz, maxHz);
							data.filter(bw, true);
							break;
					}
				}
				
				wave = new SliceWave(data);
				wave.setSlice(data.getStartTime(), data.getEndTime());
				channelDataMap.put(Integer.valueOf(channel), wave);
			}
		}
		
		if (!gotData) {
			throw new Valve3Exception("No data for any stations.");
		}
		
		// adjust the start and end times
		startTime	+= TZOffset;
		endTime		+= TZOffset;
		
		// check back in our connection to the database
		pool.checkin(client);
	}
	
	/**
	 * Initialize SliceWaveRenderer and add it to plot
	 * @throws Valve3Exception
	 */
	private void plotWaveform(Channel channel, SliceWave wave, int displayCount, int dh) throws Valve3Exception {
		
		SliceWaveRenderer wr = new SliceWaveRenderer();
		wr.setRemoveBias(removeBias);
		wr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
		wr.setWave(wave);
		wr.setViewTimes(startTime, endTime);
		if (color.equals("M"))
			wr.setColor(Color.BLACK);
		
		double bias = 0;
		if (removeBias)
			bias = wave.mean();
		
		/*
		double max = wave.max() - bias;
		double min = wave.min() - bias;
		if (component.isAutoScale("ys")) {
			wr.setMinY(min);
			wr.setMaxY(max);
		} else {
			double[] ys = component.getYScale("ys", min, max);
			double yMin = ys[0];
			double yMax = ys[1];
			if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
				throw new Valve3Exception("Illegal axis values.");

			wr.setMinY(yMin);
			wr.setMaxY(yMax);
		}
		*/
		
		double yMin = 1E300;
		double yMax = -1E300;
		if (component.isAutoScale("ys")) {
			// double buff;
			yMin	= wave.min() - bias;
			yMax	= wave.max() - bias;
			// i don't think that we need to setup a buffer for wave based plots
			// buff	= (yMax - yMin) * 0.05;
			// yMin	= yMin - buff;
			// yMax	= yMax + buff;
		} else {
			double[] ys = component.getYScale("ys", yMin, yMax);
			yMin = ys[0];
			yMax = ys[1];
			if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
				throw new Valve3Exception("Illegal axis values.");
		}
		
		wr.setMinY(yMin);
		wr.setMaxY(yMax);
		
		if (labels == 1) {
			// wr.setDisplayLabels(false);
			wr.setYLabel("");
			wr.update();
		} else {			
			wr.update();
			if (displayCount + 1 == compCount) {
				wr.getAxis().setBottomLabelAsText("Time (" + Valve3.getInstance().getTimeZoneAbbr()+ ")");	
			}
		}

		component.setTranslation(wr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		v3Plot.getPlot().addRenderer(wr);
		v3Plot.addComponent(component);
	}
	
	/**
	 * Initialize SpectraRenderer and add it to plot
	 * @throws Valve3Exception
	 */
	private void plotSpectra(Channel channel, SliceWave wave, int displayCount, int dh) {
		SpectraRenderer sr = new SpectraRenderer();
		sr.setWave(wave);
		sr.setAutoScale(true);
		sr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
		sr.setLogPower(logPower);
		sr.setLogFreq(logFreq);
		sr.setMinFreq(minFreq);
		sr.setMaxFreq(maxFreq);
		sr.update(0);
		
		component.setTranslation(sr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("xy");
		v3Plot.getPlot().addRenderer(sr);
		v3Plot.addComponent(component);
	}

	/**
	 * Initialize SpectrogramRenderer and add it to plot
	 * @throws Valve3Exception
	 */
	private void plotSpectrogram(Channel channel, SliceWave wave, int displayCount, int dh) {
		SpectrogramRenderer sr = new SpectrogramRenderer(wave);
		sr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
		sr.setOverlap(0);
		sr.setLogPower(logPower);
		sr.setViewStartTime(startTime);
		sr.setViewEndTime(endTime);
		sr.setMinFreq(minFreq);
		sr.setMaxFreq(maxFreq);
		sr.setYLabel(yLabel);
		sr.setXLabel(xLabel);
		//sr.setFftSize();
		//sr.setTimeZoneOffset(Valve.getTimeZoneAdj());
		sr.update(0);
		
		int yTick = 0;
		String yString = "";
		if (yLabel == 1) {
			yTick = 8;
			yString = "";
			//yString = "Frequency (Hz)";
			
		} else if (yLabel == 2) {
			yTick = 5;
			yString = channel.getCode().substring(0, channel.getCode().indexOf("$"));
			sr.setYAxisLabel("");
			sr.update(0);
		}
		
		int xTick = 0;
		String xString = "";
		if (xLabel == 1) {
			xTick = 8;
			xString = "Time (" + Valve3.getInstance().getTimeZoneAbbr()+ ")";
		}
		
		sr.createDefaultAxis(xTick, yTick, false, false);
		sr.setXAxisToTime(xTick);
		sr.getAxis().setLeftLabelAsText(yString);
		
		if (displayCount + 1 == compCount) {
			sr.getAxis().setBottomLabelAsText(xString);	
		}

		component.setTranslation(sr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		v3Plot.getPlot().addRenderer(sr);
		v3Plot.addComponent(component);
	}
	
	/**
	 * Loop through the list of channels and create plots
	 * @throws Valve3Exception
	 */
	public void plotData() throws Valve3Exception {
		
		/// calculate how many graphs we are going to build (number of channels)
		compCount	= channelDataMap.size();
		
		// setting up variables to decide where to plot this component
		int displayCount	= 0;
		int dh				= component.getBoxHeight() / compCount;
		
		for (int cid : channelDataMap.keySet()) {
			
			// get the relevant information for this channel
			Channel channel	= channelsMap.get(cid);
			SliceWave wave	= channelDataMap.get(cid);
			
			// verify their is something to plot
			if (wave == null) {
				continue;
			}
			
			switch(plotType) {
				case WAVEFORM:
					plotWaveform(channel, wave, displayCount, dh);
					break;
				case SPECTRA:
					plotSpectra(channel, wave, displayCount, dh);
					break;
				case SPECTROGRAM:
					plotSpectrogram(channel, wave, displayCount, dh);
					break;
			}
			displayCount++;
		}
		
		switch(plotType) {
			case WAVEFORM:
				v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Waveform");
				break;
			case SPECTRA:
				v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Spectra");
				break;
			case SPECTROGRAM:
				v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Spectrogram");
				break;
		}
	}

	/**
	 * Concrete realization of abstract method. 
	 * Generate PNG image to file with random name.
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception {
		v3Plot		= v3p;
		component	= comp;
		channelsMap	= getChannels(vdxSource, vdxClient);
		getInputs();
		getData();
		
		plotData();
		
		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		plot.writePNG(v3Plot.getLocalFilename());
	}
	
	/**
	 * @return CSV dump of binary data described by given PlotComponent
	 */
	public String toCSV(PlotComponent comp) throws Valve3Exception {
		component = comp;
		getInputs();
		getData();
		return wave.toCSV();
	}

	/**
	 * Initialize list of channels for given vdx source
	 * @param source	vdx source name
	 * @param client	vdx name
	 */
	private static Map<Integer, Channel> getChannels(String source, String client) {
		Map<Integer, Channel> channels;	
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", source);
		params.put("action", "channels");
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(client);
		VDXClient cl = pool.checkout();
		List<String> chs = cl.getTextData(params);
		pool.checkin(cl);
		channels = Channel.fromStringsToMap(chs);
		return channels;
	}
}