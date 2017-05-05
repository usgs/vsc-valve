package gov.usgs.volcanoes.valve3;

import gov.usgs.volcanoes.valve3.result.Result;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps list of {@link Entry}s to manage result set
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

	/**
	 * Default constructor
	 */
	public ResultDeleter()
	{
		results = new ArrayList<Entry>(100);
	}
	
	/**
	 * Kills execution thread
	 */
	public void kill()
	{
		kill = true;
		this.interrupt();
	}
	
	/**
	 * Adds new entry to managed list
	 * @param result {@link Result} contained in the entry
	 */
	public synchronized void addResult(Result result)
	{
		results.add(new Entry(result));
	}

	/**
	 * Delete entry from managed list
	 * @param i Serial number of entry to delete
	 */
	private synchronized void deleteResult(int i)
	{
		results.remove(i);
	}
	
	/**
	 * Deletes entries from managed list
	 * @param force if true, deletes all entries. If false, deletes only old ones, older then DELETE_THRESHOLD ms.
	 */
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
	
	/**
	 * Main execution thread. Call deleteResults() in non-force manner every DELETE_DELAY ms
	 */
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

	/**
	 * Supporting class, entry in managed list, keeps result and it's time
	 *
	 */
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
