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
 * Generate images of waveforms, spectras, and spectrograms 
 * from raw wave data from vdx source
 *  
 * $Log: not supported by cvs2svn $
 * Revision 1.23  2008/04/11 22:06:08  tparker
 * time wave plots on data rather than inputs
 *
 * Revision 1.22  2008/04/11 21:57:09  tparker
 * time wave plots on data rather than inputs
 *
 * Revision 1.21  2007/02/01 20:26:21  tparker
 * correct axis labeling
 *
 * Revision 1.20  2007/01/30 21:53:56  dcervelli
 * Changed spectra translation type from ty to xy.
 *
 * Revision 1.19  2006/10/11 00:47:43  tparker
 * suppressed double y-axis label
 *
 * Revision 1.18  2006/04/13 22:33:10  dcervelli
 * Scale options.
 *
 * Revision 1.17  2006/04/09 18:19:36  dcervelli
 * VDX type safety changes.
 *
 * Revision 1.16  2006/03/14 00:58:39  tparker
 * revisit previous label fix
 *
 * Revision 1.15  2006/03/14 00:41:05  tparker
 * Fix missing label bug
 *
 * Revision 1.14  2006/02/14 18:04:42  tparker
 * Check for null wave data prior to setting it's time.
 *
 * Revision 1.13  2006/01/27 22:54:11  tparker
 * undo unintentional change to spectra
 *
 * Revision 1.12  2006/01/27 22:39:53  tparker
 * set default color
 *
 * Revision 1.11  2006/01/27 22:35:33  tparker
 * set default color
 *
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
	
	/**
	 * Default constructor
	 */
	public WavePlotter()
	{}

	/**
	 * Gets binary data from VDX, performs filtering if needed
	 * @throws Valve3Exception
	 */
	public void getData() throws Valve3Exception
	{
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", vdxSource);
		params.put("selector", channel);
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));

		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		Wave w = (Wave)client.getBinaryData(params);
		pool.checkin(client);
		
		if (w == null)
			throw new Valve3Exception("No data available for " + channel + ".");
		
		w.setStartTime(w.getStartTime() + Valve3.getInstance().getTimeZoneOffset() * 60 * 60);
		
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
		wave.setSlice(w.getStartTime(), w.getEndTime());
		startTime += Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
		endTime += Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
	}
	
	/**
	 * Initialize internal data from PlotComponent component
	 * @throws Valve3Exception
	 */
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
		catch (Exception e){}
		
		xLabel = 1;
		try
		{
			xLabel = Integer.parseInt(component.get("xLabel"));
		}
		catch (Exception e){}

		labels = 0;
		try
		{
			labels = Integer.parseInt(component.get("labels"));
		}
		catch (Exception e){}
		
		color = component.get("color");
		if (color == null)
			color = "A";
	}
	
	/**
	 * Initialize SliceWaveRenderer and add it to plot
	 * @throws Valve3Exception
	 */
	private void plotWaveform() throws Valve3Exception
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
		
		double max = wave.max() - bias;
		double min = wave.min() - bias;
		if (component.isAutoScale("ys"))
		{
			wr.setMinY(min);
			wr.setMaxY(max);
		}
		else
		{
			double[] ys = component.getYScale("ys", min, max);
			double yMin = ys[0];
			double yMax = ys[1];
			if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
				throw new Valve3Exception("Illegal axis values.");

			wr.setMinY(yMin);
			wr.setMaxY(yMax);
		}
		
		if (labels == 1)
		{
			//wr.setDisplayLabels(false);
			wr.setYLabel("");
			wr.update();
		} 
		else 
		{			
			wr.update();
			wr.getAxis().setBottomLeftLabelAsText("Time(" + Valve3.getInstance().getTimeZoneAbbr()+ ")");
		}

		component.setTranslation(wr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		v3Plot.getPlot().addRenderer(wr);
	}
	
	/**
	 * Initialize SpectraRenderer and add it to plot
	 * @throws Valve3Exception
	 */
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
		sr.update(0);
		
		component.setTranslation(sr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("xy");
		v3Plot.getPlot().addRenderer(sr);
	}

	/**
	 * Initialize SpectrogramRenderer and add it to plot
	 * @throws Valve3Exception
	 */
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
		if (yLabel == 1) 
		{
			yTick = 8;
			yString = "";
			//yString = "Frequency (Hz)";
			
		}
		else if (yLabel == 2) 
		{
			yTick = 5;
			yString = channel.substring(0, channel.indexOf("$"));
			sr.setYAxisLabel("");
			sr.update(0);
		}
		
		int xTick = 0;
		String xString = "";
		if (xLabel == 1) 
		{
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

	/**
	 * Concrete realization of abstract method. 
	 * Generate PNG image to file with random name.
	 * @see Plotter
	 */
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
	
	/**
	 * @return CSV dump of binary data described by given PlotComponent
	 */
	public String toCSV(PlotComponent comp) throws Valve3Exception
	{
		component = comp;
		getInputs();
		getData();
		return wave.toCSV();
	}
}