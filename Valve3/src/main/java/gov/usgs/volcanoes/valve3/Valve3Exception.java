package gov.usgs.volcanoes.valve3;

/**
 * Specific exception for valve3 application
 * 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class Valve3Exception extends Exception
{
	private static final long serialVersionUID = 1L;
	
	/**
	 * Constructor
	 * @param m error message
	 */
	public Valve3Exception(String m)
	{
		super(m);
	}
}
