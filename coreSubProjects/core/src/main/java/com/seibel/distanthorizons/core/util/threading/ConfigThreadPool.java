/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *    
 *    Copyright (C) 2020-2023 James Seibel
 *    
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *    
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *    
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.util.threading;

import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.util.ThreadUtil;

import java.util.concurrent.Semaphore;

/**
 * Handles thread pools with config values for their
 * thread count and run time ratio.
 */
public class ConfigThreadPool
{
	/** Caution must be used to prevent deadlock */
	private final Semaphore activeThreadCountSemaphore;
	
	public RateLimitedThreadPoolExecutor executor = null;
	private int threadCount = 0;
	public int getThreadCount() { return this.threadCount; }
	
	public final DhThreadFactory threadFactory;
	
	public final ConfigChangeListener<Integer> threadCountConfigListener;
	public final ConfigEntry<Integer> threadCountConfig;
	public final ConfigEntry<Double> runTimeRatioConfig;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ConfigThreadPool(DhThreadFactory threadFactory, ConfigEntry<Integer> threadCountConfig, ConfigEntry<Double> runTimeRatioConfig, Semaphore activeThreadCountSemaphore)
	{
		this.threadFactory = threadFactory;
		this.activeThreadCountSemaphore = activeThreadCountSemaphore;
		
		this.threadCountConfig = threadCountConfig;
		this.threadCountConfigListener = new ConfigChangeListener<>(threadCountConfig, 
				(threadCount) -> { this.setThreadPoolSize(threadCount); });
		this.runTimeRatioConfig = runTimeRatioConfig;
		
		this.setThreadPoolSize(threadCountConfig.get());
	}
	
	
	
	
	//==============//
	// thread setup //
	//==============//
	
	public void setThreadPoolSize(int threadPoolSize)
	{
		if (this.executor != null)
		{
			// close the previous thread pool if one exists
			this.executor.shutdown();
		}
		
		this.threadCount = threadPoolSize;
		this.executor = ThreadUtil.makeRateLimitedThreadPool(this.threadCount, this.threadFactory, this.runTimeRatioConfig, this.activeThreadCountSemaphore);
	}
	
	/**
	 * Stops any executing tasks and destroys the executor. <br>
	 * Does nothing if the executor isn't running.
	 */
	public void shutdownExecutorService()
	{
		if (this.executor != null)
		{
			//LOGGER.info("Stopping thread pool");
			this.executor.shutdownNow();
		}
		
		this.threadCount = 0;
	}
	
	
}
