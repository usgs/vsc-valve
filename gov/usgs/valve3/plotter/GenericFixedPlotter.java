package gov.usgs.valve3.plotter;

import gov.usgs.math.Butterworth;
import gov.usgs.math.Butterworth.FilterType;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.util.UtilException;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.Channel;
import gov.usgs.vdx.data.Column;
import gov.usgs.vdx.data.ExportData;
import gov.usgs.vdx.data.GenericDataMatrix;
import gov.usgs.vdx.data.MatrixExporter;
import gov.usgs.vdx.data.Rank;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generate images for generic data plot to files
 *
 * @author Dan Cervelli, Loren Antolik
 */
public class GenericFixedPlotter extends RawDataPlotter {
	
	private Map<Integer, GenericDataMatrix> channelDataMap;	

	//private int columnsCount;
	//private boolean detrendCols[];
	//private boolean normalzCols[];
	private String legendsCols[];
	
	/**
	 * Default constructor
	 */
	public GenericFixedPlotter() {
		super();	
	}

	/**
	 * Initialize internal data from PlotComponent component
	 * @throws Valve3Exception
	 */
	protected void getInputs(PlotComponent component) throws Valve3Exception {
		
		parseCommonParameters(component);
		rk = component.getInt("rk");
		//detrendCols			= new boolean [columnsList.size()];
		//normalzCols			= new boolean [columnsList.size()];
		legendsCols			= new String  [columnsList.size()];
		channelLegendsCols	= new String  [columnsList.size()];
		bypassManipCols     = new boolean [columnsList.size()];
		
		leftLines		= 0;
		axisMap			= new LinkedHashMap<Integer, String>();
		
		validateDataManipOpts(component);
		columnsCount		= columnsList.size();
		
		// iterate through all the active columns and place them in a map if they are displayed
		for (int i = 0; i < columnsCount; i++) {
			Column column	= columnsList.get(i);
			String col_arg = component.get(column.name);
			if ( col_arg != null )
				column.checked	= Util.stringToBoolean(component.get(column.name));
			//detrendCols[i]	= Util.stringToBoolean(component.getString("d_" + column.name));
			//normalzCols[i]	= Util.stringToBoolean(component.getString("n_" + column.name));
			bypassManipCols[i] = column.bypassmanipulations;
			legendsCols[i]	= column.description;
			if (column.checked) {
				if(isPlotComponentsSeparately()){
					axisMap.put(i, "L");
					leftUnit	= column.unit;
					leftLines++;
				} else {
					if ((leftUnit != null && leftUnit.equals(column.unit))) {
						axisMap.put(i, "L");
						leftLines++;
					} else if (rightUnit != null && rightUnit.equals(column.unit)) {
					axisMap.put(i, "R");
					} else if (leftUnit == null) {
						leftUnit	= column.unit;
						axisMap.put(i, "L");
						leftLines++;
					} else if (rightUnit == null) {
						rightUnit = column.unit;
						axisMap.put(i, "R");
					} else {
						throw new Valve3Exception("Too many different units.");
					}
				}
			} else {
				axisMap.put(i, "");
			}
		}
		
		if (leftUnit == null && rightUnit == null)
			throw new Valve3Exception("Nothing to plot.");
	}

