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

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Holds each thread pool the system uses.
 * 
 * @see ThreadUtil
 */
public class ThreadPoolUtil
{
	//=========================//
	// standalone thread pools //
	//=========================//
	
	// standalone thread pools all handle independent systems
	// and don't interfere with any other pool
	
	public static final DhThreadFactory FILE_HANDLER_THREAD_FACTORY = new DhThreadFactory("File Handler", Thread.MIN_PRIORITY);
	private static ConfigThreadPool fileHandlerThreadPool;
	@Nullable
	public static ThreadPoolExecutor getFileHandlerExecutor() { return fileHandlerThreadPool.executor; }
	
	public static final DhThreadFactory UPDATE_PROPAGATOR_THREAD_FACTORY = new DhThreadFactory("LOD Update Propagator", Thread.MIN_PRIORITY);
	private static ConfigThreadPool updatePropagatorThreadPool;
	@Nullable
	public static ThreadPoolExecutor getUpdatePropagatorExecutor() { return updatePropagatorThreadPool.executor; }
	
	public static final DhThreadFactory WORLD_GEN_THREAD_FACTORY = new DhThreadFactory("World Gen", Thread.MIN_PRIORITY);
	private static ConfigThreadPool worldGenThreadPool;
	@Nullable
	public static ThreadPoolExecutor getWorldGenExecutor() { return worldGenThreadPool.executor; }
	
	public static final String BUFFER_UPLOADER_THREAD_NAME = "Buffer Uploader";
	private static ThreadPoolExecutor bufferUploaderThreadPool;
	@Nullable
	public static ThreadPoolExecutor getBufferUploaderExecutor() { return bufferUploaderThreadPool; }
	
	public static final String CLEANUP_THREAD_NAME = "Cleanup";
	private static ThreadPoolExecutor cleanupThreadPool;
	@Nullable
	public static ThreadPoolExecutor getCleanupExecutor() { return cleanupThreadPool; }
	
	
	
	//======================//
	// worker threads pools //
	//======================//
	
	// worker thread pools are generally related with LOD building
	// and all share an underlying number of threads.
	// WARNING: great care should be used when setting up these threads since deadlock can occur if they are handled poorly.
	
	public static final DhThreadFactory LIGHT_POPULATOR_THREAD_FACTORY = new DhThreadFactory("LOD Builder - Light Populator", Thread.MIN_PRIORITY);
	private static ConfigThreadPool lightPopulatorThreadPool;
	@Nullable
	public static ThreadPoolExecutor getLightPopulatorExecutor() { return (lightPopulatorThreadPool != null) ? lightPopulatorThreadPool.executor : null; }
	
	public static final DhThreadFactory CHUNK_TO_LOD_BUILDER_THREAD_FACTORY = new DhThreadFactory("LOD Builder - Chunk to Lod Builder", Thread.MIN_PRIORITY);
	private static ConfigThreadPool chunkToLodBuilderThreadPool;
	@Nullable
	public static ThreadPoolExecutor getChunkToLodBuilderExecutor() { return (chunkToLodBuilderThreadPool != null) ? chunkToLodBuilderThreadPool.executor : null; }
	
	public static final DhThreadFactory BUFFER_BUILDER_THREAD_FACTORY = new DhThreadFactory("LOD Builder - Buffer Builder", Thread.MIN_PRIORITY);
	private static ConfigThreadPool bufferBuilderThreadPool;
	@Nullable
	public static ThreadPoolExecutor getBufferBuilderExecutor() { return (bufferBuilderThreadPool != null) ? bufferBuilderThreadPool.executor : null; }
	
	
	/** how many total worker threads can be used */
	private static int workerThreadSemaphoreCount = Config.Client.Advanced.MultiThreading.numberOfLodBuilderThreads.get();
	public static int getWorkerThreadCount() { return workerThreadSemaphoreCount; }
	
	private static Semaphore workerThreadSemaphore = null;
	private static ConfigChangeListener<Integer> workerThreadSemaphoreConfigListener = null;
	
	
	
	//=================//
	// setup / cleanup //
	//=================//
	
