package gov.usgs.volcanoes.valve3.result;

import gov.usgs.volcanoes.valve3.Section;
import gov.usgs.volcanoes.valve3.Valve3;
import gov.usgs.volcanoes.valve3.Version;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Menu to display on the valve main screen.
 *
 * @author Dan Cervelli
 */
public class Menu extends Result {
  private List<Section> sections;

  /**
   * Constructor.
   *
   * @param s list of sections
   */
  public Menu(List<Section> s) {
    sections = s;
  }

  /**
   * Menu xml representation.
   */
  public String toXml() {
    StringBuffer sb = new StringBuffer();
    sb.append("\t<menu>\n");
    sb.append("\t\t<title>" + Valve3.getInstance().getInstallationTitle() + "</title>\n");
    sb.append("\t\t<administrator><![CDATA[" + Valve3.getInstance().getAdministrator()
              + "]]></administrator>\n");
    sb.append("\t\t<administrator-email><![CDATA[" + Valve3.getInstance().getAdministratorEmail()
              + "]]></administrator-email>\n");
    sb.append("\t\t<timeZoneAbbr>" + Valve3.getInstance().getTimeZoneAbbr() + "</timeZoneAbbr>\n");
    sb.append("\t\t<version>" + Version.POM_VERSION + "</version>\n");
    sb.append("\t\t<sections>\n");

    Collections.sort(sections);
    for (Iterator it = sections.iterator(); it.hasNext(); ) {
      Section section = (Section) it.next();
      sb.append(section.toXml());
    }
    sb.append("\t\t</sections>\n");
    sb.append("\t</menu>\n");
    return toXml("menu", sb.toString());
  }
}
