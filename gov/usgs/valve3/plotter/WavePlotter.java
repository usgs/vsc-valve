package gov.usgs.valve3.plotter;

import gov.usgs.math.Butterworth;
import gov.usgs.math.FFT;
import gov.usgs.math.Butterworth.FilterType;
import gov.usgs.plot.Data;
import gov.usgs.plot.DataRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.util.Pool;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.PlotHandler;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.wave.SliceWave;
import gov.usgs.vdx.data.wave.Wave;
import gov.usgs.vdx.data.wave.plot.SliceWaveRenderer;
import gov.usgs.vdx.data.wave.plot.SpectrogramRenderer;

import java.awt.Color;
import java.io.File;
import java.util.HashMap;

/**
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.2  2005/09/03 19:02:09  dcervelli
 * Changes for Butterworth.
 *
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
public class WavePlotter extends Plotter
{
	private enum PlotType
	{
		WAVEFORM, SPECTRA, SPECTROGRAM;
		
		public static PlotType fromString(String s)
		{
			if (s == null)
				return null;
			
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
	private String channel;
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
	
	public WavePlotter()
	{}

	public void getData() throws Valve3Exception
	{
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", vdxSource);
		params.put("selector", channel);
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));

		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		Wave w = (Wave)client.getData(params);
		pool.checkin(client);
		
		if (w == null)
			throw new Valve3Exception("No data available for " + channel + ".");
		
		if (filterType != null)
		{
			Butterworth bw = new Butterworth();
			switch(filterType)
			{
			case LOWPASS:
				if (maxHz <= 0)
					throw new Valve3Exception("Illegal max hertz value.");
				bw.set(FilterType.LOWPASS, 4, w.getSamplingRate(), maxHz, 0);
				w.filter(bw, true);
				break;
			case HIGHPASS:
				if (minHz <= 0)
					throw new Valve3Exception("Illegal minimum hertz value.");
				bw.set(FilterType.HIGHPASS, 4, w.getSamplingRate(), minHz, 0);
				w.filter(bw, true);
				break;
			case BANDPASS:
				if (minHz <= 0 || maxHz <= 0 || minHz > maxHz)
					throw new Valve3Exception("Illegal minimum/maximum hertz values.");
				bw.set(FilterType.BANDPASS, 4, w.getSamplingRate(), minHz, maxHz);
				w.filter(bw, true);
				break;
			}
		}
		
		wave = new SliceWave(w);
		wave.setSlice(startTime, endTime);
	}
	
	public void getInputs() throws Valve3Exception
	{
		channel  = component.get("ch");
		if (channel == null || channel.indexOf(";") != -1)
			throw new Valve3Exception("Illegal channel name.");

//		 TODO: move time checking to static method in Plotter
		endTime = component.getEndTime();
		if (Double.isNaN(endTime))
			throw new Valve3Exception("Illegal end time.");
		startTime = component.getStartTime(endTime);
		if (Double.isNaN(startTime))
			throw new Valve3Exception("Illegal start time.");

		plotType = PlotType.fromString(component.get("type"));
		if (plotType == null)
			throw new Valve3Exception("Illegal plot type.");
		
		removeBias = false;
		String clip = component.get("rb");
		if (clip != null && clip.toUpperCase().equals("T"))
			removeBias = true;
		
		filterType = FilterType.fromString(component.get("ftype"));
		minHz = -1;
		maxHz = -1;
		try
		{
			minHz = Double.parseDouble(component.get("fminhz"));
			maxHz = Double.parseDouble(component.get("fmaxhz"));
		}
		catch (Exception e) {}
		
		minFreq = -1;
		maxFreq = -1;
		try
		{
			minFreq = Double.parseDouble(component.get("spminf"));
			maxFreq = Double.parseDouble(component.get("spmaxf"));
		}
		catch (Exception e) {}
		
		logPower = false;
		String lp = component.get("splp");
		if (lp != null && lp.toUpperCase().equals("T"))
			logPower = true;
		
		logFreq = false;
		String lf = component.get("splf");
		if (lf != null && lf.toUpperCase().equals("T"))
			logFreq = true;
		
		if (plotType == PlotType.SPECTRA || plotType == PlotType.SPECTROGRAM)
		{
			if (minFreq < 0 || maxFreq <= 0 || minFreq >= maxFreq)
				throw new Valve3Exception("Illegal minimum/maximum frequencies.");
		}
	}
	
	private void plotWaveform()
	{
		SliceWaveRenderer wr = new SliceWaveRenderer();
		wr.setRemoveBias(removeBias);
		wr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		wr.setWave(wave);
		wr.setViewTimes(startTime, endTime);
		double bias = 0;
		if (removeBias)
			bias = wave.mean();
		wr.setMinY(wave.min() - bias);
		wr.setMaxY(wave.max() - bias);
		wr.update();
		component.setTranslation(wr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		v3Plot.getPlot().addRenderer(wr);
	}
	
	private void plotSpectra()
	{
		double[][] data = wave.fft();
		data = FFT.halve(data);
		FFT.toPowerFreq(data, wave.getSamplingRate(), logPower, logFreq);
		if (logFreq)
		{
			if (minFreq == 0)
				minFreq = data[3][0];
			else
				minFreq = Math.log(minFreq) / FFT.LOG10;
			maxFreq = Math.log(maxFreq) / FFT.LOG10;
		}
		double maxp = -1E300;
		double minp = 1E300;
		for (int i = 2; i < data.length; i++)
		{
			if (data[i][0] >= minFreq && data[i][0] <= maxFreq)
			{
				if (data[i][1] > maxp)
					maxp = data[i][1];
				if (data[i][1] < minp)
					minp = data[i][1];
			}
		}
		
		Data d = new Data(data);
		DataRenderer dr = new DataRenderer(d);
		dr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());

		if (logPower)
			maxp = Math.pow(10, maxp);
		
		if (logPower)
			maxp = Math.log(maxp) / Math.log(10);
		dr.setExtents(minFreq, maxFreq, 0, maxp);
		dr.createDefaultAxis(8, 8, false, false);
		if (logFreq)
			dr.createDefaultLogXAxis(5);	
		if (logPower)
			dr.createDefaultLogYAxis(2);
			
		dr.createDefaultLineRenderers();
		dr.getAxis().setLeftLabelAsText("Power");
		dr.getAxis().setBottomLabelAsText("Frequency (Hz)");
		
		component.setTranslation(dr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("xy");
		v3Plot.getPlot().addRenderer(dr); 	
	}
	
	private void plotSpectrogram()
	{
		SpectrogramRenderer sr = new SpectrogramRenderer(wave);
		//plot.setFrameRendererLocation(sr);
		sr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		sr.setOverlap(0);
		sr.setLogPower(logPower);
		sr.setViewStartTime(startTime);
		sr.setViewEndTime(endTime);
		sr.setMinFreq(minFreq);
		sr.setMaxFreq(maxFreq);
		//sr.setFftSize();
		//sr.setTimeZoneOffset(Valve.getTimeZoneAdj());
		sr.update(0);
		sr.createDefaultAxis(8, 8, false, false);
		sr.setXAxisToTime(8);
		sr.getAxis().setLeftLabelAsText("Power");
		sr.getAxis().setBottomLabelAsText("Time");// (Data from " + Valve.DATE_FORMAT.format(Util.j2KToDate(time1 + Valve.getTimeZoneAdj())) +
//				" to " + Valve.DATE_FORMAT.format(Util.j2KToDate(time2 + Valve.getTimeZoneAdj())) + ")");
		component.setTranslation(sr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		v3Plot.getPlot().addRenderer(sr);
	}

	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception
	{
		v3Plot = v3p;
		component = comp;
		getInputs();
		getData();
		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		
		switch(plotType)
		{
			case WAVEFORM:
				plotWaveform();
				break;
			case SPECTRA:
				plotSpectra();
				break;
			case SPECTROGRAM:
				plotSpectrogram();
				break;
		}

		v3Plot.setFilename(PlotHandler.getRandomFilename());
		plot.writePNG(Valve3.getInstance().getApplicationPath() + File.separatorChar + v3Plot.getFilename());
		v3Plot.addComponent(component);
		String ch = channel.replace('$', ' ').replace('_', ' ');
		v3Plot.setTitle("Wave: " + ch);
	}
}
