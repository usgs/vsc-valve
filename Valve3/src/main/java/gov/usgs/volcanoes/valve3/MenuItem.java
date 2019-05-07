package gov.usgs.volcanoes.valve3;

/**
 * Represents item inside menu.
 *
 * @author Dan Cervelli
 */
public class MenuItem implements Comparable<MenuItem> {
  public String menuId;
  public String name;
  public String icon;
  public String file;
  public int sortOrder;
  public String shortcuts;
  public char lineType;
  public boolean plotSeparately;
  public boolean barDisplay;
  public boolean barDefault;
  public char biasType;

  /**
   * Constructor.
   *
   * @param menuId menu id
   * @param name menu item name
   * @param icon icon for menu item
   * @param file menu file
   * @param sortOrder menu item sort order
   * @param shortcuts menu item shortcuts
   * @param lineType menu item line type
   * @param plotSeparately plot items separately
   * @param biasType       menu item type of bias removed
   */
  public MenuItem(String menuId, String name, String icon, String file, int sortOrder,
                  String shortcuts, char lineType, boolean plotSeparately, boolean bestAvailable,
                  boolean bestAvailableDefault, char biasType) {
    this.menuId = menuId;
    this.name = name;
    this.icon = icon;
    this.file = file;
    this.sortOrder = sortOrder;
    this.shortcuts = shortcuts;
    this.lineType = lineType;
    this.plotSeparately = plotSeparately;
    this.barDisplay = bestAvailable;
    this.barDefault = bestAvailableDefault;
    this.biasType = biasType;
  }

  /**
   * Yield XML representation.
   *
   * @return menu item XML representation
   */
  public String toXml() {
    String xml = "";
    xml = "<menuitem>\n"
        + "<menuid>" + menuId + "</menuid>\n"
        + "<name>" + name + "</name>\n"
        + "<icon>" + icon + "</icon>\n"
        + "<file>" + file + "</file>\n"
        + "<timeShortcuts>" + shortcuts + "</timeShortcuts>\n"
        + "<lineType>" + lineType + "</lineType>\n"
        + "<plotSeparately>" + new Boolean(plotSeparately) + "</plotSeparately>\n"
        + "<barDisplay>" + new Boolean(barDisplay) + "</barDisplay>\n"
        + "<barDefault>" + new Boolean(barDefault) + "</barDefault>\n"
        + "<biasType>" + biasType + "</biasType>\n"
        + "</menuitem>\n";
    return xml;
  }

  /**
   * Comparator, @see Comparable.
   */
  public int compareTo(MenuItem omi) {
    return sortOrder - omi.sortOrder;
  }
}
