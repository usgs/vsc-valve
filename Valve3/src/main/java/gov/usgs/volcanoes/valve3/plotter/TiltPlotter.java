package gov.usgs.volcanoes.valve3.plotter;

import gov.usgs.math.Butterworth;
import gov.usgs.math.Butterworth.FilterType;
import gov.usgs.plot.Plot;
import gov.usgs.plot.PlotException;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.util.UtilException;
import gov.usgs.volcanoes.valve3.PlotComponent;
import gov.usgs.volcanoes.valve3.Plotter;
import gov.usgs.volcanoes.valve3.Valve3;
import gov.usgs.volcanoes.valve3.Valve3Exception;
import gov.usgs.volcanoes.valve3.result.Valve3Plot;
import gov.usgs.volcanoes.vdx.client.VDXClient;
import gov.usgs.volcanoes.vdx.data.Channel;
import gov.usgs.volcanoes.vdx.data.Column;
import gov.usgs.volcanoes.vdx.data.ExportData;
import gov.usgs.volcanoes.vdx.data.MatrixExporter;
import gov.usgs.volcanoes.vdx.data.Rank;
import gov.usgs.volcanoes.vdx.data.tilt.TiltData;
import gov.usgs.proj.GeoRange;
import gov.usgs.plot.data.GenericDataMatrix;
import gov.usgs.plot.map.GeoLabel;
import gov.usgs.plot.map.GeoImageSet;
import gov.usgs.plot.map.GeoLabelSet;
import gov.usgs.plot.map.MapRenderer;
import gov.usgs.plot.render.EllipseVectorRenderer;
import gov.usgs.plot.render.MatrixRenderer;
import gov.usgs.plot.render.Renderer;
import gov.usgs.plot.render.TextRenderer;
import gov.usgs.proj.TransverseMercator;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cern.colt.matrix.DoubleMatrix2D;

/**
 * Generate tilt images from raw data got from vdx source
 *
 * @author Dan Cervelli, Loren Antolik
 */
public class TiltPlotter extends RawDataPlotter {
	
	private static final char MICRO = (char)0xb5;
	
	private enum PlotType {
		TIME_SERIES, TILT_VECTORS;		
		public static PlotType fromString(String s) {
			if (s.equals("ts")) {
				return TIME_SERIES;
			} else if (s.equals("tv")) {
				return TILT_VECTORS;
			} else {
				return null;
			}
		}
	}
	
	private enum Azimuth {
		NOMINAL, OPTIMAL, USERDEFINED;		
		public static Azimuth fromString(String s) {
			if (s.equals("n")) {
				return NOMINAL;
			} else if (s.equals("o")) {
				return OPTIMAL;
			} else if (s.equals("u")) {
				return USERDEFINED;
			} else {
				return null;
			}
		}
	}
	
	private Map<Integer, TiltData> channelDataMap;
	private static Map<Integer, Double> azimuthsMap;

	private String legendsCols[];
	
	private PlotType plotType;
	
	private Azimuth azimuth;	
	private double azimuthValue;
	private double azimuthRadial;
	private double azimuthTangential;
	
	private double vectorScale;
	private boolean isAutoVectorScale;
	
	/**
	 * Default constructor
	 */
	public TiltPlotter() {
		super();	
	}
		
