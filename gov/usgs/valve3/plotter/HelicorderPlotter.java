package gov.usgs.valve3.plotter;

import gov.usgs.plot.Plot;
import gov.usgs.util.Log;
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
import gov.usgs.vdx.data.heli.HelicorderData;
import gov.usgs.vdx.data.heli.plot.HelicorderRenderer;
import gov.usgs.vdx.data.heli.plot.HelicorderSettings;
import gov.usgs.vdx.data.MatrixExporter;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

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
	private final static Logger logger = Log.getLogger("gov.usgs.valve3.plotter.HelicorderPlotter"); 
	
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
		
		try {
			barMult = new Double(component.getDouble("barMult")).floatValue();
		} catch (Valve3Exception ex) {
			barMult = 3;
		}
		
		try {
			timeChunk = component.getInt("tc") * 60; 
		} catch (Valve3Exception ex) {
			timeChunk = 15;
		}
		
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
	 * Loop through the list of channels, initialize renderers and add them to plots
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3Plot, PlotComponent component) throws Valve3Exception {
		boolean      forExport = (v3Plot == null);	// = "prepare data for export"
		
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
			
			if ( forExport ) {
				// Add column headers to csvHdrs
				csvHdrs.append("," + channel.getCode().replace("$","_") + "_Data");
				// Initialize data for export; add to set for CSV
				ExportData ed = new ExportData( csvIndex, new MatrixExporter(data.getData(), true, null, component.getOffset(startTime)) );
				csvIndex++;
				csvData.add( ed );
				continue;
			}
			
			HelicorderRenderer hr = new HelicorderRenderer();
			hr.xTickMarks =this.xTickMarks;
			hr.xTickValues = this.xTickValues;
			hr.xUnits = this.xUnits;
			hr.xLabel = this.xLabel;
			hr.yTickMarks = this.yTickMarks;
			hr.yTickValues = this.yTickValues;
			hr.yUnits = this.yUnits;
			hr.yLabel = this.yLabel;
			hr.setColor(component.getColor());
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
			
			logger.info("componentBoxHeight:"	+ component.getBoxHeight() +
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
		
		if ( !forExport ) {
			addSuppData( vdxSource, vdxClient, v3Plot, component );
			v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name);
		}
	}

	/**
	 * Concrete realization of abstract method. 
	 * Initialize HelicorderRenderer, generate PNG image to local file.
	 * If v3p is null, prepare data for export -- assumes csvData, csvData & csvIndex initialized
	 * @throws Valve3Exception
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception {
		channelsMap	= getChannels(vdxSource, vdxClient);
		getInputs(comp);
		getData(comp);
		
		plotData(v3p, comp);
		
		if ( v3p != null ) {
			Plot plot = v3p.getPlot();
			plot.setBackgroundColor(Color.white);
			plot.writePNG(v3p.getLocalFilename());
		}
	}
}
