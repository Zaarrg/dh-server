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

package com.seibel.distanthorizons.core.generation;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGeneratorReturnType;
import com.seibel.distanthorizons.api.objects.data.DhApiChunk;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.generation.tasks.*;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.transformers.LodDataBuilder;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.render.renderer.IDebugRenderable;
import com.seibel.distanthorizons.core.util.LodUtil.AssertFailureException;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.util.objects.UncheckedInterruptedException;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class WorldGenerationQueue implements IFullDataSourceRetrievalQueue, IDebugRenderable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IWrapperFactory WRAPPER_FACTORY = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
	
	private final IDhApiWorldGenerator generator;
	
	/** contains the positions that need to be generated */
	private final ConcurrentHashMap<Long, WorldGenTask> waitingTasks = new ConcurrentHashMap<>();
	
	private final ConcurrentHashMap<Long, InProgressWorldGenTaskGroup> inProgressGenTasksByLodPos = new ConcurrentHashMap<>();
	
	// granularity is the detail level for batching world generator requests together
	public final byte maxGranularity;
	public final byte minGranularity;
	
	/** largest numerical detail level allowed */
	public final byte lowestDataDetail;
	/** smallest numerical detail level allowed */
	public final byte highestDataDetail;
	
	
	/** If not null this generator is in the process of shutting down */
	private volatile CompletableFuture<Void> generatorClosingFuture = null;
	
	// TODO this logic isn't great and can cause a limit to how many threads could be used for world generation, 
	//  however it won't cause duplicate requests or concurrency issues, so it will be good enough for now.
	//  A good long term fix may be to either:
	//  1. allow the generator to deal with larger sections (let the generator threads split up larger tasks into smaller one
	//  2. batch requests better. instead of sending 4 individual tasks of detail level N, send 1 task of detail level n+1
	private final ExecutorService queueingThread = ThreadUtil.makeSingleThreadPool("World Gen Queue");
	private boolean generationQueueRunning = false;
	private DhBlockPos2D generationTargetPos = DhBlockPos2D.ZERO;
	/** can be used for debugging how many tasks are currently in the queue */
	private int numberOfTasksQueued = 0;
	
	// debug variables to test for duplicate world generator requests //
	/** limits how many of the previous world gen requests we should track */
	private static final int MAX_ALREADY_GENERATED_COUNT = 100;
	private final HashMap<Long, StackTraceElement[]> alreadyGeneratedPosHashSet = new HashMap<>(MAX_ALREADY_GENERATED_COUNT);
	private final LongArrayFIFOQueue alreadyGeneratedPosQueue = new LongArrayFIFOQueue();
	
	/** just used for rendering to the F3 menu */
	private int estimatedTotalTaskCount = 0;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public WorldGenerationQueue(IDhApiWorldGenerator generator)
	{
		LOGGER.info("Creating world gen queue");
		this.generator = generator;
		this.maxGranularity = generator.getMaxGenerationGranularity();
		this.minGranularity = generator.getMinGenerationGranularity();
		this.lowestDataDetail = generator.getLargestDataDetailLevel();
		this.highestDataDetail = generator.getSmallestDataDetailLevel();
		
		
		if (this.minGranularity < LodUtil.CHUNK_DETAIL_LEVEL)
		{
			throw new IllegalArgumentException(IDhApiWorldGenerator.class.getSimpleName() + ": min granularity must be at least 4 (Chunk sized)!");
		}
		if (this.maxGranularity < this.minGranularity)
		{
			throw new IllegalArgumentException(IDhApiWorldGenerator.class.getSimpleName() + ": max granularity smaller than min granularity!");
		}
		DebugRenderer.register(this, Config.Client.Advanced.Debugging.DebugWireframe.showWorldGenQueue);
		LOGGER.info("Created world gen queue");
	}
	
	
	
	//=================//
	// world generator //
	// task handling   //
	//=================//
	
	@Override
	public CompletableFuture<WorldGenResult> submitGenTask(long pos, byte requiredDataDetail, IWorldGenTaskTracker tracker)
	{
		// the generator is shutting down, don't add new tasks
		if (this.generatorClosingFuture != null)
		{
			return CompletableFuture.completedFuture(WorldGenResult.CreateFail());
		}
		
		
		// make sure the generator can provide the requested position
		if (requiredDataDetail < this.highestDataDetail)
		{
			throw new UnsupportedOperationException("Current generator does not meet requiredDataDetail level");
		}
		if (requiredDataDetail > this.lowestDataDetail)
		{
			requiredDataDetail = this.lowestDataDetail;
		}
		
		// Assert that the data at least can fill in 1 single ChunkSizedFullDataAccessor
		LodUtil.assertTrue(DhSectionPos.getDetailLevel(pos) > requiredDataDetail + LodUtil.CHUNK_DETAIL_LEVEL);
		
		
		CompletableFuture<WorldGenResult> future = new CompletableFuture<>();
		this.waitingTasks.put(pos, new WorldGenTask(pos, requiredDataDetail, tracker, future));
		return future;
	}
	
	@Override
	public void removeRetrievalRequestIf(DhSectionPos.ICancelablePrimitiveLongConsumer removeIf)
	{
		this.waitingTasks.forEachKey(100, (genPos) -> 
		{
			if (removeIf.accept(genPos))
			{
				this.waitingTasks.remove(genPos);
			}
		});
	}
	
	
	
	
	//===============//
	// running tasks //
	//===============//
	
	@Override
	public void startAndSetTargetPos(DhBlockPos2D targetPos)
	{
		// update the target pos
		this.generationTargetPos = targetPos;
		
		// ensure the queuing thread is running
		if (!this.generationQueueRunning)
		{
			this.startWorldGenQueuingThread();
		}
	}
	private void startWorldGenQueuingThread()
	{
		this.generationQueueRunning = true;
		
		// queue world generation tasks on its own thread since this process is very slow and would lag the server thread
		this.queueingThread.execute(() ->
		{
			try
			{
				// loop until the generator is shutdown
				while (!Thread.interrupted())
				{
					this.generator.preGeneratorTaskStart();
					
					// queue generation tasks until the generator is full, or there are no more tasks to generate
					boolean taskStarted = true;
					while (!this.generator.isBusy() && taskStarted)
					{
						taskStarted = this.startNextWorldGenTask(this.generationTargetPos);
						if (!taskStarted)
						{
							int debugPointOne = 0;
						}
					}
					
					// if there aren't any new tasks, wait a second before checking again // TODO replace with a listener instead
					Thread.sleep(1000);
				}
			}
			catch (InterruptedException e)
			{
				/* do nothing, this means the thread is being shut down */
			}
			catch (Exception e)
			{
				LOGGER.error("queueing exception: " + e.getMessage(), e);
				this.generationQueueRunning = false;
			}
		});
	}
	
	/**
	 * @param targetPos the position to center the generation around
	 * @return false if no tasks were found to generate
	 */
	private boolean startNextWorldGenTask(DhBlockPos2D targetPos)
	{
		if (this.waitingTasks.size() == 0)
		{
			return false;
		}
		
		this.waitingTasks.forEach((pos, task) -> 
		{
			if (!task.StillValid())
			{
				this.waitingTasks.remove(pos);
				task.future.complete(WorldGenResult.CreateFail());
			}
		});
		
		
		
		Mapper closestTaskMap = this.waitingTasks.reduceEntries(1024,
				entry -> new Mapper(entry.getValue(), DhSectionPos.getSectionBBoxPos(entry.getValue().pos).getCenterBlockPos().toPos2D().chebyshevDist(targetPos.toPos2D())),
				(aMapper, bMapper) -> aMapper.dist < bMapper.dist ? aMapper : bMapper);
		
		if (closestTaskMap == null)
		{
			// FIXME concurrency issue
			return false;
		}
		
		WorldGenTask closestTask = closestTaskMap.task;
		
		// remove the task we found, we are going to start it and don't want to run it multiple times
		this.waitingTasks.remove(closestTask.pos, closestTask);
		
		// do we need to modify this task to generate it?
		if (this.canGeneratePos((byte) 0, closestTask.pos)) // TODO should detail level 0 be replaced?
		{
			// detail level is correct for generation, start generation
			
			WorldGenTaskGroup closestTaskGroup = new WorldGenTaskGroup(closestTask.pos, (byte) 0);  // TODO should 0 be replaced?
			closestTaskGroup.worldGenTasks.add(closestTask); // TODO
			
			if (!this.inProgressGenTasksByLodPos.containsKey(closestTask.pos))
			{
				// no task exists for this position, start one
				InProgressWorldGenTaskGroup newTaskGroup = new InProgressWorldGenTaskGroup(closestTaskGroup);
				boolean taskStarted = this.tryStartingWorldGenTaskGroup(newTaskGroup);
				if (!taskStarted)
				{
					//LOGGER.trace("Unable to start task: "+closestTask.pos+", skipping. Task position may have already been generated.");
				}
			}
			else
			{
				// TODO replace the previous inProgress task if one exists
				// Note: Due to concurrency reasons, even if the currently running task is compatible with 
				// 		   the newly selected task, we cannot use it,
				//         as some chunks may have already been written into.
				
				//LOGGER.trace("A task already exists for this position, todo: "+closestTask.pos);
			}
			
			// a task has been started
			return true;
		}
		else
		{
			// detail level is too high (if the detail level was too low, the generator would've ignored the request),
			// split up the task
			
			
			// split up the task and add each one to the tree
			LinkedList<CompletableFuture<WorldGenResult>> childFutures = new LinkedList<>();
			long sectionPos = closestTask.pos;
			WorldGenTask finalClosestTask = closestTask;
			DhSectionPos.forEachChild(sectionPos, (childDhSectionPos) ->
			{
				CompletableFuture<WorldGenResult> newFuture = new CompletableFuture<>();
				childFutures.add(newFuture);
				
				WorldGenTask newGenTask = new WorldGenTask(childDhSectionPos, DhSectionPos.getDetailLevel(childDhSectionPos), finalClosestTask.taskTracker, newFuture);
				this.waitingTasks.put(newGenTask.pos, newGenTask);
			});
			
			// send the child futures to the future recipient, to notify them of the new tasks
			closestTask.future.complete(WorldGenResult.CreateSplit(childFutures));
			
			// return true so we attempt to generate again
			return true;
		}
	}
	/** @return true if the task was started, false otherwise */
	private boolean tryStartingWorldGenTaskGroup(InProgressWorldGenTaskGroup newTaskGroup)
	{
		byte taskDetailLevel = newTaskGroup.group.dataDetail;
		long taskPos = newTaskGroup.group.pos;
		byte granularity = (byte) (DhSectionPos.getDetailLevel(taskPos) - taskDetailLevel);
		LodUtil.assertTrue(granularity >= this.minGranularity && granularity <= this.maxGranularity);
		LodUtil.assertTrue(taskDetailLevel >= this.highestDataDetail && taskDetailLevel <= this.lowestDataDetail);
		
		DhChunkPos chunkPosMin = new DhChunkPos(DhSectionPos.getSectionBBoxPos(taskPos).getCornerBlockPos());
		
		// check if this is a duplicate generation task
		if (this.alreadyGeneratedPosHashSet.containsKey(newTaskGroup.group.pos))
		{
			// temporary solution to prevent generating the same section multiple times
			//LOGGER.trace("Duplicate generation section " + taskPos + " with granularity [" + granularity + "] at " + chunkPosMin + ". Skipping...");
			
			// sending a success result is necessary to make sure the render sections are reloaded correctly 
			newTaskGroup.group.worldGenTasks.forEach(worldGenTask -> worldGenTask.future.complete(WorldGenResult.CreateSuccess(DhSectionPos.encode(granularity, DhSectionPos.getX(taskPos), DhSectionPos.getZ(taskPos)))));
			return false;
		}
		this.alreadyGeneratedPosHashSet.put(newTaskGroup.group.pos, Thread.currentThread().getStackTrace());
		this.alreadyGeneratedPosQueue.enqueue(newTaskGroup.group.pos);
		
		// remove extra tracked duplicate positions
		while (this.alreadyGeneratedPosQueue.size() > MAX_ALREADY_GENERATED_COUNT)
		{
			long posToRemove = this.alreadyGeneratedPosQueue.dequeueLong();
			this.alreadyGeneratedPosHashSet.remove(posToRemove);
		}
		
		
		//LOGGER.info("Generating section "+taskPos+" with granularity "+granularity+" at "+chunkPosMin);
		
		this.numberOfTasksQueued++;
		newTaskGroup.genFuture = this.startGenerationEvent(chunkPosMin, granularity, taskDetailLevel, newTaskGroup.group::consumeChunkData);
		LodUtil.assertTrue(newTaskGroup.genFuture != null);
		
		newTaskGroup.genFuture.whenComplete((voidObj, exception) ->
		{
			try
			{
				this.numberOfTasksQueued--;
				if (exception != null)
				{
					// don't log the shutdown exceptions
					if (!LodUtil.isInterruptOrReject(exception))
					{
						LOGGER.error("Error generating data for section " + taskPos, exception);
					}
					
					newTaskGroup.group.worldGenTasks.forEach(worldGenTask -> worldGenTask.future.complete(WorldGenResult.CreateFail()));
				}
				else
				{
					newTaskGroup.group.worldGenTasks.forEach(worldGenTask -> worldGenTask.future.complete(WorldGenResult.CreateSuccess(DhSectionPos.encode(granularity, DhSectionPos.getX(taskPos), DhSectionPos.getZ(taskPos)))));
				}
				boolean worked = this.inProgressGenTasksByLodPos.remove(taskPos, newTaskGroup);
				LodUtil.assertTrue(worked);
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected error completing world gen task: "+taskPos, e);
			}
		});
		
		this.inProgressGenTasksByLodPos.put(taskPos, newTaskGroup);
		return true;
	}
	/**
	 * The chunkPos is always aligned to the granularity.
	 * For example: if the granularity is 4 (chunk sized) with a data detail level of 0 (block sized),
	 * the chunkPos will be aligned to 16x16 blocks. <br> <br>
	 *
	 *
	 * <strong>Full Granularity definition (as of 2023-6-21): </strong> <br> <br>
	 *
	 * world gen actually supports (in theory) generating stuff with a data detail that's higher than the per-block. <br> <br>
	 *
	 * Granularity basically means, on a single generation task, how big such group should be, in terms of the data points it will make. <br> <br>
	 *
	 * For example, a granularity of 4 means the task will generate a 16 by 16 data points.
	 * Now, those data points might be per block, or per 4 by 4 blocks. Granularity doesn't say what detail those would be. <br> <br>
	 *
	 * Note: currently the core system sends data via the chunk sized container,
	 * which has the locked granularity of 4 (16 by 16 data columns), and thus generators should at least have min granularity of 4.
	 * (Gen chunk width in that context means how many 'chunk sized containers' it will fill up.
	 * Again, note that a 'chunk sized container' isn't necessary 16 by 16 Minecraft blocks wide.
	 * It only has to contain 16 by 16 columns of data points, in whatever data detail it might be in.)
	 * (So, with a generator whose only gen data detail is 0, it is the same as a MC chunk.)
	 */
	private CompletableFuture<Void> startGenerationEvent(
		DhChunkPos chunkPosMin,
		byte granularity,
		byte targetDataDetail,
		Consumer<FullDataSourceV2> chunkDataConsumer
		)
	{
		EDhApiDistantGeneratorMode generatorMode = Config.Client.Advanced.WorldGenerator.distantGeneratorMode.get();
		EDhApiWorldGeneratorReturnType returnType = this.generator.getReturnType();
		switch (returnType) 
		{
			case VANILLA_CHUNKS: 
			{
				return this.generator.generateChunks(
					chunkPosMin.x,
					chunkPosMin.z,
					granularity,
					targetDataDetail,
					generatorMode,
					ThreadPoolUtil.getWorldGenExecutor(),
					(Object[] generatedObjectArray) -> 
					{
						try
						{
							IChunkWrapper chunk = WRAPPER_FACTORY.createChunkWrapper(generatedObjectArray);
							FullDataSourceV2 dataSource = LodDataBuilder.createGeneratedDataSource(chunk);
							LodUtil.assertTrue(dataSource != null);
							chunkDataConsumer.accept(dataSource);
						}
						catch (ClassCastException e)
						{
							LOGGER.error("World generator return type incorrect. Error: [" + e.getMessage() + "]. World generator disabled.", e);
							Config.Client.Advanced.WorldGenerator.enableDistantGeneration.set(false);
						}
					}
				);
			}
			case API_CHUNKS: 
			{
				return this.generator.generateApiChunks(
					chunkPosMin.x,
					chunkPosMin.z,
					granularity,
					targetDataDetail,
					generatorMode,
					ThreadPoolUtil.getWorldGenExecutor(),
					(DhApiChunk dataPoints) ->
					{
						try
						{
							FullDataSourceV2 dataSource = LodDataBuilder.createFromApiChunkData(dataPoints);
							chunkDataConsumer.accept(dataSource);
						}
						catch (DataCorruptedException e)
						{
							LOGGER.error("World generator returned a corrupt chunk. Error: [" + e.getMessage() + "]. World generator disabled.", e);
							Config.Client.Advanced.WorldGenerator.enableDistantGeneration.set(false);
						}
						catch (ClassCastException e)
						{
							LOGGER.error("World generator return type incorrect. Error: [" + e.getMessage() + "]. World generator disabled.", e);
							Config.Client.Advanced.WorldGenerator.enableDistantGeneration.set(false);
						}
					}
				);
			}
			default: 
			{
				Config.Client.Advanced.WorldGenerator.enableDistantGeneration.set(false);
				throw new AssertFailureException("Unknown return type: " + returnType);
			}
		}
	}
	
	
	
	//===================//
	// getters / setters //
	//===================//
	
	public int getWaitingTaskCount() { return this.waitingTasks.size(); }
	public int getInProgressTaskCount() { return this.inProgressGenTasksByLodPos.size(); }
	
	@Override
	public byte lowestDataDetail() { return this.lowestDataDetail; }
	@Override
	public byte highestDataDetail() { return this.highestDataDetail; }
	
	@Override
	public int getEstimatedTotalTaskCount() { return this.estimatedTotalTaskCount; }
	@Override
	public void setEstimatedTotalTaskCount(int newEstimate) { this.estimatedTotalTaskCount = newEstimate; }
	
	
	
	//==========//
	// shutdown //
	//==========//
	
	public CompletableFuture<Void> startClosing(boolean cancelCurrentGeneration, boolean alsoInterruptRunning)
	{
		LOGGER.info("Closing world gen queue");
		this.queueingThread.shutdownNow();
		
		
		// stop and remove any in progress tasks
		ArrayList<CompletableFuture<Void>> inProgressTasksCancelingFutures = new ArrayList<>(this.inProgressGenTasksByLodPos.size());
		this.inProgressGenTasksByLodPos.values().forEach(runningTaskGroup ->
		{
			CompletableFuture<Void> genFuture = runningTaskGroup.genFuture; // Do this to prevent it getting swapped out
			if (genFuture == null)
			{
				// genFuture's shouldn't be null, but sometimes they are...
				LOGGER.info("Null gen future: "+runningTaskGroup.group.pos);
				return;
			}
			
			
			if (cancelCurrentGeneration)
			{
				genFuture.cancel(alsoInterruptRunning);
			}
			
			inProgressTasksCancelingFutures.add(genFuture.handle((voidObj, exception) ->
			{
				if (exception instanceof CompletionException)
				{
					exception = exception.getCause();
				}
				
				if (!UncheckedInterruptedException.isInterrupt(exception) && !(exception instanceof CancellationException))
				{
					LOGGER.error("Error when terminating data generation for section " + runningTaskGroup.group.pos, exception);
				}
				
				return null;
			}));
		});
		this.generatorClosingFuture = CompletableFuture.allOf(inProgressTasksCancelingFutures.toArray(new CompletableFuture[0]));
		
		return this.generatorClosingFuture;
	}
	
	@Override
	public void close()
	{
		LOGGER.info("Closing " + WorldGenerationQueue.class.getSimpleName() + "...");
		
		if (this.generatorClosingFuture == null)
		{
			this.startClosing(true, true);
		}
		LodUtil.assertTrue(this.generatorClosingFuture != null);
		
		
		LOGGER.info("Awaiting world generator thread pool termination...");
		try
		{
			int waitTimeInSeconds = 3;
			ThreadPoolExecutor executor = ThreadPoolUtil.getWorldGenExecutor();
			if (executor != null && !executor.awaitTermination(waitTimeInSeconds, TimeUnit.SECONDS))
			{
				LOGGER.warn("World generator thread pool shutdown didn't complete after [" + waitTimeInSeconds + "] seconds. Some world generator requests may still be running.");
			}
		}
		catch (InterruptedException e)
		{
			LOGGER.warn("World generator thread pool shutdown interrupted! Ignoring child threads...", e);
		}
		
		
		this.generator.close();
		DebugRenderer.unregister(this, Config.Client.Advanced.Debugging.DebugWireframe.showWorldGenQueue);
		
		
		try
		{
			this.generatorClosingFuture.cancel(true);
		}
		catch (Throwable e)
		{
			LOGGER.warn("Failed to close generation queue: ", e);
		}
		
		
		LOGGER.info("Finished closing " + WorldGenerationQueue.class.getSimpleName());
	}
	
	
	
	//=======//
	// debug //
	//=======//
	
	@Override
	public void debugRender(DebugRenderer renderer)
	{
		this.waitingTasks.keySet().forEach((pos) -> { renderer.renderBox(new DebugRenderer.Box(pos, -32f, 64f, 0.05f, Color.blue)); });
		this.inProgressGenTasksByLodPos.forEach((pos, t) -> { renderer.renderBox(new DebugRenderer.Box(pos, -32f, 64f, 0.05f, Color.red)); });
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	private boolean canGeneratePos(byte worldGenTaskGroupDetailLevel /*when in doubt use 0*/ , long taskPos)
	{
		byte granularity = (byte) (DhSectionPos.getDetailLevel(taskPos) - worldGenTaskGroupDetailLevel);
		return (granularity >= this.minGranularity && granularity <= this.maxGranularity);
	}
	
	/**
	 * Source: <a href="https://stackoverflow.com/questions/3706219/algorithm-for-iterating-over-an-outward-spiral-on-a-discrete-2d-grid-from-the-or">...</a>
	 * Description: Left-upper semi-diagonal (0-4-16-36-64) contains squared layer number (4 * layer^2).
	 * External if-statement defines layer and finds (pre-)result for position in corresponding row or
	 * column of left-upper semi-plane, and internal if-statement corrects result for mirror position.
	 */
	private static int gridSpiralIndexing(int X, int Y)
	{
		int index = 0;
		if (X * X >= Y * Y)
		{
			index = 4 * X * X - X - Y;
			if (X < Y)
				index = index - 2 * (X - Y);
		}
		else
		{
			index = 4 * Y * Y - X - Y;
			if (X < Y)
				index = index + 2 * (X - Y);
		}
		
		return index;
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	private static class Mapper
	{
		public final WorldGenTask task;
		public final int dist;
		public Mapper(WorldGenTask task, int dist)
		{
			this.task = task;
			this.dist = dist;
		}
		
	}
	
}
