package gov.usgs.valve3.plotter;

import gov.usgs.math.Butterworth;
import gov.usgs.math.Butterworth.FilterType;
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
import gov.usgs.vdx.data.wave.plot.SpectraRenderer;
import gov.usgs.vdx.data.wave.plot.SpectrogramRenderer;

import java.awt.Color;
import java.io.File;
import java.util.HashMap;

/**
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.10  2006/01/27 22:18:33  tparker
 * Add configure options for wave plotter
 *
 * Revision 1.9  2006/01/27 20:56:28  tparker
 * Add configure options for wave plotter
 *
 * Revision 1.8  2005/12/28 02:13:39  tparker
 * Add toCSV method to support raw data export
 *
 * Revision 1.7  2005/11/02 20:30:35  tparker
 * set local timezone per bug #68
 *
 * Revision 1.6  2005/10/07 19:43:21  dcervelli
 * Capped data requests at 24 hours.
 *
 * Revision 1.5  2005/10/07 16:55:18  dcervelli
 * Fixed y-axis label on spectrogram.
 *
 * Revision 1.4  2005/09/04 18:13:47  dcervelli
 * Uses new SpectraRenderer.
 *
 * Revision 1.3  2005/09/03 21:50:44  dcervelli
 * Fixed logPower/logFreq.
 *
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
	private int labels;
	private int yLabel;
	private int xLabel;
	private String color;
	
	private static final double MAX_DATA_REQUEST = 86400;
	
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
		w.setStartTime(startTime + Valve3.getInstance().getTimeZoneOffset() * 60 * 60);
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
		startTime += Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
		endTime += Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
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

		if (endTime - startTime > MAX_DATA_REQUEST)
			throw new Valve3Exception("Maximum waveform request is 24 hours.");
		
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
		
		yLabel = 1;
		try
		{
			yLabel = Integer.parseInt(component.get("yLabel"));
		}
		catch (Exception e) {}
		
		xLabel = 1;
		try
		{
			xLabel = Integer.parseInt(component.get("xLabel"));
		}
		catch (Exception e) {}
		
		color = "A";
		try
		{
			color = component.get("color");
		}
		catch (Exception e) {}

	}
	
	private void plotWaveform()
	{
		SliceWaveRenderer wr = new SliceWaveRenderer();
		wr.setRemoveBias(removeBias);
		wr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		wr.setWave(wave);
		wr.setViewTimes(startTime, endTime);
		if (color.equals("M"))
			wr.setColor(Color.BLACK);
		
		double bias = 0;
		if (removeBias)
			bias = wave.mean();
		wr.setMinY(wave.min() - bias);
		wr.setMaxY(wave.max() - bias);
		
		if (labels == 0)
		{
			wr.setDisplayLabels(false);
			wr.update();
		} else {			
			wr.update();
			wr.getAxis().setBottomLeftLabelAsText("Time(" + Valve3.getInstance().getTimeZoneAbbr()+ ")");
		}

		component.setTranslation(wr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		v3Plot.getPlot().addRenderer(wr);
	}
	
	private void plotSpectra()
	{
		SpectraRenderer sr = new SpectraRenderer();
		sr.setWave(wave);
		sr.setAutoScale(true);
		sr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		sr.setLogPower(logPower);
		sr.setLogFreq(logFreq);
		sr.setMinFreq(minFreq);
		sr.setMaxFreq(maxFreq);
		
		component.setTranslation(sr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		v3Plot.getPlot().addRenderer(sr);
	}
	
	private void plotSpectrogram()
	{
		SpectrogramRenderer sr = new SpectrogramRenderer(wave);
		sr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
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
			yString = "Frequency (Hz)";
			
		} else if (yLabel == 2) {
			yTick = 5;
			yString = channel.substring(0, channel.indexOf("$"));
		}
		
		int xTick = 0;
		String xString = "";
		if (xLabel == 1) {
			xTick = 8;
			xString = "Time(" + Valve3.getInstance().getTimeZoneAbbr()+ ")";
			
		}
		
		sr.createDefaultAxis(xTick, yTick, false, false);
		sr.getAxis().setLeftLabelAsText(yString);
		sr.setXAxisToTime(xTick);
		sr.getAxis().setBottomLabelAsText(xString);

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
	
	public String toCSV(PlotComponent comp) throws Valve3Exception
	{
		component = comp;
		getInputs();
		getData();
		return wave.toCSV();
	}

}
