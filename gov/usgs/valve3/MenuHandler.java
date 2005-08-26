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
//		for (Iterator it = sources.iterator(); it.hasNext(); )
		for (DataSourceDescriptor dsd : sources)
		{
//			DataSourceDescriptor dsd = (DataSourceDescriptor)it.next();
			Map<String, Object> params = dsd.getParams();
			String sec = (String)params.get("section");
			if (sec != null)
			{
				Section section = sections.get(sec);
				if (section != null)
				{
					String menu = (String)params.get("menu");
					String name = (String)params.get("name");
					int sortOrder = Integer.parseInt((String)params.get("sortOrder"));
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
		//for (Iterator it = sections.values().iterator(); it.hasNext(); )
		for (Section section : sections.values())
			list.add(section);
		
		//Collections.sort(list);
		return list;
	}
	
	public Object handle(HttpServletRequest request)
	{
		return new Menu(getSections());
	}
}
