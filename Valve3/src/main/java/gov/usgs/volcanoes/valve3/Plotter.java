package gov.usgs.volcanoes.valve3;

import java.awt.Color;
import java.io.OutputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;

import gov.usgs.volcanoes.core.legacy.plot.Plot;
import gov.usgs.volcanoes.core.legacy.plot.PlotException;
import gov.usgs.volcanoes.core.configfile.ConfigFile;
import gov.usgs.volcanoes.vdx.ExportConfig;
import gov.usgs.volcanoes.core.legacy.util.Pool;
import gov.usgs.volcanoes.valve3.result.Valve3Plot;
import gov.usgs.volcanoes.valve3.Valve3;
import gov.usgs.volcanoes.valve3.Valve3Exception;
import gov.usgs.volcanoes.vdx.client.VDXClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for plotter, all concrete plotters
 * should extend this class
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.3  2005/10/13 20:34:32  dcervelli
 * Added plotterConfig.
 *
 * Revision 1.2  2005/08/28 19:00:46  dcervelli
 * plot() now throws exceptions.
 *
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
abstract public class Plotter
{
	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected String vdxClient;
	protected String vdxSource;
	
	protected boolean forExport;
	
	protected ConfigFile plotterConfig;
	
	/**
	 * Setter for vdx name
	 * @param c vdx name ("....vdx" config file parameter)
	 */
	public void setVDXClient(String c)
	{
		vdxClient = c;
	}
	
	/**
	 * Setter for vdx source name
	 * @param s vdx source name ("....vdx.source" parameter)
	 */
	public void setVDXSource(String s)
	{
		vdxSource = s;
	}
	
	/**
	 * Setter for plotter configuration
	 * @param cf configuration subset for plotter
	 */
	public void setPlotterConfig(ConfigFile cf)
	{
		plotterConfig = cf;
	}
	
	/**
	 * Exports PlotComponent to export format
	 * @param comp PlotComponent
	 * @param cmt comment
	 * @return string with exported data
	 * @throws Valve3Exception
	 */
	public String toExport(PlotComponent comp, Map<String,String> cmt, OutputStream seedOut) throws Valve3Exception
	{
		throw new Valve3Exception("Data export not available for this data source.");
	}
	
	
	/**
	 * Returns flag if plotter output several components separately or as one plot
	 * @return boolean flag
	 */
	public boolean isPlotSeparately(){
		String value = plotterConfig.getString("plotSeparately");
		if(value==null || !value.equals("true")){
			return false;
		} else {
			return true;
		}
	}
	
	/**
	 * Getter for plotter line type
	 * @return line type
	 * @throws Valve3Exception
	 */
	public char getLineType() throws Valve3Exception{
		String value = plotterConfig.getString("lineType");
		if(value==null){
			return 'l';
		} else {
			if(value.length() != 1)
				throw new Valve3Exception("Wrong line type: " + value);
			return value.charAt(0);
		}
	}

	/**
	 * renders PlotComponent in given plot
	 * @param v3p Valve3Plot
	 * @param comp PlotComponent
	 * @throws Valve3Exception
	 */
	abstract public void plot(Valve3Plot v3p, PlotComponent comp) throws Valve3Exception, PlotException;
	
	/**
	 * Yield export configuration for specified source & client
	 * @param vdxSource	vdx source name
	 * @param vdxClient	vdx name
	 * @return export config
	 */
	public ExportConfig getExportConfig(String vdxSource, String vdxClient) {
		
		// declare variables
		List<String> stringList = null;
		Pool<VDXClient> pool	= null;
		VDXClient client		= null;
		
		Map<String, String> params = new LinkedHashMap<String, String>();
		params.put("source", vdxSource);
		params.put("action", "exportinfo");
		
		Valve3 v3 = Valve3.getInstance();
		ExportConfig ec = v3.getExportConfig(vdxSource);
		if ( ec == null ) {
			
			// Build initial config from Valve parameters
			ec = v3.getExportConfig("");
			ec.parameterize( params );
			
			// Fold in overrides from VDX for this source
			pool = v3.getDataHandler().getVDXClient(vdxClient);
			if (pool != null) {
				client = pool.checkout();
				try {
					stringList = client.getTextData(params);
				} catch (Exception e){
					stringList = new ArrayList<String>();
				} finally {
					pool.checkin(client);
				}
				ec = new ExportConfig(stringList);
				v3.putExportConfig(vdxSource, ec);
			}
		}
		return ec;
	}

	/**
	 * Yield the sample rate
	 * @return sample rate
	 */
	public double getSampleRate() {
		return 0.0;
	}

	/**
	 * Yield the data type
	 * @return data type
	 */
	public String getDataType() {
		return null;
	}

	protected void writeFile(Valve3Plot v3p) throws PlotException, Valve3Exception {
		Plot plot = v3p.getPlot();
		plot.setBackgroundColor(Color.white);

		switch (v3p.getOutputType()) {
		case XML:
		case PNG:
			plot.writePNG(v3p.getLocalFilename());
			break;
		case PS:
			plot.writePS(v3p.getLocalFilename());
			break;
		default:
			throw new Valve3Exception("Unknown output type " + v3p.getOutputType());
		}

	}
}
