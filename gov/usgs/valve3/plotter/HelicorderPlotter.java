package gov.usgs.valve3.plotter;

import gov.usgs.plot.Plot;
import gov.usgs.plot.PlotException;
import gov.usgs.util.Pool;
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

/**
 * Generate helicorder images from raw wave data from vdx source
 *
 * @author Dan Cervelli, Loren Antolik
 */
public class HelicorderPlotter extends RawDataPlotter {

	private Map<Integer, HelicorderData> channelDataMap;
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
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void getInputs(PlotComponent comp) throws Valve3Exception {

		parseCommonParameters(comp);
		if (endTime - startTime >= MAX_HELICORDER_TIME)
			throw new Valve3Exception("Illegal duration.");
		
		try{
			showClip = comp.getBoolean("sc");
		} catch (Valve3Exception ex){
			showClip = false;
		}
		
		try {
			barMult = new Double(comp.getDouble("barMult")).floatValue();
		} catch (Valve3Exception ex) {
			barMult = 3;
		}
		
		try {
			timeChunk = comp.getInt("tc") * 60; 
		} catch (Valve3Exception ex) {
			timeChunk = 15;
		}
		
		try{
			minimumAxis = comp.getBoolean("min");
		} catch (Valve3Exception ex){
			minimumAxis = false;
		}
	}
	
	/**
	 * Gets binary data from VDX
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	protected void getData(PlotComponent comp) throws Valve3Exception {
		
		// initialize variables
		boolean gotData			= false;
		Pool<VDXClient> pool	= null;
		VDXClient client		= null;
		channelDataMap			= new LinkedHashMap<Integer, HelicorderData>();
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
				HelicorderData data = null;
				try {
					data = (HelicorderData)client.getBinaryData(params);
				} catch (Exception e) {
					data = null; 
				}
				
				// if data was collected
				if (data != null && data.rows() > 0) {
					// data.adjustTime(timeOffset);
					gotData = true;
				}
				channelDataMap.put(Integer.valueOf(channel), data);
			}
			
			// check back in our connection to the database
			pool.checkin(client);
		}
		
		// if no data exists, then throw exception
		if (channelDataMap.size() == 0 || !gotData) {
			throw new Valve3Exception("No data for any channel.");
		}
	}
	
	/**
	 * Loop through the list of channels, initialize renderers and add them to plots
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception {
		
		// calculate the number of plot components that will be displayed per channel
		int channelCompCount = 1;
		
		// total components is components per channel * number of channels
		compCount = channelCompCount * channelDataMap.size();
		
		// setting up variables to decide where to plot this component
		int currentComp		= 1;
		int compBoxHeight	= comp.getBoxHeight();
		
		for (int cid : channelDataMap.keySet()) {
			
			// get the relevant information for this channel
			Channel channel			= channelsMap.get(cid);
			HelicorderData data		= channelDataMap.get(cid);
			
			// verify their is something to plot
			if (data == null || data.rows() == 0) {
				v3p.setHeight(v3p.getHeight() - channelCompCount * compBoxHeight);
				Plot plot	= v3p.getPlot();
				plot.setSize(plot.getWidth(), plot.getHeight() - channelCompCount * compBoxHeight);
				compCount = compCount - channelCompCount;
				continue;
			}
			
			if ( forExport ) {
				// Add column headers to csvHdrs
				csvHdrs.append("," + channel.getCode().replace("$","_") + "_Data");
				// Initialize data for export; add to set for CSV
				ExportData ed = new ExportData( csvIndex, new MatrixExporter(data.getData(), true, null, timeOffset) );
				csvIndex++;
				csvData.add( ed );
				continue;
			}
			
			HelicorderRenderer hr	= new HelicorderRenderer();
			hr.xTickMarks			= this.xTickMarks;
			hr.xTickValues			= this.xTickValues;
			hr.xUnits				= this.xUnits;
			hr.xLabel				= this.xLabel;
			hr.yTickMarks			= this.yTickMarks;
			hr.yTickValues			= this.yTickValues;
			hr.yUnits				= this.yUnits;
			hr.yLabel				= this.yLabel;
			hr.setColor(comp.getColor());
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
			
			settings.left					= comp.getBoxX();
			settings.top					= comp.getBoxY() + (currentComp - 1) * compBoxHeight;
			settings.width					= comp.getBoxWidth();
			settings.height					= compBoxHeight - 16;
			settings.timeZoneAbbr			= timeZoneID;
			settings.timeZoneOffset			= timeOffset/3600.0;
			settings.timeZone				= comp.getTimeZone();
			if (currentComp == compCount) {
				settings.showDecorator = true;	
			} else {
				settings.showDecorator = false;
			}
			if (isDrawLegend) {
				settings.showLegend	= true;
			}
			settings.largeChannelDisplay	= false;
			
			settings.applySettings(hr, data);
			
			currentComp++;
			
			comp.setTranslation(hr.getTranslationInfo(false));
			comp.setTranslationType("heli");
			v3p.getPlot().addRenderer(hr);
			v3p.addComponent(comp);
		}
		
		if ( !forExport ) {
			addSuppData( vdxSource, vdxClient, v3p, comp );
			v3p.setCombineable(false);
			v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Helicorder");
		}
	}

	/**
	 * Concrete realization of abstract method. 
	 * Initialize HelicorderRenderer, generate PNG image to local file.
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
		
		// plot configuration
		if (!forExport) {
			v3p.setExportable(true);
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
}