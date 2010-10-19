package gov.usgs.valve3;

/**
 * Represents item inside menu
 * 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class MenuItem implements Comparable<MenuItem>
{
	public String menuId;
	public String name;
	public String icon;
	public String file;
	public int sortOrder;
	public String shortcuts;
	public char lineType;
	public boolean plotSeparately;
	public char biasType;
	
	/**
	 * Constructor
	 * @param id menu id
	 * @param n menu item name
	 * @param i icon for menu item
	 * @param f menu file
	 * @param so menu item sort order
	 * @param sc menu item shortcuts
	 * @param lt menu item line type
	 * @param splotSeparately plot items separately
	 * @param biasType menu item type of bias removed
	 */
	public MenuItem(String id, String n, String i, String f, int so, String sc, char lt, boolean plotSeparately, char biasType)
	{
		menuId		= id;
		name		= n;
		icon		= i;
		file		= f;
		sortOrder	= so;
		shortcuts	= sc;
		this.lineType = lt;
		this.plotSeparately = plotSeparately;
		this.biasType = biasType;
	}
	
	/**
	 * Yield XML representation
	 * @return menu item XML representation
	 */
	public String toXML()
	{
		String xml = "";
		xml	= "<menuitem>\n" +
			  "<menuid>" + menuId + "</menuid>\n" +
			  "<name>" + name + "</name>\n" +
			  "<icon>" + icon + "</icon>\n" +
			  "<file>" + file + "</file>\n" +
			  "<timeShortcuts>" + shortcuts + "</timeShortcuts>\n" +
			  "<lineType>" + lineType + "</lineType>\n" +
			  "<plotSeparately>" + new Boolean(plotSeparately) + "</plotSeparately>\n" +
			  "<biasType>" + biasType + "</biasType>\n" + 
			  "</menuitem>\n";
		return xml;
	}

	/**
	 * Comparator, @see Comparable
	 */
	public int compareTo(MenuItem omi)
	{
		return sortOrder - omi.sortOrder;
	}
}
