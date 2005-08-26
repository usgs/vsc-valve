package gov.usgs.valve3.plotter;

import gov.usgs.math.Butterworth;
import gov.usgs.plot.Plot;
import gov.usgs.util.Pool;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.PlotHandler;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.wave.Wave;
import gov.usgs.vdx.data.wave.plot.WaveRenderer;

import java.awt.Color;
import java.io.File;
import java.util.HashMap;

/**
 * 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class WavePlotter extends Plotter
{
	public WavePlotter()
	{}

	private void plotWaveform(Valve3Plot v3Plot, PlotComponent component, Wave w)
	{
		boolean removeBias = component.get("rb").equals("T");
		Wave wave = new Wave(w);
		WaveRenderer wr = new WaveRenderer();
		wr.setRemoveBias(removeBias);
		wr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		wr.setWave(wave);
		double end = component.getEndTime();
		double start = component.getStartTime(end);
		wr.setViewTimes(start, end);
		double bias = 0;
		if (removeBias)
			bias = w.mean();
		wr.setMinY(w.min() - bias);
		wr.setMaxY(w.max() - bias);
		wr.update();
		component.setTranslation(wr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		v3Plot.getPlot().addRenderer(wr);
	}
	
	/*
	private void plotSpectra(Valve3Plot v3Plot, PlotComponent component, Wave w)
	{
//		double minFreq = 0;
//		double maxFreq = 15.0;
		double minFreq = Double.parseDouble(component.get("spminf"));
		double maxFreq = Double.parseDouble(component.get("spmaxf"));
		boolean logFreq = false;
		double[][] data = w.fft();
		data = FFT.halve(data);
		FFT.toPowerFreq(data, w.getSamplingRate(), logFreq);
		double maxPower = -1E300;
		for (int i = 2; i < data.length; i++)
		{
			if (data[i][0] >= minFreq && data[i][0] <= maxFreq && data[i][1] > maxPower)
			{
				maxPower = data[i][1];
			}
		}
		Data d = new Data(data);
		DataRenderer dr = new DataRenderer(d);
		dr.setUnit("frequency");
		dr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());

		dr.setExtents(minFreq, maxFreq, 0.0, logFreq ? maxPower : maxPower / 2);
//		dr.createDefaultAxis(plot.getDefaultXTicks(), plot.getDefaultYTicks(), false, true);
		dr.createDefaultAxis(8, 8, false, true);
		dr.createDefaultLineRenderers();
//		dr.createDefaultLegendRenderer(new String[] {legendString});
		dr.getAxis().setLeftLabelAsText("Power");
		dr.getAxis().setBottomLabelAsText("Frequency (Hz)");
		component.setTranslation(dr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("xy");
		v3Plot.getPlot().addRenderer(dr); 	
	}
	
	private void plotSpectrogram(Valve3Plot v3Plot, PlotComponent component, Wave w)
	{
		Wave wave = new Wave(w);
		SpectrogramRenderer sr = new SpectrogramRenderer(wave);
		//plot.setFrameRendererLocation(sr);
		sr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		sr.setOverlap(0);
//		sr.setHTicks(plot.getDefaultXTicks());
//		sr.setVTicks(plot.getDefaultYTicks());
		sr.setViewStartTime(wave.getStartTime());
		sr.setViewEndTime(wave.getEndTime());
		sr.setMinFreq(0);
		sr.setMaxFreq(15);
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
	*/
	public void plot(Valve3Plot v3Plot, PlotComponent component)
	{
		v3Plot.setFilename(PlotHandler.getRandomFilename());
		double end = component.getEndTime();
		double start = component.getStartTime(end);
		if (Double.isNaN(start) || Double.isNaN(end))
		{
			// return an error
			return;
		}
		
		String channel  = component.get("ch");
		
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", vdxSource);
		params.put("selector", channel);
		params.put("st", Double.toString(start));
		params.put("et", Double.toString(end));
//		SampledWave sw = (SampledWave)dataSource.getData(params);
		
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		Wave sw = (Wave)client.getData(params);
		pool.checkin(client);
		
		// filter
		String ft = component.get("ftype");
		if (ft != null || ft.equals("N"))
		{
			Butterworth bw = new Butterworth();
			if (ft.equals("L"))
			{
				double maxHz = Double.parseDouble(component.get("fmaxhz"));
				bw.set(Butterworth.LOWPASS, 4, sw.getSamplingRate(), maxHz, 0);
				sw.filter(bw, true);
			}
			else if (ft.equals("H"))
			{
				double minHz = Double.parseDouble(component.get("fminhz"));
				bw.set(Butterworth.HIGHPASS, 4, sw.getSamplingRate(), minHz, 0);
				sw.filter(bw, true);
			}
			else if (ft.equals("B"))
			{
				double minHz = Double.parseDouble(component.get("fminhz"));
				double maxHz = Double.parseDouble(component.get("fmaxhz"));
				bw.set(Butterworth.BANDPASS, 4, sw.getSamplingRate(), minHz, maxHz);
				sw.filter(bw, true);
			}
		}
		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		
		String outputType = component.get("type");
		if (outputType.equals("wf"))
			plotWaveform(v3Plot, component, sw);
		/*
		else if (outputType.equals("sp"))
			plotSpectra(v3Plot, component, sw);
		else if (outputType.equals("sg"))
			plotSpectrogram(v3Plot, component, sw);
			*/
		
		plot.writePNG(Valve3.getInstance().getApplicationPath() + File.separatorChar + v3Plot.getFilename());
		v3Plot.addComponent(component);
		String ch = channel.replace('$', ' ').replace('_', ' ');
		v3Plot.setTitle("Wave: " + ch);
	}
}
