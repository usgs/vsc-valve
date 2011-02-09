package gov.usgs.valve3.plotter;

import gov.usgs.math.Butterworth;
import gov.usgs.math.Butterworth.FilterType;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.PlotException;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
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

	private String legendsCols[];
	
	/**
	 * Default constructor
	 */
	public GenericFixedPlotter() {
		super();
	}

	/**
	 * Initialize internal data from PlotComponent component
	 * @param component data to initialize from
	 * @throws Valve3Exception
	 */
	protected void getInputs(PlotComponent component) throws Valve3Exception {
		
		parseCommonParameters(component);
		
		rk = component.getInt("rk");
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
	 * @param component to get data for
	 * @throws Valve3Exception
	 */
	protected void getData(PlotComponent component) throws Valve3Exception {
		
		// initialize variables
		boolean gotData			= false;
		Pool<VDXClient> pool	= null;
		VDXClient client		= null;
		channelDataMap			= new LinkedHashMap<Integer, GenericDataMatrix>();
		String[] channels		= ch.split(",");
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("rk", Integer.toString(rk));
		addDownsamplingInfo(params);
		
		// checkout a connection to the database
		pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		if (pool != null) {
			client	= pool.checkout();
		
			// iterate through each of the selected channels and place the data in the map
			for (String channel : channels) {
				params.put("ch", channel);
				GenericDataMatrix data = null;
				try {
					data = (GenericDataMatrix)client.getBinaryData(params);		
				} catch(Exception e) {
					data = null;
				}
				
				// if data was collected
				if (data != null && data.rows() > 0) {
					data.adjustTime(timeOffset);
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
	 * If v3Plot is null, prepare data for exporting
	 * Otherwise, initialize MatrixRenderers for left and right axis, adds them to plot
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3Plot, PlotComponent component) throws Valve3Exception {
		
		// setup the rank for the legend
		Rank rank	= new Rank();
		if (rk == 0) {
			if (forExport) {
				throw new Valve3Exception( "Exports for Best Possible Rank not allowed" );
			}
			rank	= rank.bestPossible();
		} else {
			rank	= ranksMap.get(rk);
		}
		String rankLegend	= rank.getName();
		
		// calculate the number of plot components that will be displayed per channel
		int channelCompCount = 0;
		if(isPlotComponentsSeparately()){
			for(Column col: columnsList){
				if(col.checked){
					channelCompCount++;
				}
			}
		} else {
			channelCompCount = 1;
		}
		
		// total components is components per channel * number of channels
		compCount = channelCompCount * channelDataMap.size();
		
		// setting up variables to decide where to plot this component
		int currentComp		= 1;
		int compBoxHeight	= component.getBoxHeight();
		
		for (int cid : channelDataMap.keySet()) {
			
			// get the relevant information for this channel
			Channel channel			= channelsMap.get(cid);
			GenericDataMatrix gdm	= channelDataMap.get(cid);			
			
			// if there is no data for this channel, then resize the plot window 
			if (gdm == null || gdm.rows() == 0) {
				v3Plot.setHeight(v3Plot.getHeight() - channelCompCount * compBoxHeight);
				Plot plot	= v3Plot.getPlot();
				plot.setSize(plot.getWidth(), plot.getHeight() - channelCompCount * compBoxHeight);
				compCount = compCount - channelCompCount;
				continue;
			}
			
			// detrend and normalize the data that the user requested to be detrended		
			for (int i = 0; i < columnsCount; i++) {
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
			
			if (forExport) {
				// Add column headers to csvHdrs
				for (int i = 0; i < columnsList.size(); i++) {
					if ( !axisMap.get(i).equals("") ) {
						csvHdrs.append(String.format( ",%s_%s", channel.getCode(), legendsCols[i] ));
					}
				}
				// Initialize data for export; add to set for CSV
				ExportData ed = new ExportData(csvIndex, new MatrixExporter(gdm.getData(), ranks, axisMap) );
				csvData.add(ed);
				csvIndex++;
			} else {
				// set up the legend 
				for (int i = 0; i < legendsCols.length; i++) {
					channelLegendsCols[i] = String.format("%s %s %s", channel.getCode(), rankLegend, legendsCols[i]);
				}

				// create an individual matrix renderer for each component selected
				if (isPlotComponentsSeparately()) {
					for (int i = 0; i < columnsList.size(); i++) {
						Column col = columnsList.get(i);
						if(col.checked){
							MatrixRenderer leftMR	= getLeftMatrixRenderer(component, channel, gdm, currentComp, compBoxHeight, i, col.unit);
							MatrixRenderer rightMR	= getRightMatrixRenderer(component, channel, gdm, currentComp, compBoxHeight, i, leftMR.getLegendRenderer());
							v3Plot.getPlot().addRenderer(leftMR);
							if (rightMR != null)
								v3Plot.getPlot().addRenderer(rightMR);
							component.setTranslation(leftMR.getDefaultTranslation(v3Plot.getPlot().getHeight()));
							component.setTranslationType("ty");
							v3Plot.addComponent(component);
							currentComp++;	
						}
					}
					
				// create a single matrix renderer for each component selected
				} else {
					MatrixRenderer leftMR	= getLeftMatrixRenderer(component, channel, gdm, currentComp, compBoxHeight, -1, leftUnit);
					MatrixRenderer rightMR	= getRightMatrixRenderer(component, channel, gdm, currentComp, compBoxHeight, -1, leftMR.getLegendRenderer());
					v3Plot.getPlot().addRenderer(leftMR);
					if (rightMR != null)
						v3Plot.getPlot().addRenderer(rightMR);
					component.setTranslation(leftMR.getDefaultTranslation(v3Plot.getPlot().getHeight()));
					component.setTranslationType("ty");
					v3Plot.addComponent(component);
					currentComp++;
				}
			}
		}
		if (!forExport) {
			addSuppData( vdxSource, vdxClient, v3Plot, component );
			v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Time Series");
		}
	}
	
	/**
	 * Concrete realization of abstract method. 
	 * Initialize MatrixRenderers for left and right axis
	 * (plot may have 2 different value axis)
	 * Generate PNG image to file with random file name if v3p isn't null.
	 * If v3p is null, prepare data for export -- assumes csvData, csvData & csvIndex initialized
	 * @param comp PlotComponent
	 * @throws Valve3Exception
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception, PlotException {
		
		forExport	= (v3p == null);
		comp.setPlotter(this.getClass().getName());
		channelsMap	= getChannels(vdxSource, vdxClient);
		ranksMap	= getRanks(vdxSource, vdxClient);
		columnsList	= getColumns(vdxSource, vdxClient);
		
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
