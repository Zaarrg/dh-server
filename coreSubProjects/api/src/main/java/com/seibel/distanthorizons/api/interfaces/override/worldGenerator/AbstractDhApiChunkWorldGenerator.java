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

package com.seibel.distanthorizons.api.interfaces.override.worldGenerator;

import com.seibel.distanthorizons.api.enums.EDhApiDetailLevel;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.interfaces.override.IDhApiOverrideable;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * @author James Seibel
 * @version 2023-6-22
 * @since API 1.0.0
 */
public abstract class AbstractDhApiChunkWorldGenerator implements Closeable, IDhApiOverrideable, IDhApiWorldGenerator
{
	//============//
	// parameters //
	//============//
	
	@Override
	public final byte getSmallestDataDetailLevel() { return EDhApiDetailLevel.BLOCK.detailLevel; }
	@Override
	public final byte getLargestDataDetailLevel() { return EDhApiDetailLevel.BLOCK.detailLevel; }
	@Override
	public final byte getMinGenerationGranularity() { return EDhApiDetailLevel.CHUNK.detailLevel; }
	@Override
	public final byte getMaxGenerationGranularity() { return (byte) (EDhApiDetailLevel.CHUNK.detailLevel + 2); }
	
	
	
	//=================//
	// world generator //
	//=================//
	
	@Override
	public final CompletableFuture<Void> generateChunks(
			int chunkPosMinX, int chunkPosMinZ,
			byte granularity, byte targetDataDetail, EDhApiDistantGeneratorMode generatorMode,
			ExecutorService worldGeneratorThreadPool, Consumer<Object[]> resultConsumer) throws ClassCastException
	{
		return CompletableFuture.runAsync(() ->
		{
			// TODO what does this mean?
			int genChunkWidth = BitShiftUtil.powerOfTwo(granularity - 4);
			
			for (int chunkX = chunkPosMinX; chunkX < chunkPosMinX + genChunkWidth; chunkX++)
			{
				for (int chunkZ = chunkPosMinZ; chunkZ < chunkPosMinZ + genChunkWidth; chunkZ++)
				{
					Object[] rawMcObjectArray = this.generateChunk(chunkX, chunkZ, generatorMode);
					resultConsumer.accept(rawMcObjectArray);
				}
			}
		}, worldGeneratorThreadPool);
	}
	
	/**
	 * This method is called to generate terrain over a given area
	 * from a thread defined by Distant Horizons. <br><br>
	 * 
	 * @param chunkPosX the chunk X position in the level (not to be confused with the chunk's BlockPos in the level)
	 * @param chunkPosZ the chunk Z position in the level (not to be confused with the chunk's BlockPos in the level)
	 * @param generatorMode how far into the world gen pipeline this method run. See {@link EDhApiDistantGeneratorMode} for additional documentation.
	 * 
	 * @return See {@link IDhApiWorldGenerator#generateChunks(int, int, byte, byte, EDhApiDistantGeneratorMode, ExecutorService, Consumer) IDhApiWorldGenerator.generateChunks}
	 *         for the list of Object's this method should return along with additional documentation.
	 *         
	 * @see IDhApiWorldGenerator#generateChunks(int, int, byte, byte, EDhApiDistantGeneratorMode, ExecutorService, Consumer) IDhApiWorldGenerator#generateChunks
	 */
	public abstract Object[] generateChunk(int chunkPosX, int chunkPosZ, EDhApiDistantGeneratorMode generatorMode);
	
}
