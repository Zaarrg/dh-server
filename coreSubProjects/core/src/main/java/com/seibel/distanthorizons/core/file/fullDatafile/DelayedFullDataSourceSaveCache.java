package com.seibel.distanthorizons.core.file.fullDatafile;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.TimerUtil;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Used to batch together multiple data source updates that all
 * affect the same position.
 */
public class DelayedFullDataSourceSaveCache
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	private static final Timer DELAY_UPDATE_TIMER = TimerUtil.CreateTimer("Delayed Full Datasource Save Timer");
	
	
	public final ConcurrentHashMap<Long, FullDataSourceV2> dataSourceByPosition = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, TimerTask> saveTimerTasksBySectionPos = new ConcurrentHashMap<>();
	
	private final ISaveDataSourceFunc onSaveTimeoutFunc;
	private final int saveDelayInMs;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DelayedFullDataSourceSaveCache(@NotNull ISaveDataSourceFunc onSaveTimeoutFunc, int saveDelayInMs)
	{
		this.onSaveTimeoutFunc = onSaveTimeoutFunc;
		this.saveDelayInMs = saveDelayInMs;
	}
	
	
	
	//==============//
	// update queue //
	//==============//
	
	public void queueDataSourceForUpdateAndSave(FullDataSourceV2 inputDataSource)
	{
		long dataSourcePos = inputDataSource.getPos();
		this.dataSourceByPosition.compute(dataSourcePos, (inputPos, temporaryDataSource) ->
		{
			if (temporaryDataSource == null)
			{
				temporaryDataSource = FullDataSourceV2.createEmpty(inputPos);
			}
			temporaryDataSource.update(inputDataSource);
			
			
			TimerTask timerTask = new TimerTask()
			{
				@Override
				public void run()
				{
					DelayedFullDataSourceSaveCache.this.saveTimerTasksBySectionPos.remove(dataSourcePos);
					
					try
					{
						FullDataSourceV2 dataSourceToSave = DelayedFullDataSourceSaveCache.this.dataSourceByPosition.remove(dataSourcePos);
						if (dataSourceToSave != null)
						{
							DelayedFullDataSourceSaveCache.this.onSaveTimeoutFunc.save(dataSourceToSave);
						}
					}
					catch (Exception e) // this can throw errors (not exceptions) when installed in Iris' dev environment for some reason due to an issue with LZ4's compression library
					{
						LOGGER.error("Failed to save updated data for section ["+dataSourcePos+"], error: ["+e.getMessage()+"]", e);
					}
				}
			};
			try
			{
				DELAY_UPDATE_TIMER.schedule(timerTask, this.saveDelayInMs);
			}
			catch (IllegalStateException ignore)
			{
				// James isn't sure why this is possible since this logic is inside a lock, 
				// maybe the timer is just async enough that there can be problems?
				LOGGER.warn("Attempted to queue an already canceled task. Pos: ["+dataSourcePos+"], task already queued for pos: ["+this.saveTimerTasksBySectionPos.containsKey(dataSourcePos)+"]");
			}
			
			
			// cancel the old save timer if present
			// (this is equivalent to restarting the timer)
			TimerTask oldTask = this.saveTimerTasksBySectionPos.put(dataSourcePos, timerTask);
			if (oldTask != null)
			{
				oldTask.cancel();
			}
			
			return temporaryDataSource;
		});
	}
	
	public int getUnsavedCount() { return this.dataSourceByPosition.size(); }
	
	
	
	//================//
	// helper classes //
	//================//
	
	@FunctionalInterface
	public interface ISaveDataSourceFunc
	{
		/** called after the timeout expires */
		void save(FullDataSourceV2 inputDataSource);
	}
	
}
