package gov.usgs.valve3.plotter;

import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.ShapeRenderer;
import gov.usgs.plot.SmartTick;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.PlotHandler;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.Column;
import gov.usgs.vdx.data.GenericDataMatrix;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cern.colt.matrix.DoubleMatrix2D;

/**
 * Generate images for generic data plot to files
 * 
 * @author Tom Parker
 */
public class GenericVariablePlotter extends Plotter
{
	private Valve3Plot v3Plot;
	private PlotComponent component;
	private double startTime;
	private double endTime;
	private GenericDataMatrix data;
	private String channel;
	
	private String leftUnit;
	private List<Column> leftColumns;
	private String rightUnit;
	private List<Column> rightColumns;
	
	public final boolean ranks	= true;

	/**
	 * Default constructor
	 */
	public GenericVariablePlotter()
	{}

	/**
	 * Gets binary data from VDX server.
	 * @throws Valve3Exception
	 */
	private void getData() throws Valve3Exception
	{
		Map<String, String> params = new LinkedHashMap<String, String>();
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("cid", channel);
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		params.put("selectedTypes", component.get("selectedTypes"));

		data = (GenericDataMatrix)client.getBinaryData(params);
		pool.checkin(client);
		
		if (data == null || data.rows() == 0)
			throw new Valve3Exception("No data.");
		data.adjustTime(Valve3.getInstance().getTimeZoneOffset() * 60 * 60);
		startTime += Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
		endTime += Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
	}

	/**
	 * Initialize internal data from PlotComponent component
	 * @throws Valve3Exception
	 */
	private void getInputs() throws Valve3Exception
	{
		
		channel = component.get("ch");
		if (channel == null || channel.length() <= 0)
			throw new Valve3Exception("Illegal channel found in plotter.");
		
		endTime = component.getEndTime();
		if (Double.isNaN(endTime))
			throw new Valve3Exception("Illegal end time found in plotter.");
		startTime = component.getStartTime(endTime);
		if (Double.isNaN(startTime))
			throw new Valve3Exception("Illegal start time found in plotter.");
		
		String[] types = component.get("dataTypes").split("\\$");
		String[] selectedTypes = component.get("selectedTypes").split(":");
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
	}

	/**
	 * Initialize MatrixRenderer for left plot axis
	 * @throws Valve3Exception
	 */
	private MatrixRenderer getLeftMatrixRenderer()
	{
		MatrixRenderer mr = new MatrixRenderer(data.getData(), ranks);
		mr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		
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
		
		mr.setExtents(startTime, endTime, min, max);		
		mr.createDefaultAxis(8, 8, false, true);
		mr.createDefaultLineRenderers();
		mr.setXAxisToTime(8);

		mr.getAxis().setLeftLabelAsText(leftColumns.get(0).description, Color.blue);
		mr.getAxis().setBottomLabelAsText("Time");
		return mr;
	}

	/**
	 * Initialize MatrixRenderer for right plot axis
	 * @throws Valve3Exception
	 */
	private MatrixRenderer getRightMatrixRenderer()
	{
		if (rightUnit == null)
			return null;
		
		MatrixRenderer mr = new MatrixRenderer(data.getData(), ranks);
		mr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		
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

		mr.setExtents(startTime, endTime, min, max);
		AxisRenderer ar = new AxisRenderer(mr);
		ar.createRightTickLabels(SmartTick.autoTick(min, max, 8, false), null);
		mr.setAxis(ar);
		mr.createDefaultLineRenderers();
		ShapeRenderer[] r = (ShapeRenderer[])mr.getLineRenderers();
		r[1].color = Color.red;

		mr.getAxis().setRightLabelAsText(rightColumns.get(0).description, Color.red);
		return mr;
	}

	/**
	 * Initialize MatrixRenderers for left and right axis,
	 * adds them to plot
	 * @throws Valve3Exception
	 */
	public void plotData()
	{
		MatrixRenderer leftMR = getLeftMatrixRenderer();
		MatrixRenderer rightMR = getRightMatrixRenderer();
		v3Plot.getPlot().addRenderer(leftMR);
		if (rightMR != null)
			v3Plot.getPlot().addRenderer(rightMR);
		
		component.setTranslation(leftMR.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
	}

	/**
	 * Concrete realization of abstract method. 
	 * Initialize MatrixRenderers for left and right axis
	 * (plot may have 2 different value axis)
	 * Generate PNG image to file with random file name.
	 * @see Plotter
	 */
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception
	{
				
		v3Plot = v3p;
		component = comp;
		getInputs();
		getData();

		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		
		plotData();

		v3Plot.addComponent(component);
		//v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + ":" + comp.get("ch"));
		v3Plot.setTitle(Valve3.getInstance().getMenuHandler().getItem(vdxSource).name + ": " + comp.get("selectedStation"));
		v3Plot.setFilename(PlotHandler.getRandomFilename());
		plot.writePNG(Valve3.getInstance().getApplicationPath() + File.separatorChar + v3Plot.getFilename());
	}

	/**
	 * @return CSV dump of binary data described by given PlotComponent
	 */
	public String toCSV(PlotComponent comp) throws Valve3Exception
	{
		
//		HashMap<String, String> params = new HashMap<String, String>();
//		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
//		VDXClient client = pool.checkout();
//		
//		params.put("source", vdxSource);
//		params.put("action", "genericMenu");
//		menu = new GenericMenu(client.getTextData(params));
//		pool.checkin(client);
		
		component = comp;
		getInputs();
		getData();
		DoubleMatrix2D d = data.getData();
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<d.rows(); i++)
		{
			sb.append(Util.j2KToDateString(d.get(i, 0)) + ",");
			for (int j=1; j<d.columns(); j++)
				sb.append(d.get(i,j) + ",");
			sb.append("\n");
		}
			
		return sb.toString();
	}

}
