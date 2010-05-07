package gov.usgs.valve3;

import gov.usgs.util.ConfigFile;
import gov.usgs.util.Util;
import gov.usgs.valve3.data.DataHandler;
import gov.usgs.valve3.data.DataSourceDescriptor;
import gov.usgs.valve3.result.Menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Generates menu from http request, 
 * keeps information about sections and menu items,
 * parse configuration to initialize them.
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.3  2006/04/09 18:17:49  dcervelli
 * ConfigFile changes to DataSourceDescriptor.
 *
 * Revision 1.2  2006/04/09 18:09:19  dcervelli
 * ConfigFile changes.
 *
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
public class MenuHandler implements HttpHandler
{
	/**
	 * map of section name-section pairs
	 */
	private Map<String, Section> sections;
	/**
	 * map of data source descriptor name-menu item pairs
	 */
	private Map<String, MenuItem> items;
	
	/**
	 * Constructor
	 * @param dh data handler for this menu handler
	 */
	public MenuHandler(DataHandler dh)
	{
		sections = new HashMap<String, Section>();
		items = new HashMap<String, MenuItem>();
		
		ConfigFile config = dh.getConfig();
		List<String> ss = config.getList("section");
		for (String sec : ss)
		{
			Section section = new Section(sec, "", Integer.parseInt(config.getString(sec + ".sortOrder")));
			sections.put(sec, section);
		}
		
		List<DataSourceDescriptor> sources = dh.getDataSources();
		for (DataSourceDescriptor dsd : sources)
		{
			ConfigFile cf = dsd.getConfig();
			String sec = cf.getString("section");
			if (sec != null)
			{
				Section section = sections.get(sec);
				if (section != null)
				{
					boolean plotSeparately = cf.getBoolean("plotter.plotComponentsSeparately");
					String menu 		= cf.getString("menu");
					String name 		= cf.getString("name");
					String shortcuts	= Util.stringToString(cf.getString("shortcuts"), "");
					int sortOrder 		= Integer.parseInt(cf.getString("sortOrder"));
					MenuItem item 		= new MenuItem(dsd.getName(), name, "", menu, sortOrder, shortcuts, plotSeparately);
					section.addMenuItem(item);
					items.put(item.menuId, item);
				}
			}
		}
	}
	
	// TODO: cache
	// TODO: sortOrder
	
	/**
	 * @return list of Sections associated with this menu handler
	 */
	public List<Section> getSections()
	{
		ArrayList<Section> list = new ArrayList<Section>();
		for (Section section : sections.values())
			list.add(section);
		
		return list;
	}
	
	/** 
	 * @param id menu item id
	 * @return menu item from internal list found by it's id
	 */
	public MenuItem getItem(String id)
	{
		return items.get(id);
	}

	/**
	 * Handle the given http request and generate an appropriate response. 
	 * @see HttpHandler#handle 
	 */
	public Object handle(HttpServletRequest request)
	{
		return new Menu(getSections());
	}
}
