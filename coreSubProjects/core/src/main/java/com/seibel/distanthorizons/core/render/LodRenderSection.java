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

package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnRenderBufferBuilder;
import com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.file.fullDatafile.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.render.renderer.IDebugRenderable;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnRenderBuffer;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import javax.annotation.WillNotClose;
import java.awt.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A render section represents an area that could be rendered.
 * For more information see {@link LodQuadTree}.
 */
public class LodRenderSection implements IDebugRenderable, AutoCloseable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	
	public final long pos;
	
	private final IDhClientLevel level;
	@WillNotClose
	private final FullDataSourceProviderV2 fullDataSourceProvider;
	private final LodQuadTree quadTree;
	
	
	public boolean renderingEnabled = false;
	private boolean canRender = false;
	
	/** this reference is necessary so we can determine what VBO to render */
	public ColumnRenderBuffer renderBuffer; 
	
	
	/** 
	 * Encapsulates everything between pulling data from the database (including neighbors)
	 * up to the point when geometry data is uploaded to the GPU.
	 */
	private CompletableFuture<Void> uploadRenderDataToGpuFuture = null;
	
	private final ReentrantLock getRenderSourceLock = new ReentrantLock();
	/** Stored as a class variable so we can reuse it's result across multiple LOD loads if necessary */
	private ReferencedFutureWrapper renderSourceLoadingRefFuture = null;
	/** Stored as a class variable so we can decrement reference counts as each {@link LodRenderSection} finishes using them. */
	private ReferencedFutureWrapper[] adjacentLoadRefFutures;
	
	private boolean missingPositionsCalculated = false;
	/** should be an empty array if no positions need to be generated */
	private LongArrayList missingGenerationPos = null;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public LodRenderSection(long pos, LodQuadTree quadTree, IDhClientLevel level, FullDataSourceProviderV2 fullDataSourceProvider)
	{
		this.pos = pos;
		this.quadTree = quadTree;
		this.level = level;
		this.fullDataSourceProvider = fullDataSourceProvider;
		
		DebugRenderer.register(this, Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionStatus);
	}
	
	
	
	//===============================//
	// render data loading/uploading //
	//===============================//
	
	public synchronized void uploadRenderDataToGpuAsync()
	{
		if (!GLProxy.hasInstance())
		{
			// it's possible to try uploading buffers before the GLProxy has been initialized
			// which would cause the system to crash
			return;
		}
		
		if (this.uploadRenderDataToGpuFuture != null)
		{
			// don't accidentally queue multiple uploads at the same time
			return;
		}
		
		
		
		ThreadPoolExecutor executor = ThreadPoolUtil.getFileHandlerExecutor();
		if (executor == null || executor.isTerminated())
		{
			return;
		}
		
		this.uploadRenderDataToGpuFuture = CompletableFuture.runAsync(() -> 
		{
			//==================//
			// load render data //
			//==================//
			
			this.tryDecrementingLoadFutureArray(this.adjacentLoadRefFutures);
			
			ReferencedFutureWrapper thisLoadFuture = this.getRenderSourceAsync();
			ReferencedFutureWrapper[] adjLoadRefFutures = this.getNeighborRenderSourcesAsync();
			
			
			// wait for all futures to complete together,
			// merging the futures makes loading significantly faster than loading this position then loading its neighbors
			ArrayList<CompletableFuture<ColumnRenderSource>> futureList = new ArrayList<>();
			futureList.add(thisLoadFuture.future);
			for (ReferencedFutureWrapper refFuture : adjLoadRefFutures)
			{
				futureList.add(refFuture.future);
			}
			
			CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).thenAccept((voidObj) ->
			{
				try
				{
					ColumnRenderSource renderSource = thisLoadFuture.future.get();
					if (renderSource == null || renderSource.isEmpty())
					{
						thisLoadFuture.decrementRefCount();
						for (ReferencedFutureWrapper futureWrapper : adjLoadRefFutures)
						{
							futureWrapper.decrementRefCount();
						}
						
						// nothing needs to be rendered
						this.canRender = false;
						this.uploadRenderDataToGpuFuture = null;
						return;
					}
					
					
					
					//==============================//
					// build/upload new render data //
					//==============================//
					
					try
					{
						ColumnRenderBuffer previousBuffer = this.renderBuffer;
						
						ColumnRenderSource[] adjacentRenderSections = new ColumnRenderSource[EDhDirection.ADJ_DIRECTIONS.length];
						for (int i = 0; i < EDhDirection.ADJ_DIRECTIONS.length; i++)
						{
							adjacentRenderSections[i] = adjLoadRefFutures[i].future.getNow(null);
						}
						ColumnRenderBufferBuilder.buildAndUploadBuffersAsync(this.level, renderSource, adjacentRenderSections).thenAccept((buffer) ->
						{
							// upload complete, clean up the old data if 
							this.renderBuffer = buffer;
							this.canRender = true;
							this.uploadRenderDataToGpuFuture = null;
							
							
							if (previousBuffer != null)
							{
								previousBuffer.close();
							}
							
							thisLoadFuture.decrementRefCount();
							this.tryDecrementingLoadFutureArray(adjLoadRefFutures);
							this.adjacentLoadRefFutures = null;
						});
					}
					catch (Exception e)
					{
						thisLoadFuture.decrementRefCount();
						this.tryDecrementingLoadFutureArray(adjLoadRefFutures);
						this.adjacentLoadRefFutures = null;
						
						LOGGER.error("Unexpected error in LodRenderSection loading, Error: "+e.getMessage(), e);
						this.uploadRenderDataToGpuFuture = null;
					}
				}
				catch (Exception e)
				{
					thisLoadFuture.decrementRefCount();
					this.tryDecrementingLoadFutureArray(adjLoadRefFutures);
					this.adjacentLoadRefFutures = null;
					
					LOGGER.error("Unexpected error in LodRenderSection loading, Error: "+e.getMessage(), e);
					this.uploadRenderDataToGpuFuture = null;
				}
			});
		}, executor);
	}
	/** Should be called on the {@link ThreadPoolUtil#getFileHandlerExecutor()} */
	private ReferencedFutureWrapper[] getNeighborRenderSourcesAsync()
	{
		ReferencedFutureWrapper[] futureArray = new ReferencedFutureWrapper[EDhDirection.ADJ_DIRECTIONS.length];
		for (int i = 0; i < EDhDirection.ADJ_DIRECTIONS.length; i++)
		{
			EDhDirection direction = EDhDirection.ADJ_DIRECTIONS[i];
			int arrayIndex = direction.ordinal() - 2;
			
			long adjPos = DhSectionPos.getAdjacentPos(this.pos, direction);
			try
			{
				LodRenderSection adjRenderSection = this.quadTree.getValue(adjPos);
				if (adjRenderSection != null)
				{
					futureArray[arrayIndex] = adjRenderSection.getRenderSourceAsync();
				}
			}
			catch (IndexOutOfBoundsException ignore) {}
			
			if (futureArray[arrayIndex] == null)
			{
				futureArray[arrayIndex] = new ReferencedFutureWrapper(CompletableFuture.completedFuture(null));
			}
		}
		
		this.adjacentLoadRefFutures = futureArray;
		return futureArray;
	}
	/** Will try to return the same {@link CompletableFuture} if multiple requests are made for the same position */
	private ReferencedFutureWrapper getRenderSourceAsync()
	{
		try
		{
			this.getRenderSourceLock.lock();
			
			
			// if a load is already in progress, use that existing one
			// (this reduces the number of duplicate loads that may happen when initially loading the world)
			if (this.renderSourceLoadingRefFuture != null)
			{
				// increment the number of objects needing this future
				this.renderSourceLoadingRefFuture.incrementRefCount();
				return this.renderSourceLoadingRefFuture;
			}
			
			
			
			ThreadPoolExecutor executor = ThreadPoolUtil.getFileHandlerExecutor();
			if (executor == null || executor.isTerminated())
			{
				return new ReferencedFutureWrapper(CompletableFuture.completedFuture(null));
			}
			
			this.renderSourceLoadingRefFuture = new ReferencedFutureWrapper(CompletableFuture.supplyAsync(() ->
			{
				try (FullDataSourceV2 fullDataSource = this.fullDataSourceProvider.get(this.pos))
				{
					ColumnRenderSource renderSource = FullDataToRenderDataTransformer.transformFullDataToRenderSource(fullDataSource, this.level);
					this.renderSourceLoadingRefFuture = null;
					return renderSource;
				}
				catch (Exception e)
				{
					LOGGER.warn("Unable to get render source " + this.pos + ", error: " + e.getMessage(), e);
					this.renderSourceLoadingRefFuture = null;
					return null;
				}
			}, executor));
			return this.renderSourceLoadingRefFuture;
		}
		finally
		{
			this.getRenderSourceLock.unlock();
		}
	}
	
	
	
	//========================//
	// getters and properties //
	//========================//
	
	public boolean canRender() { return this.canRender; }
	
	public boolean gpuUploadInProgress() { return this.uploadRenderDataToGpuFuture != null; }
	
	
	
	//=================================//
	// full data retrieval (world gen) //
	//=================================//
	
	public boolean isFullyGenerated() { return this.missingPositionsCalculated && this.missingGenerationPos.size() == 0; }
	public boolean missingPositionsCalculated() { return this.missingPositionsCalculated; }
	public int ungeneratedPositionCount() { return (this.missingGenerationPos != null) ? this.missingGenerationPos.size() : 0; }
	
	public void tryQueuingMissingLodRetrieval()
	{
		if (this.fullDataSourceProvider.canRetrieveMissingDataSources() && this.fullDataSourceProvider.canQueueRetrieval())
		{
			// calculate the missing positions if not already done
			if (!this.missingPositionsCalculated)
			{
				this.missingGenerationPos = this.fullDataSourceProvider.getPositionsToRetrieve(this.pos);
				if (this.missingGenerationPos != null)
				{
					this.missingPositionsCalculated = true;
				}
			}
			
			// if the missing positions were found, queue them
			if (this.missingGenerationPos != null)
			{
				// queue from last to first to prevent shifting the array unnecessarily
				for (int i = this.missingGenerationPos.size() - 1; i >= 0; i--)
				{
					if (!this.fullDataSourceProvider.canQueueRetrieval())
					{
						// the data source provider isn't accepting any more jobs
						break;
					}
					
					long pos = this.missingGenerationPos.removeLong(i);
					boolean positionQueued = this.fullDataSourceProvider.queuePositionForRetrieval(pos);
					if (!positionQueued)
					{
						// shouldn't normally happen, but just in case
						this.missingGenerationPos.add(pos);
						break;
					}
				}
			}
		}
	}
	
	
	
	//=========//
	// cleanup //
	//=========//
	
	/** does nothing if the passed in value is null. */
	private void tryDecrementingLoadFutureArray(@Nullable ReferencedFutureWrapper[] refFutures)
	{
		if (refFutures != null)
		{
			for (ReferencedFutureWrapper futureWrapper : refFutures)
			{
				if (futureWrapper != null)
				{
					futureWrapper.decrementRefCount();
				}
			}
		}
	}
	
	
	
	//==============//
	// base methods //
	//==============//
	
	@Override
	public String toString()
	{
		return "LodRenderSection{" +
				"pos=" + this.pos +
				'}';
	}
	
	@Override
	public void close()
	{
		DebugRenderer.unregister(this, Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionStatus);
		
		if (Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionStatus.get())
		{
			// show a particle for the closed section
			DebugRenderer.makeParticle(
				new DebugRenderer.BoxParticle(
					new DebugRenderer.Box(this.pos, 128f, 156f, 0.09f, Color.RED.darker()),
					0.5, 32f
				)
			);
		}
		
		
		
		if (this.renderBuffer != null)
		{
			this.renderBuffer.close();
		}
		
		if (this.uploadRenderDataToGpuFuture != null)
		{
			this.uploadRenderDataToGpuFuture.cancel(true);
		}
		
		// this render section won't be rendering, we don't need to load any data for it
		this.tryDecrementingLoadFutureArray(this.adjacentLoadRefFutures);
		if (this.renderSourceLoadingRefFuture != null)
		{
			this.renderSourceLoadingRefFuture.decrementRefCount();
		}
		
		
		// remove any active world gen requests that may be for this position
		ThreadPoolExecutor executor = ThreadPoolUtil.getCleanupExecutor();
		if (executor != null && !executor.isTerminated())
		{
			// while this should generally be a fast operation 
			// this is run on a separate thread to prevent lag on the render thread
			
			try
			{
				executor.execute(() -> this.fullDataSourceProvider.removeRetrievalRequestIf((genPos) -> DhSectionPos.contains(this.pos, genPos)));
			}
			catch (RejectedExecutionException ignore)
			{ /* If this happens that means everything is already shut down and no additional cleanup will be necessary */ }
		}
	}
	
	@Override
	public void debugRender(DebugRenderer debugRenderer)
	{
		Color color = Color.red;
		if (this.renderingEnabled)
		{
			color = Color.green;
		}
		else if (this.uploadRenderDataToGpuFuture != null)
		{
			color = Color.yellow;
		}
		else if (this.canRender)
		{
			color = Color.cyan;
		}
		
		debugRenderer.renderBox(new DebugRenderer.Box(this.pos, 400, 8f, Objects.hashCode(this), 0.1f, color));
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	/**
	 * Used to keep track of whether a {@link ColumnRenderSource} {@link CompletableFuture}
	 * is in use or not, and if not in use cancels the future. <br> <br>
	 *  
	 * This helps speed up LOD loading by canceling loads that are no longer needed,
	 * IE out of range or in an unloaded dimension.
	 */
	private static class ReferencedFutureWrapper
	{
		public final CompletableFuture<ColumnRenderSource> future;
		// starts at 1 since the constructing method is referencing this future
		private final AtomicInteger refCount = new AtomicInteger(1);
		
		
		
		public ReferencedFutureWrapper(CompletableFuture<ColumnRenderSource> future) { this.future = future; }
		
		public void incrementRefCount() { this.refCount.incrementAndGet(); }
		public void decrementRefCount()
		{
			// automatically clean up this future if no one else is referencing it
			if (this.refCount.decrementAndGet() <= 0)
			{
				if (this.future != null)
				{
					if (!this.future.isDone())
					{
						this.future.cancel(true);
					}
				}
			}
		}
		
		
		
		@Override 
		public String toString() { return this.future.toString() + " - " + this.refCount.get(); }
		
	}
	
}
