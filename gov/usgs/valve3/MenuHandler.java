package gov.usgs.valve3;

import gov.usgs.util.ConfigFile;
import gov.usgs.valve3.data.DataHandler;
import gov.usgs.valve3.data.DataSourceDescriptor;
import gov.usgs.valve3.result.Menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * 
 * $Log: not supported by cvs2svn $
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
	private Map<String, Section> sections;
	
	public MenuHandler(DataHandler dh)
	{
		sections = new HashMap<String, Section>();
		
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
					String menu = cf.getString("menu");
					String name = cf.getString("name");
					int sortOrder = Integer.parseInt(cf.getString("sortOrder"));
					MenuItem item = new MenuItem(dsd.getName(), name, "", menu, sortOrder);
					section.addMenuItem(item);
				}
			}
		}
	}
	
	// TODO: cache
	// TODO: sortOrder
	public List<Section> getSections()
	{
		ArrayList<Section> list = new ArrayList<Section>();
		for (Section section : sections.values())
			list.add(section);
		
		return list;
	}
	
	public Object handle(HttpServletRequest request)
	{
		return new Menu(getSections());
	}
}
