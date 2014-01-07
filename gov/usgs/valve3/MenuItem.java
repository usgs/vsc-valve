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
	public boolean bestPossible;
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
	 * @param plotSeparately plot items separately
	 * @param biasType menu item type of bias removed
	 */
	public MenuItem(String menuId, String name, String icon, String file, int sortOrder, String shortcuts, char lineType, boolean plotSeparately, boolean bestPossible, char biasType)
	{
		this.menuId			= menuId;
		this.name			= name;
		this.icon			= icon;
		this.file			= file;
		this.sortOrder		= sortOrder;
		this.shortcuts		= shortcuts;
		this.lineType 		= lineType;
		this.plotSeparately = plotSeparately;
		this.bestPossible	= bestPossible;
		this.biasType 		= biasType;
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
			  "<bestPossible>" + new Boolean(bestPossible) + "</bestPossible>\n" +
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
