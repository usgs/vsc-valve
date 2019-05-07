package gov.usgs.volcanoes.valve3.result;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps URL to generated result.
 *
 * @author Dan Cervelli
 */
public abstract class Result {
  protected final Logger logger = LoggerFactory.getLogger(getClass());
  protected String url;

  /**
   * Get URL.
   * @return URL to generated result.
   */
  public String getUrl() {
    return url;
  }

  /**
   * Setter for URL.
   *
   * @param u to set
   */
  public void setUrl(String u) {
    url = u;
  }

  /**
   * Deletes generated result.
   */
  public void delete() {
    logger.debug("Result.delete()");
  }

  /**
   * Yield XML representation.
   *
   * @param type   result type
   * @param nested String with xml representation of result's content
   * @return String with xml representation of generated result
   */
  public String toXml(String type, String nested) {
    StringBuffer sb = new StringBuffer();
    sb.append("<valve3result>\n");
    sb.append("\t<type>" + type + "</type>\n");
    //sb.append("\t<url>" + url + "</url>\n");
    sb.append(nested + "\n");
    sb.append("</valve3result>\n");
    return sb.toString();
  }

  public abstract String toXml();
}
