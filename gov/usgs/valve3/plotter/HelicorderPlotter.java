package gov.usgs.valve3.plotter;

import gov.usgs.plot.Plot;
import gov.usgs.util.Pool;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.heli.HelicorderData;
import gov.usgs.vdx.data.heli.plot.HelicorderRenderer;
import gov.usgs.vdx.data.heli.plot.HelicorderSettings;

import java.awt.Color;
import java.util.HashMap;

/**
 * $Log: not supported by cvs2svn $
 * Revision 1.2  2005/08/29 22:54:04  dcervelli
 * Totally refactored; input validation.
 *
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
public class HelicorderPlotter extends Plotter
{
	private static final double MAX_HELICORDER_TIME = 31 * 86400;
	private HelicorderData data;
	private HelicorderSettings settings;
	private PlotComponent component;
	private Valve3Plot v3Plot;
	
	public HelicorderPlotter()
	{}
	
	public void getData()
	{
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", vdxSource);
		params.put("selector", settings.channel);
		params.put("st", Double.toString(settings.startTime));
		params.put("et", Double.toString(settings.endTime));

		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		data = (HelicorderData)client.getData(params);
		pool.checkin(client);
	}
	
	public void getInputs() throws Valve3Exception
	{
		settings = new HelicorderSettings();
		settings.channel = component.get("ch");
		if (settings.channel == null || settings.channel.length() == 0 || settings.channel.indexOf(";") != -1)
			throw new Valve3Exception("Illegal channel name.");
		
		// TODO: move time checking to static method in Plotter
		settings.endTime = component.getEndTime();
		if (Double.isNaN(settings.endTime))
			throw new Valve3Exception("Illegal end time.");
		settings.startTime = component.getStartTime(settings.endTime);
		if (Double.isNaN(settings.startTime))
			throw new Valve3Exception("Illegal start time.");
		
		if (settings.endTime - settings.startTime >= MAX_HELICORDER_TIME)
			throw new Valve3Exception("Illegal duration.");
		
		settings.showClip = false;
		String clip = component.get("sc");
		if (clip != null && clip.toUpperCase().equals("T"))
			settings.showClip = true;

		settings.timeChunk = -1;
		try { settings.timeChunk = Integer.parseInt(component.get("tc")) * 60; } catch (Exception e) {}
		if (settings.timeChunk <= 0)
			throw new Valve3Exception("Illegal time chunk.");
		
		settings.minimumAxis = false;
		String min = component.get("min");
		if (min != null && min.toUpperCase().equals("T"))
			settings.minimumAxis = true;
		
		settings.left = component.getBoxX();
		settings.top = component.getBoxY();
		settings.width = component.getBoxWidth();
		settings.height = component.getBoxHeight();
	}
	
	public void plot(Valve3Plot p, PlotComponent c) throws Valve3Exception
	{
		v3Plot = p;
		component = c;
		getInputs();
		getData();
		HelicorderRenderer heliRenderer = new HelicorderRenderer();
		heliRenderer.setData(data);
		settings.applySettings(heliRenderer, data);
		
		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		plot.addRenderer(heliRenderer);
		plot.writePNG(v3Plot.getLocalFilename());
		
		component.setTranslation(heliRenderer.getTranslationInfo(false));
		component.setTranslationType("heli");
		v3Plot.addComponent(component);
		String ch = settings.channel.replace('$', ' ').replace('_', ' ');
		v3Plot.setTitle("Helicorder: " + ch);
	}
}
