package gov.usgs.volcanoes.valve3.result;

/**
 * Result of unsuccessful operation, keeps error message.
 *
 * @author Dan Cervelli
 */
public class ErrorMessage extends Result {
  protected String message;

  /**
   * Constructor.
   *
   * @param m error message to store in object
   */
  public ErrorMessage(String m) {
    message = m;
  }

  /**
   * Yield XML representation.
   *
   * @return String with xml representation of error message result
   */
  public String toXml() {
    StringBuffer sb = new StringBuffer();
    sb.append("\t<error>\n");
    sb.append("\t\t<message>" + message + "</message>\n");
    sb.append("\t</error>\n");
    return toXml("error", sb.toString());
  }
}
