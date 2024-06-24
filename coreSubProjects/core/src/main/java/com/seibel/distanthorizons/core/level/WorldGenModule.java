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

package com.seibel.distanthorizons.core.level;

import com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;
import com.seibel.distanthorizons.core.generation.IFullDataSourceRetrievalQueue;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.pos.DhBlockPos2D;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class WorldGenModule implements Closeable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	private final GeneratedFullDataSourceProvider.IOnWorldGenCompleteListener onWorldGenCompleteListener;
	
	private final AtomicReference<AbstractWorldGenState> worldGenStateRef = new AtomicReference<>();
	private final F3Screen.DynamicMessage worldGenF3Message;
	
	
	
	public WorldGenModule(GeneratedFullDataSourceProvider.IOnWorldGenCompleteListener onWorldGenCompleteListener)
	{
		this.onWorldGenCompleteListener = onWorldGenCompleteListener;
		this.worldGenF3Message = new F3Screen.DynamicMessage(() ->
		{
			AbstractWorldGenState worldGenState = this.worldGenStateRef.get();
			if (worldGenState != null)
			{
				int waitingCount = worldGenState.worldGenerationQueue.getWaitingTaskCount();
				int inProgressCount = worldGenState.worldGenerationQueue.getInProgressTaskCount();
				int totalCountEstimate = worldGenState.worldGenerationQueue.getEstimatedTotalTaskCount();
				
				return "World Gen Tasks: "+waitingCount+"/"+totalCountEstimate+", (in progress: "+inProgressCount+")";
			}
			else
			{
				return "World Gen Disabled";
			}
		});
		
	}
	
	
	
	//===================//
	// world gen control //
	//===================//
	
	public void startWorldGen(GeneratedFullDataSourceProvider dataFileHandler, AbstractWorldGenState newWgs)
	{
		// create the new world generator
		if (!this.worldGenStateRef.compareAndSet(null, newWgs))
		{
			LOGGER.warn("Failed to start world gen due to concurrency");
			newWgs.closeAsync(false);
		}
		dataFileHandler.addWorldGenCompleteListener(this.onWorldGenCompleteListener);
		dataFileHandler.setWorldGenerationQueue(newWgs.worldGenerationQueue);
	}
	
	public void stopWorldGen(GeneratedFullDataSourceProvider dataFileHandler)
	{
		AbstractWorldGenState worldGenState = this.worldGenStateRef.get();
		if (worldGenState == null)
		{
			LOGGER.warn("Attempted to stop world gen when it was not running");
			return;
		}
		
		// shut down the world generator
		while (!this.worldGenStateRef.compareAndSet(worldGenState, null))
		{
			worldGenState = this.worldGenStateRef.get();
			if (worldGenState == null)
			{
				return;
			}
		}
		dataFileHandler.clearRetrievalQueue();
		worldGenState.closeAsync(true).join(); //TODO: Make it async.
		dataFileHandler.removeWorldGenCompleteListener(this.onWorldGenCompleteListener);
	}
	
	/** @param targetPosForGeneration the position that world generation should be centered around */
	public void worldGenTick(DhBlockPos2D targetPosForGeneration)
	{
		AbstractWorldGenState worldGenState = this.worldGenStateRef.get();
		if (worldGenState != null)
		{
			worldGenState.startGenerationQueueAndSetTargetPos(targetPosForGeneration);
		}
	}
	
	@Override
	public void close()
	{
		// shutdown the world-gen
		AbstractWorldGenState worldGenState = this.worldGenStateRef.get();
		if (worldGenState != null)
		{
			while (!this.worldGenStateRef.compareAndSet(worldGenState, null))
			{
				worldGenState = this.worldGenStateRef.get();
				if (worldGenState == null)
				{
					break;
				}
			}
			
			if (worldGenState != null)
			{
				worldGenState.closeAsync(true).join(); //TODO: Make it async.
			}
		}
		
		this.worldGenF3Message.close();
	}
	
	
	
	//=========//
	// getters //
	//=========//
	
	public boolean isWorldGenRunning() { return this.worldGenStateRef.get() != null; }
	
	
	
	//================//
	// helper classes //
	//================//
	
	/** Handles the {@link IFullDataSourceRetrievalQueue} and any other necessary world gen information. */
	public static abstract class AbstractWorldGenState
	{
		public IFullDataSourceRetrievalQueue worldGenerationQueue;
		
		CompletableFuture<Void> closeAsync(boolean doInterrupt)
		{
			return this.worldGenerationQueue.startClosing(true, doInterrupt)
					.exceptionally(ex ->
							{
								LOGGER.error("Error closing generation queue", ex);
								return null;
							}
					).thenRun(this.worldGenerationQueue::close)
					.exceptionally(ex ->
					{
						LOGGER.error("Error closing world gen", ex);
						return null;
					});
		}
		
		/** @param targetPosForGeneration the position that world generation should be centered around */
		public void startGenerationQueueAndSetTargetPos(DhBlockPos2D targetPosForGeneration) 
		{ this.worldGenerationQueue.startAndSetTargetPos(targetPosForGeneration); }
	}
	
}
