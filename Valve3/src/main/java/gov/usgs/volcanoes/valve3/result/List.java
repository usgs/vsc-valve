package gov.usgs.volcanoes.valve3.result;

/**
 * Result which contains list of items.
 *
 * @author Dan Cervelli
 */
public class List extends Result {
  public java.util.List list;

  /**
   * Constructor.
   *
   * @param l list of objects to store in result
   */
  public List(java.util.List l) {
    list = l;
  }

  /**
   * Yield XML representation.
   *
   * @return XML representation of this object
   */
  public String toXml() {
    StringBuilder sb = new StringBuilder();
    for (Object o : list) {
      sb.append("<list-item>" + o.toString() + "</list-item>");
    }

    return toXml("list", sb.toString());
  }
}
