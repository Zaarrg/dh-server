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

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.coreapi.util.MathUtil;

public class ColumnBox
{
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	public static void addBoxQuadsToBuilder(
			LodQuadBuilder builder,
			short xSize, short ySize, short zSize,
			short x, short minY, short z,
			int color, byte irisBlockMaterialId, byte skyLight, byte blockLight,
			long topData, long bottomData, ColumnArrayView[][] adjData)
	{
		short maxX = (short) (x + xSize);
		short maxY = (short) (minY + ySize);
		short maxZ = (short) (z + zSize);
		byte skyLightTop = skyLight;
		byte skyLightBot = RenderDataPointUtil.doesDataPointExist(bottomData) ? RenderDataPointUtil.getLightSky(bottomData) : 0;
		
		boolean isTransparent = ColorUtil.getAlpha(color) < 255 && LodRenderer.transparencyEnabled;
		boolean overVoid = !RenderDataPointUtil.doesDataPointExist(bottomData);
		boolean isTopTransparent = RenderDataPointUtil.getAlpha(topData) < 255 && LodRenderer.transparencyEnabled;
		boolean isBottomTransparent = RenderDataPointUtil.getAlpha(bottomData) < 255 && LodRenderer.transparencyEnabled;
		
		// if there isn't any data below this LOD, make this LOD's color opaque to prevent seeing void through transparent blocks
		// Note: this LOD should still be considered transparent for this method's checks, otherwise rendering bugs may occur
		// FIXME this transparency change should be applied before this point since this could affect other areas
		//  This may also be better than handling the LOD as transparent, but that is TBD
		if (!RenderDataPointUtil.doesDataPointExist(bottomData))
		{
			color = ColorUtil.setAlpha(color, 255);
		}
		
		
		// cave culling prevention
		// prevents certain faces from being culled underground that should be allowed
		if (builder.skipQuadsWithZeroSkylight
			&& 0 == skyLight
			&& builder.skyLightCullingBelow > maxY
			&& (
					(RenderDataPointUtil.getAlpha(topData) < 255 && RenderDataPointUtil.getYMax(topData) >= builder.skyLightCullingBelow)
					|| (RenderDataPointUtil.getYMin(topData) >= builder.skyLightCullingBelow)
					|| !RenderDataPointUtil.doesDataPointExist(topData)
				)
			)
		{
			maxY = builder.skyLightCullingBelow;
		}
		
		
		
		// fake ocean transparency
		if (LodRenderer.transparencyEnabled && LodRenderer.fakeOceanFloor)
		{
			if (!isTransparent && isTopTransparent && RenderDataPointUtil.doesDataPointExist(topData))
			{
				skyLightTop = (byte) MathUtil.clamp(0, 15 - (RenderDataPointUtil.getYMax(topData) - minY), 15);
				ySize = (short) (RenderDataPointUtil.getYMax(topData) - minY - 1);
			}
			else if (isTransparent && !isBottomTransparent && RenderDataPointUtil.doesDataPointExist(bottomData))
			{
				minY = (short) (minY + ySize - 1);
				ySize = 1;
			}
			
			maxY = (short) (minY + ySize);
		}
		
		
		
		// add top and bottom faces if requested //
		
		boolean skipTop = RenderDataPointUtil.doesDataPointExist(topData) && (RenderDataPointUtil.getYMin(topData) == maxY) && !isTopTransparent;
		if (!skipTop)
		{
			builder.addQuadUp(x, maxY, z, xSize, zSize, ColorUtil.applyShade(color, MC.getShade(EDhDirection.UP)), irisBlockMaterialId, skyLightTop, blockLight);
		}
		
		boolean skipBottom = RenderDataPointUtil.doesDataPointExist(bottomData) && (RenderDataPointUtil.getYMax(bottomData) == minY) && !isBottomTransparent;
		if (!skipBottom)
		{
			builder.addQuadDown(x, minY, z, xSize, zSize, ColorUtil.applyShade(color, MC.getShade(EDhDirection.DOWN)), irisBlockMaterialId, skyLightBot, blockLight);
		}
		
		
		// add North, south, east, and west faces if requested //
		
		// TODO merge duplicate code
		//NORTH face vertex creation
		{
			ColumnArrayView[] adjDataNorth = adjData[EDhDirection.NORTH.ordinal() - 2]; // TODO can we use something other than ordinal-2?
			int adjOverlapNorth = ColorUtil.INVISIBLE;
			if (adjDataNorth == null)
			{
				// add an adjacent face if this is opaque face or transparent over the void
				if (!isTransparent || overVoid)
				{
					builder.addQuadAdj(EDhDirection.NORTH, x, minY, z, xSize, ySize, color, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight);
				}
			}
			else if (adjDataNorth.length == 1)
			{
				makeAdjVerticalQuad(builder, adjDataNorth[0], EDhDirection.NORTH, x, minY, z, xSize, ySize,
						color, adjOverlapNorth, irisBlockMaterialId, skyLightTop, blockLight,
						topData, bottomData);
			}
			else
			{
				makeAdjVerticalQuad(builder, adjDataNorth[0], EDhDirection.NORTH, x, minY, z, (short) (xSize / 2), ySize,
						color, adjOverlapNorth, irisBlockMaterialId, skyLightTop, blockLight,
						topData, bottomData);
				makeAdjVerticalQuad(builder, adjDataNorth[1], EDhDirection.NORTH, (short) (x + xSize / 2), minY, z, (short) (xSize / 2), ySize,
						color, adjOverlapNorth, irisBlockMaterialId, skyLightTop, blockLight,
						topData, bottomData);
			}
		}
		
		//SOUTH face vertex creation
		{
			ColumnArrayView[] adjDataSouth = adjData[EDhDirection.SOUTH.ordinal() - 2];
			int adjOverlapSouth = ColorUtil.INVISIBLE;
			if (adjDataSouth == null)
			{
				if (!isTransparent || overVoid)
					builder.addQuadAdj(EDhDirection.SOUTH, x, minY, maxZ, xSize, ySize, color, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight);
			}
			else if (adjDataSouth.length == 1)
			{
				makeAdjVerticalQuad(builder, adjDataSouth[0], EDhDirection.SOUTH, x, minY, maxZ, xSize, ySize,
						color, adjOverlapSouth, irisBlockMaterialId, skyLightTop, blockLight,
						topData, bottomData);
			}
			else
			{
				makeAdjVerticalQuad(builder, adjDataSouth[0], EDhDirection.SOUTH, x, minY, maxZ, (short) (xSize / 2), ySize,
						color, adjOverlapSouth, irisBlockMaterialId, skyLightTop, blockLight,
						topData, bottomData);
				
				makeAdjVerticalQuad(builder, adjDataSouth[1], EDhDirection.SOUTH, (short) (x + xSize / 2), minY, maxZ, (short) (xSize / 2), ySize,
						color, adjOverlapSouth, irisBlockMaterialId, skyLightTop, blockLight,
						topData, bottomData);
			}
		}
		
		//WEST face vertex creation
		{
			ColumnArrayView[] adjDataWest = adjData[EDhDirection.WEST.ordinal() - 2];
			int adjOverlapWest = ColorUtil.INVISIBLE;
			if (adjDataWest == null)
			{
				if (!isTransparent || overVoid)
					builder.addQuadAdj(EDhDirection.WEST, x, minY, z, zSize, ySize, color, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight);
			}
			else if (adjDataWest.length == 1)
			{
				makeAdjVerticalQuad(builder, adjDataWest[0], EDhDirection.WEST, x, minY, z, zSize, ySize,
						color, adjOverlapWest, irisBlockMaterialId, skyLightTop, blockLight,
						topData, bottomData);
			}
			else
			{
				makeAdjVerticalQuad(builder, adjDataWest[0], EDhDirection.WEST, x, minY, z, (short) (zSize / 2), ySize,
						color, adjOverlapWest, irisBlockMaterialId, skyLightTop, blockLight,
						topData, bottomData);
				makeAdjVerticalQuad(builder, adjDataWest[1], EDhDirection.WEST, x, minY, (short) (z + zSize / 2), (short) (zSize / 2), ySize,
						color, adjOverlapWest, irisBlockMaterialId, skyLightTop, blockLight,
						topData, bottomData);
			}
		}
		
		//EAST face vertex creation
		{
			ColumnArrayView[] adjDataEast = adjData[EDhDirection.EAST.ordinal() - 2];
			int adjOverlapEast = ColorUtil.INVISIBLE;
			if (adjData[EDhDirection.EAST.ordinal() - 2] == null)
			{
				if (!isTransparent || overVoid)
					builder.addQuadAdj(EDhDirection.EAST, maxX, minY, z, zSize, ySize, color, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight);
			}
			else if (adjDataEast.length == 1)
			{
				makeAdjVerticalQuad(builder, adjDataEast[0], EDhDirection.EAST, maxX, minY, z, zSize, ySize,
						color, adjOverlapEast, irisBlockMaterialId, skyLightTop, blockLight,
						topData, bottomData);
			}
			else
			{
				makeAdjVerticalQuad(builder, adjDataEast[0], EDhDirection.EAST, maxX, minY, z, (short) (zSize / 2), ySize,
						color, adjOverlapEast, irisBlockMaterialId, skyLightTop, blockLight,
						topData, bottomData);
				makeAdjVerticalQuad(builder, adjDataEast[1], EDhDirection.EAST, maxX, minY, (short) (z + zSize / 2), (short) (zSize / 2), ySize,
						color, adjOverlapEast, irisBlockMaterialId, skyLightTop, blockLight,
						topData, bottomData);
			}
		}
	}
	
