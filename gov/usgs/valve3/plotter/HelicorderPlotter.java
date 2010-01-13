package gov.usgs.valve3.plotter;

import gov.usgs.plot.Plot;
import gov.usgs.util.Pool;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.Channel;
import gov.usgs.vdx.data.heli.HelicorderData;
import gov.usgs.vdx.data.heli.plot.HelicorderRenderer;
import gov.usgs.vdx.data.heli.plot.HelicorderSettings;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Generate helicorder images from raw wave data from vdx source
 *
 * @author Dan Cervelli, Loren Antolik
 */
public class HelicorderPlotter extends Plotter {

	private Valve3Plot v3Plot;
	private PlotComponent component;
	int compCount;
	private Map<Integer, HelicorderData> channelDataMap;
	private static Map<Integer, Channel> channelsMap;
	private HelicorderData data;
	private static final double MAX_HELICORDER_TIME = 31 * 86400;
	
	private String 		ch;
	private double		startTime, endTime;
	private boolean		showClip;
	private float		barMult;
	private int 		timeChunk;
	private boolean		minimumAxis;
	
	/**
	 * Default constructor
	 */
	public HelicorderPlotter()
	{}

	/**
	 * Initialize internal data from PlotComponent
	 * @throws Valve3Exception
	 */
	public void getInputs() throws Valve3Exception {
		
		ch	= component.get("ch");
		if (ch == null || ch.length() <= 0) {
			throw new Valve3Exception("Illegal channel.");
		}
		
		endTime = component.getEndTime();
		if (Double.isNaN(endTime))
			throw new Valve3Exception("Illegal end time.");
		
		startTime = component.getStartTime(endTime);
		if (Double.isNaN(startTime))
			throw new Valve3Exception("Illegal start time.");
		
		if (endTime - startTime >= MAX_HELICORDER_TIME)
			throw new Valve3Exception("Illegal duration.");
		
		showClip = false;
		String clip = component.get("sc");
		if (clip != null && clip.toUpperCase().equals("T"))
			showClip = true;
		
		barMult = Float.parseFloat(component.get("barMult"));

		timeChunk = -1;
		try { 
			timeChunk = Integer.parseInt(component.get("tc")) * 60; 
		} catch (Exception e) {			
		}
		if (timeChunk <= 0)
			throw new Valve3Exception("Illegal time chunk.");
		
		minimumAxis = false;
		String min = component.get("min");
		if (min != null && min.toUpperCase().equals("T"))
			minimumAxis = true;
	}
	
	/**
	 * Gets binary data from VDX
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
		
		// double TZOffset = Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
		
		// create a map to hold all the channel data
		channelDataMap		= new LinkedHashMap<Integer, HelicorderData>();
		String[] channels	= ch.split(",");
		
		// iterate through each of the selected channels and place the data in the map
		for (String channel : channels) {
			params.put("ch", channel);
			HelicorderData data = (HelicorderData)client.getBinaryData(params);
			if (data != null && data.rows() > 0) {
				gotData = true;
				channelDataMap.put(Integer.valueOf(channel), data);
			}
		}
		
		if (!gotData) {
			throw new Valve3Exception("No data for any stations.");
		}

        // check back in our connection to the database
		pool.checkin(client);
	}
	
	/**
	 * Loop through the list of channels and create plots
	 * @throws Valve3Exception
	 */
	public void plotData() throws Valve3Exception {
		
		/// calculate how many graphs we are going to build (number of channels)
		compCount	= channelDataMap.size();
		
		// setting up variables to decide where to plot this component
		int plotCount	= 0;
		
		for (int cid : channelDataMap.keySet()) {
			
			// get the relevant information for this channel
			Channel channel			= channelsMap.get(cid);
			HelicorderData data		= channelDataMap.get(cid);
			
			// verify their is something to plot
			if (data == null || data.rows() == 0) {
				continue;
			}
			
			HelicorderRenderer hr = new HelicorderRenderer();			
			hr.setData(data);

			HelicorderSettings settings		= new HelicorderSettings();
			settings.channel				= channel.getCode();
			settings.channelCode			= channel.getCode().replace('$', ' ').replace('_', ' ');
			settings.startTime				= startTime;
			settings.endTime				= endTime;
			settings.showClip				= showClip;
			settings.barMult				= barMult;
			settings.timeChunk				= timeChunk;
			settings.minimumAxis			= minimumAxis;
			
			settings.left					= component.getBoxX();
			settings.top					= component.getBoxY();
			settings.width					= component.getBoxWidth();
			settings.height					= component.getBoxHeight() / compCount;
			settings.plotCount				= plotCount;
			
			settings.timeZoneAbbr	= Valve3.getInstance().getTimeZoneAbbr();
			settings.timeZoneOffset	= Valve3.getInstance().getTimeZoneOffset();
			settings.timeZone		= TimeZone.getTimeZone(settings.timeZoneAbbr);
			
			plotCount++;
			if (plotCount == compCount) {
				settings.showDecorator = true;	
			} else {
				settings.showDecorator = false;
			}
			
			// hr.createDefaultLegendRenderer(new String[] {settings.channelCode});
			settings.largeChannelDisplay	= true;
			
			settings.applySettings(hr, data);
			
			System.out.println("componentBoxHeight:"	+ component.getBoxHeight() +
							  "/settingHeight:" 		+ settings.height + 
							  "/graphHeight:"   		+ hr.getGraphHeight() + 
					          "/graphWidth:"  			+ hr.getGraphWidth() + 
					          "/graphX:"      			+ hr.getGraphX() + 
					          "/graphY:"      			+ hr.getGraphY());
			
			component.setTranslation(hr.getTranslationInfo(false));
			component.setTranslationType("heli");
			v3Plot.getPlot().addRenderer(hr);
			v3Plot.addComponent(component);
		}
		
		v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name);
	}

	/**
	 * Concrete realization of abstract method. 
	 * Initialize HelicorderRenderer, generate PNG image to local file.
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
	public String toCSV(PlotComponent c) throws Valve3Exception {
		component = c;
		getInputs();
		getData();
        return data.toCSV();
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
		Pool<VDXClient> pool	= Valve3.getInstance().getDataHandler().getVDXClient(client);
		VDXClient cl			= pool.checkout();
		List<String> chs		= cl.getTextData(params);
		pool.checkin(cl);
		channels				= Channel.fromStringsToMap(chs);
		return channels;
	}
}
