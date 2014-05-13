package gov.usgs.valve3.plotter;

import gov.usgs.math.Butterworth;
import gov.usgs.math.Butterworth.FilterType;
import gov.usgs.plot.Plot;
import gov.usgs.plot.PlotException;
import gov.usgs.plot.data.GenericDataMatrix;
import gov.usgs.plot.render.MatrixRenderer;
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
	protected void getInputs(PlotComponent comp) throws Valve3Exception {
		
		parseCommonParameters(comp);
		
		rk = comp.getInt("rk");
		columnsCount		= columnsList.size();
		legendsCols			= new String  [columnsCount];
		channelLegendsCols	= new String  [columnsCount];
		bypassCols		    = new boolean [columnsCount];
		accumulateCols		= new boolean [columnsCount];
		
		leftLines		= 0;
		axisMap			= new LinkedHashMap<Integer, String>();
		
		validateDataManipOpts(comp);
		
		// iterate through all the active columns and place them in a map if they are displayed
		for (int i = 0; i < columnsCount; i++) {
			Column column	= columnsList.get(i);
			String col_arg = comp.get(column.name);
			if ( col_arg != null )
				column.checked	= Util.stringToBoolean(comp.get(column.name));
			bypassCols[i]		= column.bypassmanip;
			accumulateCols[i]	= column.accumulate;
			legendsCols[i]		= column.description;
			if (column.checked) {
				if(isPlotSeparately()){
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
	protected void getData(PlotComponent comp) throws Valve3Exception {
		
		// initialize variables
		boolean gotData			= false;
		boolean exceptionThrown	= false;
		String exceptionMsg		= "";
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
				} catch (UtilException e) {
					exceptionThrown	= true;
					exceptionMsg	= e.getMessage();
					break;
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
		
		// if a data limit message exists, then throw exception
		if (exceptionThrown) {
			throw new Valve3Exception(exceptionMsg);

		// if no data exists, then throw exception
		} else if (channelDataMap.size() == 0 || !gotData) {
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
	public void plotData(Valve3Plot v3p, PlotComponent comp, Rank rank) throws Valve3Exception {
		
		// setup the legend for the rank
		String rankLegend	= rank.getName();
		
		// calculate the number of plot components that will be displayed per channel
		int channelCompCount = 0;
		if(isPlotSeparately()){
			for(Column col: columnsList){
				if(col.checked || (forExport && exportAll) ){
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
		int compBoxHeight	= comp.getBoxHeight();
		
		for (int cid : channelDataMap.keySet()) {
			
			// get the relevant information for this channel
			Channel channel			= channelsMap.get(cid);
			GenericDataMatrix gdm	= channelDataMap.get(cid);
			
			// if there is no data for this channel, then resize the plot window 
			if (gdm == null || gdm.rows() == 0) {
				v3p.setHeight(v3p.getHeight() - channelCompCount * compBoxHeight);
				Plot plot	= v3p.getPlot();
				plot.setSize(plot.getWidth(), plot.getHeight() - channelCompCount * compBoxHeight);
				compCount = compCount - channelCompCount;
				continue;
			}
			
			// detrend and normalize the data that the user requested to be detrended		
			for (int i = 0; i < columnsCount; i++) {
				if (accumulateCols[i]) { gdm.accumulate(i + 2); }
				if (bypassCols[i])
					continue;
				if (doDespike) { gdm.despike(i + 2, despikePeriod ); }
				if (doDetrend) { gdm.detrend(i + 2); }
				if (filterPick != 0) {
					switch(filterPick) {
						case 1: // Bandpass
							Butterworth bw = new Butterworth();
							FilterType ft = FilterType.BANDPASS;
							Double singleBand = 0.0;
							if ( !Double.isNaN(filterMax) ) {
								if ( filterMax <= 0 )
									throw new Valve3Exception("Illegal max period value.");
							} else {
								ft = FilterType.LOWPASS;
								singleBand = filterMin;
							}
							if ( !Double.isNaN(filterMin) ) {
								if ( filterMin <= 0 )
									throw new Valve3Exception("Illegal min period value.");
							} else {
								ft = FilterType.HIGHPASS;
								singleBand = filterMax;
							}
							if ( ft == FilterType.BANDPASS )
								bw.set(ft, 4, Math.pow(filterPeriod, -1), Math.pow(filterMax, -1), Math.pow(filterMin, -1));
							else
								bw.set(ft, 4, Math.pow(filterPeriod, -1), Math.pow(singleBand, -1), 0);
							gdm.filter(bw, i+2, true);
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
				if (doArithmetic) { gdm.doArithmetic(i+2, arithmeticType, arithmeticValue); }
			}

			if (forExport) {
				// Add column headers to csvHdrs
				int i = 0;
				for (Column col: columnsList) {
					if ( (forExport && exportAll) || !axisMap.get(i).equals("") ) {
						String[] newHdr = {null, null, channel.getCode(), col.name};
						csvHdrs.add( newHdr );
					}
					i++;
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
				if (isPlotSeparately()) {
					for (int i = 0; i < columnsList.size(); i++) {
						Column col = columnsList.get(i);
						if(col.checked){
							MatrixRenderer leftMR	= getLeftMatrixRenderer(comp, channel, gdm, currentComp, compBoxHeight, i, col.unit);
							MatrixRenderer rightMR	= getRightMatrixRenderer(comp, channel, gdm, currentComp, compBoxHeight, i, leftMR.getLegendRenderer());
							if (rightMR != null)
								v3p.getPlot().addRenderer(rightMR);
							v3p.getPlot().addRenderer(leftMR);
							comp.setTranslation(leftMR.getDefaultTranslation(v3p.getPlot().getHeight()));
							comp.setTranslationType("ty");
							v3p.addComponent(comp);
							currentComp++;	
						}
					}
					
				// create a single matrix renderer for each component selected
				} else {
					MatrixRenderer leftMR	= getLeftMatrixRenderer(comp, channel, gdm, currentComp, compBoxHeight, -1, leftUnit);
					MatrixRenderer rightMR	= getRightMatrixRenderer(comp, channel, gdm, currentComp, compBoxHeight, -1, leftMR.getLegendRenderer());
					if (rightMR != null)
						v3p.getPlot().addRenderer(rightMR);
					v3p.getPlot().addRenderer(leftMR);
					comp.setTranslation(leftMR.getDefaultTranslation(v3p.getPlot().getHeight()));
					comp.setTranslationType("ty");
					v3p.addComponent(comp);
					currentComp++;
				}
			}
		}
		if (!forExport) {
			addSuppData( vdxSource, vdxClient, v3p, comp );
			if(channelDataMap.size()!=1){
				v3p.setCombineable(false);
			} else {
				v3p.setCombineable(true);
			}
			v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Time Series");
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
		channelsMap	= getChannels(vdxSource, vdxClient);
		ranksMap	= getRanks(vdxSource, vdxClient);
		columnsList	= getColumns(vdxSource, vdxClient);
		comp.setPlotter(this.getClass().getName());		
		getInputs(comp);
		
		// get the rank object for this request
		Rank rank	= new Rank();
		if (rk == 0) {
			rank	= rank.bestPossible();
		} else {
			rank	= ranksMap.get(rk);
		}
		
		// plot configuration
		if (!forExport) {
			v3p.setExportable(true);
		}
		
		/* if (!forExport) {
			if (rk == 0) {
				v3p.setExportable(false);
			} else {
				v3p.setExportable(true);
			}
			
		// export configuration
		} else {
			if (rk == 0) {
				throw new Valve3Exception( "Data Export Not Available for Best Possible Rank");
			}
		} */
		
		// this is a legitimate request so lookup the data from the database and plot it
		getData(comp);
		plotData(v3p, comp, rank);
				
		if (!forExport) {
			Plot plot = v3p.getPlot();
			plot.setBackgroundColor(Color.white);
			plot.writePNG(v3p.getLocalFilename());
		}
	}
}