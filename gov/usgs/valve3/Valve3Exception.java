package gov.usgs.valve3;

public class Valve3Exception extends Exception
{
	private static final long serialVersionUID = 1L;
	
	public Valve3Exception(String m)
	{
		super(m);
		System.out.println("Valve3Exception: " + m);
	}
}
