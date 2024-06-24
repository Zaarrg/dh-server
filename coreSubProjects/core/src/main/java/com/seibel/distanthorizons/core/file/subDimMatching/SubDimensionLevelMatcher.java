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

package com.seibel.distanthorizons.core.file.subDimMatching;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.file.structure.ClientOnlySaveStructure;
import com.seibel.distanthorizons.core.generation.DhLightingEngine;
import com.seibel.distanthorizons.core.level.DhClientLevel;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.dataObjects.transformers.LodDataBuilder;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.logging.log4j.LogManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Used to allow multiple levels using the same dimension type. <br/>
 * This is specifically needed for servers running the Multiverse plugin (or similar).
 *
 * @author James Seibel
 * @version 12-17-2022
 */
public class SubDimensionLevelMatcher implements AutoCloseable
{
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	public static final ConfigBasedLogger LOGGER = new ConfigBasedLogger(LogManager.getLogger(),
			() -> Config.Client.Advanced.Logging.logFileSubDimEvent.get());
	
	private final ExecutorService matcherThread = ThreadUtil.makeSingleThreadPool("Sub Dimension Matcher");
	
	private SubDimensionPlayerData playerData = null;
	private SubDimensionPlayerData firstSeenPlayerData = null;
	
	/** If true the LodDimensionFileHelper is attempting to determine the folder for this dimension */
	private final AtomicBoolean determiningWorldFolder = new AtomicBoolean(false);
	private final IClientLevelWrapper currentClientLevel;
	private volatile File foundLevelFile = null;
	private final List<File> potentialLevelFolders;
	private final File levelsFolder;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public SubDimensionLevelMatcher(IClientLevelWrapper targetLevel, File levelsFolder, List<File> potentialLevelFolders)
	{
		this.currentClientLevel = targetLevel;
		this.potentialLevelFolders = potentialLevelFolders;
		this.levelsFolder = levelsFolder;
		
		if (potentialLevelFolders.size() == 0)
		{
			String newId = UUID.randomUUID().toString();
			LOGGER.info("No potential level files found. Creating a new sub dimension with the ID ["+LodUtil.shortenString(newId, 8)+"]...");
			this.foundLevelFile = this.CreateSubDimFolder(newId);
		}
	}
	
	
	
	//==============//
	// level finder //
	//==============//
	
	public boolean isFindingLevel(ILevelWrapper level) { return Objects.equals(level, this.currentClientLevel); }
	
	/** May return null if the level isn't known yet */
	public File tryGetLevel()
	{
		this.tryGetLevelInternalAsync();
		return this.foundLevelFile;
	}
	private void tryGetLevelInternalAsync()
	{
		if (this.foundLevelFile != null)
		{
			return;
		}
		
		// prevent multiple threads running at the same time
		if (this.determiningWorldFolder.getAndSet(true))
		{
			return;
		}
		
		
		this.matcherThread.submit(() ->
		{
			try
			{
				// attempt to get the file handler
				File saveDir = this.attemptToDetermineSubDimensionFolder();
				if (saveDir != null)
				{
					this.foundLevelFile = saveDir;
				}
			}
			catch (IOException e)
			{
				LOGGER.error("Unable to set the dimension file handler for level [" + this.currentClientLevel + "]. Error: ", e);
			}
			finally
			{
				// make sure we unlock this method
				this.determiningWorldFolder.set(false);
			}
		});
	}
	
