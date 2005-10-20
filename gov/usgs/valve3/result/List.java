package gov.usgs.valve3.result;

/**
 * 
 * $Log: not supported by cvs2svn $
 * Revision 1.1  2005/08/26 20:41:31  dcervelli
 * Initial avosouth commit.
 *
 * @author Dan Cervelli
 */
public class List extends Result
{
	public java.util.List list;
	
	public List(java.util.List l)
	{
		list = l;
	}
	
	public String toXML()
	{
		StringBuilder sb = new StringBuilder();
		for (Object o : list)
			sb.append("<list-item>" + o.toString() + "</list-item>");
		
		return toXML("list", sb.toString());
	}
}
