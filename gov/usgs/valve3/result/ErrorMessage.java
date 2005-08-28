package gov.usgs.valve3.result;

/**
 * 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class ErrorMessage extends Result
{
	protected String message;
	
	public ErrorMessage(String m)
	{
		message = m;
	}
	
	public String toXML()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("\t<error>\n");
		sb.append("\t\t<message>" + message + "</message>\n");
		sb.append("\t</error>\n");
		return toXML("error", sb.toString());
	}
}
