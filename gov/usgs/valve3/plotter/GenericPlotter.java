package gov.usgs.valve3.plotter;

import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.SmartTick;
import gov.usgs.util.Pool;
import gov.usgs.util.Util;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.PlotHandler;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.GenericMenu;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.GenericDataMatrix;
import gov.usgs.vdx.data.generic.GenericColumn;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.3  2005/11/08 23:31:09  tparker
 * Adjust for timzone bug #68
 *
 * Revision 1.2  2005/10/20 17:26:45  dcervelli
 * More development.
 *
 * Revision 1.1  2005/10/20 05:10:09  dcervelli
 * Initial commit.
 *
 * @author Dan Cervelli
 */
public class GenericPlotter extends Plotter
{
	private Valve3Plot v3Plot;
	private PlotComponent component;
	private double startTime;
	private double endTime;
	private GenericDataMatrix data;
	private String channel;
	private GenericMenu menu;
	
	private String leftUnit;
	private List<GenericColumn> leftColumns;
	private String rightUnit;
	private List<GenericColumn> rightColumns;

	public GenericPlotter()
	{}
	
	private void getData() throws Valve3Exception
	{
		HashMap<String, String> params = new HashMap<String, String>();
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("cid", channel);
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		data = (GenericDataMatrix)client.getData(params);
		pool.checkin(client);
		
		if (data == null || data.rows() == 0)
			throw new Valve3Exception("No data.");
		data.adjustTime(Valve3.getInstance().getTimeZoneOffset() * 60 * 60);
		startTime += Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
		endTime += Valve3.getInstance().getTimeZoneOffset() * 60 * 60;
	}
	
	private void getInputs() throws Valve3Exception
	{
		channel = component.get("ch");
		if (channel == null || channel.length() <= 0)
			throw new Valve3Exception("Illegal channel.");
		
		endTime = component.getEndTime();
		if (Double.isNaN(endTime))
			throw new Valve3Exception("Illegal end time.");
		startTime = component.getStartTime(endTime);
		if (Double.isNaN(startTime))
			throw new Valve3Exception("Illegal start time.");
		
		for (String c : menu.columns)
		{
			GenericColumn col = new GenericColumn(c);
			boolean display = Util.stringToBoolean(component.get(col.name));
			if (display)
			{
				if (leftUnit != null && leftUnit.equals(col.unit))
					leftColumns.add(col);
				else if (rightUnit != null && rightUnit.equals(col.unit))
					rightColumns.add(col);
				else if (leftUnit == null)
				{
					leftUnit = col.unit;
					leftColumns = new ArrayList<GenericColumn>();
					leftColumns.add(col);
				}
				else if (rightUnit == null)
				{
					rightUnit = col.unit;
					rightColumns = new ArrayList<GenericColumn>();
					rightColumns.add(col);
				}
				else
					throw new Valve3Exception("Too many different units.");
			}
		}
		
		if (leftUnit == null && rightUnit == null)
			throw new Valve3Exception("Nothing to plot.");
		
		if (rightUnit != null)
		{
			int minRight = Integer.MAX_VALUE;
			for (GenericColumn col : rightColumns)
				minRight = Math.min(minRight, col.index);
			
			int minLeft = Integer.MAX_VALUE;
			for (GenericColumn col : leftColumns)
				minLeft = Math.min(minLeft, col.index);
			
			if (minLeft > minRight)
			{
				String tempUnit = leftUnit;
				List<GenericColumn> tempColumns = leftColumns;
				leftUnit = rightUnit;
				leftColumns = rightColumns;
				rightUnit = tempUnit;
				rightColumns = tempColumns;
			}
		}
	}
	
	private MatrixRenderer getLeftMatrixRenderer()
	{
		MatrixRenderer mr = new MatrixRenderer(data.getData());
		mr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		
		double max = -1E300;
		double min = 1E300;
		
		mr.setAllVisible(false);
		for (GenericColumn col : leftColumns)
		{
			mr.setVisible(col.index - 1, true);
			max = Math.max(max, data.max(col.index));
			min = Math.min(min, data.min(col.index));
		}
		
		mr.setExtents(startTime, endTime, min, max);		
		mr.createDefaultAxis(8, 8, false, true);
		mr.createDefaultLineRenderers();
		mr.setXAxisToTime(8);
		mr.getAxis().setLeftLabelAsText(leftColumns.get(0).description + " (" + leftUnit + ")");
		mr.getAxis().setBottomLabelAsText("Time");
		return mr;
	}
	
	private MatrixRenderer getRightMatrixRenderer()
	{
		if (rightUnit == null)
			return null;
		
		MatrixRenderer mr = new MatrixRenderer(data.getData());
		mr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		
		double max = -1E300;
		double min = 1E300;
		
		mr.setAllVisible(false);
		for (GenericColumn col : rightColumns)
		{
			mr.setVisible(col.index - 1, true);
			max = Math.max(max, data.max(col.index));
			min = Math.min(min, data.min(col.index));
		}
		
		mr.setExtents(startTime, endTime, min, max);
		AxisRenderer ar = new AxisRenderer(mr);
		ar.createRightTickLabels(SmartTick.autoTick(min, max, 8, false), null);
		mr.setAxis(ar);
		mr.createDefaultLineRenderers();
//		Renderer[] r = mr.getLineRenderers();
//		((ShapeRenderer)r[0]).color = Color.red;
		
		mr.getAxis().setRightLabelAsText(rightColumns.get(0).description + " (" + rightUnit + ")");
		return mr;
	}
	
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
	
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception
	{
		HashMap<String, String> params = new HashMap<String, String>();
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		
		params.put("source", vdxSource);
		params.put("action", "genericMenu");
		menu = new GenericMenu((List<String>)client.getData(params));
		pool.checkin(client);
		
		v3Plot = v3p;
		component = comp;
		getInputs();
		getData();
				
		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		
		plotData();

		v3Plot.addComponent(component);
		v3Plot.setTitle(menu.title);
		v3Plot.setFilename(PlotHandler.getRandomFilename());
		plot.writePNG(Valve3.getInstance().getApplicationPath() + File.separatorChar + v3Plot.getFilename());
	}
	
	public String toCSV(PlotComponent comp) throws Valve3Exception
	{
		
		HashMap<String, String> params = new HashMap<String, String>();
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		
		params.put("source", vdxSource);
		params.put("action", "genericMenu");
		menu = new GenericMenu((List<String>)client.getData(params));
		pool.checkin(client);
		
		component = comp;
		getInputs();
		getData();
		
		return data.getData().toString();
	}

}
