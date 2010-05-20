package gov.usgs.valve3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents section in the valve data sources list
 * 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class Section implements Comparable<Section>
{
	public int sortOrder;
	public String name;
	public String icon;
	public boolean expanded;
	
	public List<MenuItem> menuItems;
	
	/**
	 * Constructor
	 * @param n section name
	 * @param i section icon file name
	 * @param so section sort order
	 * @param e section initally expanded?
	 */
	public Section(String n, String i, int so, boolean e)
	{
		name = n;
		icon = i;
		sortOrder = so;
		expanded = e;
		menuItems = new ArrayList<MenuItem>(5);
	}
	
	/**
	 * Adds menu item to display inside this section's menu
	 */
	public void addMenuItem(MenuItem mi)
	{
		menuItems.add(mi);
	}
	
	/**
	 * @return section xml representation 
	 */
	public String toXML()
	{
		Collections.sort(menuItems);
		StringBuffer sb = new StringBuffer();
		sb.append("<section>\n");
		sb.append("<name>" + name + "</name>\n");
		if (icon != null)
			sb.append("<icon>" + icon + "</icon>\n");
		sb.append("<menuitems>\n");
		for (Iterator it = menuItems.iterator(); it.hasNext(); )
			sb.append(((MenuItem)it.next()).toXML());
		sb.append("</menuitems>\n");
		sb.append("<expanded>" + expanded + "</expanded>");
		sb.append("</section>\n");
		return sb.toString();
	}

	/**
	 * Comparator, @see Comparable
	 */
	public int compareTo(Section os)
	{
		return sortOrder - os.sortOrder;
	}
}
