package gov.usgs.valve3.plotter;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import gov.usgs.plot.MatrixRenderer;
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
import gov.usgs.vdx.data.GenericDataMatrix;
import gov.usgs.vdx.data.MatrixExporter;
import gov.usgs.vdx.data.rsam.RSAMData;

/**
 * Get RSAM information from vdx server and  
 * generate images of RSAM values and RSAM event count histograms
 * in files with random names.
 *
 * @author Dan Cervelli, Loren Antolik
 */
public class RatSAMPlotter extends RawDataPlotter {
	
	private enum PlotType {
		VALUES, COUNTS;		
		public static PlotType fromString(String s) {
			if (s == null) {
				return null;			
			} else if (s.equals("values")) {
				return VALUES;
			} else if (s.equals("cnts")) {
				return COUNTS;
			} else {
				return null;
			}
		}
	}

	private PlotType plotType;
	private static Map<Integer, Channel> channelsMap;
	RSAMData data;

	/**
	 * Default constructor
	 */
	public RatSAMPlotter() {
		super();
		ranks	= false;
	}

	/**
	 * Initialize internal data from PlotComponent
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */	
	protected void getInputs(PlotComponent component) throws Valve3Exception {
		
		parseCommonParameters(component);
		
		channelLegendsCols	= new String  [1];
		
		String pt = component.get("plotType");
		if ( pt == null )
			plotType = PlotType.VALUES;
		else {
			plotType	= PlotType.fromString(pt);
			if (plotType == null) {
				throw new Valve3Exception("Illegal plot type: " + pt);
			}
		}
		
		switch(plotType) {
		
		case VALUES:
			leftLines	= 0;
			axisMap		= new LinkedHashMap<Integer, String>();
			// validateDataManipOpts(component);
			axisMap.put(0, "L");
			leftUnit	= "RatSAM";
			leftLines++;
			break;
			
		case COUNTS:
			break;
		}
	}

	/**
	 * Gets binary data from VDX
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	protected void getData(PlotComponent component) throws Valve3Exception {
		
		// initialize variables
		boolean gotData			= false;
		Pool<VDXClient> pool	= null;
		VDXClient client		= null;
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "ratdata");
		params.put("ch", ch);
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("plotType", plotType.toString());
		addDownsamplingInfo(params);
		
		// checkout a connection to the database
		pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		if (pool != null) {
			client = pool.checkout();	
			try {
				data = (RSAMData)client.getBinaryData(params);
			} catch (Exception e) {
				data = null;
			}		
			if (data != null && data.rows() > 0) {
				data.adjustTime(timeOffset);
				gotData = true;
			}
			
	        // check back in our connection to the database
			pool.checkin(client);
		}
		
		// if no data exists, then throw exception
		if (!gotData) {
			throw new Valve3Exception("No data for any channel.");
		}
	}
	
	/**
	 * Initialize DataRenderer, add it to plot, remove mean from rsam data if needed 
	 * and render rsam values to PNG image in local file
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @param channel1 Channel
	 * @param channel2 Channel
	 * @param rd RSAMData
	 * @throws Valve3Exception
	 */
	protected void plotValues(Valve3Plot v3Plot, PlotComponent component, Channel channel1, Channel channel2, RSAMData rd, int currentComp, int compBoxHeight) throws Valve3Exception {
		
		String channelCode1		= channel1.getCode().replace('$', ' ').replace('_', ' ').replace(',', '/');
		String channelCode2		= channel2.getCode().replace('$', ' ').replace('_', ' ').replace(',', '/');
		channel1.setCode(channelCode1 + "-" + channelCode2);
		GenericDataMatrix gdm	= new GenericDataMatrix(rd.getData());	
		channelLegendsCols[0]	= String.format("%s %s", channel1.getCode(), leftUnit);	
		
		if (forExport) {
			
			// Add column header to csvHdrs
			csvHdrs.append(",");
			csvHdrs.append(channel1.getCode() + "_" + leftUnit);
			
			// Initialize data for export; add to set for CSV
			ExportData ed = new ExportData( csvIndex, new MatrixExporter(gdm.getData(), ranks, axisMap) );
			csvData.add( ed );
			
		} else {
			try {
				MatrixRenderer leftMR	= getLeftMatrixRenderer(component, channel1, gdm, currentComp, compBoxHeight, 0, leftUnit);
				v3Plot.getPlot().addRenderer(leftMR);
				component.setTranslation(leftMR.getDefaultTranslation(v3Plot.getPlot().getHeight()));
				component.setTranslationType("ty");
				v3Plot.addComponent(component);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Loop through the list of channels and create plots
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3Plot, PlotComponent component) throws Valve3Exception {

		String[] channels	= ch.split(",");
		Channel channel1	= channelsMap.get(Integer.valueOf(channels[0]));
		Channel channel2	= channelsMap.get(Integer.valueOf(channels[1]));
		
		// calculate the number of plot components that will be displayed per channel
		int channelCompCount = 1;
		
		// total components is components per channel * number of channels
		compCount = channelCompCount;
		
		// setting up variables to decide where to plot this component
		int currentComp		= 1;
		int compBoxHeight	= component.getBoxHeight();
			
		switch(plotType) {
			case VALUES:
				plotValues(v3Plot, component, channel1, channel2, data, currentComp, compBoxHeight);
				v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Values");
				break;
			case COUNTS:
				break;
		}
	}

	/**
	 * Concrete realization of abstract method. 
	 * Generate PNG images for values or event count histograms (depends from plot type) to file with random name.
	 * @param v3p Valve3Plot
	 * @param comp PlotComponent
	 * @throws Valve3Exception
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception, PlotException {
		
		forExport	= (v3p == null);
		channelsMap	= getChannels(vdxSource, vdxClient);
		
		getInputs(comp);
		getData(comp);
		
		plotData(v3p, comp);

		if (!forExport) {
			Plot plot = v3p.getPlot();
			plot.setBackgroundColor(Color.white);
			plot.writePNG(v3p.getLocalFilename());
		}
	}

}