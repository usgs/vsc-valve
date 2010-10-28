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
import gov.usgs.vdx.data.ExportData;
import gov.usgs.vdx.data.SliceWaveExporter;
import gov.usgs.vdx.data.wave.SliceWave;
import gov.usgs.vdx.data.wave.Wave;
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
//	private boolean removeBias;
	private FilterType filterType;
	private SliceWave wave;
	private double minHz;
	private double maxHz;
	private double minFreq;
	private double maxFreq;
	private boolean logPower;
	private boolean logFreq;
	private Map<Integer, SliceWave> channelDataMap;	
	private double samplingRate = 0.0;
	private String dataType = null;
	
	private static final double MAX_DATA_REQUEST = 86400;
	
	/**
	 * Default constructor
	 */
	public WavePlotter(){
		super();
	}
	
	/**
	 * Initialize internal data from PlotComponent
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	protected void getInputs(PlotComponent component) throws Valve3Exception {
		
		parseCommonParameters(component);
		if (endTime - startTime > MAX_DATA_REQUEST)
			throw new Valve3Exception("Maximum waveform request is 24 hours.");
		
		String pt = component.get("plotType");
		if ( pt == null )
			plotType = PlotType.WAVEFORM;
		else {
			plotType	= PlotType.fromString(pt);
			if (plotType == null) {
				throw new Valve3Exception("Illegal plot type: " + pt);
			}
		}
		
		validateDataManipOpts(component);
		
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
			logPower = true;
		}
		
		try{
			logFreq = component.getBoolean("splf");
		} catch (Valve3Exception ex){
			logFreq = false;
		}
		
		if (plotType == PlotType.SPECTRA || plotType == PlotType.SPECTROGRAM) {
			try {
				minFreq = component.getDouble("spminf");
			} catch (Valve3Exception ex ) {
				minFreq = 0.0;
			}
			try {
				maxFreq = component.getDouble("spmaxf");
			} catch (Valve3Exception ex ) {
				maxFreq = 15.0;
			}
			if (minFreq < 0 || maxFreq <= 0 || minFreq >= maxFreq)
				throw new Valve3Exception("Illegal minimum/maximum frequencies: " + minFreq + " and " + maxFreq);
		}
	}

	/**
	 * Gets binary data from VDX, performs filtering if needed
	 * @param component PlotComponent
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
				if ( forExport ) {
					String toadd = "#sr=" + data.getSamplingRate() + "\n#datatype=" + data.getDataType() + "\n";
					csvHdrs.insert( 0, toadd );
					samplingRate = data.getSamplingRate();
					dataType = data.getDataType();
				}
				data.setStartTime(data.getStartTime() + component.getOffset(startTime));
				gotData = true;
				data.handleBadData();
				if (doDespike) { data.despike(despikePeriod); }
				if (doDetrend) { data.detrend(); }
				if (filterPick != 0) {
					switch(filterPick) {
						case 1: // Bandpass
							FilterType ft = FilterType.BANDPASS;
							Double singleBand = 0.0;
							if ( !Double.isNaN(filterMax) ) {
								if ( filterMax <= 0 )
									throw new Valve3Exception("Illegal max hertz value.");
							} else {
								ft = FilterType.HIGHPASS;
								singleBand = filterMin;
							}
							if ( !Double.isNaN(filterMin) ) {
								if ( filterMin <= 0 )
									throw new Valve3Exception("Illegal min hertz value.");
							} else {
								ft = FilterType.LOWPASS;
								singleBand = filterMax;
							}
							Butterworth bw = new Butterworth();
							if ( ft == FilterType.BANDPASS )
								bw.set(ft, 4, data.getSamplingRate(), filterMin, filterMax);
							else
								bw.set(ft, 4, data.getSamplingRate(), singleBand, 0);
							data.filter(bw, true);
							break;
						case 2: // Running median
							data.set2median( filterPeriod );
							break;
						case 3: // Running mean
							data.set2mean( filterPeriod );
							break;
					}
				}
				if (debiasPick != 0 ) {
					int bias = 0;
					Double dbias;
					switch ( debiasPick ) {
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
					}
					data.subtract(bias);
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
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @param channel Channel
	 * @param wave SliceWave
	 * @param displayCount ?
	 * @param dh display height
	 * @throws Valve3Exception
	 */
	private void plotWaveform(Valve3Plot v3Plot, PlotComponent component, Channel channel, SliceWave wave, int displayCount, int dh) throws Valve3Exception {
		if ( v3Plot != null )
			v3Plot.setWaveform( true );
		double timeOffset = component.getOffset(startTime);
		SliceWaveExporter wr = new SliceWaveExporter();
		wr.xTickMarks = this.xTickMarks;
		wr.xTickValues = this.xTickValues;
		wr.xUnits = this.xUnits;
		wr.xLabel = this.xLabel;
	    wr.yTickMarks = this.yTickMarks;
	    wr.yTickValues = this.yTickValues;
	    if(yLabel){
			wr.setYLabelText(channel.getCode());
		}
		wr.setWave(wave);
		wr.setViewTimes(startTime+timeOffset, endTime+timeOffset , component.getTimeZone().getID());
		if(yLabel){
			wr.setYLabelText(channel.getCode());
		}
		if ( forExport ) {
			if ( inclTime )
				csvHdrs.append(",");
			csvHdrs.append(channel.getCode().replace('$', '_').replace(',', '/'));
			csvHdrs.append("_Count");
			ExportData ed = new ExportData( csvIndex, wr );
			csvIndex++;
			csvData.add( ed );
			return;
		}
		
		wr.setRemoveBias(removeBias);
		wr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
		Color color = component.getColor();
		if(color != null){
			wr.setColor(color);
		}
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
		if (displayCount + 1 == compCount) {
				wr.update();
		}
		if(isDrawLegend){
			channelLegendsCols	= new String  [1];
			channelLegendsCols[0] = channel.getName() + " " + (filterType==null?"":"("+filterType.name()+")");
			wr.createDefaultLegendRenderer(channelLegendsCols);
		}
		component.setTranslation(wr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		v3Plot.getPlot().addRenderer(wr);
		v3Plot.addComponent(component);
	}
	
	/**
	 * Initialize SpectraRenderer and add it to plot
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @param channel Channel
	 * @param wave SliceWave
	 * @param displayCount ?
	 * @param dh display height
	 * @throws Valve3Exception
	 */
	private void plotSpectra(Valve3Plot v3Plot, PlotComponent component, Channel channel, SliceWave wave, int displayCount, int dh) throws Valve3Exception {
		SpectraRenderer sr = new SpectraRenderer();
		sr.xTickMarks = this.xTickMarks;
		sr.xTickValues = this.xTickValues;
		sr.xUnits = this.xUnits;
		sr.xLabel = this.xLabel;
	    sr.yTickMarks = this.yTickMarks;
	    sr.yTickValues = this.yTickValues;
		sr.setWave(wave);
		sr.setAutoScale(true);
		sr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
		sr.setLogPower(logPower);
		sr.setLogFreq(logFreq);
		sr.setMinFreq(minFreq);
		sr.setMaxFreq(maxFreq);
		sr.setColor(component.getColor());
		if(yLabel){
			sr.setYLabelText(channel.getCode());
		}
		if(yUnits){
			sr.setYUnitText("Power");
		}
		sr.update(0);
		if(isDrawLegend){
			channelLegendsCols	= new String  [1];
			channelLegendsCols[0] = channel.getName() + " " + (filterType==null?"":"("+filterType.name()+")");
			sr.createDefaultLegendRenderer(channelLegendsCols);
		}
		component.setTranslation(sr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("xy");
		v3Plot.getPlot().addRenderer(sr);
		v3Plot.addComponent(component);
	}

	/**
	 * Initialize SpectrogramRenderer and add it to plot
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @param channel Channel
	 * @param wave SliceWave
	 * @param displayCount ?
	 * @param dh display height
	 */
	private void plotSpectrogram(Valve3Plot v3Plot, PlotComponent component, Channel channel, SliceWave wave, int displayCount, int dh) {
		double timeOffset = component.getOffset(startTime);
		SpectrogramRenderer sr = new SpectrogramRenderer(wave);
		sr.setLocation(component.getBoxX(), component.getBoxY() + displayCount * dh + 8, component.getBoxWidth(), dh - 16);
		sr.setOverlap(0);
		sr.setLogPower(logPower);
		sr.setViewStartTime(startTime+timeOffset);
		sr.setViewEndTime(endTime+timeOffset);
		sr.setTimeZone(component.getTimeZone().getID());
		sr.setMinFreq(minFreq);
		sr.setMaxFreq(maxFreq);
		sr.xTickMarks = this.xTickMarks;
		sr.xTickValues = this.xTickValues;
		sr.xUnits = this.xUnits;
		sr.xLabel = this.xLabel;
	    sr.yTickMarks = this.yTickMarks;
	    sr.yTickValues = this.yTickValues;
	    if(yUnits){
	    	sr.setYUnitText("Frequency (Hz)");
	    }
	    if(yLabel){
			sr.setYLabelText(channel.getCode());
		}
		sr.update(0);
		if(isDrawLegend){
			channelLegendsCols	= new String  [1];
			channelLegendsCols[0] = channel.getName() + " " + (filterType==null?"":"("+filterType.name()+")");
			sr.createDefaultLegendRenderer(channelLegendsCols);
		}
		component.setTranslation(sr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		v3Plot.getPlot().addRenderer(sr);
		v3Plot.addComponent(component);
	}
	
	 /**
	 * If v3Plot is null, prepare data for exporting
	 * Otherwise, Loop through the list of channels and create plots
	 * @param v3p Valve3Plot
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3Plot, PlotComponent component) throws Valve3Exception {

		if ( forExport )
			switch(plotType) {
				case WAVEFORM:
					break;
				case SPECTRA:
					throw new Valve3Exception( "Spectra cannot be exported" );
				case SPECTROGRAM:
					throw new Valve3Exception( "Spectrograms cannot be exported" );
			}
		
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
				if ( !forExport ) {
					addSuppData( vdxSource, vdxClient, v3Plot, component );
					v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Waveform");
				}
				break;
			case SPECTRA:
				v3Plot.setExportable( false ); // Can't export vectors
				v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Spectra");
				break;
			case SPECTROGRAM:
				v3Plot.setExportable( false ); // Can't export vectors
				v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Spectrogram");
				break;
		}
	}

	/**
	 * Concrete realization of abstract method. 
	 * Generate PNG image to file with random name.
	 * If v3p is null, prepare data for export -- assumes csvData, csvData & csvIndex initialized
	 * @param v3p Valve3Plot
	 * @param comp PlotComponent
	 * @throws Valve3Exception
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception {
		forExport = (v3p == null);	// = "prepare data for export"
		channelsMap	= getChannels(vdxSource, vdxClient);
		getInputs(comp);
		getData(comp);
		
		plotData(v3p, comp);
		
		if ( !forExport ) {
			Plot plot = v3p.getPlot();
			plot.setBackgroundColor(Color.white);
			plot.writePNG(v3p.getLocalFilename());
		}
	}
	
	/**
	 * Yield the sample rate
	 * @return sample rate
	 */
	public double getSampleRate() {
		return samplingRate;
	}
	
	/**
	 * Yield the data type
	 * @return data type
	 */
	public String getDataType() {
		return dataType;
	}
	
}