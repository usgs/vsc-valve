package gov.usgs.valve3.plotter;

import gov.usgs.plot.Plot;
import gov.usgs.util.CurrentTime;
import gov.usgs.util.Pool;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.heli.HelicorderData;
import gov.usgs.vdx.data.heli.plot.HelicorderRenderer;
import gov.usgs.vdx.data.heli.plot.HelicorderSettings;

import java.awt.Color;
import java.util.HashMap;

/**
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class HelicorderPlotter extends Plotter
{
	public HelicorderPlotter()
	{}
	
	public HelicorderData getHelicorderData(HelicorderSettings settings)
	{
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", vdxSource);
		params.put("selector", settings.channel);
		params.put("st", Double.toString(settings.startTime));
		params.put("et", Double.toString(settings.endTime));

		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		HelicorderData hd = (HelicorderData)client.getData(params);
		pool.checkin(client);

		return hd;
	}
	
	public HelicorderSettings getHelicorderSettings(PlotComponent component)
	{
		HelicorderSettings settings = new HelicorderSettings();
		settings.channel = component.get("ch");
		
		double end = component.getEndTime();
		double start = component.getStartTime(end);
		if (Double.isNaN(start) || Double.isNaN(end))
		{
			double now = CurrentTime.nowJ2K();
			start = now - 43200;
			end = now;
		}
		
		settings.startTime = start;
		settings.endTime = end;
		settings.showClip = component.get("sc").equals("T");
		settings.timeChunk = Integer.parseInt(component.get("tc")) * 60;
		settings.minimumAxis = component.get("min") != null;
		
		settings.left = component.getBoxX();
		settings.top = component.getBoxY();
		settings.width = component.getBoxWidth();
		settings.height = component.getBoxHeight();
		
		settings.timeZoneAbbr = "AKDT";
		settings.timeZoneOffset = -8;
		
		return settings;
	}
	
	public void plot(Valve3Plot v3Plot, PlotComponent component)
	{
		HelicorderSettings heliSettings = getHelicorderSettings(component);
		HelicorderData heliData = getHelicorderData(heliSettings);
		HelicorderRenderer heliRenderer = new HelicorderRenderer();
		heliRenderer.setData(heliData);
		heliSettings.applySettings(heliRenderer, heliData);
		
		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		plot.addRenderer(heliRenderer);
		plot.writePNG(v3Plot.getLocalFilename());
		
		component.setTranslation(heliRenderer.getTranslationInfo(false));
		component.setTranslationType("heli");
		v3Plot.addComponent(component);
		String ch = heliSettings.channel.replace('$', ' ').replace('_', ' ');
		v3Plot.setTitle("Helicorder: " + ch);
	}
}
