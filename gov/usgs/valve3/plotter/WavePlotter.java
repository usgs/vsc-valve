package gov.usgs.valve3.plotter;

import gov.usgs.math.Butterworth;
import gov.usgs.math.Butterworth.FilterType;
import gov.usgs.plot.Plot;
import gov.usgs.util.Pool;
import gov.usgs.util.UtilException;
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
import java.util.Map;

/**
 * Generate images of waveforms, spectras, and spectrograms 
 * from raw wave data from vdx source
 *
 * @author Dan Cervelli
 */
public class WavePlotter extends RawDataPlotter {
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
	
	private static final double MAX_DATA_REQUEST = 86400;
	
	/**
	 * Default constructor
	 */
	public WavePlotter(){
		super();
	}
	
	/**
	 * Initialize internal data from PlotComponent
	 * @throws Valve3Exception
	 */
	protected void getInputs(PlotComponent component) throws Valve3Exception {
		
		parseCommonParameters(component);
		if (endTime - startTime > MAX_DATA_REQUEST)
			throw new Valve3Exception("Maximum waveform request is 24 hours.");
		
		String pt = component.getString("plotType");
		plotType	= PlotType.fromString(pt);
		if (plotType == null) {
			throw new Valve3Exception("Illegal plot type: " + pt);
		}
		
		try{
			removeBias = component.getBoolean("rb");
		} catch (Valve3Exception ex){
			removeBias = false;
		}
		
		String ft = component.get("ftype");
		if(ft!=null){
			if(!(ft.equals("L") || ft.equals("H") || ft.equals("B") || ft.equals("N"))){
				throw new Valve3Exception("Illegal filter type: " + ft);
			}
			filterType = FilterType.fromString(ft);
		}
		
		minHz = component.getDouble("fminhz");
		maxHz = component.getDouble("fmaxhz");
		
		try{
			logPower = component.getBoolean("splp");
		} catch (Valve3Exception ex){
			logPower = false;
		}
		
		try{
			logFreq = component.getBoolean("splf");
		} catch (Valve3Exception ex){
			logFreq = false;
		}
		
		if (plotType == PlotType.SPECTRA || plotType == PlotType.SPECTROGRAM) {
			minFreq = component.getDouble("spminf");
			maxFreq = component.getDouble("spmaxf");
			if (minFreq < 0 || maxFreq <= 0 || minFreq >= maxFreq)
				throw new Valve3Exception("Illegal minimum/maximum frequencies: " + minFreq + " and " + maxFreq);
		}
		
		try{
			yLabel = component.getInt("yLabel");
		} catch (Valve3Exception ex){
			yLabel = 1;
		}

		try{
			xLabel = component.getInt("xLabel");
		} catch (Valve3Exception ex){
			xLabel = 1;
		}
		
		try{
			labels = component.getInt("labels");
		} catch (Valve3Exception ex){
			labels = 0;
		}
		
		try{
			color = component.getString("color");
		} catch (Valve3Exception ex){
			color = "A";
		}
	}

	/**
	 * Gets binary data from VDX, performs filtering if needed
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
		if(maxrows!=0){
			params.put("maxrows", Integer.toString(maxrows));
		}
		// checkout a connection to the database
		Pool<VDXClient> pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client		= pool.checkout();
		if (client == null)
			return;
		
		// create a map to hold all the channel data
		channelDataMap			= new LinkedHashMap<Integer, SliceWave>();
		String[] channels		= ch.split(",");
		
		// iterate through each of the selected channels and place the data in the map
		for (String channel : channels) {
			params.put("ch", channel);
			Wave data = null;
			try{
				data = (Wave)client.getBinaryData(params);
			}
			catch(UtilException e){
				throw new Valve3Exception(e.getMessage()); 
			}
			if (data != null) {
				gotData = true;
				data.setStartTime(data.getStartTime() + component.getOffset(startTime));
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
		// check back in our connection to the database
		pool.checkin(client);
	}
	
	/**
	 * Initialize SliceWaveRenderer and add it to plot
	 * @throws Valve3Exception
	 */
	private void plotWaveform(Valve3Plot v3Plot, PlotComponent component, Channel channel, SliceWave wave, int displayCount, int dh) throws Valve3Exception {
		double timeOffset = component.getOffset(startTime);
		SliceWaveRenderer wr = new SliceWaveRenderer();
		wr.setRemoveBias(removeBias);
		wr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
		wr.setWave(wave);
		wr.setViewTimes(startTime+timeOffset, endTime+timeOffset);
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
				wr.getAxis().setBottomLabelAsText("Time (" + component.getTimeZone().getID()+ ")");	
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
	private void plotSpectra(Valve3Plot v3Plot, PlotComponent component, Channel channel, SliceWave wave, int displayCount, int dh) {
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
	private void plotSpectrogram(Valve3Plot v3Plot, PlotComponent component, Channel channel, SliceWave wave, int displayCount, int dh) {
		double timeOffset = component.getOffset(startTime);
		SpectrogramRenderer sr = new SpectrogramRenderer(wave);
		sr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
		sr.setOverlap(0);
		sr.setLogPower(logPower);
		sr.setViewStartTime(startTime+timeOffset);
		sr.setViewEndTime(endTime+timeOffset);
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
			xString = "Time (" + component.getTimeZone().getID()+ ")";
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
	public void plotData(Valve3Plot v3Plot, PlotComponent component) throws Valve3Exception {
		
		/// calculate how many graphs we are going to build (number of channels)
		compCount	= channelDataMap.size();
		
		// setting up variables to decide where to plot this component
		int displayCount	= 0;
		int dh				= component.getBoxHeight();
		
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
					plotWaveform(v3Plot, component, channel, wave, displayCount, dh);
					break;
				case SPECTRA:
					plotSpectra(v3Plot, component, channel, wave, displayCount, dh);
					break;
				case SPECTROGRAM:
					plotSpectrogram(v3Plot, component, channel, wave, displayCount, dh);
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
		channelsMap	= getChannels(vdxSource, vdxClient);
		getInputs(comp);
		getData(comp);
		
		plotData(v3p, comp);
		
		Plot plot = v3p.getPlot();
		plot.setBackgroundColor(Color.white);
		plot.writePNG(v3p.getLocalFilename());
	}
	
	/**
	 * @return CSV dump of binary data described by given PlotComponent
	 */
	public String toCSV(PlotComponent comp) throws Valve3Exception {
		getInputs(comp);
		getData(comp);
		return wave.toCSV();
	}
}