package gov.usgs.valve3;

import gov.usgs.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

/**
 * Keeps map of pairs action name-http handler, apply action with given name to http requests.
 * 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class ActionHandler implements HttpHandler
{
	private final static Logger logger = Log.getLogger("gov.usgs.valve3.ActionHandler"); 
	protected Map<String, HttpHandler> handlers;
	protected String key;
	
	/**
	 * Constructor
	 * @param k name of action to apply 
	 */
	public ActionHandler(String k)
	{
		key = k;
		handlers = new HashMap<String, HttpHandler>();
	}

	/**
	 * Yield map of action names to http handlers
	 * @return map of pairs action name-http handler
	 */
	public Map<String, HttpHandler> getHandlers()
	{
		return handlers;
	}
	
	/**
	 * apply action with name containing in the 'key' field to http requests.
	 * See {@link HttpHandler}
	 * @param request - got http request
	 * @throws Valve3Exception
	 * @return result of handling request
	 */
	public Object handle(HttpServletRequest request) throws Valve3Exception
	{
		logger.info(request.getQueryString());
		String action = request.getParameter(key);
		if (action == null){
			action = Valve3.getInstance().getDefaults().getString("parameter."+key);
			if(action==null){
				if(key.equals("a"))
				action="plot";
			}
			if(action==null){
				throw new Valve3Exception("Can't find defaults for parameter " + key);
			}
			logger.info("Parameter "+ key +" was set to default value");
		}
		HttpHandler handler = handlers.get(action);
		if (handler == null)
			return null;

		return handler.handle(request);
	}
}