	/**
	 * Gets binary data from VDX server.
	 * @throws Valve3Exception
	 */
	protected void getData(PlotComponent component) throws Valve3Exception {
		
		boolean gotData = false;
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("rk", Integer.toString(rk));
		addDownsamplingInfo(params);
		// checkout a connection to the database
		Pool<VDXClient> pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client		= pool.checkout();
		if (client == null)
			return;

		// create a map to hold all the channel data
		channelDataMap		= new LinkedHashMap<Integer, GenericDataMatrix>();
		String[] channels	= ch.split(",");
		
		// iterate through each of the selected channels and place the data in the map
		for (String channel : channels) {
			params.put("ch", channel);
			GenericDataMatrix data = null;
			try{
				data = (GenericDataMatrix)client.getBinaryData(params);		
			}
			catch(UtilException e){
				throw new Valve3Exception(e.getMessage()); 
			}
			if (data != null && data.rows() > 0) {
				gotData = true;
				data.adjustTime(component.getOffset(startTime));
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
	 * If v3Plot is null, prepare data for exporting
	 * Otherwise, initialize MatrixRenderers for left and right axis, adds them to plot
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3Plot, PlotComponent component) throws Valve3Exception {
		boolean     forExport = (v3Plot == null); // = "prepare data for export"
		//int         csvIndex = 0;
		
		int displayCount = 0, dh = 0;
		Rank rank = null;
		String rankLegend = null;
		
		// setting up variables to decide where to plot this component
		displayCount	= 0;
		dh				= component.getBoxHeight();
		
		// setup the display for the legend
		rank	= new Rank();
		if (rk == 0) {
			rank	= rank.bestPossible();
			if ( !forExport )
				v3Plot.setExportable( false );
			else
				throw new Valve3Exception( "Exports for Best Possible Rank not allowed" );
		} else {
			rank	= ranksMap.get(rk);
		}
		rankLegend	= rank.getName();
		for (int cid : channelDataMap.keySet()) {
			
			// get the relevant information for this channel
			Channel channel			= channelsMap.get(cid);
			GenericDataMatrix gdm	= channelDataMap.get(cid);
			
			// verify their is something to plot
			if (gdm == null || gdm.rows() == 0) {
				continue;
			}
			
			// detrend and normalize the data that the user requested to be detrended		
			for (int i = 0; i < columnsCount; i++) {
				//if (detrendCols[i]) { gdm.detrend(i + 2); }
				//if (normalzCols[i]) { gdm.add(i + 2, -gdm.mean(i + 2)); }
				if ( bypassManipCols[i] )
					continue;
				if (doDespike) { gdm.despike(i + 2, despikePeriod ); }
				if (doDetrend) { gdm.detrend(i + 2); }
				if (filterPick != 0) {
					Butterworth bw = new Butterworth();
					FilterType ft = FilterType.BANDPASS;
					Double singleBand = 0.0;
					switch(filterPick) {
						case 1: // Bandpass
							if ( !Double.isNaN(filterMax) ) {
								if ( filterMax <= 0 )
									throw new Valve3Exception("Illegal max hertz value.");
							} else {
								ft = FilterType.HIGHPASS;
								singleBand = filterMin;
							}
							if ( !Double.isNaN(filterMin) ) {
								if ( filterMin <= 0 )
									throw new Valve3Exception("Illegal min hertz value.");
							} else {
								ft = FilterType.LOWPASS;
								singleBand = filterMax;
							}
							/* SBH
							if ( ft == FilterType.BANDPASS )
								bw.set(ft, 4, gdm.getSamplingRate(), filterMin, filterMax);
							else
								bw.set(ft, 4, gdm.getSamplingRate(), singleBand, 0);
							data.filter(bw, true); */
							break;
						case 2: // Running median
							gdm.set2median( i+2, filterPeriod );
							break;
						case 3: // Running mean
							gdm.set2mean( i+2, filterPeriod );
							break;
					}
				}
				if (debiasPick != 0 ) {
					double bias = 0.0;
					switch ( debiasPick ) {
						case 1: // remove mean 
							bias = gdm.mean(i+2);
							break;
						case 2: // remove initial value
							bias = gdm.first(i+2);
							break;
						case 3: // remove user value
							bias = debiasValue;
							break;
					}
					gdm.add(i + 2, -bias);
				}
			}
			
			if ( forExport ) {
				// Add column headers to csvHdrs
				for (int i = 0; i < columnsList.size(); i++) {
					if ( !axisMap.get(i).equals("") ) {
						csvHdrs.append(String.format( ",%s_%s", channel.getCode(), legendsCols[i] ));
					}
				}
				// Initialize data for export; add to set for CSV
				ExportData ed = new ExportData( csvIndex, new MatrixExporter(gdm.getData(), ranks, axisMap) );
				csvIndex++;
				csvData.add( ed );			
			} else {
				// set up the legend 
				for (int i = 0; i < legendsCols.length; i++) {
					channelLegendsCols[i] = String.format("%s %s %s", channel.getCode(), rankLegend, legendsCols[i]);
				}
				
				if(isPlotComponentsSeparately()){
					for(Column col: columnsList){
						if(col.checked){
							compCount++;
						}
					}
					// create an individual matrix renderer for each component selected
					for (int i = 0; i < columnsList.size(); i++) {
						Column col = columnsList.get(i);
						if(col.checked){
							MatrixRenderer leftMR	= getLeftMatrixRenderer(component, channel, gdm, displayCount, dh, i, col.unit);
							MatrixRenderer rightMR	= getRightMatrixRenderer(component, channel, gdm, displayCount, dh, i);
							v3Plot.getPlot().addRenderer(leftMR);
							if (rightMR != null)
								v3Plot.getPlot().addRenderer(rightMR);
							component.setTranslation(leftMR.getDefaultTranslation(v3Plot.getPlot().getHeight()));
							component.setTranslationType("ty");
							v3Plot.addComponent(component);
							displayCount++;	
						}
					}
				} else {
					compCount = channelDataMap.size();
					MatrixRenderer leftMR	= getLeftMatrixRenderer(component, channel, gdm, displayCount, dh, -1, leftUnit);
					MatrixRenderer rightMR	= getRightMatrixRenderer(component, channel, gdm, displayCount, dh, -1);
					v3Plot.getPlot().addRenderer(leftMR);
					if (rightMR != null)
						v3Plot.getPlot().addRenderer(rightMR);
					component.setTranslation(leftMR.getDefaultTranslation(v3Plot.getPlot().getHeight()));
					component.setTranslationType("ty");
					v3Plot.addComponent(component);
					displayCount++;
				}
			}
		}
		if ( !forExport ) {
			addSuppData( vdxSource, vdxClient, v3Plot, component );
			v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name);
		}
	}
	
	/**
	 * Concrete realization of abstract method. 
	 * Initialize MatrixRenderers for left and right axis
	 * (plot may have 2 different value axis)
	 * Generate PNG image to file with random file name if v3p isn't null.
	 * If v3p is null, prepare data for export -- assumes csvData, csvData & csvIndex initialized
	 * @throws Valve3Exception
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception {		
		forExport = (v3p == null);	// = "prepare data for export"
		channelsMap	= getChannels(vdxSource, vdxClient);
		ranksMap	= getRanks(vdxSource, vdxClient);
		columnsList	= getColumns(vdxSource, vdxClient);
		getInputs(comp);
		getData(comp);
		
		plotData(v3p, comp);
				
		if ( !forExport ) {
			Plot plot = v3p.getPlot();
			plot.setBackgroundColor(Color.white);
			plot.writePNG(v3p.getLocalFilename());
		}
	}
}
