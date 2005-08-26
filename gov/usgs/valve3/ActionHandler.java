package gov.usgs.valve3;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class ActionHandler implements HttpHandler
{
	protected Map<String, HttpHandler> handlers;
	protected String key;
	
	public ActionHandler(String k)
	{
		key = k;
		handlers = new HashMap<String, HttpHandler>();
	}

	public Map<String, HttpHandler> getHandlers()
	{
		return handlers;
	}
	
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
