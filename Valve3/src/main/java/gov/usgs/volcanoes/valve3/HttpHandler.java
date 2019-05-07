package gov.usgs.volcanoes.valve3;

import javax.servlet.http.HttpServletRequest;

/**
 * Generic interface to handle with HttpServletRequest.
 *
 * @author Dan Cervelli
 */
public interface HttpHandler {
  /**
   * Handle the given request and generate an appropriate response.
   *
   * @param request Request got from js user interface or manually entered in the browser
   * @return constructed Java object - for example menu or plot.
   * @throws Valve3Exception exception
   */
  public Object handle(HttpServletRequest request) throws Valve3Exception;
}