	// the overlap color can be used to see faces that shouldn't be rendered
	private static void makeAdjVerticalQuad(
			LodQuadBuilder builder, ColumnArrayView adjColumnView, EDhDirection direction,
			short x, short yMin, short z, short horizontalWidth, short ySize,
			int color, int debugOverlapColor, byte irisBlockMaterialId, byte skyLightTop, byte blockLight,
			long topData, long bottomData)
	{
		color = ColorUtil.applyShade(color, MC.getShade(direction));
		
		if (adjColumnView == null || adjColumnView.size == 0 || RenderDataPointUtil.isVoid(adjColumnView.get(0)))
		{
			// there isn't any data adjacent to this LOD, add the vertical quad
			builder.addQuadAdj(direction, x, yMin, z, horizontalWidth, ySize, color, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight);
			return;
		}
		
		
		int yMax = yMin + ySize;
		
		int adjIndex;
		boolean firstFace = true;
		boolean inputAboveAdjLods = true;
		short previousAdjDepth = -1;
		byte nextTopSkyLight = skyLightTop;
		boolean inputTransparent = ColorUtil.getAlpha(color) < 255 && LodRenderer.transparencyEnabled;
		boolean lastAdjWasTransparent = false;
		
		
		
		if (!RenderDataPointUtil.doesDataPointExist(bottomData))
		{
			// there isn't anything under this LOD,
			// to prevent seeing through the world, make it opaque
			color = ColorUtil.setAlpha(color, 255);
		}
		
		
		// Add adjacent faces if this LOD is surrounded by transparent LODs
		// (prevents invisible sides underwater)
		int adjCount = adjColumnView.size();
		for (adjIndex = 0; // iterates top down
				adjIndex < adjCount
						&& RenderDataPointUtil.doesDataPointExist(adjColumnView.get(adjIndex))
						&& !RenderDataPointUtil.isVoid(adjColumnView.get(adjIndex));
				adjIndex++)
		{
			long adjPoint = adjColumnView.get(adjIndex);
			
			// if the adjacent data point is over the void
			// don't consider it as transparent
			// FIXME this transparency change should be applied before this point since this could affect other areas
			boolean adjOverVoid = false;
			if (adjIndex > 0)
			{
				long adjBellowPoint = adjColumnView.get(adjIndex-1);
				adjOverVoid = !RenderDataPointUtil.doesDataPointExist(adjBellowPoint);	
			}
			boolean adjTransparent = !adjOverVoid && RenderDataPointUtil.getAlpha(adjPoint) < 255 && LodRenderer.transparencyEnabled;
			
			
			// continue if this data point is transparent or the adjacent point is not 
			if (inputTransparent || !adjTransparent) // TODO inputIsTransparent may be unnecessary
			{
				short adjYMin = RenderDataPointUtil.getYMin(adjPoint);
				short adjYMax = RenderDataPointUtil.getYMax(adjPoint);
				
				
				// if fake transparency is enabled, allow for 1 block of transparency,
				// everything under that should be opaque
				if (LodRenderer.transparencyEnabled && LodRenderer.fakeOceanFloor)
				{
					if (lastAdjWasTransparent && !adjTransparent)
					{
						adjYMax = (short) (RenderDataPointUtil.getYMax(adjColumnView.get(adjIndex - 1)) - 1);
					}
					else if (adjTransparent && (adjIndex + 1) < adjCount)
					{
						if (RenderDataPointUtil.getAlpha(adjColumnView.get(adjIndex + 1)) == 255)
						{
							adjYMin = (short) (adjYMax - 1);
						}
					}
				}
				
				
				if (yMax <= adjYMin)
				{
					// the adjacent LOD is above the input LOD and won't affect its rendering,
					// skip to the next adjacent
					continue;
				}
				inputAboveAdjLods = false;
				
				
				if (adjYMax < yMin)
				{
					// the adjacent LOD is below the input LOD
					
					// getting the skylight is more complicated
					// since LODs can be adjacent to water, which changes how skylight works
					byte skyLight;
					if (adjIndex == 0)
					{
						// this adj LOD is at the highest position,
						// its sky lighting won't be affected by anything above it
						skyLight = RenderDataPointUtil.getLightSky(adjPoint);
					}
					else
					{
						// TODO improve the comments here, this is a bit confusing
						long aboveAdjPoint = adjColumnView.get(adjIndex - 1);
						if (RenderDataPointUtil.getAlpha(aboveAdjPoint) != 255)
						{
							// above adjacent LOD is transparent...
							
							boolean inputMaxHigherThanAboveAdj = yMax > RenderDataPointUtil.getYMax(aboveAdjPoint);
							if (inputMaxHigherThanAboveAdj)
							{
								// ...and higher than the input yMax,
								// use its sky light
								skyLight = RenderDataPointUtil.getLightSky(aboveAdjPoint);
							}
							else
							{
								// ...and at or below the input yMax,
								skyLight = RenderDataPointUtil.getLightSky(adjPoint);
							}
						}
						else
						{
							// LOD above adjacent is opaque, use the adj LOD's skylight
							skyLight = RenderDataPointUtil.getLightSky(adjPoint);
						}
					}
					
					
					if (firstFace)
					{
						builder.addQuadAdj(direction, x, yMin, z, horizontalWidth, ySize, color, irisBlockMaterialId, skyLight, blockLight);
					}
					else
					{
						// Now: adjMaxHeight < y < previousAdjDepth < yMax
						if (previousAdjDepth == -1)
						{
							// TODO why is this an error?
							throw new RuntimeException("Loop error");
						}
						
						builder.addQuadAdj(direction, x, yMin, z, horizontalWidth, (short) (previousAdjDepth - yMin), color, irisBlockMaterialId, skyLight, blockLight);
						
						previousAdjDepth = -1;
					}
					
					
					// TODO why break here?
					break;
				}
				
				
				if (adjYMin <= yMin)
				{
					// the adjacent LOD's base is at or below the input's base
					
					if (yMax <= adjYMax)
					{
						// The input face is completely inside the adj's face, don't render it
						if (debugOverlapColor != 0)
						{
							builder.addQuadAdj(direction, x, yMin, z, horizontalWidth, ySize, debugOverlapColor, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, LodUtil.MAX_MC_LIGHT);
						}
					}
					else
					{
						// the adj data intersects the lower part of the input data, don't render below the intersection
						
						if (adjYMax > yMin && debugOverlapColor != 0)
						{
							builder.addQuadAdj(direction, x, yMin, z, horizontalWidth, (short) (adjYMax - yMin), debugOverlapColor, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, LodUtil.MAX_MC_LIGHT);
						}
						
						// if this is the only face, use the yMax and break,
						// if there was another face finish the last one and then break
						if (firstFace)
						{
							builder.addQuadAdj(direction, x, adjYMax, z, horizontalWidth, (short) (yMax - adjYMax), color, irisBlockMaterialId,
									RenderDataPointUtil.getLightSky(adjPoint), blockLight);
						}
						else
						{
							// Now: depth <= y <= height <= previousAdjDepth < yMax
							if (previousAdjDepth == -1)
							{
								// TODO why is this an error?
								throw new RuntimeException("Loop error");
							}
							
							if (previousAdjDepth > adjYMax)
							{
								builder.addQuadAdj(direction, x, adjYMax, z, horizontalWidth, (short) (previousAdjDepth - adjYMax), color, irisBlockMaterialId,
										RenderDataPointUtil.getLightSky(adjPoint), blockLight);
							}
							previousAdjDepth = -1;
						}
					}
					
					
					// we don't need to check any other adjacent LODs 
					// since this one completely covers the input
					break;
				}
				
				
				
				// In here always true: y < adjYMin < yMax
				// _________________&&: y < ________ (height and yMax)
				
				if (adjYMax >= yMax)
				{
					// Basically: y _______ < yMax <= height
					// _______&&: y < depth < yMax
					// the adj data intersects the higher part of the current data
					if (debugOverlapColor != 0)
					{
						builder.addQuadAdj(direction, x, adjYMin, z, horizontalWidth, (short) (yMax - adjYMin), debugOverlapColor, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, LodUtil.MAX_MC_LIGHT);
					}
					
					// we start the creation of a new face
				}
				else
				{
					// Otherwise: y < _____ height < yMax
					// _______&&: y < depth ______ < yMax
					if (debugOverlapColor != 0)
					{
						builder.addQuadAdj(direction, x, adjYMin, z, horizontalWidth, (short) (adjYMax - adjYMin), debugOverlapColor, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, LodUtil.MAX_MC_LIGHT);
					}
					
					if (firstFace)
					{
						builder.addQuadAdj(direction, x, adjYMax, z, horizontalWidth, (short) (yMax - adjYMax), color, irisBlockMaterialId,
								RenderDataPointUtil.getLightSky(adjPoint), blockLight);
					}
					else
					{
						// Now: y < depth < height <= previousAdjDepth < yMax
						if (previousAdjDepth == -1)
							throw new RuntimeException("Loop error");
						if (previousAdjDepth > adjYMax)
						{
							if (irisBlockMaterialId == IBlockStateWrapper.IrisBlockMaterial.GRASS)
							{
								// this LOD is underneath another, grass will never show here
								irisBlockMaterialId = IBlockStateWrapper.IrisBlockMaterial.DIRT;
							}
							
							builder.addQuadAdj(direction, x, adjYMax, z, horizontalWidth, (short) (previousAdjDepth - adjYMax), color, irisBlockMaterialId,
									RenderDataPointUtil.getLightSky(adjPoint), blockLight);
						}
						previousAdjDepth = -1;
					}
				}
				
				
				// set next top as current depth
				previousAdjDepth = adjYMin;
				firstFace = false;
				nextTopSkyLight = skyLightTop;
				
				if (adjIndex + 1 < adjColumnView.size() && RenderDataPointUtil.doesDataPointExist(adjColumnView.get(adjIndex + 1)))
				{
					nextTopSkyLight = RenderDataPointUtil.getLightSky(adjColumnView.get(adjIndex + 1));
				}
				
				lastAdjWasTransparent = adjTransparent;
			}
		}
		
		
		
		if (inputAboveAdjLods)
		{
			// the input LOD is above all adjacent LODs and won't be affected
			// by them, add the vertical quad using the input's lighting and height
			builder.addQuadAdj(direction, x, yMin, z, horizontalWidth, ySize, color, irisBlockMaterialId, skyLightTop, blockLight);
		}
		else if (previousAdjDepth != -1)
		{
			// We need to finish the last quad.
			builder.addQuadAdj(direction, x, yMin, z, horizontalWidth, (short) (previousAdjDepth - yMin), color, irisBlockMaterialId, nextTopSkyLight, blockLight);
		}
	}
	
}
