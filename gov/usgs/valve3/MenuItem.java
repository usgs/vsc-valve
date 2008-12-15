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
	
	/**
	 * Constructor
	 * @param id menu id
	 * @param n menu item name
	 * @param i icon for menu item
	 * @param f menu file
	 * @param so menu item sort order
	 */
	public MenuItem(String id, String n, String i, String f, int so)
	{
		menuId = id;
		name = n;
		icon = i;
		file = f;
		sortOrder = so;
	}
	
	/**
	 * @return menu item XML representation
	 */
	public String toXML()
	{
		return "<menuitem>\n<menuid>" + menuId + "</menuid>\n<name>" + name + 
			"</name>\n<icon>" + icon + "</icon>\n<file>" + file + "</file>\n</menuitem>";
	}

	/**
	 * Comparator, @see Comparable
	 */
	public int compareTo(MenuItem omi)
	{
		return sortOrder - omi.sortOrder;
	}
}