	/**
	 * Initialize internal data from PlotComponent
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */	
	protected void getInputs(PlotComponent comp) throws Valve3Exception {
		
		parseCommonParameters(comp);
	
		rk = comp.getInt("rk");
	
		String pt = comp.get("plotType");
		if ( pt == null )
			plotType = PlotType.TIME_SERIES;
		else {
			plotType	= PlotType.fromString(pt);
			if (plotType == null) {
				throw new Valve3Exception("Illegal plot type: " + pt);
			}
		}
		
		switch(plotType) {
		
		case TIME_SERIES:
			
			String az = comp.get("az");
			if ( az == null )
				az = "n";
			azimuth	= Azimuth.fromString(az);
			if (azimuth == null) {
				throw new Valve3Exception("Illegal azimuth: " + az);
			}
		
			columnsCount		= columnsList.size();
			legendsCols			= new String  [columnsCount];
			channelLegendsCols	= new String  [columnsCount];
			bypassCols     		= new boolean [columnsCount];
			accumulateCols		= new boolean [columnsCount];
			
			leftLines		= 0;
			axisMap			= new LinkedHashMap<Integer, String>();
			
			validateDataManipOpts(comp);
			
			// iterate through all the active columns and place them in a map if they are displayed
			for (int i = 0; i < columnsList.size(); i++) {
				Column column	= columnsList.get(i);
				String col_arg = comp.get(column.name);
				if ( forExport && exportAll )
					column.checked = true;
				else if ( col_arg != null )
					column.checked	= Util.stringToBoolean(comp.get(column.name));
				bypassCols[i]		= column.bypassmanip;
				accumulateCols[i]	= column.accumulate;
				legendsCols[i]		= column.description;
				if (column.checked) {
					if(forExport || isPlotSeparately()){
						axisMap.put(i, "L");
						leftUnit	= column.unit;
						leftLines++;
					} else {
						if (leftUnit != null && leftUnit.equals(column.unit)) {
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
			
			break;
			
		case TILT_VECTORS:
			
			// if auto vector scaling is not enabled, look up to see what the user input
			isAutoVectorScale	= comp.isVectorAutoScale("vs");
			if (!isAutoVectorScale) {
				vectorScale = Util.stringToDouble(comp.get("vs"));
				if (vectorScale <= 0 || Double.isNaN(vectorScale)) {
					isAutoVectorScale = true;
				}
			}
			break;
		}
	}

	/**
	 * Gets binary data from VDX
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	protected void getData(PlotComponent comp) throws Valve3Exception {
		
		// initialize variables
		boolean exceptionThrown	= false;
		String exceptionMsg		= "";
		Pool<VDXClient> pool	= null;
		VDXClient client		= null;
		channelDataMap			= new LinkedHashMap<Integer, TiltData>();
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
				
			// iterate through each of the selected channels and get the data from the db
			for (String channel : channels) {
				params.put("ch", channel);	
				TiltData data = null;
				try {
					data = (TiltData)client.getBinaryData(params);
				} catch (UtilException e) {
					exceptionThrown	= true;
					exceptionMsg	= e.getMessage();
					break;
				} catch (Exception e) {
					exceptionThrown = true;
					exceptionMsg	= e.getMessage();
					break;
				}
				
				// if data was collected
				if (data != null && data.rows() > 0) {
					data.adjustTime(timeOffset);
				}
				channelDataMap.put(Integer.valueOf(channel), data);
			}
		
			// check back in our connection to the database
			pool.checkin(client);
		}
		
		// if a data limit message exists, then throw exception
		if (exceptionThrown) {
			throw new Valve3Exception(exceptionMsg);
		}
	}	

	/**
	 * Create MapRenderer for tilt vector, adds it to plot
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @param rank Rank
	 */
	public void plotTiltVectors(Valve3Plot v3p, PlotComponent comp, Rank rank) throws Valve3Exception {
		
		List<Point2D.Double> locs = new ArrayList<Point2D.Double>();

		// add a location for each channel that is being plotted
		for (int cid : channelDataMap.keySet()) {
			TiltData data = channelDataMap.get(cid);
			if (data != null) {
				locs.add(channelsMap.get(cid).getLonLat());
			}
		}

		// create the dimensions of the plot based on these stations
		GeoRange range = GeoRange.getBoundingBox(locs);
		
		TransverseMercator proj = new TransverseMercator();
		Point2D.Double origin = range.getCenter();
		proj.setup(origin, 0, 0);
		
		MapRenderer mr = new MapRenderer(range, proj);
		mr.setLocationByMaxBounds(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(), comp.getBoxMapHeight());
		v3p.getPlot().setSize(v3p.getPlot().getWidth(), mr.getGraphHeight() + 60 + 16);

		GeoLabelSet labels = Valve3.getInstance().getGeoLabelSet();
		labels = labels.getSubset(range);
		mr.setGeoLabelSet(labels);
		
		GeoImageSet images = Valve3.getInstance().getGeoImageSet();
		RenderedImage ri = images.getMapBackground(proj, range, comp.getBoxWidth());
		
		mr.setMapImage(ri);
		mr.createBox(8);
		mr.createGraticule(8, xTickMarks, yTickMarks, xTickValues, yTickValues, Color.BLACK);
		mr.createScaleRenderer();
		
		double[] trans = mr.getDefaultTranslation(v3p.getPlot().getHeight());
		trans[4] = startTime+timeOffset;
		trans[5] = endTime+timeOffset;
		trans[6] = origin.x;
		trans[7] = origin.y;
		mr.createEmptyAxis();
		if(xUnits){
			mr.getAxis().setBottomLabelAsText("Longitude");
		}
		if(yUnits){
			mr.getAxis().setLeftLabelAsText("Latitude");
		}
		mr.getAxis().setTopLabelAsText(getTopLabel(rank));
		v3p.getPlot().addRenderer(mr);

		double maxMag = -1E300;
		List<Renderer> vrs = new ArrayList<Renderer>();
		
		for (int cid : channelDataMap.keySet()) {
			Channel channel	= channelsMap.get(cid);
			TiltData data	= channelDataMap.get(cid);
			
			labels.add(new GeoLabel(channel.getCode(), channel.getLon(), channel.getLat()));
			
			if (data == null || data.rows() <= 1) {
				continue;
			}
			
			DoubleMatrix2D dm = data.getAllData(0.0);
			
			// iterate through each matrix and get the value that is not null.  we can't calculate vectors from potentially null values
			double et1	= 0.0;
			double et2	= 0.0;
			double nt1	= 0.0;
			double nt2	= 0.0;
			int i;
			for (i = 0; i < dm.rows(); i++) {
				if (!Double.isNaN(dm.getQuick(i, 4))) {
					et1 = dm.getQuick(i, 4);
					break;
				} else {
					continue;
				}
			}
			for (i = 0; i < dm.rows(); i++) {
				if (!Double.isNaN(dm.getQuick(i, 5))) {
					nt1 = dm.getQuick(i, 5);
					break;
				} else {
					continue;
				}
			}
			for (i = dm.rows() - 1; i > -1; i--) {
				if (!Double.isNaN(dm.getQuick(i, 4))) {
					et2 = dm.getQuick(i, 4);
					break;
				} else {
					continue;
				}
			}
			for (i = dm.rows() - 1; i > -1; i--) {
				if (!Double.isNaN(dm.getQuick(i, 5))) {
					nt2 = dm.getQuick(i, 5);
					break;
				} else {
					continue;
				}
			}
			// double et1	= dm.getQuick(0, 2);
			// double nt1	= dm.getQuick(0, 3);
			// double et2	= dm.getQuick(dm.rows() - 1, 2);
			// double nt2	= dm.getQuick(dm.rows() - 1, 3);
			double e	= et2 - et1;
			double n	= nt2 - nt1;

			EllipseVectorRenderer evr = new EllipseVectorRenderer();
			evr.frameRenderer = mr;
			Point2D.Double ppt = proj.forward(channel.getLonLat());
			evr.x = ppt.x;
			evr.y = ppt.y;
			evr.u = e;
			evr.v = n;
			evr.z = 0;
			evr.displayHoriz	= true;
			evr.displayVert		= false;

			maxMag = Math.max(evr.getMag(), maxMag);
			v3p.getPlot().addRenderer(evr);
			vrs.add(evr);
		}
		
		if (maxMag == -1E300) {
			// return;
			maxMag = 1;
		}
		
		// lookup the correct scaling factor
		double scale = 0.0;
		if (isAutoVectorScale) {
			scale = EllipseVectorRenderer.getBestScale(maxMag);
		} else {
			scale = vectorScale;
		}
		
		// set the length of the legend vector to 1/5 of the width of the shortest side of the map
		double desiredLength = Math.min((mr.getMaxY() - mr.getMinY()), (mr.getMaxX() - mr.getMinX())) / 5;
		// logger.info("Scale: " + scale);
		// logger.info("desiredLength: " + desiredLength);
		// logger.info("desiredLength/scale: " + desiredLength / scale);
		
		for (int i = 0; i < vrs.size(); i++) {
			EllipseVectorRenderer evr = (EllipseVectorRenderer)vrs.get(i);
			evr.setScale(desiredLength / scale);
		}
		
		// draw the legend vector
		EllipseVectorRenderer svr = new EllipseVectorRenderer();
		svr.frameRenderer = mr;
		svr.drawEllipse = false;
		svr.x = mr.getMinX();
		svr.y = mr.getMinY();
		svr.u = desiredLength;
		svr.v = 0;
		svr.z = 0;
		svr.displayHoriz	= true;
		svr.displayVert		= false;
		v3p.getPlot().addRenderer(svr);
		
		// draw the legend vector units
		TextRenderer tr = new TextRenderer();
		tr.x = mr.getGraphX() + 10;
		tr.y = mr.getGraphY() + mr.getGraphHeight() - 5;
		tr.text = scale + " " + MICRO + "R";
		v3p.getPlot().addRenderer(tr);
		
		comp.setTranslation(trans);
		comp.setTranslationType("map");
		v3p.addComponent(comp);
	}

	/**
	 * If v3Plot is null, prepare data for exporting
	 * Otherwise, Initialize MatrixRenderers for left and right axis, adds them to plot
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3p, PlotComponent comp, Rank rank) throws Valve3Exception {
		
		// setup the rank for the legend
		String rankLegend = rank.getName();
		
		switch (plotType) {
		
			case TIME_SERIES:
				// calculate the number of plot components that will be displayed per channel
				int channelCompCount = 0;
				if(isPlotSeparately()){
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
				int compBoxHeight	= comp.getBoxHeight();
				
				for (int cid : channelDataMap.keySet()) {
					
					// get the relevant information for this channel
					Channel channel	= channelsMap.get(cid);
					TiltData data	= channelDataMap.get(cid);
					
					// if there is no data for this channel, then resize the plot window 
					if (data == null || data.rows() == 0) {
						v3p.setHeight(v3p.getHeight() - channelCompCount * compBoxHeight);
						Plot plot	= v3p.getPlot();
						plot.setSize(plot.getWidth(), plot.getHeight() - channelCompCount * compBoxHeight);
						compCount = compCount - channelCompCount;
						continue;
					}
					
					// instantiate the azimuth and tangential values based on the user selection
					switch (azimuth) {
					case NOMINAL:
						azimuthValue = azimuthsMap.get(channel.getCId());
						break;
					case OPTIMAL:
						azimuthValue = data.getOptimalAzimuth();
						break;
					case USERDEFINED:
						String azval = comp.get("azval");
						if ( azval == null )
							azimuthValue = 0.0;
						else
							azimuthValue = comp.getDouble("azval");
						break;
					default:
						azimuthValue = 0.0;
						break;
					}
					// azimuthValue 	   -= 90.0;
					azimuthValue		= (azimuthValue) % 360.0;
					azimuthRadial		= azimuthValue;
					azimuthTangential	= (azimuthValue + 90.0) % 360.0;
					
					// subtract the mean from the data to get it on a zero based scale (for east and north)
					data.add(2, -data.mean(2));
					data.add(3, -data.mean(3));
					
					// set up the legend 
					String tiltLegend = null;
					if ( !forExport ) {
						for (int i = 0; i < legendsCols.length; i++) {
							if (legendsCols[i].equals("Radial")) {
								tiltLegend	= String.valueOf(azimuthRadial);
							} else if (legendsCols[i].equals("Tangential")) {
								tiltLegend	= String.valueOf(azimuthTangential);
							} else {
								tiltLegend	= legendsCols[i];
							}
							channelLegendsCols[i] = String.format("%s %s %s", channel.getCode(), rankLegend, tiltLegend);
						}
					}
					GenericDataMatrix gdm	= new GenericDataMatrix(data.getAllData(azimuthValue));

					// detrend the data that the user requested to be detrended					
					for (int i = 0; i < columnsCount; i++) {
						Column col = columnsList.get(i);
						if ( !col.checked ) {
							continue;
						}
						if (accumulateCols[i]) { gdm.accumulate(i + 2); }
						if (bypassCols[i]) {
							continue;
						}
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
							}
						}
						if (doArithmetic) { gdm.doArithmetic(i+2, arithmeticType, arithmeticValue); }
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
						
						// Add the headers to the CSV file
						int i = 0;
						for ( Column col: columnsList ) {
							if ( !axisMap.get(i).equals("") ) {
								String[] hdr = {null, null, channel.getCode(), col.name };
								csvHdrs.add( hdr );
							}
							i++;
						}
						// Initialize data for export; add to set for CSV
						ExportData ed = new ExportData( csvIndex, new MatrixExporter(gdm.getData(), ranks, axisMap) );
						csvData.add( ed );
						csvIndex++;
						
					} else {
						// create an individual matrix renderer for each component selected
						if (isPlotSeparately()){
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
					if(channelDataMap.size()!=1){
						v3p.setCombineable(false);
					} else {
						v3p.setCombineable(true);
					}
					v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Time Series");
					addSuppData( vdxSource, vdxClient, v3p, comp );
					addMetaData( vdxSource, vdxClient, v3p, comp );
				}
				break;
				
			case TILT_VECTORS:
				if (!forExport) {
					v3p.setCombineable(false);
					v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Vectors");
				}
				plotTiltVectors(v3p, comp, rank);
				break;
		}
	}
	
	/**
	 * Concrete realization of abstract method. 
	 * Generate tilt PNG image to file with random name.
	 * If v3p is null, prepare data for export -- assumes csvData, csvData & csvIndex initialized
	 * @param v3p Valve3Plot
	 * @param comp PlotComponent
	 * @throws Valve3Exception
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception, PlotException	{
		
		forExport	= (v3p == null);
		channelsMap	= getChannels(vdxSource, vdxClient);
		ranksMap	= getRanks(vdxSource, vdxClient);
		azimuthsMap	= getAzimuths(vdxSource, vdxClient);
		columnsList	= getColumns(vdxSource, vdxClient);
		comp.setPlotter(this.getClass().getName());		
		getInputs(comp);
		
		// get the rank object for this request
		Rank rank	= new Rank();
		if (rk == 0) {
			rank	= rank.bestAvailable();
		} else {
			rank	= ranksMap.get(rk);
		}
		
		// set the exportable based on the output and plot type
		switch (plotType) {
		
		case TIME_SERIES:
			
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
					throw new Valve3Exception( "Data Export Not Available for Best Available Rank");
				}
			} */
			break;
			
		case TILT_VECTORS:
			
			// plot configuration
			if (!forExport) {
				v3p.setExportable(false);
				
			// export configuration
			} else {
				throw new Valve3Exception("Data Export Not Available for Tilt Vectors");
			}
			break;
		}
		
		// this is a legitimate request so lookup the data from the database and plot it
		getData(comp);		
		plotData(v3p, comp, rank);
		
		if (!forExport)
			writeFile(v3p);
}

	/**
	 * 
	 * @return plot top label text
	 */
	private String getTopLabel(Rank rank) {
		StringBuilder top = new StringBuilder(100);
		top.append(rank.getName() + " Vectors between ");
		top.append(Util.j2KToDateString(startTime+timeOffset, dateFormatString));
		top.append(" and ");
		top.append(Util.j2KToDateString(endTime+timeOffset, dateFormatString));
		top.append(" " + timeZoneID + " Time");
		return top.toString();
	}
}
