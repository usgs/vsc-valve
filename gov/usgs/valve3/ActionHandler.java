package gov.usgs.valve3;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Keeps map of pairs action name-http handler, apply action with given name to http requests.
 * 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class ActionHandler implements HttpHandler
{
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
	 * 
	 * @return map of pairs action name-http handler
	 */
	public Map<String, HttpHandler> getHandlers()
	{
		return handlers;
	}
	
	/**
	 * apply action with name containing in the 'key' field to http requests.
	 * See {@link HttpHandler}
	 */
	public Object handle(HttpServletRequest request)
	{
		System.out.println(request.getQueryString());
		String action = request.getParameter(key);
		System.out.println("Action: " + action);
		if (action == null)
			return null;
		
		HttpHandler handler = handlers.get(action);
		if (handler == null)
			return null;

		return handler.handle(request);
	}
}
