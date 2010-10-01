package gov.usgs.valve3;

import javax.servlet.http.HttpServletRequest;

/**
 * Generic interface to handle with HttpServletRequest
 * 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public interface HttpHandler
{
	/**
	 * Handle the given request and generate an appropriate response. 
	 * @param request Request got from js user interface or manually entered in the browser
	 * @return constructed Java object - for example menu or plot.
	 */
	public Object handle(HttpServletRequest request) throws Valve3Exception;
}
