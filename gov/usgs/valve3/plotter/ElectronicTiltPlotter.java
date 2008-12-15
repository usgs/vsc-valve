package gov.usgs.valve3.plotter;

import gov.usgs.plot.AxisRenderer;
import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.plot.Renderer;
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
import gov.usgs.vdx.data.GenericDataMatrix;
import gov.usgs.vdx.data.tilt.ElectronicTiltData;

import java.awt.Color;
import java.io.File;
import java.util.HashMap;

import cern.colt.matrix.DoubleMatrix2D;

/**
 * Generate tilt plots images to files
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.4  2006/04/13 22:33:19  dcervelli
 * Scale options.
 *
 * Revision 1.3  2006/04/09 18:19:36  dcervelli
 * VDX type safety changes.
 *
 * Revision 1.2  2005/10/20 05:10:42  dcervelli
 * Added degrees symbol.
 *
 * @author Dan Cervelli
 */
public class ElectronicTiltPlotter extends Plotter
{
	private static final char MICRO = (char)0xb5;
	private static final char DEGREES = (char)0xb0;
	
	private enum RightAxis 
	{
		NONE, TEMPERATURE, VOLTAGE;
		
		public static RightAxis fromString(String s)
		{
			if (s.equals("T"))
				return TEMPERATURE;
			else if (s.equals("V"))
				return VOLTAGE;
			else
				return NONE;
		}
	}
	
	private Valve3Plot v3Plot;
	private PlotComponent component;
	private double startTime;
	private double endTime;
	private RightAxis rightAxis;
	private ElectronicTiltData data;
	private String channel;
	
	private boolean showEast = false;
	private boolean showNorth = false;
	private boolean showRadial = false;
	private boolean showTangential = false;
	private boolean showMagnitude = false;
//	private double azimuth = 0;
	
	/**
	 * Default constructor
	 */
	public ElectronicTiltPlotter()
	{}

	/**
	 * Initialize internal data from PlotComponent component
	 * @throws Valve3Exception
	 */
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
		
		rightAxis = RightAxis.fromString(component.get("right"));
		
