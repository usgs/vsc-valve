package gov.usgs.volcanoes.valve3;

import gov.usgs.util.Util;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps map of pairs action name-http handler, apply action with given name to http requests.
 * 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class ActionHandler implements HttpHandler
{
	private final static Logger logger = LoggerFactory.getLogger(ActionHandler.class);
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
	public Object handle(HttpServletRequest request) throws Valve3Exception {
		
		// log the request to the log file
		logger.info("{}", request.getQueryString());
		
		// get the parameter, default to "plot" if not specified
		String action = Util.stringToString(request.getParameter(key), "plot");
		
		// lookup the handler from the map
		HttpHandler handler = handlers.get(action);
		if (handler == null)
			return null;

		return handler.handle(request);
	}
}
