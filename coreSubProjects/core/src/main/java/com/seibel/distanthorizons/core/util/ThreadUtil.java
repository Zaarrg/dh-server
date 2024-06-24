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

package com.seibel.distanthorizons.core.util;

import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.util.threading.DhThreadFactory;
import com.seibel.distanthorizons.core.util.threading.RateLimitedThreadPoolExecutor;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.coreapi.ModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;

/**
 * Handles thread pool creation.
 * 
 * @see ThreadPoolUtil
 * @see TimerUtil
 */
public class ThreadUtil
{
	private static final Logger LOGGER = LogManager.getLogger();
	
	public static final String THREAD_NAME_PREFIX = ModInfo.THREAD_NAME_PREFIX;
	
	/** used to track and remove old listeners for certain pools if the thread pool is recreated. */
	private static final ConcurrentHashMap<String, ConfigChangeListener<Double>> THREAD_CHANGE_LISTENERS_BY_THREAD_NAME = new ConcurrentHashMap<>();
	
	// TODO move all "Runtime.getRuntime().availableProcessors()" calls here
	
	
	
	//===================//
	// rate limited pool //
	//===================//
	
	public static RateLimitedThreadPoolExecutor makeRateLimitedThreadPool(int poolSize, DhThreadFactory threadFactory, ConfigEntry<Double> runTimeRatioConfigEntry, Semaphore activeThreadCountSemaphore)
	{
		// remove the old listener if one exists
		if (THREAD_CHANGE_LISTENERS_BY_THREAD_NAME.containsKey(threadFactory.threadName))
		{
			// note: this assumes only one thread pool exists with a given name
			THREAD_CHANGE_LISTENERS_BY_THREAD_NAME.get(threadFactory.threadName).close();
			THREAD_CHANGE_LISTENERS_BY_THREAD_NAME.remove(threadFactory.threadName);
		}
		
		if (!threadFactory.threadName.startsWith(THREAD_NAME_PREFIX))
		{
			// this will only happen if a ThreadFactory is passed in that doesn't have the correct thread name
			LOGGER.warn("Thread pool with the name ["+threadFactory.threadName+"] is missing the expected Distant Horizons thread prefix ["+THREAD_NAME_PREFIX+"].");
		}
		
		
		RateLimitedThreadPoolExecutor executor = makeRateLimitedThreadPool(poolSize, runTimeRatioConfigEntry.get(), threadFactory, activeThreadCountSemaphore);
		
		ConfigChangeListener<Double> changeListener = new ConfigChangeListener<>(runTimeRatioConfigEntry, (newRunTimeRatio) -> { executor.runTimeRatio = newRunTimeRatio; });
		THREAD_CHANGE_LISTENERS_BY_THREAD_NAME.put(threadFactory.threadName, changeListener);
		
		return executor;
	}
	
	
	/** should only be used if there isn't a config controlling the run time ratio of this thread pool */
	public static RateLimitedThreadPoolExecutor makeRateLimitedThreadPool(int poolSize, String name, Double runTimeRatio, int threadPriority, Semaphore activeThreadCountSemaphore) 
	{
		return new RateLimitedThreadPoolExecutor(poolSize, runTimeRatio, new DhThreadFactory(name, threadPriority), activeThreadCountSemaphore);
	}
	public static RateLimitedThreadPoolExecutor makeRateLimitedThreadPool(int poolSize, Double runTimeRatio, DhThreadFactory threadFactory, Semaphore activeThreadCountSemaphore) 
	{
		return new RateLimitedThreadPoolExecutor(poolSize, runTimeRatio, threadFactory, activeThreadCountSemaphore);
	}
	
	
	
	//===============//
	// standard pool // 
	//===============//
	
	public static ThreadPoolExecutor makeThreadPool(int poolSize, String name, int priority)
	{
		// this is what was being internally used by Executors.newFixedThreadPool
		// I'm just calling it explicitly here so we can reference the more feature-rich
		// ThreadPoolExecutor vs the more generic ExecutorService
		return new ThreadPoolExecutor(/*corePoolSize*/ poolSize, /*maxPoolSize*/ poolSize,
				0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<Runnable>(),
				new DhThreadFactory(name, priority));
	}
	
	public static ThreadPoolExecutor makeThreadPool(int poolSize, Class<?> clazz, int priority) { return makeThreadPool(poolSize, clazz.getSimpleName(), priority); }
	public static ThreadPoolExecutor makeThreadPool(int poolSize, String name) { return makeThreadPool(poolSize, name, Thread.NORM_PRIORITY); }
	public static ThreadPoolExecutor makeThreadPool(int poolSize, Class<?> clazz) { return makeThreadPool(poolSize, clazz.getSimpleName(), Thread.NORM_PRIORITY); }
	
	
	
	//====================//
	// single thread pool //
	//====================//
	
	public static ThreadPoolExecutor makeSingleThreadPool(String name, int priority) { return makeThreadPool(1, name, priority); }
	public static ThreadPoolExecutor makeSingleThreadPool(Class<?> clazz, int priority) { return makeThreadPool(1, clazz.getSimpleName(), priority); }
	public static ThreadPoolExecutor makeSingleThreadPool(String name) { return makeThreadPool(1, name, Thread.NORM_PRIORITY); }
	public static ThreadPoolExecutor makeSingleThreadPool(Class<?> clazz) { return makeThreadPool(1, clazz.getSimpleName(), Thread.NORM_PRIORITY); }
	
	
}
