package gov.usgs.valve3;

import javax.servlet.http.HttpServletRequest;

/**
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public interface HttpHandler
{
	public Object handle(HttpServletRequest request);
}
