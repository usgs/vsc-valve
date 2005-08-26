package gov.usgs.valve3;

import gov.usgs.valve3.result.Result;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * $Log: not supported by cvs2svn $
 * @author Dan Cervelli
 */
public class ResultDeleter extends Thread
{
	private static final int DELETE_DELAY = 60 * 1000;
	private static final int DELETE_THRESHOLD =  10 * 60 * 1000;
	private List<Entry> results;
	private boolean kill = false;
	
	public ResultDeleter()
	{
		results = new ArrayList<Entry>(100);
	}
	
	public void kill()
	{
		kill = true;
		this.interrupt();
	}
	
	public synchronized void addResult(Result result)
	{
		results.add(new Entry(result));
	}

	private synchronized void deleteResult(int i)
	{
		results.remove(i);
	}
	
	public void deleteResults(boolean force)
	{
		long now = System.currentTimeMillis();
		for (int i = 0; i < results.size(); i++)
		{
			Entry e = results.get(i);
			if (force || (now - e.time > DELETE_THRESHOLD))
			{
				e.result.delete();
				deleteResult(i);
				i--;
			}
		}
	}
	
	public void run()
	{
		while (!kill)
		{
			try
			{
				Thread.sleep(DELETE_DELAY);
				deleteResults(false);
			}
			catch (InterruptedException e)
			{}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	private class Entry
	{
		public long time;
		public Result result;
		
		public Entry(Result r)
		{
			time = System.currentTimeMillis();
			result = r;
		}
	}
}
