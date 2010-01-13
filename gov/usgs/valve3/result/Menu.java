package gov.usgs.valve3.result;

import gov.usgs.valve3.Section;
import gov.usgs.valve3.Valve3;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Menu to display on the valve main screen
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.3  2005/10/26 18:43:40  tparker
 * bug Id #68
 *
 * Revision 1.2  2005/10/26 17:59:15  tparker
 * Add timezone for Bug #68
 *
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
public class Menu extends Result
{
	private List<Section> sections;
	
	/**
	 * Constructor
	 * @param s list of sections
	 */
	public Menu(List<Section> s)
	{
		sections = s;
	}
	
	/**
	 * Menu xml representation
	 */
	public String toXML()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("\t<menu>\n");
		sb.append("\t\t<title>" + Valve3.getInstance().getInstallationTitle() + "</title>\n");
		sb.append("\t\t<administrator><![CDATA[" + Valve3.getInstance().getAdministrator() + "]]></administrator>\n");
		sb.append("\t\t<administrator-email><![CDATA[" + Valve3.getInstance().getAdministratorEmail() + "]]></administrator-email>\n");
		sb.append("\t\t<timeZoneAbbr>" + Valve3.getInstance().getTimeZoneAbbr() + "</timeZoneAbbr>\n");
		sb.append("\t\t<timeZoneOffset>" + Valve3.getInstance().getTimeZoneOffset() + "</timeZoneOffset>\n");
		sb.append("\t\t<version>" + Valve3.VERSION + ", " + Valve3.BUILD_DATE + "</version>\n");
		sb.append("\t\t<sections>\n");
		
		Collections.sort(sections);
		for (Iterator it = sections.iterator(); it.hasNext(); )
		{
			Section section = (Section)it.next();
			sb.append(section.toXML());
		}
		sb.append("\t\t</sections>\n");
		sb.append("\t</menu>\n");
		return toXML("menu", sb.toString());
	}
}
