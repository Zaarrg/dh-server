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

import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.util.objects.UncheckedInterruptedException;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Used to populate the buffers in a {@link ColumnRenderSource} object.
 *
 * @see ColumnRenderSource
 */
public class ColumnRenderBufferBuilder
{
	public static final ConfigBasedLogger EVENT_LOGGER = new ConfigBasedLogger(LogManager.getLogger(),
			() -> Config.Client.Advanced.Logging.logRendererBufferEvent.get());
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	public static final int MAX_NUMBER_OF_CONCURRENT_CALLS_PER_THREAD = 3;
	public static int maxNumberOfConcurrentCalls = MAX_NUMBER_OF_CONCURRENT_CALLS_PER_THREAD;
	
	
	
	
	//==============//
	// vbo building //
	//==============//
	
	public static CompletableFuture<ColumnRenderBuffer> buildAndUploadBuffersAsync(
			IDhClientLevel clientLevel,
			ColumnRenderSource renderSource, ColumnRenderSource[] adjData)
	{
		ThreadPoolExecutor bufferBuilderExecutor = ThreadPoolUtil.getBufferBuilderExecutor();
		ThreadPoolExecutor bufferUploaderExecutor = ThreadPoolUtil.getBufferUploaderExecutor();
		if ((bufferBuilderExecutor == null || bufferBuilderExecutor.isTerminated()) ||
			(bufferUploaderExecutor == null || bufferUploaderExecutor.isTerminated()))
		{
			// one or more of the thread pools has been shut down
			CompletableFuture<ColumnRenderBuffer> future = new CompletableFuture<>();
			future.cancel(true);
			return future;
		}
		
		try
		{
			return CompletableFuture.supplyAsync(() ->
				{
					try
					{
						boolean enableTransparency = Config.Client.Advanced.Graphics.Quality.transparency.get().transparencyEnabled;
						
						//EVENT_LOGGER.trace("RenderRegion start QuadBuild @ " + renderSource.sectionPos);
						boolean enableSkyLightCulling =
								Config.Client.Advanced.Graphics.AdvancedGraphics.enableCaveCulling.get()
								&& (
									// dimensions with a ceiling will be all caves so we don't want cave culling
									!clientLevel.getLevelWrapper().hasCeiling()
									// the end has a lot of overhangs with 0 lighting above the void, which look broken with
									// the current cave culling logic (this could probably be improved, but just skipping it works best for now)
									&& !clientLevel.getLevelWrapper().getDimensionType().isTheEnd()
									// FIXME temporary fix
									//  Cave culling is currently broken for any detail level above 0
									&& DhSectionPos.getDetailLevel(renderSource.pos) == DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL
								);
						
						int skyLightCullingBelow = Config.Client.Advanced.Graphics.AdvancedGraphics.caveCullingHeight.get();
						// FIXME: Clamp also to the max world height.
						skyLightCullingBelow = Math.max(skyLightCullingBelow, clientLevel.getMinY());
						
						
						long builderStartTime = System.currentTimeMillis();
						
						LodQuadBuilder builder = new LodQuadBuilder(enableSkyLightCulling, (short) (skyLightCullingBelow - clientLevel.getMinY()), enableTransparency, clientLevel.getClientLevelWrapper());
						makeLodRenderData(builder, renderSource, adjData);
						
						long builderEndTime = System.currentTimeMillis();
						long buildMs = builderEndTime - builderStartTime;
						LOGGER.debug("RenderRegion end QuadBuild @ " + renderSource.pos + " took: " + buildMs);
						
						return builder;
					}
					catch (UncheckedInterruptedException e)
					{
						throw e;
					}
					catch (Throwable e3)
					{
						LOGGER.error("\"LodNodeBufferBuilder\" was unable to build quads: ", e3);
						throw e3;
					}
				}, bufferBuilderExecutor)
				.thenApplyAsync((quadBuilder) ->
				{
					try
					{
						ColumnRenderBuffer buffer = new ColumnRenderBuffer(new DhBlockPos(DhSectionPos.getMinCornerBlockX(renderSource.pos), clientLevel.getMinY(), DhSectionPos.getMinCornerBlockZ(renderSource.pos)));
						try
						{
							buffer.uploadBuffer(quadBuilder, GLProxy.getInstance().getGpuUploadMethod());
							LodUtil.assertTrue(buffer.buffersUploaded);
							return buffer;
						}
						catch (Exception e)
						{
							buffer.close();
							throw e;
						}
					}
					catch (InterruptedException e)
					{
						throw UncheckedInterruptedException.convert(e);
					}
					catch (Throwable e3)
					{
						LOGGER.error("LodNodeBufferBuilder was unable to upload buffer: " + e3.getMessage(), e3);
						throw e3;
					}
				}, bufferUploaderExecutor);
		}
		catch (RejectedExecutionException ignore) 
		{
			// the thread pool was probably shut down because it's size is being changed, just wait a sec and it should be back
			
			CompletableFuture<ColumnRenderBuffer> future = new CompletableFuture<>();
			future.cancel(true);
			return future;
		}
	}
	private static void makeLodRenderData(LodQuadBuilder quadBuilder, ColumnRenderSource renderSource, ColumnRenderSource[] adjRegions)
	{
		// Variable initialization
		EDhApiDebugRendering debugMode = Config.Client.Advanced.Debugging.debugRendering.get();
		
		// can be used to limit which section positions are build and thus, rendered
		// useful when debugging a specific section
		boolean enableColumnBufferLimit = Config.Client.Advanced.Debugging.columnBuilderDebugEnable.get();
		if (enableColumnBufferLimit)
		{
			if (DhSectionPos.getDetailLevel(renderSource.pos) == Config.Client.Advanced.Debugging.columnBuilderDebugDetailLevel.get()
				&& DhSectionPos.getX(renderSource.pos) == Config.Client.Advanced.Debugging.columnBuilderDebugXPos.get()
				&& DhSectionPos.getZ(renderSource.pos) == Config.Client.Advanced.Debugging.columnBuilderDebugZPos.get())
			{
				int test = 0;
			}
			else
			{
				return;
			}
		}
		
		byte detailLevel = renderSource.getDataDetailLevel();
		for (int x = 0; x < ColumnRenderSource.SECTION_SIZE; x++)
		{
			for (int z = 0; z < ColumnRenderSource.SECTION_SIZE; z++)
			{
				// TODO make a config for this
				// can be uncommented to limit the buffer building to a specific
				// relative position in this section.
				// useful for debugging a single column's rendering
//				if (x != 0 || (z != 0 && z != 1))
//				{
//					continue;
//				}
				
				
				UncheckedInterruptedException.throwIfInterrupted();
				
				ColumnArrayView columnRenderData = renderSource.getVerticalDataPointView(x, z);
				if (columnRenderData.size() == 0
						|| !RenderDataPointUtil.doesDataPointExist(columnRenderData.get(0))
						|| RenderDataPointUtil.isVoid(columnRenderData.get(0)))
				{
					continue;
				}
				
				ColumnRenderSource.DebugSourceFlag debugSourceFlag = renderSource.debugGetFlag(x, z);
				
				ColumnArrayView[][] adjColumnViews = new ColumnArrayView[4][];
				// We extract the adj data in the four cardinal direction
				
				// we first reset the adjShadeDisabled. This is used to disable the shade on the
				// border when we have transparent block like water or glass
				// to avoid having a "darker border" underground
				// Arrays.fill(adjShadeDisabled, false);
				
				
				// We check every adj block in each direction
				
				// If the adj block is rendered in the same region and with same detail
				// and is positioned in a place that is not going to be rendered by vanilla game
				// then we can set this position as adj
				// We avoid cases where the adjPosition is in player chunk while the position is
				// not
				// to always have a wall underwater
				for (EDhDirection lodDirection : EDhDirection.ADJ_DIRECTIONS)
				{
					try
					{
						int xAdj = x + lodDirection.getNormal().x;
						int zAdj = z + lodDirection.getNormal().z;
						boolean isCrossRegionBoundary =
								(xAdj < 0 || xAdj >= ColumnRenderSource.SECTION_SIZE) ||
										(zAdj < 0 || zAdj >= ColumnRenderSource.SECTION_SIZE);
						
						ColumnRenderSource adjRenderSource;
						byte adjDetailLevel;
						
						//we check if the detail of the adjPos is equal to the correct one (region border fix)
						//or if the detail is wrong by 1 value (region+circle border fix)
						if (isCrossRegionBoundary)
						{
							//we compute at which detail that position should be rendered
							adjRenderSource = adjRegions[lodDirection.ordinal() - 2];
							if (adjRenderSource == null)
							{
								continue;
							}
							
							adjDetailLevel = adjRenderSource.getDataDetailLevel();
							if (adjDetailLevel != detailLevel)
							{
								//TODO: Implement this
							}
							else
							{
								if (xAdj < 0)
									xAdj += ColumnRenderSource.SECTION_SIZE;
								
								if (zAdj < 0)
									zAdj += ColumnRenderSource.SECTION_SIZE;
								
								if (xAdj >= ColumnRenderSource.SECTION_SIZE)
									xAdj -= ColumnRenderSource.SECTION_SIZE;
								
								if (zAdj >= ColumnRenderSource.SECTION_SIZE)
									zAdj -= ColumnRenderSource.SECTION_SIZE;
							}
						}
						else
						{
							adjRenderSource = renderSource;
							adjDetailLevel = detailLevel;
						}
						
						if (adjDetailLevel < detailLevel - 1 || adjDetailLevel > detailLevel + 1)
						{
							continue;
						}
						
						if (adjDetailLevel == detailLevel || adjDetailLevel > detailLevel)
						{
							adjColumnViews[lodDirection.ordinal() - 2] = new ColumnArrayView[1];
							adjColumnViews[lodDirection.ordinal() - 2][0] = adjRenderSource.getVerticalDataPointView(xAdj, zAdj);
						}
						else
						{
							adjColumnViews[lodDirection.ordinal() - 2] = new ColumnArrayView[2];
							adjColumnViews[lodDirection.ordinal() - 2][0] = adjRenderSource.getVerticalDataPointView(xAdj, zAdj);
							adjColumnViews[lodDirection.ordinal() - 2][1] = adjRenderSource.getVerticalDataPointView(
									xAdj + (lodDirection.getAxis() == EDhDirection.Axis.X ? 0 : 1),
									zAdj + (lodDirection.getAxis() == EDhDirection.Axis.Z ? 0 : 1));
						}
					}
					catch (RuntimeException e)
					{
						EVENT_LOGGER.warn("Failed to get adj data for [" + detailLevel + ":" + x + "," + z + "] at [" + lodDirection + "], Error: "+e.getMessage(), e);
					}
				} // for adjacent directions
				
				
				// We render every vertical lod present in this position
				// We only stop when we find a block that is void or non-existing block
				for (int i = 0; i < columnRenderData.size(); i++)
				{
					// TODO make a config for this
					// can be uncommented to limit which vertical LOD is generated
//					if (i != 0)
//					{
//						continue;
//					}
					
					long data = columnRenderData.get(i);
					// If the data is not render-able (Void or non-existing) we stop since there is
					// no data left in this position
					if (RenderDataPointUtil.isVoid(data) || !RenderDataPointUtil.doesDataPointExist(data))
					{
						break;
					}
					
					long topDataPoint = (i - 1) >= 0 ? columnRenderData.get(i - 1) : RenderDataPointUtil.EMPTY_DATA;
					long bottomDataPoint = (i + 1) < columnRenderData.size() ? columnRenderData.get(i + 1) : RenderDataPointUtil.EMPTY_DATA;
					
					CubicLodTemplate.addLodToBuffer(data, topDataPoint, bottomDataPoint, adjColumnViews, detailLevel,
							x, z, quadBuilder, debugMode, debugSourceFlag);
				}
				
			}// for z
		}// for x
		
		quadBuilder.finalizeData();
	}
	
	
	
	//=================//
	// vbo interaction //
	//=================//
	
	public static GLVertexBuffer[] resizeBuffer(GLVertexBuffer[] vbos, int newSize)
	{
		if (vbos.length == newSize)
		{
			return vbos;
		}
		
		GLVertexBuffer[] newVbos = new GLVertexBuffer[newSize];
		System.arraycopy(vbos, 0, newVbos, 0, Math.min(vbos.length, newSize));
		if (newSize < vbos.length)
		{
			for (int i = newSize; i < vbos.length; i++)
			{
				if (vbos[i] != null)
				{
					vbos[i].close();
				}
			}
		}
		return newVbos;
	}
	
}