		showEast = Util.stringToBoolean(component.get("e"));
		showNorth = Util.stringToBoolean(component.get("n"));
		showRadial = Util.stringToBoolean(component.get("r"));
		showTangential = Util.stringToBoolean(component.get("t"));
		showMagnitude = Util.stringToBoolean(component.get("m"));
//		azimuth = Util.stringToDouble(component.get("az"), 0);
	}

	/**
	 * Gets binary data from VDX
	 * @throws Valve3Exception
	 */
	private void getData() throws Valve3Exception
	{
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("cid", channel);
		params.put("st", Double.toString(startTime));
		params.put("et", Double.toString(endTime));

		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		data = (ElectronicTiltData)client.getBinaryData(params);
		pool.checkin(client);
		
		if (data == null || data.getTiltData().rows() == 0)
			throw new Valve3Exception("No data.");
	}

	
	/**
	 * Initialize MatrixRenderer for left plot axis
	 * @throws Valve3Exception
	 */
	private MatrixRenderer getLeftAxis() throws Valve3Exception
	{
//		double az = td.getOptimalAzimuth();
//		ct.mark("getOptimal");

		DoubleMatrix2D mm = data.getTiltData().getAllData(0);
		
		GenericDataMatrix dm = new GenericDataMatrix(mm);
		
		dm.add(1, -dm.mean(1));
		dm.add(2, -dm.mean(2));
		
		double max = Math.max(dm.max(1), dm.max(2));
		double min = Math.min(dm.min(1), dm.min(2));
		double yMin, yMax;
		boolean allowExpand = true;
		if (component.isAutoScale("ysL"))
		{
			yMin = min;
			yMax = max;
		}
		else
		{
			double[] ys = component.getYScale("ysL", min, max);
			yMin = ys[0];
			yMax = ys[1];
			allowExpand = false;
		}
		if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
			throw new Valve3Exception("Illegal axis values.");
		
		MatrixRenderer mr = new MatrixRenderer(dm.getData());
		mr.setVisible(0, showEast);
		mr.setVisible(1, showNorth);
		mr.setVisible(2, showRadial);
		mr.setVisible(3, showTangential);
		mr.setVisible(4, showMagnitude);
		mr.setVisible(5, false);
		mr.setUnit("tilt");
		mr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
//		mr.setExtents(start, end, td.min(2), td.max(2));
		mr.setExtents(startTime, endTime, yMin, yMax);
		mr.createDefaultAxis(8, 8, false, allowExpand);
		mr.createDefaultLineRenderers();
		mr.setXAxisToTime(8);
		mr.getAxis().setLeftLabelAsText("Tilt (" + MICRO + "R)");
		mr.getAxis().setBottomLabelAsText("Time");//(Data from " + Valve.DATE_FORMAT.format(Util.j2KToDate(d.getMinTime())) +
//				" to " + Valve.DATE_FORMAT.format(Util.j2KToDate(d.getMaxTime())) + ")");
		component.setTranslation(mr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		return mr;
	}

	/**
	 * Initialize MatrixRenderer for right graph axis
	 * @throws Valve3Exception
	 */
	private MatrixRenderer getRightAxis() throws Valve3Exception
	{
		if (rightAxis == RightAxis.NONE)
			return null;
		
		DoubleMatrix2D rightData = null;
		String label = "";
		String unit = "";
		switch (rightAxis)
		{
			case TEMPERATURE:
				rightData = data.getThermalData().getData();
				label = "Temperature (" + DEGREES + "C)";
				unit = "degrees c";
				break;
			case VOLTAGE:
				rightData = data.getVoltageData().getData();
				label = "Voltage (V)";
				unit = "volts";
				break;
		}
		
		GenericDataMatrix dm = new GenericDataMatrix(rightData);
		
		double max = dm.max(1);
		double min = dm.min(1);
		double yMin, yMax;
		if (component.isAutoScale("ysR"))
		{
			yMin = min;
			yMax = max;
			if (max - min < 0.2)
			{
				yMax += 0.1;
				yMin -= 0.1;
			}
		}
		else
		{
			double[] ys = component.getYScale("ysR", min, max);
			yMin = ys[0];
			yMax = ys[1];
		}
		if (Double.isNaN(yMin) || Double.isNaN(yMax) || yMin > yMax)
			throw new Valve3Exception("Illegal axis values.");
		
		MatrixRenderer mr = new MatrixRenderer(dm.getData());
		mr.setUnit(unit);
		mr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
		mr.setExtents(startTime, endTime, yMin, yMax);
		AxisRenderer ar = new AxisRenderer(mr);
		ar.createRightTickLabels(SmartTick.autoTick(yMin, yMax, 8, false), null);
		mr.setAxis(ar);
		mr.createDefaultLineRenderers();
		Renderer[] r = mr.getLineRenderers();
		((ShapeRenderer)r[0]).color = Color.red;

		mr.getAxis().setRightLabelAsText(label);
		component.setTranslation(mr.getDefaultTranslation(v3Plot.getPlot().getHeight()));
		component.setTranslationType("ty");
		return mr;
	}

	/**
	 * Concrete realization of abstract method. 
	 * Initialize MatrixRenderers for left and right axis
	 * (plot may have 2 different value axis)
	 * Generate PNG image to local file.
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

		plot.addRenderer(getLeftAxis());
		MatrixRenderer rmr = getRightAxis();
		if (rmr != null)
			plot.addRenderer(getRightAxis());
		
		v3Plot.addComponent(component);
		v3Plot.setTitle("Tilt");
		v3Plot.setFilename(PlotHandler.getRandomFilename());
		plot.writePNG(Valve3.getInstance().getApplicationPath() + File.separatorChar + v3Plot.getFilename());
	}
	
	/**
	 * @return CSV dump of binary data described by given PlotComponent
	 */
	public String toCSV(PlotComponent c) throws Valve3Exception
	{
		component = c;
		getInputs();
		getData();

        return data.toCSV();
	}

}
