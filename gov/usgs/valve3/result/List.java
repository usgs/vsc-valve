package gov.usgs.valve3.result;

import java.util.Iterator;

/**
 * 
 * $Log: not supported by cvs2svn $
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
		StringBuffer sb = new StringBuffer();
		for (Iterator it = list.iterator(); it.hasNext(); )
			sb.append("<list-item>" + it.next().toString() + "</list-item>");
		
		return toXML("list", sb.toString());
	}
}
