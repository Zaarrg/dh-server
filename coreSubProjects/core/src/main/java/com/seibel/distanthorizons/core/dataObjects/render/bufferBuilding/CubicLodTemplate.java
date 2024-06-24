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

package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.pos.DhLodPos;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;

/**
 * Builds LODs as rectangular prisms.
 *
 * @author James Seibel
 * @version 2022-1-2
 */
public class CubicLodTemplate
{
	
	public static void addLodToBuffer(
			long data, long topData, long bottomData, ColumnArrayView[][] adjColumnViews,
			byte detailLevel, int offsetPosX, int offsetOosZ, LodQuadBuilder quadBuilder,
			EDhApiDebugRendering debugging, ColumnRenderSource.DebugSourceFlag debugSource)
	{
		DhLodPos blockOffsetPos = new DhLodPos(detailLevel, offsetPosX, offsetOosZ).convertToDetailLevel(LodUtil.BLOCK_DETAIL_LEVEL);
		
		short width = (short) BitShiftUtil.powerOfTwo(detailLevel);
		short x = (short) blockOffsetPos.x;
		short yMin = RenderDataPointUtil.getYMin(data);
		short z = (short) (short) blockOffsetPos.z;
		short ySize = (short) (RenderDataPointUtil.getYMax(data) - yMin);
		
		if (ySize == 0)
		{
			return;
		}
		else if (ySize < 0)
		{
			throw new IllegalArgumentException("Negative y size for the data! Data: " + RenderDataPointUtil.toString(data));
		}
		
		byte blockMaterialId = RenderDataPointUtil.getBlockMaterialId(data);
		
		
		
		int color;
		boolean fullBright = false;
		switch (debugging)
		{
			case OFF:
			{
				float saturationMultiplier = Config.Client.Advanced.Graphics.AdvancedGraphics.saturationMultiplier.get().floatValue();
				float brightnessMultiplier = Config.Client.Advanced.Graphics.AdvancedGraphics.brightnessMultiplier.get().floatValue();
				if (saturationMultiplier == 1.0 && brightnessMultiplier == 1.0)
				{
					color = RenderDataPointUtil.getColor(data);
				}
				else
				{
					float[] ahsv = ColorUtil.argbToAhsv(RenderDataPointUtil.getColor(data));
					color = ColorUtil.ahsvToArgb(ahsv[0], ahsv[1], ahsv[2] * saturationMultiplier, ahsv[3] * brightnessMultiplier);
					//ApiShared.LOGGER.info("Raw color:[{}], AHSV:{}, Out color:[{}]",
					//		ColorUtil.toString(DataPointUtil.getColor(data)),
					//		ahsv, ColorUtil.toString(color));
				}
				break;
			}
			case SHOW_DETAIL:
			{
				color = LodUtil.DEBUG_DETAIL_LEVEL_COLORS[detailLevel];
				fullBright = true;
				break;
			}
			case SHOW_BLOCK_MATERIAL:
			{
				switch (blockMaterialId)
				{
					case IBlockStateWrapper.IrisBlockMaterial.UNKOWN:
					case IBlockStateWrapper.IrisBlockMaterial.AIR: // shouldn't normally be rendered, but just in case
						color = ColorUtil.HOT_PINK;
						break;
						
					case IBlockStateWrapper.IrisBlockMaterial.LEAVES:
						color = ColorUtil.DARK_GREEN;
						break;
					case IBlockStateWrapper.IrisBlockMaterial.STONE:
						color = ColorUtil.GRAY;
						break;
					case IBlockStateWrapper.IrisBlockMaterial.WOOD:
						color = ColorUtil.BROWN;
						break;
					case IBlockStateWrapper.IrisBlockMaterial.METAL:
						color = ColorUtil.DARK_GRAY;
						break;
					case IBlockStateWrapper.IrisBlockMaterial.DIRT:
						color = ColorUtil.LIGHT_BROWN;
						break;
					case IBlockStateWrapper.IrisBlockMaterial.LAVA:
						color = ColorUtil.ORANGE;
						break;
					case IBlockStateWrapper.IrisBlockMaterial.DEEPSLATE:
						color = ColorUtil.BLACK;
						break;
					case IBlockStateWrapper.IrisBlockMaterial.SNOW:
						color = ColorUtil.WHITE;
						break;
					case IBlockStateWrapper.IrisBlockMaterial.SAND:
						color = ColorUtil.TAN;
						break;
					case IBlockStateWrapper.IrisBlockMaterial.TERRACOTTA:
						color = ColorUtil.DARK_ORANGE;
						break;
					case IBlockStateWrapper.IrisBlockMaterial.NETHER_STONE:
						color = ColorUtil.DARK_RED;
						break;
					case IBlockStateWrapper.IrisBlockMaterial.WATER:
						color = ColorUtil.BLUE;
						break;
					case IBlockStateWrapper.IrisBlockMaterial.GRASS:
						color = ColorUtil.GREEN;
						break;
					case IBlockStateWrapper.IrisBlockMaterial.ILLUMINATED:
						color = ColorUtil.YELLOW;
						break;
					
					default:
						// undefined color
						color = ColorUtil.CYAN;
						break;
				}
				
				fullBright = true;
				break;
			}
			case SHOW_OVERLAPPING_QUADS:
			{
				color = ColorUtil.WHITE;
				fullBright = true;
				break;
			}
			case SHOW_RENDER_SOURCE_FLAG:
			{
				color = debugSource == null ? ColorUtil.RED : debugSource.color;
				fullBright = true;
				break;
			}
			default:
				throw new IllegalArgumentException("Unknown debug mode: " + debugging);
		}
		
		ColumnBox.addBoxQuadsToBuilder(
				quadBuilder, // buffer
				width, ySize, width, // setWidth
				x, yMin, z, // setOffset
				color, // setColor
				blockMaterialId, // irisBlockMaterialId
				RenderDataPointUtil.getLightSky(data), // setSkyLights
				fullBright ? 15 : RenderDataPointUtil.getLightBlock(data), // setBlockLights
				topData, bottomData, adjColumnViews); // setAdjData
	}
	
}
