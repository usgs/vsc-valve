package gov.usgs.valve3.plotter;

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
import gov.usgs.vdx.data.heli.HelicorderData;
import gov.usgs.vdx.data.heli.plot.HelicorderRenderer;
import gov.usgs.vdx.data.heli.plot.HelicorderSettings;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generate helicorder images from raw wave data from vdx source
 *
 * @author Dan Cervelli, Loren Antolik
 */
public class HelicorderPlotter extends RawDataPlotter {

	int compCount;
	private Map<Integer, HelicorderData> channelDataMap;
	private HelicorderData data;
	private static final double MAX_HELICORDER_TIME = 31 * 86400;
	
	private boolean		showClip;
	private float		barMult;
	private int 		timeChunk;
	private boolean		minimumAxis;
	
	/**
	 * Default constructor
	 */
	public HelicorderPlotter(){
		super();
	}

	/**
	 * Initialize internal data from PlotComponent
	 * @throws Valve3Exception
	 */
	public void getInputs(PlotComponent component) throws Valve3Exception {
		
		if (endTime - startTime >= MAX_HELICORDER_TIME)
			throw new Valve3Exception("Illegal duration.");
		
		try{
			showClip = component.getBoolean("sc");
		} catch (Valve3Exception ex){
			showClip = false;
		}
		
		barMult = new Double(component.getDouble("barMult")).floatValue();
		timeChunk = component.getInt("tc") * 60; 
		
		try{
			minimumAxis = component.getBoolean("min");
		} catch (Valve3Exception ex){
			minimumAxis = false;
		}
	}
	
	/**
	 * Gets binary data from VDX
	 * @throws Valve3Exception
	 */
	protected void getData(PlotComponent component) throws Valve3Exception {
		parseCommonParameters(component);
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
		
		// double TZOffset = Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
		
		// create a map to hold all the channel data
		channelDataMap		= new LinkedHashMap<Integer, HelicorderData>();
		String[] channels	= ch.split(",");
		
		// iterate through each of the selected channels and place the data in the map
		for (String channel : channels) {
			params.put("ch", channel);
			HelicorderData data = null;
			try{
				data = (HelicorderData)client.getBinaryData(params);
			}
			catch(UtilException e){
				throw new Valve3Exception(e.getMessage()); 
			}
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
	public void plotData(Valve3Plot v3Plot, PlotComponent component) throws Valve3Exception {
		
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
			settings.timeZoneAbbr			= component.getTimeZone().getID();
			settings.timeZoneOffset			= component.getOffset(startTime)/3600.0;
			settings.timeZone				= component.getTimeZone();
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
	public String toCSV(PlotComponent c) throws Valve3Exception {
		getInputs(c);
		getData(c);
        return data.toCSV();
	}
}