	/**
	 * Currently this method checks a single chunk (where the player is)
	 * and compares it against the same chunk position in the other dimension worlds to
	 * guess which world the player is in.
	 *
	 * @throws IOException if the folder doesn't exist or can't be accessed
	 */
	public File attemptToDetermineSubDimensionFolder() throws IOException
	{
		// Update PlayerData
		SubDimensionPlayerData newPlayerData = SubDimensionPlayerData.tryGetPlayerData(MC_CLIENT);
		if (newPlayerData != null)
		{
			if (this.firstSeenPlayerData == null)
			{
				this.firstSeenPlayerData = newPlayerData;
			}
			this.playerData = newPlayerData;
		}
		
		
		
		//================================//
		// generate a LOD to test against //
		//================================//
		
		// attempt to get a chunk at the player's pos
		IChunkWrapper newlyLoadedChunk = MC_CLIENT.getWrappedClientLevel().tryGetChunk(new DhChunkPos(this.playerData.playerBlockPos));
		if (newlyLoadedChunk == null)
		{
			return null;
		}
		DhLightingEngine.INSTANCE.lightChunk(newlyLoadedChunk, new ArrayList<>(), MC_CLIENT.getWrappedClientLevel().hasSkyLight() ? 15 : 0);
		
		// build the chunk LOD
		if (!LodDataBuilder.canGenerateLodFromChunk(newlyLoadedChunk))
		{
			LOGGER.warn("unable to build lod for chunk:"+newlyLoadedChunk.getChunkPos());
			return null;
		}
		FullDataSourceV2 newChunkSizedFullDataView = FullDataSourceV2.createFromChunk(newlyLoadedChunk);
		// convert to a data source for easier comparing
		FullDataSourceV2 newDataSource = FullDataSourceV2.createEmpty(DhSectionPos.encode(this.playerData.playerBlockPos));
		newDataSource.update(newChunkSizedFullDataView);
		
		
		
		//================================//
		// test each known sub-dim folder //
		//================================//
		
		// log the start of this attempt
		LOGGER.info("Attempting to determine sub-dimension for [" + MC_CLIENT.getWrappedClientLevel().getDimensionType().getDimensionName() + "]");
		LOGGER.info("Player block pos in dimension: [" + this.playerData.playerBlockPos.x + "," + this.playerData.playerBlockPos.y + "," + this.playerData.playerBlockPos.z + "]");
		LOGGER.info("Potential Sub Dimension folders: [" + this.potentialLevelFolders.size() + "]");
		
		SubDimCompare mostSimilarSubDim = null;
		for (File testLevelFolder : this.potentialLevelFolders)
		{
			LOGGER.info("Testing level folder: [" + LodUtil.shortenString(testLevelFolder.getName(), 8) + "]");
			
			FullDataSourceV2 testFullDataSource = null;
			try
			{
				// get the data source to compare against
				try (IDhLevel tempLevel = new DhClientLevel(new ClientOnlySaveStructure(), this.currentClientLevel, testLevelFolder, false, null))
				{
					testFullDataSource = tempLevel.getFullDataProvider().getAsync(DhSectionPos.encode(this.playerData.playerBlockPos)).join();
					if (testFullDataSource == null)
					{
						continue;
					}
				}
				
				
				// confirm both data sources have the same section pos
				long newSectionChunkPos = DhSectionPos.convertToDetailLevel(newDataSource.getPos(), DhSectionPos.SECTION_CHUNK_DETAIL_LEVEL);
				long testSectionChunkPos = DhSectionPos.convertToDetailLevel(testFullDataSource.getPos(), DhSectionPos.SECTION_CHUNK_DETAIL_LEVEL);
				LodUtil.assertTrue(newSectionChunkPos == testSectionChunkPos, "data source positions don't match");
				
				
				
				// compare the data sources
				int equalDataPoints = 0;
				int totalDataPointCount = 0;
				for (int x = 0; x < FullDataSourceV2.WIDTH; x++)
				{
					for (int z = 0; z < FullDataSourceV2.WIDTH; z++)
					{
						LongArrayList newColumn = newDataSource.get(x, z);
						LongArrayList testColumn = testFullDataSource.get(x, z);
						
						if (newColumn != null && testColumn != null)
						{
							// compare each data point in the column
							
							FullDataPointIdMap newDataMap = newDataSource.mapping;
							FullDataPointIdMap testDataMap = testFullDataSource.mapping;
							
							// use min to prevent going out of bounds
							int minColumnIndex = Math.min(newColumn.size(), testColumn.size());
							for (int i = 0; i < minColumnIndex; i++)
							{
								long newDataPoint = newColumn.getLong(i);
								long testDataPoint = testColumn.getLong(i);
								
								int newId = FullDataPointUtil.getId(newDataPoint);
								int testId = FullDataPointUtil.getId(testDataPoint);
								
								
								// bottom Y
								int newBottom = FullDataPointUtil.getBottomY(newDataPoint);
								int testBottom = FullDataPointUtil.getBottomY(testDataPoint);
								if (newBottom == testBottom)
								{
									equalDataPoints++;	
								}
								totalDataPointCount++;
								
								// height
								int newHeight = FullDataPointUtil.getHeight(newDataPoint);
								int testHeight = FullDataPointUtil.getHeight(testDataPoint);
								if (newHeight == testHeight)
								{
									equalDataPoints++;
								}
								totalDataPointCount++;
								
								// biome
								IBiomeWrapper newBiome = newDataMap.getBiomeWrapper(newId);
								IBiomeWrapper testBiome = testDataMap.getBiomeWrapper(testId);
								if (newBiome.equals(testBiome))
								{
									equalDataPoints++;
								}
								totalDataPointCount++;
								
								// block
								IBlockStateWrapper newBlock = newDataMap.getBlockStateWrapper(newId);
								IBlockStateWrapper testBlock = testDataMap.getBlockStateWrapper(testId);
								if (newBlock.equals(testBlock))
								{
									equalDataPoints++;
								}
								totalDataPointCount++;
								
								// ignore light values 
								// since we are using the DH lighting engine and only 1 chunk the values will never be the same
							}
						}
						else if (newColumn != null)
						{
							// missing test column
							totalDataPointCount += newColumn.size();
						}
						else
						{
							// new column present, test absent, can't compare
						}
					}
				}
				
				
				
				// get the player data for this dimension folder
				SubDimensionPlayerData testPlayerData = new SubDimensionPlayerData(testLevelFolder);
				LOGGER.info("Last known player pos: [" + testPlayerData.playerBlockPos.x + "," + testPlayerData.playerBlockPos.y + "," + testPlayerData.playerBlockPos.z + "]");
				
				// check if the block positions are close
				int playerBlockDist = testPlayerData.playerBlockPos.getManhattanDistance(this.playerData.playerBlockPos);
				LOGGER.info("Player block position distance between saved sub dimension and first seen is [" + playerBlockDist + "]");
				
				// determine if this world is closer to the newly loaded world
				SubDimCompare subDimCompare = new SubDimCompare(equalDataPoints, totalDataPointCount, playerBlockDist, testLevelFolder);
				if (mostSimilarSubDim == null || subDimCompare.compareTo(mostSimilarSubDim) > 0)
				{
					mostSimilarSubDim = subDimCompare;
				}
				
				
				String subDimShortName = LodUtil.shortenString(testLevelFolder.getName(), 8); // variables are separated out for easier debugging
				String equalPercent = LodUtil.shortenString(mostSimilarSubDim.getPercentEqual()+"", 5);
				LOGGER.info("Sub dimension ["+subDimShortName+"...] is current dimension probability: "+equalPercent+" ("+equalDataPoints+"/"+totalDataPointCount+")");
			}
			catch (Exception e)
			{
				// this sub dimension isn't formatted correctly
				// for now we are just assuming it is an unrelated file
				LOGGER.warn("Error checking level: "+e.getMessage(), e);
			}
			finally
			{
				if (testFullDataSource != null)
				{
					try { testFullDataSource.close(); } catch (Exception ignore) {}
				}
			}
		}
		
		
		
		//================================//
		// return the found sub dimension //
		//================================//
		
		// the first seen player data is no longer needed, the sub dimension has been determined
		this.firstSeenPlayerData = null;
		if (mostSimilarSubDim != null && mostSimilarSubDim.isValidSubDim())
		{
			// we found a sub dim folder that is similar, use it
			
			LOGGER.info("Sub Dimension set to: [" + LodUtil.shortenString(mostSimilarSubDim.folder.getName(), 8) + "...] with an equality of [" + mostSimilarSubDim.getPercentEqual() + "]");
			return mostSimilarSubDim.folder;
		}
		else
		{
			// no sub dim folder, create a new one
			
			String newId = UUID.randomUUID().toString();
			
			double highestEqualityPercent = mostSimilarSubDim != null ? mostSimilarSubDim.getPercentEqual() : 0;
			String message = "No suitable sub dimension found. The highest equality was [" + LodUtil.shortenString(highestEqualityPercent + "", 5) + "]. Creating a new sub dimension with ID: " + LodUtil.shortenString(newId, 8) + "...";
			LOGGER.info(message);
			
			File folder = this.CreateSubDimFolder(newId);
			folder.mkdirs();
			return folder;
		}
	}
	
	
	private File CreateSubDimFolder(String subDimId) { return new File(this.levelsFolder.getPath() + File.separatorChar + this.currentClientLevel.getDimensionType().getDimensionName(), subDimId); }
	
	@Override
	public void close() { this.matcherThread.shutdownNow(); }
	
}