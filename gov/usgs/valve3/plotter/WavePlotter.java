package gov.usgs.valve3.plotter;

import gov.usgs.math.Butterworth;
import gov.usgs.math.Butterworth.FilterType;
import gov.usgs.plot.Plot;
import gov.usgs.plot.PlotException;
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
	protected void getInputs(PlotComponent comp) throws Valve3Exception {
		
		parseCommonParameters(comp);
		if (endTime - startTime > MAX_DATA_REQUEST)
			throw new Valve3Exception("Maximum waveform request is 24 hours.");
		
		String pt = comp.get("plotType");
		if ( pt == null )
			plotType = PlotType.WAVEFORM;
		else {
			plotType	= PlotType.fromString(pt);
			if (plotType == null) {
				throw new Valve3Exception("Illegal plot type: " + pt);
			}
		}
		
		validateDataManipOpts(comp);
		
		String ft = comp.get("ftype");
		if(ft!=null){
			if(!(ft.equals("L") || ft.equals("H") || ft.equals("B") || ft.equals("N"))){
				throw new Valve3Exception("Illegal filter type: " + ft);
			}
			filterType = FilterType.fromString(ft);
		}
		
		try{
			logPower = comp.getBoolean("splp");
		} catch (Valve3Exception ex){
			logPower = true;
		}
		
		try{
			logFreq = comp.getBoolean("splf");
		} catch (Valve3Exception ex){
			logFreq = false;
		}
		
		if (plotType == PlotType.SPECTRA || plotType == PlotType.SPECTROGRAM) {
			try {
				nfft = comp.getInt("nfft");
			} catch (Valve3Exception ex ) {
				nfft = 0;
			}
			
			try {
				binSize = comp.getInt("binSize");
			} catch (Valve3Exception ex ) {
				binSize = 256;
			}
			
			try {
				overlap = comp.getDouble("overlap");
			} catch (Valve3Exception ex ) {
				overlap = 0.859375;
			}
			
			if (overlap < 0.0 || overlap > 1.0)
				throw new Valve3Exception("Illegal overlap: " + overlap + " must be between 0 and 1");
			
			try {
				minPower = comp.getInt("minPower");
			} catch (Valve3Exception ex ) {
				minPower = 20;
			}
			
			try {
				maxPower = comp.getInt("maxPower");
			} catch (Valve3Exception ex ) {
				maxPower = 120;
			}
			
			if (minPower >= maxPower) 
				throw new Valve3Exception("Illegal minimum/maximum power: " + minPower + " and " + maxPower);
			
			try {
				minFreq = comp.getDouble("spminf");
			} catch (Valve3Exception ex ) {
				minFreq = 0.0;
			}
			
			try {
				maxFreq = comp.getDouble("spmaxf");
			} catch (Valve3Exception ex ) {
				maxFreq = 20.0;
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
	protected void getData(PlotComponent comp) throws Valve3Exception {
		
		// initialize variables
		boolean gotData			= false;
		boolean exceptionThrown	= false;
		String exceptionMsg		= "";
		Pool<VDXClient> pool	= null;
		VDXClient client		= null;
		channelDataMap			= new LinkedHashMap<Integer, SliceWave>();
		String[] channels		= ch.split(",");
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		
		// checkout a connection to the database
		pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		if (pool != null) {
			client	= pool.checkout();
		
			// iterate through each of the selected channels and place the data in the map
			for (String channel : channels) {
				params.put("ch", channel);
				Wave data = null;
				try {
					data = (Wave)client.getBinaryData(params);
				} catch (UtilException e) {
					exceptionThrown	= true;
					exceptionMsg	= e.getMessage();
					break;
				} catch (Exception e) {
					data = null; 
				}
				
				// if data was collected
				if (data != null) {
					if ( forExport ) {
						samplingRate = data.getSamplingRate();
						if ( inclTime )
							dataType = data.getDataType();
						else
							dataType = "i4";
						String toadd = "#sr=" + samplingRate + "\n#datatype=" + dataType + "\n";
						csvHdrs.insert( 0, toadd );
					}
					data.setStartTime(data.getStartTime() + timeOffset);
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
			
			// check back in our connection to the database
			pool.checkin(client);
		}
		
		// if a data limit message exists, then throw exception
		if (exceptionThrown) {
			throw new Valve3Exception(exceptionMsg);

		// if no data exists, then throw exception
		} else if (channelDataMap.size() == 0 || !gotData) {
			throw new Valve3Exception("No data for any channel.");
		}
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
	private void plotWaveform(Valve3Plot v3p, PlotComponent comp, Channel channel, SliceWave wave, int currentComp, int compBoxHeight) throws Valve3Exception {
		
		double yMin = 1E300;
		double yMax = -1E300;
		if (comp.isAutoScale("ysL")) {
			yMin	= wave.min();
			yMax	= wave.max();
		} else {
			double[] ys = comp.getYScale("ysL", yMin, yMax);
			yMin 	= ys[0];
			yMax 	= ys[1];
			if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
				throw new Valve3Exception("Illegal axis values.");
		}

		SliceWaveExporter wr	= new SliceWaveExporter();
		
		if (currentComp == compCount) {
			wr.xTickMarks			= this.xTickMarks;
			wr.xTickValues			= this.xTickValues;
			wr.xUnits				= this.xUnits;
			wr.xLabel				= this.xLabel;
		} else {
			wr.xTickMarks			= this.xTickMarks;
			wr.xTickValues			= false;
			wr.xUnits				= false;
			wr.xLabel				= false;
		}
	    wr.yTickMarks			= this.yTickMarks;
	    wr.yTickValues			= this.yTickValues;
		wr.setWave(wave);
		wr.setLocation(comp.getBoxX(), comp.getBoxY() + (currentComp - 1) * compBoxHeight, comp.getBoxWidth(), compBoxHeight - 16);
		wr.setViewTimes(startTime+timeOffset, endTime+timeOffset , timeZoneID);		
		wr.setMinY(yMin);
		wr.setMaxY(yMax);
		wr.setColor(comp.getColor());		
		if(yLabel){
			wr.setYLabelText(channel.getName());
		}		
		wr.update();		
		if (isDrawLegend) {
			channelLegendsCols	= new String  [1];
			channelLegendsCols[0] = channel.getName() + " " + (filterType==null?"":"("+filterType.name()+")");
			wr.createDefaultLegendRenderer(channelLegendsCols);
		}

		if ( forExport ) {
			if ( inclTime )
				csvHdrs.append(",");
			csvHdrs.append(channel.getCode().replace('$', '_').replace(',', '/'));
			scnl = channel.getCode().split("[$]");
			csvHdrs.append("_Count");
			ExportData ed = new ExportData( csvIndex, wr );
			csvIndex++;
			csvData.add( ed );
			
		} else {		
			comp.setTranslation(wr.getDefaultTranslation(v3p.getPlot().getHeight()));
			comp.setTranslationType("ty");
			v3p.getPlot().addRenderer(wr);
			v3p.addComponent(comp);
		}
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
	private void plotSpectra(Valve3Plot v3p, PlotComponent comp, Channel channel, SliceWave wave, int currentComp, int compBoxHeight) throws Valve3Exception {
		
		SpectraRenderer spectraRenderer	= new SpectraRenderer();
		
		if (currentComp == compCount) {
			spectraRenderer.xTickMarks			= this.xTickMarks;
			spectraRenderer.xTickValues			= this.xTickValues;
			spectraRenderer.xUnits				= this.xUnits;
			spectraRenderer.xLabel				= this.xLabel;
		} else {
			spectraRenderer.xTickMarks			= this.xTickMarks;
			spectraRenderer.xTickValues			= false;
			spectraRenderer.xUnits				= false;
			spectraRenderer.xLabel				= false;
		}
	    spectraRenderer.yTickMarks		= this.yTickMarks;
	    spectraRenderer.yTickValues		= this.yTickValues;
	    System.out.printf("isDrawLegend: %s, WavePlotter: logPower: %s, logFreq: %s, minFreq: %f, maxFreq: %f\n",isDrawLegend, logPower, logFreq, minFreq,maxFreq);
		spectraRenderer.setWave(wave);
		spectraRenderer.setAutoScale(true);
		spectraRenderer.setLocation(comp.getBoxX(), comp.getBoxY() + (currentComp - 1) * compBoxHeight, comp.getBoxWidth(), compBoxHeight - 16);
		spectraRenderer.setLogPower(logPower);
		spectraRenderer.setLogFreq(logFreq);
		spectraRenderer.setMinFreq(minFreq);
		spectraRenderer.setMaxFreq(maxFreq);
		spectraRenderer.setColor(comp.getColor());
		if(yLabel){
			spectraRenderer.setYLabelText(channel.getName());
		}
		if(yUnits){
			spectraRenderer.setYUnitText("Power");
		}
		spectraRenderer.update();

		if (isDrawLegend) {
			channelLegendsCols	= new String  [1];
			channelLegendsCols[0] = channel.getName() + " " + (filterType==null?"":"("+filterType.name()+")");
			spectraRenderer.createDefaultLegendRenderer(channelLegendsCols);
		}
		
		comp.setTranslation(spectraRenderer.getDefaultTranslation(v3p.getPlot().getHeight()));
		comp.setTranslationType("xy");
		v3p.getPlot().addRenderer(spectraRenderer);
		v3p.addComponent(comp);
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
	private void plotSpectrogram(Valve3Plot v3p, PlotComponent comp, Channel channel, SliceWave wave, int currentComp, int compBoxHeight) {
		SpectrogramRenderer spectrogramRenderer	= new SpectrogramRenderer(wave);
		if (currentComp == compCount) {
			spectrogramRenderer.xTickMarks			= this.xTickMarks;
			spectrogramRenderer.xTickValues			= this.xTickValues;
			spectrogramRenderer.xUnits				= this.xUnits;
			spectrogramRenderer.xLabel				= this.xLabel;
		} else {
			spectrogramRenderer.xTickMarks			= this.xTickMarks;
			spectrogramRenderer.xTickValues			= false;
			spectrogramRenderer.xUnits				= false;
			spectrogramRenderer.xLabel				= false;
		}
	    spectrogramRenderer.yTickMarks			= this.yTickMarks;
	    spectrogramRenderer.yTickValues			= this.yTickValues;
		spectrogramRenderer.setLocation(comp.getBoxX(), comp.getBoxY() + (currentComp - 1) * compBoxHeight, comp.getBoxWidth(), compBoxHeight - 16);
		spectrogramRenderer.setOverlap(0);
		spectrogramRenderer.setLogPower(logPower);
		spectrogramRenderer.setViewStartTime(startTime+timeOffset);
		spectrogramRenderer.setViewEndTime(endTime+timeOffset);
		spectrogramRenderer.setTimeZone(timeZoneID);
		spectrogramRenderer.setMinFreq(minFreq);
		spectrogramRenderer.setMaxFreq(maxFreq);
	    spectrogramRenderer.setNfft(nfft);
	    spectrogramRenderer.setBinSize(binSize);
		spectrogramRenderer.setOverlap(overlap);
		spectrogramRenderer.setMaxPower(maxPower);
		spectrogramRenderer.setMinPower(minPower);
		
	    if(yUnits){
	    	spectrogramRenderer.setYUnitText("Frequency (Hz)");
	    }
	    if(yLabel){
			spectrogramRenderer.setYLabelText(channel.getName());
		}
		
		if(isDrawLegend){
			channelLegendsCols	= new String  [1];
			channelLegendsCols[0] = channel.getName() + " " + (filterType==null?"":"("+filterType.name()+")");
			spectrogramRenderer.createDefaultLegendRenderer(channelLegendsCols);
		}
		
		spectrogramRenderer.update();
		
		comp.setTranslation(spectrogramRenderer.getDefaultTranslation(v3p.getPlot().getHeight()));
		comp.setTranslationType("ty");
		v3p.getPlot().addRenderer(spectrogramRenderer);
		v3p.addComponent(comp);
	}
	
	 /**
	 * If v3Plot is null, prepare data for exporting
	 * Otherwise, Loop through the list of channels and create plots
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception {
		
		if (forExport)
			switch(plotType) {
				case WAVEFORM:
					break;
				case SPECTRA:
					throw new Valve3Exception( "Spectra cannot be exported" );
				case SPECTROGRAM:
					throw new Valve3Exception( "Spectrograms cannot be exported" );
			}
		
		// calculate the number of plot components that will be displayed per channel
		int channelCompCount = 1;
		
		// total components is components per channel * number of channels
		compCount = channelCompCount * channelDataMap.size();
		
		// setting up variables to decide where to plot this component
		int currentComp		= 1;
		int compBoxHeight	= comp.getBoxHeight();
		
		for (int cid : channelDataMap.keySet()) {
			
			// get the relevant information for this channel
			Channel channel	= channelsMap.get(cid);
			SliceWave wave	= channelDataMap.get(cid);			
			
			// if there is no data for this channel, then resize the plot window 
			if (wave == null) {
				v3p.setHeight(v3p.getHeight() - channelCompCount * compBoxHeight);
				Plot plot	= v3p.getPlot();
				plot.setSize(plot.getWidth(), plot.getHeight() - channelCompCount * compBoxHeight);
				compCount = compCount - channelCompCount;
				continue;
			}
			
			switch(plotType) {
				case WAVEFORM:
					plotWaveform(v3p, comp, channel, wave, currentComp, compBoxHeight);
					break;
				case SPECTRA:
					plotSpectra(v3p, comp, channel, wave, currentComp, compBoxHeight);
					break;
				case SPECTROGRAM:
					plotSpectrogram(v3p, comp, channel, wave, currentComp, compBoxHeight);
					break;
			}
			currentComp++;
		}
		switch(plotType) {
			case WAVEFORM:
				if (!forExport) {
					addSuppData( vdxSource, vdxClient, v3p, comp );
					if(channelDataMap.size()!=1){
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
					v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Spectrogram");
				}
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
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception, PlotException {
		
		forExport	= (v3p == null);
		channelsMap	= getChannels(vdxSource, vdxClient);
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
		}
		
		// this is a legitimate request so lookup the data from the database and plot it
		getData(comp);		
		plotData(v3p, comp);
		
		if (!forExport) {
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