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

import java.util.List;

import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.api.objects.data.DhApiChunk;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.logging.log4j.Logger;

public class LodDataBuilder
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IBlockStateWrapper AIR = SingletonInjector.INSTANCE.get(IWrapperFactory.class).getAirBlockStateWrapper();
	/** how many chunks wide the {@link FullDataSourceV2} is. */
	private static final int NUMB_OF_CHUNKS_WIDE = FullDataSourceV2.WIDTH / LodUtil.CHUNK_WIDTH;
	
	private static boolean getTopErrorLogged = false;
	
	
	
	//============//
	// converters //
	//============//
	
	public static FullDataSourceV2 createGeneratedDataSource(IChunkWrapper chunkWrapper)
	{
		if (!canGenerateLodFromChunk(chunkWrapper))
		{
			return null;
		}
		
		
		
		// get the section position
		int sectionPosX = chunkWrapper.getChunkPos().x;
		// negative positions start at -1 so the logic there is slightly different
		sectionPosX = (sectionPosX < 0) ? ((sectionPosX + 1) / NUMB_OF_CHUNKS_WIDE) - 1 : (sectionPosX / NUMB_OF_CHUNKS_WIDE);
		int sectionPosZ = chunkWrapper.getChunkPos().z;
		sectionPosZ = (sectionPosZ < 0) ? ((sectionPosZ + 1) / NUMB_OF_CHUNKS_WIDE) - 1 : (sectionPosZ / NUMB_OF_CHUNKS_WIDE);
		long pos = DhSectionPos.encode(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, sectionPosX, sectionPosZ);
		
		FullDataSourceV2 dataSource = FullDataSourceV2.createEmpty(pos);
		dataSource.isEmpty = false;
		
		
		
		// compute the chunk dataSource offset
		// this offset is used to determine where in the dataSource this chunk's data should go
		int chunkOffsetX = chunkWrapper.getChunkPos().x;
		if (chunkWrapper.getChunkPos().x < 0)
		{
			// expected offset positions:
			// chunkPos -> offset
			//  5 -> 1
			//  4 -> 0 ---
			//  3 -> 3
			//  2 -> 2
			//  1 -> 1
			//  0 -> 0 ===
			// -1 -> 3
			// -2 -> 2
			// -3 -> 1
			// -4 -> 0 ---
			// -5 -> 3
			chunkOffsetX = ((chunkOffsetX) % NUMB_OF_CHUNKS_WIDE);
			if (chunkOffsetX != 0)
			{
				chunkOffsetX += NUMB_OF_CHUNKS_WIDE;
			}
		}
		else
		{
			chunkOffsetX %= NUMB_OF_CHUNKS_WIDE;
		}
		chunkOffsetX *= LodUtil.CHUNK_WIDTH;
		
		int chunkOffsetZ = chunkWrapper.getChunkPos().z;
		if (chunkWrapper.getChunkPos().z < 0)
		{
			chunkOffsetZ = ((chunkOffsetZ) % NUMB_OF_CHUNKS_WIDE);
			if (chunkOffsetZ != 0)
			{
				chunkOffsetZ += NUMB_OF_CHUNKS_WIDE;
			}
		}
		else
		{
			chunkOffsetZ %= NUMB_OF_CHUNKS_WIDE;
		}
		chunkOffsetZ *= LodUtil.CHUNK_WIDTH;
		
		
		
		//==========================//
		// populate the data source //
		//==========================//
		
		EDhApiWorldCompressionMode worldCompressionMode = Config.Client.Advanced.LodBuilding.worldCompression.get();
		boolean ignoreHiddenBlocks = (worldCompressionMode != EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
		
		try
		{
			int minBuildHeight = chunkWrapper.getMinNonEmptyHeight();
			for (int relBlockX = 0; relBlockX < LodUtil.CHUNK_WIDTH; relBlockX++)
			{
				for (int relBlockZ = 0; relBlockZ < LodUtil.CHUNK_WIDTH; relBlockZ++)
				{
					LongArrayList longs = new LongArrayList(chunkWrapper.getHeight() / 4);
					int lastY = chunkWrapper.getMaxBuildHeight();
					IBiomeWrapper biome = chunkWrapper.getBiome(relBlockX, lastY, relBlockZ);
					IBlockStateWrapper blockState = AIR;
					int mappedId = dataSource.mapping.addIfNotPresentAndGetId(biome, blockState);
					
					
					byte blockLight;
					byte skyLight;
					if (lastY < chunkWrapper.getMaxBuildHeight())
					{
						// FIXME: The lastY +1 offset is to reproduce the old behavior. Remove this when we get per-face lighting
						blockLight = (byte) chunkWrapper.getBlockLight(relBlockX, lastY + 1, relBlockZ);
						skyLight = (byte) chunkWrapper.getSkyLight(relBlockX, lastY + 1, relBlockZ);
					}
					else
					{
						//we are at the height limit. There are no torches here, and sky is not obscured.
						blockLight = 0;
						skyLight = 15;
					}
					
					
					// determine the starting Y Pos
					int y = chunkWrapper.getLightBlockingHeightMapValue(relBlockX, relBlockZ);
					// go up until we reach open air or the world limit
					IBlockStateWrapper topBlockState = chunkWrapper.getBlockState(relBlockX, y, relBlockZ);
					while (!topBlockState.isAir() && y < chunkWrapper.getMaxBuildHeight())
					{
						try
						{
							// This is necessary in some edge cases with snow layers and some other blocks that may not appear in the height map but do block light.
							// Interestingly this doesn't appear to be the case in the DhLightingEngine, if this same logic is added there the lighting breaks for the affected blocks.
							y++;
							topBlockState = chunkWrapper.getBlockState(relBlockX, y, relBlockZ);
						}
						catch (Exception e)
						{
							if (!getTopErrorLogged)
							{
								LOGGER.warn("Unexpected issue in LodDataBuilder, future errors won't be logged. Chunk [" + chunkWrapper.getChunkPos() + "] with max height: [" + chunkWrapper.getMaxBuildHeight() + "] had issue getting block at pos [" + relBlockX + "," + y + "," + relBlockZ + "] error: " + e.getMessage(), e);
								getTopErrorLogged = true;
							}
							
							y--;
							break;
						}
					}
					
					
					for (; y >= minBuildHeight; y--)
					{
						IBiomeWrapper newBiome = chunkWrapper.getBiome(relBlockX, y, relBlockZ);
						IBlockStateWrapper newBlockState = chunkWrapper.getBlockState(relBlockX, y, relBlockZ);
						byte newBlockLight = (byte) chunkWrapper.getBlockLight(relBlockX, y + 1, relBlockZ);
						byte newSkyLight = (byte) chunkWrapper.getSkyLight(relBlockX, y + 1, relBlockZ);
						
						// save the biome/block change
						if (!newBiome.equals(biome) || !newBlockState.equals(blockState))
						{
							// if we ignore hidden blocks, don't save this biome/block change
							// wait until the block is visible and then save the new datapoint
							if (!ignoreHiddenBlocks
									// if the last block is air, this block will always be visible
									|| blockState.isAir()
									// check if this block is visible from any direction 
									|| blockVisible(chunkWrapper, relBlockX, y, relBlockZ))
							{
								longs.add(FullDataPointUtil.encode(mappedId, lastY - y, y + 1 - chunkWrapper.getMinBuildHeight(), blockLight, skyLight));
								biome = newBiome;
								blockState = newBlockState;
								mappedId = dataSource.mapping.addIfNotPresentAndGetId(biome, blockState);
								blockLight = newBlockLight;
								skyLight = newSkyLight;
								lastY = y;
							}
						}
					}
					longs.add(FullDataPointUtil.encode(mappedId, lastY - y, y + 1 - chunkWrapper.getMinBuildHeight(), blockLight, skyLight));
					
					dataSource.setSingleColumn(longs,
							relBlockX + chunkOffsetX,
							relBlockZ + chunkOffsetZ,
							EDhApiWorldGenerationStep.LIGHT,
							worldCompressionMode);
				}
			}
		}
		catch (DataCorruptedException e)
		{
			LOGGER.error("Unable to convert chunk at pos ["+chunkWrapper.getChunkPos()+"] to an LOD. Error: "+e.getMessage(), e);
			return null;
		}
		
		LodUtil.assertTrue(!dataSource.isEmpty);
		return dataSource;
	}
	private static boolean blockVisible(IChunkWrapper chunkWrapper, int relBlockX, int blockY, int relBlockZ)
	{
		DhBlockPos originalBlockPos = new DhBlockPos(relBlockX,blockY,relBlockZ);
		DhBlockPos testBlockPos = new DhBlockPos(relBlockX,blockY,relBlockZ);
		
		// up/down
		if (blockInDirectionVisible(chunkWrapper, EDhDirection.UP, originalBlockPos, testBlockPos))
		{
			return true;
		}
		if (blockInDirectionVisible(chunkWrapper, EDhDirection.DOWN, originalBlockPos, testBlockPos))
		{
			return true;
		}
		
		// north/south
		if (blockInDirectionVisible(chunkWrapper, EDhDirection.NORTH, originalBlockPos, testBlockPos))
		{
			return true;
		}
		if (blockInDirectionVisible(chunkWrapper, EDhDirection.SOUTH, originalBlockPos, testBlockPos))
		{
			return true;
		}
		
		// east/west
		if (blockInDirectionVisible(chunkWrapper, EDhDirection.EAST, originalBlockPos, testBlockPos))
		{
			return true;
		}
		if (blockInDirectionVisible(chunkWrapper, EDhDirection.WEST, originalBlockPos, testBlockPos))
		{
			return true;
		}
		
		
		return false;
	}
	private static boolean blockInDirectionVisible(IChunkWrapper chunkWrapper, EDhDirection direction, DhBlockPos originalBlockPos, DhBlockPos testBlockPos)
	{
		originalBlockPos.mutateOffset(direction, testBlockPos);
		
		// if the block is next to the border of a chunk, assume it's visible
		if (testBlockPos.x < 0 || testBlockPos.x >= LodUtil.CHUNK_WIDTH)
		{
			return true;
		}
		if (testBlockPos.z < 0 || testBlockPos.z >= LodUtil.CHUNK_WIDTH)
		{
			return true;
		}
		if (testBlockPos.y < chunkWrapper.getMinBuildHeight() || testBlockPos.y > chunkWrapper.getMaxBuildHeight())
		{
			return true;
		}
		
		// this block isn't on a chunk boundary, check if it is next to a transparent/air block
		IBlockStateWrapper blockState = chunkWrapper.getBlockState(testBlockPos);
		return blockState.isAir() || blockState.getOpacity() != IBlockStateWrapper.FULLY_OPAQUE;
	}
	
	
	/** @throws ClassCastException if an API user returns the wrong object type(s) */
	public static FullDataSourceV2 createFromApiChunkData(DhApiChunk dataPoints) throws ClassCastException, DataCorruptedException
	{
		FullDataSourceV2 accessor = FullDataSourceV2.createEmpty(DhSectionPos.encode(new DhChunkPos(dataPoints.chunkPosX, dataPoints.chunkPosZ)));
		for (int relZ = 0; relZ < LodUtil.CHUNK_WIDTH; relZ++)
		{
			for (int relX = 0; relX < LodUtil.CHUNK_WIDTH; relX++)
			{
				List<DhApiTerrainDataPoint> columnDataPoints = dataPoints.getDataPoints(relX, relZ);
				
				
				// this null check does 2 nice things at the same time:
				// if columnDataPoints is null,
				// then packedDataPoints will be of length 0
				// AND the below loop won't run.
				int size = (columnDataPoints != null) ? columnDataPoints.size() : 0;
				
				LongArrayList packedDataPoints = new LongArrayList(new long[size]);
				for (int index = 0; index < size; index++)
				{
					DhApiTerrainDataPoint dataPoint = columnDataPoints.get(index);
					
					int id = accessor.mapping.addIfNotPresentAndGetId(
							(IBiomeWrapper) (dataPoint.biomeWrapper),
							(IBlockStateWrapper) (dataPoint.blockStateWrapper)
					);
					
					packedDataPoints.set(index, FullDataPointUtil.encode(
							id,
							dataPoint.topYBlockPos - dataPoint.bottomYBlockPos,
							dataPoint.bottomYBlockPos - dataPoints.topYBlockPos,
							(byte) (dataPoint.blockLightLevel),
							(byte) (dataPoint.skyLightLevel)
					));
				}
				
				// TODO add the ability for API users to define a different compression mode
				//  or add a "unkown" compression mode
				accessor.setSingleColumn(packedDataPoints, relX, relZ, EDhApiWorldGenerationStep.LIGHT, EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
			}
		}
		
		return accessor;
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	public static boolean canGenerateLodFromChunk(IChunkWrapper chunk) { return chunk != null && chunk.isLightCorrect(); }
	
}
