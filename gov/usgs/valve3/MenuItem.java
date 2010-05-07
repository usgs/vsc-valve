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
	public boolean plotSeparately;
	
	/**
	 * Constructor
	 * @param id menu id
	 * @param n menu item name
	 * @param i icon for menu item
	 * @param f menu file
	 * @param so menu item sort order
	 */
	public MenuItem(String id, String n, String i, String f, int so, String sc, boolean plotSeparately)
	{
		menuId		= id;
		name		= n;
		icon		= i;
		file		= f;
		sortOrder	= so;
		shortcuts	= sc;
		this.plotSeparately = plotSeparately;
	}
	
	/**
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
			  "<plotSeparately>" + new Boolean(plotSeparately) + "</plotSeparately>\n" +
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
