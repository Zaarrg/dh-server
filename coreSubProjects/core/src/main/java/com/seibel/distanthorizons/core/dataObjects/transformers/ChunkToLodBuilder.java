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

package com.seibel.distanthorizons.core.dataObjects.transformers;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import org.apache.logging.log4j.LogManager;

public class ChunkToLodBuilder implements AutoCloseable
{
	public static final ConfigBasedLogger LOGGER = new ConfigBasedLogger(LogManager.getLogger(), () -> Config.Client.Advanced.Logging.logLodBuilderEvent.get());
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	public static final long MAX_TICK_TIME_NS = 1000000000L / 20L;
	
	private final ConcurrentHashMap<DhChunkPos, IChunkWrapper> concurrentChunkToBuildByChunkPos = new ConcurrentHashMap<>();
	private final ConcurrentLinkedDeque<Task> concurrentTaskToBuildList = new ConcurrentLinkedDeque<>();
	private final AtomicInteger runningCount = new AtomicInteger(0);
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public ChunkToLodBuilder() { }
	
	
	
	//=================//
	// data generation //
	//=================//
	
	public CompletableFuture<FullDataSourceV2> tryGenerateData(IChunkWrapper chunkWrapper)
	{
		if (chunkWrapper == null)
		{
			throw new NullPointerException("ChunkWrapper cannot be null!");
		}
		
		IChunkWrapper oldChunk = this.concurrentChunkToBuildByChunkPos.put(chunkWrapper.getChunkPos(), chunkWrapper); // an Exchange operation
		// If there's old chunk, that means we just replaced an unprocessed old request on generating data on this pos.
		//   if so, we can just return null to signal this, as the old request's future will instead be the proper one
		//   that will return the latest generated data.
		if (oldChunk != null)
		{
			return null;
		}
		
		// Otherwise, it means we're the first to do so. Let's submit our task to this entry.
		CompletableFuture<FullDataSourceV2> future = new CompletableFuture<>();
		this.concurrentTaskToBuildList.addLast(new Task(chunkWrapper.getChunkPos(), future));
		return future;
	}
	
	// TODO why on tick?
	public void tick()
	{
		int threadCount = ThreadPoolUtil.getWorkerThreadCount();
		if (this.runningCount.get() >= threadCount)
		{
			return;
		}
		else if (this.concurrentTaskToBuildList.isEmpty())
		{
			return;
		}
		else if (MC != null && !MC.playerExists())
		{
			// MC hasn't finished loading (or is currently unloaded)
			
			// can be uncommented if tasks aren't being cleared correctly
			//this.clearCurrentTasks();
			return;
		}
		
		ThreadPoolExecutor lodBuilderExecutor = ThreadPoolUtil.getChunkToLodBuilderExecutor();
		if (lodBuilderExecutor == null)
		{
			return;
		}
		
		
		for (int i = 0; i < threadCount; i++)
		{
			this.runningCount.incrementAndGet();
			try
			{
				CompletableFuture.runAsync(() ->
				{
					try
					{
						this.tickThreadTask();
					}
					finally
					{
						this.runningCount.decrementAndGet();
					}
				}, lodBuilderExecutor);
			}
			catch (RejectedExecutionException ignore) { /* the thread pool was probably shut down because it's size is being changed, just wait a sec and it should be back */ }
		}
	}
	private void tickThreadTask()
	{
		long time = System.nanoTime();
		int count = 0;
		boolean allDone = false;
		while (true)
		{
			// run until we either run out of time, or all tasks are complete
			if (System.nanoTime() - time > MAX_TICK_TIME_NS && !this.concurrentTaskToBuildList.isEmpty())
			{
				break;
			}
			
			Task task = this.concurrentTaskToBuildList.pollFirst();
			if (task == null)
			{
				allDone = true;
				break;
			}
			
			count++;
			IChunkWrapper latestChunk = this.concurrentChunkToBuildByChunkPos.remove(task.chunkPos); // Basically an Exchange operation
			if (latestChunk == null)
			{
				LOGGER.error("Somehow Task at " + task.chunkPos + " has latestChunk as null. Skipping task.");
				task.future.complete(null);
				continue;
			}
			
			try
			{
				if (LodDataBuilder.canGenerateLodFromChunk(latestChunk))
				{
					FullDataSourceV2 dataSource = LodDataBuilder.createGeneratedDataSource(latestChunk);
					if (dataSource != null)
					{
						task.future.complete(dataSource);
						continue;
					}
				}
				else if (task.generationAttemptExpirationTimeMs < System.currentTimeMillis())
				{
					// this task won't be re-queued
					//LOGGER.trace("removed chunk "+task.chunkPos);
					continue;
				}
			}
			catch (Exception ex)
			{
				LOGGER.error("Error while processing Task at " + task.chunkPos, ex);
			}
			
			// Failed to build due to chunk not meeting requirement,
			// re-add it to the queue so it can be tested next time
			IChunkWrapper casChunk = this.concurrentChunkToBuildByChunkPos.putIfAbsent(task.chunkPos, latestChunk); // CAS operation with expected=null
			if (casChunk == null || latestChunk.isStillValid()) // That means CAS have been successful
			{
				this.concurrentTaskToBuildList.addLast(task); // Then add back the same old task.
			}
			else // Else, it means someone managed to sneak in a new gen request in this pos. Then lets drop this old task.
			{
				task.future.complete(null);
			}
			
			count--;
		}
		
		long time2 = System.nanoTime();
		if (!allDone)
		{
			//LOGGER.info("Completed {} tasks in {} in this tick", count, Duration.ofNanos(time2 - time));
		}
		else if (count > 0)
		{
			//LOGGER.info("Completed all {} tasks in {}", count, Duration.ofNanos(time2 - time));
		}
	}
	
	/**
	 * should be called whenever changing levels/worlds
	 * to prevent trying to generate LODs for chunk(s) that are no longer loaded
	 * (which can cause exceptions)
	 */
	public void clearCurrentTasks()
	{
		this.concurrentTaskToBuildList.clear();
		this.concurrentChunkToBuildByChunkPos.clear();
	}
	
	
	
	
	//==============//
	// base methods //
	//==============//
	
	@Override
	public void close() { this.clearCurrentTasks(); }
	
	
	
	//================//
	// helper classes //
	//================//
	
	private static class Task
	{
		public final DhChunkPos chunkPos;
		public final CompletableFuture<FullDataSourceV2> future;
		/** This is tracked so impossible tasks can be removed from the queue */
		public long generationAttemptExpirationTimeMs = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
		
		Task(DhChunkPos chunkPos, CompletableFuture<FullDataSourceV2> future)
		{
			this.chunkPos = chunkPos;
			this.future = future;
		}
		
	}
	
}

