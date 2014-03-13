package gov.usgs.valve3.plotter;

import gov.usgs.plot.Plot;
import gov.usgs.plot.PlotException;
import gov.usgs.plot.data.GenericDataMatrix;
import gov.usgs.plot.decorate.SmartTick;
import gov.usgs.plot.render.AxisRenderer;
import gov.usgs.plot.render.MatrixRenderer;
import gov.usgs.plot.render.PointRenderer;
import gov.usgs.plot.render.ShapeRenderer;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.util.UtilException;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.Column;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generate images for generic data plot to files
 * 
 * @author Tom Parker
 */
public class GenericVariablePlotter extends RawDataPlotter
{

	private GenericDataMatrix data;
	
	private List<Column> leftColumns;
	private List<Column> rightColumns;

	/**
	 * Default constructor
	 */
	public GenericVariablePlotter(){
		super();
	}

	/**
	 * Initialize internal data from PlotComponent component
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	protected void getInputs(PlotComponent comp) throws Valve3Exception
	{
		parseCommonParameters(comp);
		//ToDo: check for one channel in ch only
		String[] types = comp.getString("dataTypes").split("\\$");
		String[] selectedTypes = comp.getString("selectedTypes").split(":");
		int c = 0;
		for (int i = 0; i<types.length; i++)
		{
			int type = Integer.parseInt(types[i].split(":")[0]);
			String description = types[i].split(":")[1];
			boolean sel = false;
			
			for (int j = 0; j<selectedTypes.length; j++)
				if (Integer.parseInt(selectedTypes[j]) == type)
					sel = true;
			
			if (!sel)
				continue;		
			
			String columnSpec = c++ + ":" + type + ":" + description + ":" + description + ":T";
			
			Column col = new Column(columnSpec);
			
			if (leftUnit != null && leftUnit.equals(col.unit))
				leftColumns.add(col);
			else if (rightUnit != null && rightUnit.equals(col.unit))
				rightColumns.add(col);
			else if (leftUnit == null)
			{
				leftUnit = col.unit;
				leftColumns = new ArrayList<Column>();
				leftColumns.add(col);
			}
			else if (rightUnit == null)
			{
				rightUnit = col.unit;
				rightColumns = new ArrayList<Column>();
				rightColumns.add(col);
			}
			else
				throw new Valve3Exception("Too many different units.");
		}
		
		if (leftUnit == null && rightUnit == null)
			throw new Valve3Exception("Nothing to plot.");
		
		if (rightUnit != null)
		{
			int minRight = Integer.MAX_VALUE;
			for (Column col : rightColumns)
				minRight = Math.min(minRight, col.idx);
			
			int minLeft = Integer.MAX_VALUE;
			for (Column col : leftColumns)
				minLeft = Math.min(minLeft, col.idx);
			
			if (minLeft > minRight)
			{
				String tempUnit = leftUnit;
				List<Column> tempColumns = leftColumns;
				leftUnit = rightUnit;
				leftColumns = rightColumns;
				rightUnit = tempUnit;
				rightColumns = tempColumns;
			}
		}
		// set up the legend 
		channelLegendsCols	= new String  [leftColumns.size() + rightColumns.size()];
		for (int i = 0; i < leftColumns.size(); i++) {
			channelLegendsCols[i] = String.format("%s", leftColumns.get(i).description);
		}
		for (int i = leftColumns.size(); i < channelLegendsCols.length; i++) {
			channelLegendsCols[i] = String.format("%s", rightColumns.get(i-leftColumns.size()).description);
		}
	}

	/**
	 * Gets binary data from VDX server.
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	protected void getData(PlotComponent comp) throws Valve3Exception {
		
		// initialize variables
		boolean gotData			= false;
		boolean exceptionThrown	= false;
		String exceptionMsg		= "";
		Pool<VDXClient> pool	= null;
		VDXClient client		= null;
		
		// create a map of all the input parameters
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("ch", ch);	
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("rk", Integer.toString(rk));
		params.put("selectedTypes", comp.getString("selectedTypes"));
		addDownsamplingInfo(params);
		
		// checkout a connection to the database
		pool	= Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		if (pool != null) {
			client	= pool.checkout();		
			try {
				data = (GenericDataMatrix)client.getBinaryData(params);
			} catch (UtilException e) {
				exceptionThrown	= true;
				exceptionMsg	= e.getMessage();
			} catch (Exception e) {
				data = null; 
			}
		
			// if data was collected
			if (data != null || data.rows() > 0) {
				data.adjustTime(timeOffset);
				gotData = true;
			}
			
			// check back in our connection to the database
			pool.checkin(client);
		}
		
		// if a data limit message exists, then throw exception
		if (exceptionThrown) {
			throw new Valve3Exception(exceptionMsg);

		// if no data exists, then throw exception
		} else if (!gotData) {
			throw new Valve3Exception("No data for any channel.");
		}
	}

	/**
	 * Initialize MatrixRenderer for left plot axis
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	private MatrixRenderer getLeftMatrixRenderer(PlotComponent comp) throws Valve3Exception
	{
		MatrixRenderer mr = new MatrixRenderer(data.getData(), ranks);
		mr.setLocation(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(), comp.getBoxHeight());
		
		double max = -1E300;
		double min = 1E300;
		
		mr.setAllVisible(false);
		for (Column col : leftColumns)
		{
			mr.setVisible(col.idx, true);
			if (col.name.equals("45"))
				data.sum(col.idx+1);

			max = Math.max(max, data.max(col.idx + 1));
			min = Math.min(min, data.min(col.idx + 1));
			max += Math.abs(max - min) * .1;
			min -= Math.abs(max - min) * .1;
		}
		mr.setExtents(startTime+timeOffset, endTime+timeOffset, min, max);	
		mr.createDefaultAxis(8,8,xTickMarks,yTickMarks, false, true, xTickValues, yTickValues);
		if(shape==null){
			mr.createDefaultPointRenderers(comp.getColor());
		} else {
			if (shape.equals("l")) {
				mr.createDefaultLineRenderers(comp.getColor());
			} else {
				mr.createDefaultPointRenderers(shape.charAt(0), comp.getColor());
			}
		}
		mr.setXAxisToTime(8, xTickMarks, xTickValues);
		if(yLabel){
			mr.getAxis().setLeftLabelAsText(leftColumns.get(0).description, Color.blue);
		}
		if(xUnits){
			mr.getAxis().setBottomLabelAsText(timeZoneID + " Time (" + Util.j2KToDateString(startTime+timeOffset, dateFormatString) + " to " + Util.j2KToDateString(endTime+timeOffset, dateFormatString)+ ")");
		}
		if(isDrawLegend) mr.createDefaultLegendRenderer(channelLegendsCols);
		return mr;
	}

	/**
	 * Initialize MatrixRenderer for right plot axis
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	private MatrixRenderer getRightMatrixRenderer(PlotComponent comp) throws Valve3Exception
	{
		if (rightUnit == null)
			return null;
		MatrixRenderer mr = new MatrixRenderer(data.getData(), ranks);
		mr.setLocation(comp.getBoxX(), comp.getBoxY(), comp.getBoxWidth(), comp.getBoxHeight());
		
		double max = -1E300;
		double min = 1E300;
		
		mr.setAllVisible(false);
		for (Column col : rightColumns)
		{
			mr.setVisible(col.idx, true);
			if (col.name.equals("45"))
				data.sum(col.idx+1);

			max = Math.max(max, data.max(col.idx + 1));
			min = Math.min(min, data.min(col.idx + 1));
			max += Math.abs(max - min) * .1;
			min -= Math.abs(max - min) * .1;
		}
		mr.setExtents(startTime+timeOffset, endTime+timeOffset, min, max);
		AxisRenderer ar = new AxisRenderer(mr);
		if(yTickValues){
			ar.createRightTickLabels(SmartTick.autoTick(min, max, 8, false), null);
		}
		mr.setAxis(ar);
		if(shape==null){
			mr.createDefaultPointRenderers(comp.getColor());
			PointRenderer[] r = (PointRenderer[])mr.getPointRenderers();
			r[1].color = Color.red;
		} else {
			if (shape.equals("l")) {
				mr.createDefaultLineRenderers(comp.getColor());
				ShapeRenderer[] r = (ShapeRenderer[])mr.getLineRenderers();
				r[1].color = Color.red;
			} else {
				mr.createDefaultPointRenderers(shape.charAt(0), comp.getColor());
				PointRenderer[] r = (PointRenderer[])mr.getPointRenderers();
				r[1].color = Color.red;
			}
		}
		if(yLabel){
			mr.getAxis().setRightLabelAsText(rightColumns.get(0).description, Color.red);
		}
		if(isDrawLegend) mr.createDefaultLegendRenderer(channelLegendsCols);
		return mr;
	}

	/**
	 * Initialize MatrixRenderers for left and right axis,
	 * adds them to plot
	 * @param v3Plot Valve3Plot
	 * @param component PlotComponent
	 * @throws Valve3Exception
	 */
	public void plotData(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception {
		
		MatrixRenderer leftMR = getLeftMatrixRenderer(comp);
		MatrixRenderer rightMR = getRightMatrixRenderer(comp);
		if (rightMR != null)
			v3p.getPlot().addRenderer(rightMR);
		v3p.getPlot().addRenderer(leftMR);		
		
		comp.setTranslation(leftMR.getDefaultTranslation(v3p.getPlot().getHeight()));
		comp.setTranslationType("ty");
		v3p.addComponent(comp);
		
		if (!forExport) {
			addSuppData( vdxSource, vdxClient, v3p, comp );
			v3p.setCombineable(true);
			v3p.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + " Time Series");
		}
	}

	/**
	 * Concrete realization of abstract method. 
	 * Initialize MatrixRenderers for left and right axis
	 * (plot may have 2 different value axis)
	 * Generate PNG image to file with random file name.
	 * @param v3p Valve3Plot
	 * @param comp PlotComponent
	 * @throws Valve3Exception
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception, PlotException {
		
		forExport	= (v3p == null);
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