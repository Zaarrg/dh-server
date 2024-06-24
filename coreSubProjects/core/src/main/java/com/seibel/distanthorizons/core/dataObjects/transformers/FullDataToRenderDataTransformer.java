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

import com.seibel.distanthorizons.api.enums.config.EDhApiBlocksToAvoid;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;

/**
 * Handles converting {@link FullDataSourceV2}'s to {@link ColumnRenderSource}.
 */
public class FullDataToRenderDataTransformer
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	private static final IWrapperFactory WRAPPER_FACTORY = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	private static final LongOpenHashSet brokenPos = new LongOpenHashSet();
	
	
	
	//==============================//
	// public transformer interface //
	//==============================//
	
	public static ColumnRenderSource transformFullDataToRenderSource(FullDataSourceV2 fullDataSource, IDhClientLevel level)
	{
		if (fullDataSource == null)
		{
			return null;
		}
		else if (level == null)
		{
			// if the client is no longer loaded in the world, render sources cannot be created 
			return null;
		}
		
		
		try
		{
			return transformCompleteFullDataToColumnData(level, fullDataSource);
		}
		catch (InterruptedException e)
		{
			return null;
		}
	}
	
	
	
	//==============//
	// transformers //
	//==============//
	
	/**
	 * Creates a LodNode for a chunk in the given world.
	 *
	 * @throws IllegalArgumentException thrown if either the chunk or world is null.
	 * @throws InterruptedException Can be caused by interrupting the thread upstream.
	 * Generally thrown if the method is running after the client leaves the current world.
	 */
	private static ColumnRenderSource transformCompleteFullDataToColumnData(IDhClientLevel level, FullDataSourceV2 fullDataSource) throws InterruptedException
	{
 		final long pos = fullDataSource.getPos();
		final byte dataDetail = fullDataSource.getDataDetailLevel();
		final int vertSize = Config.Client.Advanced.Graphics.Quality.verticalQuality.get().calculateMaxVerticalData(fullDataSource.getDataDetailLevel());
		final ColumnRenderSource columnSource = ColumnRenderSource.getPooledRenderSource(pos, vertSize, level.getMinY(), true);
		if (fullDataSource.isEmpty)
		{
			return columnSource;
		}
		
		columnSource.markNotEmpty();
		
		if (dataDetail == columnSource.getDataDetailLevel())
		{
			int baseX = DhSectionPos.getMinCornerBlockX(pos);
			int baseZ = DhSectionPos.getMinCornerBlockZ(pos);
			
			for (int x = 0; x < DhSectionPos.getWidthCountForLowerDetailedSection(pos, dataDetail); x++)
			{
				for (int z = 0; z < DhSectionPos.getWidthCountForLowerDetailedSection(pos, dataDetail); z++)
				{
					throwIfThreadInterrupted();
					
					ColumnArrayView columnArrayView = columnSource.getVerticalDataPointView(x, z);
					LongArrayList dataColumn = fullDataSource.get(x, z);
					convertColumnData(level, fullDataSource.mapping, baseX + x, baseZ + z, columnArrayView, dataColumn);
				}
			}
			
			columnSource.fillDebugFlag(0, 0, ColumnRenderSource.SECTION_SIZE, ColumnRenderSource.SECTION_SIZE, ColumnRenderSource.DebugSourceFlag.FULL);
			
		}
		else
		{
			throw new UnsupportedOperationException("To be implemented");
			//FIXME: Implement different size creation of renderData
		}
		return columnSource;
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	/**
	 * Called in loops that may run for an extended period of time. <br>
	 * This is necessary to allow canceling these transformers since running
	 * them after the client has left a given world will throw exceptions.
	 */
	private static void throwIfThreadInterrupted() throws InterruptedException
	{
		if (Thread.interrupted())
		{
			throw new InterruptedException(FullDataToRenderDataTransformer.class.getSimpleName() + " task interrupted.");
		}
	}
	
	
	// TODO what does this mean?
	private static void iterateAndConvert(
			IDhClientLevel level, FullDataPointIdMap fullDataMapping, 
			int blockX, int blockZ, 
			ColumnArrayView renderColumnData, LongArrayList fullColumnData)
	{
		boolean avoidSolidBlocks = (Config.Client.Advanced.Graphics.Quality.blocksToIgnore.get() == EDhApiBlocksToAvoid.NON_COLLIDING);
		boolean colorBelowWithAvoidedBlocks = Config.Client.Advanced.Graphics.Quality.tintWithAvoidedBlocks.get();
		
		HashSet<IBlockStateWrapper> blockStatesToIgnore = WRAPPER_FACTORY.getRendererIgnoredBlocks(level.getLevelWrapper());
		
		boolean isVoid = true;
		int colorToApplyToNextBlock = -1;
		int lastColor = 0;
		int lastBottom = -10000;
		int skylightToApplyToNextBlock = -1;
		int blocklightToApplyToNextBlock = -1;
		int columnOffset = 0;
		
		// goes from the top down
		for (int i = 0; i < fullColumnData.size(); i++)
		{
			long fullData = fullColumnData.getLong(i);
			int bottomY = FullDataPointUtil.getBottomY(fullData);
			int blockHeight = FullDataPointUtil.getHeight(fullData);
			int id = FullDataPointUtil.getId(fullData);
			int blockLight = FullDataPointUtil.getBlockLight(fullData);
			int skyLight = FullDataPointUtil.getSkyLight(fullData);
			
			// TODO how should corrupted data be handled?
			// TODO why is the full data corrupted in the first place? FullDataPointUtil hasn't been changed in a long time, could one of the full data point objects be corrupted?
			// TODO if either of these happen the ID might also be invalid
			//if (bottomY + blockHeight > 300)
			//{
			//  // this data point is too tall, it's probably a monolith
			//	int k = 0;
			//	throw new RuntimeException();
			//}
			//if (light > 16 || light < 0)
			//{
			//  // light is out of range
			//	throw new RuntimeException();
			//}
			
			IBiomeWrapper biome;
			IBlockStateWrapper block;
			try
			{
				biome = fullDataMapping.getBiomeWrapper(id);
				block = fullDataMapping.getBlockStateWrapper(id);
			}
			catch (IndexOutOfBoundsException e)
			{
				// FIXME sometimes the data map has a length of 0
				if (!brokenPos.contains(fullDataMapping.getPos()))
				{
					brokenPos.add(fullDataMapping.getPos());
					String dimName = level.getLevelWrapper().getDimensionType().getDimensionName();
					LOGGER.warn("Unable to get data point with id ["+id+"] " +
							"(Max possible ID: ["+fullDataMapping.getMaxValidId()+"]) " +
							"for pos ["+fullDataMapping.getPos()+"] in dimension ["+dimName+"]. " +
							"Error: ["+e.getMessage()+"]. " +
							"Further errors for this position won't be logged.");
				}
				
				// skip rendering broken data
				continue;
			}
			
			
			if (blockStatesToIgnore.contains(block))
			{
				// Don't render: air, barriers, light blocks, etc.
				continue;
			}
			
			
			// solid block check
			if (avoidSolidBlocks && !block.isSolid() && !block.isLiquid() && block.getOpacity() != IBlockStateWrapper.FULLY_OPAQUE)
			{
				if (colorBelowWithAvoidedBlocks)
				{
					int tempColor = level.computeBaseColor(new DhBlockPos(blockX, bottomY + level.getMinY(), blockZ), biome, block);
					if (ColorUtil.getAlpha(tempColor) == 0)
					{
						//make sure to not transfer the color when alpha is 0
						continue;
					}
					//mare sure to not trnasfer alpha if for some reason grass is semi transparent
					colorToApplyToNextBlock = ColorUtil.setAlpha(tempColor,255);
					skylightToApplyToNextBlock = skyLight;
					blocklightToApplyToNextBlock = blockLight;
				}
				
				// don't add this block
				continue;
			}
			
			
			int color;
			if (colorToApplyToNextBlock == -1)
			{
				// use this block's color
				color = level.computeBaseColor(new DhBlockPos(blockX, bottomY + level.getMinY(), blockZ), biome, block);
			}
			else
			{
				// use the previous block's color
				color = colorToApplyToNextBlock;
				colorToApplyToNextBlock = -1;
				skyLight = skylightToApplyToNextBlock;
				blockLight = blocklightToApplyToNextBlock;
			}

			//check if they share a top-bottom face and if they have same collor
			if (color == lastColor && bottomY + blockHeight == lastBottom  && columnOffset > 0)
			{
				//replace the previus block with new bottom
				long columnData = renderColumnData.get(columnOffset - 1);
				columnData = RenderDataPointUtil.setYMin(columnData, bottomY);
				renderColumnData.set(columnOffset - 1, columnData);
			}
			else
			{
				// add the block
				isVoid = false;
				long columnData = RenderDataPointUtil.createDataPoint(bottomY + blockHeight, bottomY, color, skyLight, blockLight, block.getIrisBlockMaterialId());
				renderColumnData.set(columnOffset, columnData);
				columnOffset++;
			}
			lastBottom = bottomY;
			lastColor = color;

		}
		
		
		if (isVoid)
		{
			renderColumnData.set(0, RenderDataPointUtil.createVoidDataPoint());
		}
	}
	
	// TODO what does this mean?
	public static void convertColumnData(IDhClientLevel level, FullDataPointIdMap fullDataMapping, int blockX, int blockZ, ColumnArrayView columnArrayView, LongArrayList fullDataColumn)
	{
		if (fullDataColumn == null || fullDataColumn.size() == 0)
		{
			return;
		}
		
		int dataTotalLength = fullDataColumn.size();
		if (dataTotalLength > columnArrayView.verticalSize())
		{
			ColumnArrayView totalColumnData = new ColumnArrayView(new LongArrayList(new long[dataTotalLength]), dataTotalLength, 0, dataTotalLength);
			iterateAndConvert(level, fullDataMapping, blockX, blockZ, totalColumnData, fullDataColumn);
			columnArrayView.changeVerticalSizeFrom(totalColumnData);
		}
		else
		{
			iterateAndConvert(level, fullDataMapping, blockX, blockZ, columnArrayView, fullDataColumn); //Directly use the arrayView since it fits.
		}
	}
	
}
