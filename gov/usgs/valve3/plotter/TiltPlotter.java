package gov.usgs.valve3.plotter;

import gov.usgs.plot.MatrixRenderer;
import gov.usgs.plot.Plot;
import gov.usgs.util.CodeTimer;
import gov.usgs.util.Pool;
import gov.usgs.valve3.PlotComponent;
import gov.usgs.valve3.PlotHandler;
import gov.usgs.valve3.Plotter;
import gov.usgs.valve3.Valve3;
import gov.usgs.valve3.Valve3Exception;
import gov.usgs.valve3.result.Valve3Plot;
import gov.usgs.vdx.client.VDXClient;
import gov.usgs.vdx.data.GenericDataMatrix;
import gov.usgs.vdx.data.tilt.TiltData;

import java.awt.Color;
import java.io.File;
import java.util.HashMap;

import cern.colt.matrix.DoubleMatrix2D;

/**
 * 
 * TODO: tilt vectors.
 * TODO: validate.
 * TODO: rotations.
 * TODO: legend.
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.1  2005/09/06 20:10:24  dcervelli
 * Initial commit.
 *
 * @author Dan Cervelli
 */
public class TiltPlotter extends Plotter
{
	public TiltPlotter()
	{}
		
	public void plot(Valve3Plot v3Plot, PlotComponent component) throws Valve3Exception
	{
		v3Plot.setFilename(PlotHandler.getRandomFilename());
		double end = component.getEndTime();
		double start = component.getStartTime(end);
		if (Double.isNaN(start) || Double.isNaN(end))
		{
			// return an error
			return;
		}
		
		String channel = component.get("ch");
		
		HashMap<String, String> params = new HashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "data");
		params.put("cid", channel);
		params.put("st", Double.toString(start));
		params.put("et", Double.toString(end));

		Pool<VDXClient> pool = Valve3.getInstance().getDataHandler().getVDXClient(vdxClient);
		VDXClient client = pool.checkout();
		TiltData td = (TiltData)client.getData(params);
		pool.checkin(client);
		
		if (td == null || td.rows() == 0)
			throw new Valve3Exception("No data.");
		
		Plot plot = v3Plot.getPlot();
		plot.setBackgroundColor(Color.white);

		CodeTimer ct = new CodeTimer("tilt");
		double az = td.getOptimalAzimuth();
		ct.mark("getOptimal");
		DoubleMatrix2D mm = td.getAllData(az);
		ct.mark("getAllData");
		
		GenericDataMatrix dm = new GenericDataMatrix(mm);
		ct.stop();
		
		dm.add(1, -dm.mean(1));
		dm.add(2, -dm.mean(2));
		
		double max = Math.max(dm.max(1), dm.max(2));
		double min = Math.min(dm.min(1), dm.min(2));
		
		MatrixRenderer mr = new MatrixRenderer(dm.getData());
		mr.setVisible(2, false);
		mr.setVisible(3, false);
		mr.setVisible(4, false);
		mr.setVisible(5, false);
		mr.setUnit("tilt");
		mr.setLocation(component.getBoxX(), component.getBoxY(), component.getBoxWidth(), component.getBoxHeight());
//		mr.setExtents(start, end, td.min(2), td.max(2));
		mr.setExtents(start, end, min, max);
		mr.createDefaultAxis(8, 8, false, true);
		mr.createDefaultLineRenderers();
		//mr.createDefaultLegendRenderer(new String[] {ch + " RSAM"});
		mr.setXAxisToTime(8);
		mr.getAxis().setLeftLabelAsText("Tilt");
		mr.getAxis().setBottomLabelAsText("Time");//(Data from " + Valve.DATE_FORMAT.format(Util.j2KToDate(d.getMinTime())) +
//				" to " + Valve.DATE_FORMAT.format(Util.j2KToDate(d.getMaxTime())) + ")");
		plot.addRenderer(mr);
		plot.writePNG(Valve3.getInstance().getApplicationPath() + File.separatorChar + v3Plot.getFilename());
		component.setTranslation(mr.getDefaultTranslation(plot.getHeight()));
		component.setTranslationType("ty");
		v3Plot.addComponent(component);
		
		v3Plot.setTitle("Tilt");
	}
}