	public static void setupThreadPools()
	{
		// standalone threads //
		
		fileHandlerThreadPool = new ConfigThreadPool(FILE_HANDLER_THREAD_FACTORY, Config.Client.Advanced.MultiThreading.numberOfFileHandlerThreads, Config.Client.Advanced.MultiThreading.runTimeRatioForFileHandlerThreads, null);
		updatePropagatorThreadPool = new ConfigThreadPool(UPDATE_PROPAGATOR_THREAD_FACTORY, Config.Client.Advanced.MultiThreading.numberOfUpdatePropagatorThreads, Config.Client.Advanced.MultiThreading.runTimeRatioForUpdatePropagatorThreads, null);
		worldGenThreadPool = new ConfigThreadPool(WORLD_GEN_THREAD_FACTORY, Config.Client.Advanced.MultiThreading.numberOfWorldGenerationThreads, Config.Client.Advanced.MultiThreading.runTimeRatioForWorldGenerationThreads, null);
		bufferUploaderThreadPool = ThreadUtil.makeSingleThreadPool(BUFFER_UPLOADER_THREAD_NAME);
		cleanupThreadPool = ThreadUtil.makeSingleThreadPool(CLEANUP_THREAD_NAME);
		
		
		
		// worker threads //
		
		// create thread semaphore
		if (Config.Client.Advanced.MultiThreading.enableLodBuilderThreadLimiting.get())
		{
			workerThreadSemaphoreCount = Config.Client.Advanced.MultiThreading.numberOfLodBuilderThreads.get();
			workerThreadSemaphore = new Semaphore(workerThreadSemaphoreCount);
			
			workerThreadSemaphoreConfigListener = new ConfigChangeListener<>(Config.Client.Advanced.MultiThreading.numberOfLodBuilderThreads, (val) ->
			{
				int changePermit = val - workerThreadSemaphoreCount;
				if (changePermit > 0)
				{
					workerThreadSemaphore.release(changePermit);
				}
				else
				{
					workerThreadSemaphore.acquireUninterruptibly(changePermit * -1);
				}
				workerThreadSemaphoreCount = workerThreadSemaphoreCount + changePermit;
			});
		}
		
		// create thread pools
		lightPopulatorThreadPool = new ConfigThreadPool(LIGHT_POPULATOR_THREAD_FACTORY, Config.Client.Advanced.MultiThreading.numberOfLodBuilderThreads, Config.Client.Advanced.MultiThreading.runTimeRatioForLodBuilderThreads, workerThreadSemaphore);
		chunkToLodBuilderThreadPool = new ConfigThreadPool(CHUNK_TO_LOD_BUILDER_THREAD_FACTORY, Config.Client.Advanced.MultiThreading.numberOfLodBuilderThreads, Config.Client.Advanced.MultiThreading.runTimeRatioForLodBuilderThreads, workerThreadSemaphore);
		bufferBuilderThreadPool = new ConfigThreadPool(BUFFER_BUILDER_THREAD_FACTORY, Config.Client.Advanced.MultiThreading.numberOfLodBuilderThreads, Config.Client.Advanced.MultiThreading.runTimeRatioForLodBuilderThreads, workerThreadSemaphore);
		
	}
	
	public static void shutdownThreadPools()
	{
		// standalone threads
		fileHandlerThreadPool.shutdownExecutorService();
		updatePropagatorThreadPool.shutdownExecutorService();
		worldGenThreadPool.shutdownExecutorService();
		bufferUploaderThreadPool.shutdown();
		cleanupThreadPool.shutdown();
		
		
		// worker threads
		ThreadPoolUtil.lightPopulatorThreadPool.shutdownExecutorService();
		ThreadPoolUtil.chunkToLodBuilderThreadPool.shutdownExecutorService();
		ThreadPoolUtil.bufferBuilderThreadPool.shutdownExecutorService();
		
		workerThreadSemaphore = null;
		
		if (workerThreadSemaphoreConfigListener != null)
		{
			workerThreadSemaphoreConfigListener.close();
			workerThreadSemaphoreConfigListener = null;
		}
	}
	
}
