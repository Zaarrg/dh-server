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

package com.seibel.distanthorizons.api.objects.data;

import com.seibel.distanthorizons.api.interfaces.factories.IDhApiWrapperFactory;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains a list of {@link DhApiTerrainDataPoint} representing the blocks in a Minecraft chunk.
 *
 * @author Builderb0y, James Seibel
 * @version 2023-12-21
 * @since API 2.0.0
 * 
 * @see IDhApiWrapperFactory
 * @see DhApiTerrainDataPoint
 * @see IDhApiWorldGenerator
 */
public class DhApiChunk 
{
	public final int chunkPosX;
	public final int chunkPosZ;
	
	public final int topYBlockPos;
	public final int bottomYBlockPos;
	
	private final List<List<DhApiTerrainDataPoint>> dataPoints;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public DhApiChunk(int chunkPosX, int chunkPosZ, int topYBlockPos, int bottomYBlockPos) 
	{
		this.chunkPosX = chunkPosX;
		this.chunkPosZ = chunkPosZ;
		this.topYBlockPos = topYBlockPos;
		this.bottomYBlockPos = bottomYBlockPos;
		
		// populate the array to prevent null pointers
		this.dataPoints = new ArrayList<>(16 * 16); // 256
		for (int i = 0; i < (16*16); i++)
		{
			this.dataPoints.add(i, null);
		}
	}
	
	
	
	//=================//
	// getters/setters //
	//=================//
	
	/**
	 * @param relX a block position between 0 and 15 (inclusive) representing the X axis in the chunk
	 * @param relZ a block position between 0 and 15 (inclusive) representing the Z axis in the chunk
	 * @return the {@link DhApiTerrainDataPoint}'s representing the blocks at the relative X and Z position in the chunk.
	 * @throws IndexOutOfBoundsException if relX or relZ are outside the chunk
	 */
	public List<DhApiTerrainDataPoint> getDataPoints(int relX, int relZ) throws IndexOutOfBoundsException
	{
		throwIfRelativePosOutOfBounds(relX, relZ);
		return this.dataPoints.get((relZ << 4) | relX); 
	}
	
	/**
	 * @param relX a block position between 0 and 15 (inclusive) representing the X axis in the chunk
	 * @param relZ a block position between 0 and 15 (inclusive) representing the Z axis in the chunk
	 * @param dataPoints Represents the blocks at the relative X and Z position in the chunk.
	 *                  Cannot contain null objects or data points with any detail level but 0 (block-sized).
	 * @throws IndexOutOfBoundsException if relX or relZ are outside the chunk
	 */
	public void setDataPoints(int relX, int relZ, List<DhApiTerrainDataPoint> dataPoints) throws IndexOutOfBoundsException, IllegalArgumentException
	{
		throwIfRelativePosOutOfBounds(relX, relZ);
		
		// validate the incoming datapoints
		if (dataPoints != null)
		{
			for (int i = 0; i < dataPoints.size(); i++) // standard for-loop used instead of an enhanced for-loop to slightly reduce GC overhead due to iterator allocation
			{
				DhApiTerrainDataPoint dataPoint = dataPoints.get(i);
				if (dataPoint == null)
				{
					throw new IllegalArgumentException("Null DhApiTerrainDataPoints are not allowed. If you want to represent empty terrain, please use AIR.");
				}
				
				if (dataPoint.detailLevel != 0)
				{
					throw new IllegalArgumentException("DhApiTerrainDataPoints has the wrong detail level ["+dataPoint.detailLevel+"], all data points must be block sized; IE their detail level must be [0].");
				}
			}
		}
		
		this.dataPoints.set((relZ << 4) | relX, dataPoints); 
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	/** Included to prevent users accidentally setting columns outside the chunk */
	private static void throwIfRelativePosOutOfBounds(int relX, int relZ)
	{
		if (relX < 0 || relX > 15 ||
			relZ < 0 || relZ > 15)
		{
			throw new IndexOutOfBoundsException("Relative block positions must be between 0 and 15 (inclusive) the block pos: ("+relX+","+relZ+") is outside of those boundaries.");
		}
	}
	
}