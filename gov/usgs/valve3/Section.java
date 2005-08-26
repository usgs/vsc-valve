package gov.usgs.valve3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class Section implements Comparable<Section>
{
	public int sortOrder;
	public String name;
	public String icon;
	
	public List<MenuItem> menuItems;
	
	public Section(String n, String i, int so)
	{
		name = n;
		icon = i;
		sortOrder = so;
		menuItems = new ArrayList<MenuItem>(5);
	}
	
	public void addMenuItem(MenuItem mi)
	{
		menuItems.add(mi);
	}
	
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
		sb.append("</section>\n");
		return sb.toString();
	}

	public int compareTo(Section os)
	{
		return sortOrder - os.sortOrder;
	}
}
