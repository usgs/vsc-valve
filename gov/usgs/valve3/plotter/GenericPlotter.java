package gov.usgs.valve3.plotter;

import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
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
import java.util.Map;

/**
 * 
 * $Log: not supported by cvs2svn $
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
	private Map<String, List<String>> columns;

	public GenericPlotter()
	{
		columns = new HashMap<String, List<String>>();
	}
	
	private void getData() throws Valve3Exception
	{
		HashMap<String, String> params = new HashMap<String, String>();
		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		
		params.put("source", vdxSource);
		params.put("action", "genericMenu");
		menu = new GenericMenu((List<String>)client.getData(params));
		
		params.put("action", "data");
		params.put("cid", channel);
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));
		
		data = (GenericDataMatrix)client.getData(params);
		pool.checkin(client);
		
		if (data == null || data.rows() == 0)
			throw new Valve3Exception("No data.");
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
				List<String> cols = columns.get(col.unit);
				if (cols == null)
				{
					cols = new ArrayList<String>();
					cols.add(col.name);
					columns.put(col.unit, cols);
				}
			}
		}
	}
	
	public void plotData()
	{
//		double az = td.getOptimalAzimuth();
//		ct.mark("getOptimal");

//		DoubleMatrix2D mm = data.getTiltData().getAllData(0);
		
//		GenericDataMatrix dm = new GenericDataMatrix(mm);
		
//		dm.add(1, -dm.mean(1));
//		dm.add(2, -dm.mean(2));
		
//		double max = Math.max(data.max(1), data.max(2));
//		double min = Math.min(data.min(1), data.min(2));
		double max = 2500;
		double min = -2500;
		
		MatrixRenderer mr = new MatrixRenderer(data.getData());
		//mr.setVisible(0, true);
//		mr.setVisible(0, showEast);
//		mr.setVisible(1, showNorth);
//		mr.setVisible(2, showRadial);
//		mr.setVisible(3, showTangential);
//		mr.setVisible(4, showMagnitude);
//		mr.setVisible(5, false);
//		mr.setUnit("tilt");
		mr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
//		mr.setExtents(start, end, td.min(2), td.max(2));
		mr.setExtents(startTime, endTime, min, max);
		mr.createDefaultAxis(8, 8, false, true);
		mr.createDefaultLineRenderers();
		mr.setXAxisToTime(8);
		mr.getAxis().setLeftLabelAsText("Left");
		mr.getAxis().setBottomLabelAsText("Time");//(Data from " + Valve.DATE_FORMAT.format(Util.j2KToDate(d.getMinTime())) +
//				" to " + Valve.DATE_FORMAT.format(Util.j2KToDate(d.getMaxTime())) + ")");
		component.setTranslation(mr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		v3Plot.getPlot().addRenderer(mr);
	}
	
	public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception
	{
		v3Plot = v3p;
		component = comp;
		getInputs();
		getData();
				
		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);
		
		plotData();

//		plot.addRenderer(getLeftAxis());
//		MatrixRenderer rmr = getRightAxis();
//		if (rmr != null)
//			plot.addRenderer(getRightAxis());
		
		v3Plot.addComponent(component);
		v3Plot.setTitle(menu.title);
		v3Plot.setFilename(PlotHandler.getRandomFilename());
		plot.writePNG(Valve3.getInstance().getApplicationPath() + File.separatorChar + v3Plot.getFilename());
	}
}
