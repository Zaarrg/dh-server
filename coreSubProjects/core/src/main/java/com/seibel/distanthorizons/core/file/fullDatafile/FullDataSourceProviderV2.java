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

package com.seibel.distanthorizons.core.file.fullDatafile;

import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV1;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.file.structure.AbstractSaveStructure;
import com.seibel.distanthorizons.core.file.AbstractDataSourceHandler;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.render.renderer.IDebugRenderable;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.sql.repo.FullDataSourceV2Repo;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Handles reading/writing {@link FullDataSourceV2}
 * to and from the database.
 */
public class FullDataSourceProviderV2
		extends AbstractDataSourceHandler<FullDataSourceV2, FullDataSourceV2DTO, FullDataSourceV2Repo, IDhLevel>
		implements IDebugRenderable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	protected static final int NUMBER_OF_PARENT_UPDATE_TASKS_PER_THREAD = 50;
	/** how many parent update tasks can be in the queue at once */
	protected static final int MAX_UPDATE_TASK_COUNT = NUMBER_OF_PARENT_UPDATE_TASKS_PER_THREAD * Config.Client.Advanced.MultiThreading.numberOfFileHandlerThreads.get();
	
	/** indicates how long the update queue thread should wait between queuing ticks */
	protected static final int UPDATE_QUEUE_THREAD_DELAY_IN_MS = 250;
	
	/** how many data sources should be pulled down for migration at once */
	private static final int MIGRATION_BATCH_COUNT = NUMBER_OF_PARENT_UPDATE_TASKS_PER_THREAD;
	private static final String MIGRATION_THREAD_NAME_PREFIX = "Full Data Migration Thread: ";
	/**
	 * 5 minutes <br>
	 * This should be much longer than any update should take. This is just
	 * to make sure the thread doesn't get stuck.
	 */
	private static final int MIGRATION_MAX_UPDATE_TIMEOUT_IN_MS = 5 * 60 * 1_000;
	
	
	protected final ThreadPoolExecutor migrationThreadPool;
	/**
	 * Interrupting the migration thread pool doesn't work well and may corrupt the database
	 * vs gracefully shutting down the thread ourselves.
	 */
	protected final AtomicBoolean migrationThreadRunning = new AtomicBoolean(true);
	protected final FullDataSourceProviderV1<IDhLevel> legacyFileHandler;
	
	protected boolean migrationStartMessageQueued = false;
	
	protected long legacyDeletionCount = -1;
	protected long migrationCount = -1;
	
	/**
	 * Tracks which positions are currently being updated
	 * to prevent duplicate concurrent updates.
	 */
	public final Set<Long> parentUpdatingPosSet = ConcurrentHashMap.newKeySet();
	
	// TODO only run thread if modifications happened recently
	/**
	 * This isn't in {@link AbstractDataSourceHandler} since we don't need parent updating logic
	 * for render data, only full data.
	 */
	private final ThreadPoolExecutor updateQueueProcessor;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public FullDataSourceProviderV2(IDhLevel level, AbstractSaveStructure saveStructure) { this(level, saveStructure, null); }
	public FullDataSourceProviderV2(IDhLevel level, AbstractSaveStructure saveStructure, @Nullable File saveDirOverride)
	{
		super(level, saveStructure, saveDirOverride);
		this.legacyFileHandler = new FullDataSourceProviderV1<>(level, saveStructure, saveDirOverride);
		
		DebugRenderer.register(this, Config.Client.Advanced.Debugging.DebugWireframe.showFullDataUpdateStatus);
		
		String dimensionName = level.getLevelWrapper().getDimensionType().getDimensionName();
		
		// start migrating any legacy data sources present in the background
		this.migrationThreadPool = ThreadUtil.makeRateLimitedThreadPool(1, MIGRATION_THREAD_NAME_PREFIX + "[" + dimensionName + "]", Config.Client.Advanced.MultiThreading.runTimeRatioForUpdatePropagatorThreads.get(), Thread.MIN_PRIORITY, (Semaphore) null);
		this.migrationThreadPool.execute(() -> this.convertLegacyDataSources());
		
		this.updateQueueProcessor = ThreadUtil.makeSingleThreadPool("Parent Update Queue [" + dimensionName + "]");
		this.updateQueueProcessor.execute(() -> this.runUpdateQueue());
	}
	
	
	
	//====================//
	// Abstract overrides //
	//====================//
	
	@Override
	protected FullDataSourceV2Repo createRepo()
	{
		try
		{
			return new FullDataSourceV2Repo("jdbc:sqlite", this.saveDir.getPath() + "/" + AbstractSaveStructure.DATABASE_NAME);
		}
		catch (SQLException e)
		{
			// should only happen if there is an issue with the database (it's locked or the folder path is missing) 
			// or the database update failed
			throw new RuntimeException(e);
		}
	}
	
	@Override
	protected FullDataSourceV2DTO createDtoFromDataSource(FullDataSourceV2 dataSource)
	{
		try
		{
			// when creating new data use the compressor currently selected in the config
			EDhApiDataCompressionMode compressionModeEnum = Config.Client.Advanced.LodBuilding.dataCompression.get();
			return FullDataSourceV2DTO.CreateFromDataSource(dataSource, compressionModeEnum);
		}
		catch (IOException e)
		{
			LOGGER.warn("Unable to create DTO, error: " + e.getMessage(), e);
			return null;
		}
	}
	
	@Override
	protected FullDataSourceV2 createDataSourceFromDto(FullDataSourceV2DTO dto) throws InterruptedException, IOException, DataCorruptedException
	{ return dto.createPooledDataSource(this.level.getLevelWrapper()); }
	
	@Override
	protected FullDataSourceV2 makeEmptyDataSource(long pos) { return FullDataSourceV2.DATA_SOURCE_POOL.getPooledSource(pos, true); }
	
	@Nullable
	public Long getTimestampForPos(long pos)
	{
		try
		{
			PreparedStatement preparedStatement = this.repo.createPreparedStatement(
					"SELECT LastModifiedUnixDateTime " +
							"FROM " + this.repo.getTableName() + " " +
							"WHERE DetailLevel = ? " +
							"AND PosX = ? " +
							"AND PosZ = ?;"
			);
			preparedStatement.setInt(1, DhSectionPos.getDetailLevel(pos) - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL);
			preparedStatement.setInt(2, DhSectionPos.getX(pos));
			preparedStatement.setInt(3, DhSectionPos.getZ(pos));
			
			List<Map<String, Object>> row = this.repo.query(preparedStatement);
			return !row.isEmpty() ? (Long) row.get(0).get("LastModifiedUnixDateTime") : null;
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
	}
	public Map<Long, Long> getTimestampsForRange(byte detailLevel, int startPosX, int startPosZ, int endPosX, int endPosZ)
	{
		try
		{
			PreparedStatement preparedStatement = this.repo.createPreparedStatement(
					"SELECT PosX, PosZ, LastModifiedUnixDateTime " +
							"FROM " + this.repo.getTableName() + " " +
							"WHERE DetailLevel = ? " +
							"AND PosX BETWEEN ? AND ? " +
							"AND PosZ BETWEEN ? AND ?;"
			);
			preparedStatement.setInt(1, detailLevel - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL);
			preparedStatement.setInt(2, startPosX);
			preparedStatement.setInt(3, endPosX);
			preparedStatement.setInt(4, startPosZ);
			preparedStatement.setInt(5, endPosZ);
			
			return this.repo.query(preparedStatement).stream().collect(Collectors.toMap(
					row -> DhSectionPos.encode(detailLevel, (int) row.get("PosX"), (int) row.get("PosZ")),
					row -> (long) row.get("LastModifiedUnixDateTime"))
			);
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	
	
	//================//
	// parent updates //
	//================//
	
	private void runUpdateQueue()
	{
		while (!Thread.interrupted())
		{
			try
			{
				Thread.sleep(UPDATE_QUEUE_THREAD_DELAY_IN_MS);
				
				ThreadPoolExecutor executor = ThreadPoolUtil.getUpdatePropagatorExecutor();
				if (executor == null || executor.isTerminated())
				{
					continue;
				}
				
				// TODO it might be worth skipping this logic if no parent updates happened
				
				// queue parent updates
				if (executor.getQueue().size() < MAX_UPDATE_TASK_COUNT
						&& this.parentUpdatingPosSet.size() < MAX_UPDATE_TASK_COUNT)
				{
					// get the positions that need to be applied to their parents
					LongArrayList parentUpdatePosList = this.repo.getPositionsToUpdate(MAX_UPDATE_TASK_COUNT);
					
					// combine updates together based on their parent
					HashMap<Long, HashSet<Long>> updatePosByParentPos = new HashMap<>();
					for (Long pos : parentUpdatePosList)
					{
						updatePosByParentPos.compute(DhSectionPos.getParentPos(pos), (parentPos, updatePosSet) ->
						{
							if (updatePosSet == null)
							{
								updatePosSet = new HashSet<>();
							}
							updatePosSet.add(pos);
							return updatePosSet;
						});
					}
					
					// queue the updates
					for (Long parentUpdatePos : updatePosByParentPos.keySet())
					{
						// stop if there are already a bunch of updates queued
						if (this.parentUpdatingPosSet.size() > MAX_UPDATE_TASK_COUNT
								|| !this.parentUpdatingPosSet.add(parentUpdatePos))
						{
							break;
						}
						
						try
						{
							executor.execute(() ->
							{
								ReentrantLock parentWriteLock = this.updateLockProvider.getLock(parentUpdatePos);
								boolean parentLocked = false;
								try
								{
									//LOGGER.info("updating parent: "+parentUpdatePos);
									
									// Locking the parent before the children should prevent deadlocks.
									// TryLock is used instead of lock so this thread can handle a different update.
									if (parentWriteLock.tryLock())
									{
										parentLocked = true;
										this.lockedPosSet.add(parentUpdatePos);
										
										// apply each child pos to the parent
										for (Long childPos : updatePosByParentPos.get(parentUpdatePos))
										{
											ReentrantLock childReadLock = this.updateLockProvider.getLock(childPos);
											try
											{
												childReadLock.lock();
												this.lockedPosSet.add(childPos);
												
												try (FullDataSourceV2 dataSource = this.get(childPos))
												{
													// can return null when the file handler is being shut down
													if (dataSource != null)
													{
														this.updateDataSourceAtPos(parentUpdatePos, dataSource, false);
														this.repo.setApplyToParent(childPos, false);
													}
												}
											}
											catch (Exception e)
											{
												LOGGER.error("issue in update for parent pos: " + parentUpdatePos+ " Error: "+e.getMessage(), e);
											}
											finally
											{
												childReadLock.unlock();
												this.lockedPosSet.remove(childPos);
											}
										}
									}
								}
								finally
								{
									if (parentLocked)
									{
										parentWriteLock.unlock();
										this.lockedPosSet.remove(parentUpdatePos);
									}
									
									this.parentUpdatingPosSet.remove(parentUpdatePos);
								}
							});
						}
						catch (RejectedExecutionException ignore)
						{ /* the executor was shut down, it should be back up shortly and able to accept new jobs */ }
						catch (Exception e)
						{
							this.parentUpdatingPosSet.remove(parentUpdatePos);
							throw e;
						}
					}
				}
				
			}
			catch (InterruptedException ignored)
			{
				Thread.currentThread().interrupt();
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected error in the parent update queue thread. Error: " + e.getMessage(), e);
			}
		}
		
		LOGGER.info("Update thread [" + Thread.currentThread().getName() + "] terminated.");
	}
	
	
	
	//=======================//
	// data source migration //
	//=======================//
	
	private void convertLegacyDataSources()
	{
		String dimensionName = this.level.getLevelWrapper().getDimensionType().getDimensionName();
		LOGGER.info("Attempting to migrate data sources for: [" + dimensionName + "]-[" + this.saveDir + "]...");
		
		
		
		//============================//
		// delete unused data sources //
		//============================//
		
		// this could be done all at once via SQL, 
		// but doing it in chunks prevents locking the database for long periods of time 
		long unusedCount = 0;
		long totalDeleteCount = this.legacyFileHandler.repo.getUnusedDataSourceCount();
		if (totalDeleteCount != 0)
		{
			// this should only be shown once per session but should be shown during 
			// either when the deletion or migration phases start
			this.showMigrationStartMessage();
			
			
			LOGGER.info("deleting [" + dimensionName + "] - [" + totalDeleteCount + "] unused data sources...");
			this.legacyDeletionCount = totalDeleteCount;
			
			ArrayList<String> unusedDataPosList = this.legacyFileHandler.repo.getUnusedDataSourcePositionStringList(50);
			while (unusedDataPosList.size() != 0)
			{
				unusedCount += unusedDataPosList.size();
				this.legacyDeletionCount -= unusedDataPosList.size();
				
				
				long startTime = System.currentTimeMillis();
				
				// delete batch and get next batch 
				this.legacyFileHandler.repo.deleteUnusedLegacyData(unusedDataPosList);
				unusedDataPosList = this.legacyFileHandler.repo.getUnusedDataSourcePositionStringList(50);
				
				long endStart = System.currentTimeMillis();
				long deleteTime = endStart - startTime;
				LOGGER.info("Deleting [" + dimensionName + "] - [" + unusedCount + "/" + totalDeleteCount + "] in [" + deleteTime + "]ms ...");
				
				
				// a slight delay is added to prevent accidentally locking the database when deleting a lot of rows
				// (that shouldn't be the case since we're using WAL journaling, but just in case)
				try
				{
					// use the delete time so we don't make powerful computers wait super long
					// and weak computers wait no time at all
					Thread.sleep(deleteTime / 2);
				}
				catch (InterruptedException ignore)
				{
				}
			}
			
			LOGGER.info("Done deleting [" + dimensionName + "] - [" + totalDeleteCount + "] unused data sources.");
		}
		
		
		
		//===========//
		// migration //
		//===========//
		
		long totalMigrationCount = this.legacyFileHandler.getDataSourceMigrationCount();
		this.migrationCount = totalMigrationCount;
		LOGGER.info("Found [" + totalMigrationCount + "] data sources that need migration.");
		
		ArrayList<FullDataSourceV1> legacyDataSourceList = this.legacyFileHandler.getDataSourcesToMigrate(MIGRATION_BATCH_COUNT);
		if (!legacyDataSourceList.isEmpty())
		{
			this.showMigrationStartMessage();
			
			
			// keep going until every data source has been migrated
			int progressCount = 0;
			while (!legacyDataSourceList.isEmpty() && this.migrationThreadRunning.get())
			{
				LOGGER.info("Migrating [" + dimensionName + "] - [" + progressCount + "/" + totalMigrationCount + "]...");
				
				ArrayList<CompletableFuture<Void>> updateFutureList = new ArrayList<>();
				for (int i = 0; i < legacyDataSourceList.size() && this.migrationThreadRunning.get(); i++)
				{
					FullDataSourceV1 legacyDataSource = legacyDataSourceList.get(i);
					
					try
					{
						// convert the legacy data source to the new format,
						// this is a relatively cheap operation
						FullDataSourceV2 newDataSource = FullDataSourceV2.createFromLegacyDataSourceV1(legacyDataSource);
						newDataSource.applyToParent = true;
						
						// the actual update process can be moderately expensive due to having to update
						// the render data along with the full data, so running it async on the update threads gains us a good bit of speed
						CompletableFuture<Void> future = this.updateDataSourceAsync(newDataSource);
						updateFutureList.add(future);
						future.thenRun(() ->
						{
							// after the update finishes the legacy data source can be safely deleted
							this.legacyFileHandler.repo.deleteWithKey(legacyDataSource.getPos());
							
							try
							{
								newDataSource.close();
							}
							catch (Exception ignore)
							{
							}
						});
					}
					catch (Exception e)
					{
						Long migrationPos = legacyDataSource.getPos();
						LOGGER.warn("Unexpected issue migrating data source at pos " + migrationPos + ". Error: " + e.getMessage(), e);
						this.legacyFileHandler.markMigrationFailed(migrationPos);
					}
				}
				
				
				try
				{
					// wait for each thread to finish updating
					CompletableFuture<Void> combinedFutures = CompletableFuture.allOf(updateFutureList.toArray(new CompletableFuture[0]));
					combinedFutures.get(MIGRATION_MAX_UPDATE_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS);
				}
				catch (InterruptedException | TimeoutException e)
				{
					LOGGER.warn("Migration update timed out after [" + MIGRATION_MAX_UPDATE_TIMEOUT_IN_MS + "] milliseconds. Migration will re-try the same positions again in a moment..", e);
				}
				catch (ExecutionException e)
				{
					LOGGER.warn("Migration update failed. Migration will re-try the same positions again. Error:" + e.getMessage(), e);
				}
				
				legacyDataSourceList = this.legacyFileHandler.getDataSourcesToMigrate(MIGRATION_BATCH_COUNT);
				
				progressCount += legacyDataSourceList.size();
				this.migrationCount -= legacyDataSourceList.size();
			}
			
			
			if (this.migrationThreadRunning.get())
			{
				LOGGER.info("migration complete for: [" + dimensionName + "]-[" + this.saveDir + "].");
				this.showMigrationEndMessage(true);
				this.migrationCount = 0;
			}
			else
			{
				LOGGER.info("migration stopped for: [" + dimensionName + "]-[" + this.saveDir + "].");
				this.showMigrationEndMessage(false);
			}
		}
		else
		{
			LOGGER.info("No migration necessary.");
		}
		
		this.migrationThreadRunning.set(false);
	}
	
	public long getLegacyDeletionCount() { return this.legacyDeletionCount; }
	public long getTotalMigrationCount() { return this.migrationCount; }
	
	
	private void showMigrationStartMessage()
	{
		if (this.migrationStartMessageQueued)
		{
			return;
		}
		this.migrationStartMessageQueued = true;
		
		String dimName = this.level.getLevelWrapper().getDimensionType().getDimensionName();
		ClientApi.INSTANCE.showChatMessageNextFrame(
				"Old Distant Horizons data is being migrated for ["+dimName+"]. \n" +
				"While migrating LODs may load slowly \n" +
				"and DH world gen will be disabled. \n" +
				"You can see migration progress in the F3 menu."
			);
	}
	
	private void showMigrationEndMessage(boolean success)
	{
		String dimName = this.level.getLevelWrapper().getDimensionType().getDimensionName();
		
		if (success)
		{
			ClientApi.INSTANCE.showChatMessageNextFrame("Distant Horizons data migration for ["+dimName+"] completed.");
		}
		else
		{
			ClientApi.INSTANCE.showChatMessageNextFrame(
					"Distant Horizons data migration for ["+dimName+"] stopped. \n" +
					"Some data may not have been migrated."
				);
		}
	}
	
	
	
	//=======================//
	// retrieval (world gen) //
	//=======================//
	
	/**
	 * Returns true if this provider can generate or retrieve
	 * {@link FullDataSourceV2}'s that aren't currently in the database.
	 */
	public boolean canRetrieveMissingDataSources()
	{
		// the base handler just handles basic reading/writing
		// to the database and as such can't retrieve anything else.
		return false;
	}
	
	/**
	 * Returns false if this provider isn't accepting new requests,
	 * this can be due to having a full queue or some other
	 * limiting factor. <br><br>
	 *
	 * Note: when overriding make sure to add: <br>
	 * <code>
	 * if (!super.canQueueRetrieval()) <br>
	 * { <br>
	 * return false; <br>
	 * } <br>
	 * </code>
	 * to the beginning of your override.
	 * Otherwise, parent retrieval limits will be ignored.
	 */
	public boolean canQueueRetrieval()
	{
		// Retrieval shouldn't happen while an unknown number of
		// legacy data sources are present.
		// If retrieval was allowed we might run into concurrency issues.
		return !this.migrationThreadRunning.get();
	}
	
	/**
	 * @return null if this provider can't generate any positions and
	 * an empty array if all positions were generated
	 */
	@Nullable
	public LongArrayList getPositionsToRetrieve(Long pos) { return null; }
	/**
	 * Returns how many positions could potentially be generated for this position assuming the position is empty.
	 * Used when estimating the total number of retrieval requests.
	 */
	public int getMaxPossibleRetrievalPositionCountForPos(Long pos) { return -1; }
	
	/** @return true if the position was queued, false if not */
	public boolean queuePositionForRetrieval(Long genPos) { return false; }
	
	/** does nothing if the given position isn't present in the queue */
	public void removeRetrievalRequestIf(DhSectionPos.ICancelablePrimitiveLongConsumer removeIf) { }
	
	public void clearRetrievalQueue() { }
	
	/** Can be used to display how many total retrieval requests might be available. */
	public void setTotalRetrievalPositionCount(int newCount) { }
	
	/**
	 * Returns how many data sources are currently in memory and haven't
	 * been saved to the database.
	 * Returns -1 if this provider never stores data sources to memory.
	 */
	public int getUnsavedDataSourceCount() { return -1; }
	
	public boolean fileExists(long pos) { return this.repo.getDataSizeInBytes(pos) > 0; }
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	public void debugRender(DebugRenderer renderer)
	{
		this.lockedPosSet
				.forEach((pos) -> { renderer.renderBox(new DebugRenderer.Box(pos, -32f, 74f, 0.15f, Color.PINK)); });
		
		this.queuedUpdateCountsByPos
				.forEach((pos, updateCountRef) -> { renderer.renderBox(new DebugRenderer.Box(pos, -32f, 80f + (updateCountRef.get() * 16f), 0.20f, Color.WHITE)); });
		this.parentUpdatingPosSet
				.forEach((pos) -> { renderer.renderBox(new DebugRenderer.Box(pos, -32f, 80f, 0.20f, Color.MAGENTA)); });
	}
	
	@Override
	public void close()
	{
		super.close();
		this.updateQueueProcessor.shutdownNow();
		
		this.legacyFileHandler.close();
		
		this.migrationThreadRunning.set(false);
		this.migrationThreadPool.shutdown();
	}
	
}