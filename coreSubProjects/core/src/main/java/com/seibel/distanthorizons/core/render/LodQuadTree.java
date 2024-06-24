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

package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.api.enums.config.EDhApiHorizontalQuality;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.file.fullDatafile.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.render.renderer.IDebugRenderable;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import com.seibel.distanthorizons.core.util.objects.quadTree.QuadNode;
import com.seibel.distanthorizons.core.util.objects.quadTree.QuadTree;
import com.seibel.distanthorizons.coreapi.util.MathUtil;
import it.unimi.dsi.fastutil.longs.LongIterator;
import org.apache.logging.log4j.Logger;

import javax.annotation.WillNotClose;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This quadTree structure is our core data structure and holds
 * all rendering data.
 */
public class LodQuadTree extends QuadTree<LodRenderSection> implements IDebugRenderable, AutoCloseable
{
	public static final byte TREE_LOWEST_DETAIL_LEVEL = ColumnRenderSource.SECTION_SIZE_OFFSET;
	
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	/** there should only ever be one {@link LodQuadTree} so having the thread static should be fine */
	private static final ThreadPoolExecutor FULL_DATA_RETRIEVAL_QUEUE_THREAD = ThreadUtil.makeSingleThreadPool("QuadTree Full Data Retrieval Queue Populator");
	private static final int WORLD_GEN_QUEUE_UPDATE_DELAY_IN_MS = 1_000;
	
	
	public final int blockRenderDistanceDiameter;
	@WillNotClose
	private final FullDataSourceProviderV2 fullDataSourceProvider;
	
	/**
	 * This holds every {@link DhSectionPos} that should be reloaded next tick. <br>
	 * This is a {@link ConcurrentLinkedQueue} because new sections can be added to this list via the world generator threads.
	 */
	private final ConcurrentLinkedQueue<Long> sectionsToReload = new ConcurrentLinkedQueue<>();
	private final IDhClientLevel level; //FIXME: Proper hierarchy to remove this reference!
	private final ConfigChangeListener<EDhApiHorizontalQuality> horizontalScaleChangeListener;
	private final ReentrantLock treeReadWriteLock = new ReentrantLock();
	private final AtomicBoolean fullDataRetrievalQueueRunning = new AtomicBoolean(false);
	
	private ArrayList<LodRenderSection> debugRenderSections = new ArrayList<>();
	private ArrayList<LodRenderSection> altDebugRenderSections = new ArrayList<>();
	private final ReentrantLock debugRenderSectionLock = new ReentrantLock();
	
	/** the smallest numerical detail level number that can be rendered */
	private byte maxRenderDetailLevel;
	/** the largest numerical detail level number that can be rendered */
	private byte minRenderDetailLevel;
	
	/** used to calculate when a detail drop will occur */
	private double detailDropOffDistanceUnit;
	/** used to calculate when a detail drop will occur */
	private double detailDropOffLogBase;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public LodQuadTree(
			IDhClientLevel level, int viewDiameterInBlocks,
			int initialPlayerBlockX, int initialPlayerBlockZ,
			FullDataSourceProviderV2 fullDataSourceProvider)
	{
		super(viewDiameterInBlocks, new DhBlockPos2D(initialPlayerBlockX, initialPlayerBlockZ), TREE_LOWEST_DETAIL_LEVEL);
		
		DebugRenderer.register(this, Config.Client.Advanced.Debugging.DebugWireframe.showQuadTreeRenderStatus);
		
		this.level = level;
		this.fullDataSourceProvider = fullDataSourceProvider;
		this.blockRenderDistanceDiameter = viewDiameterInBlocks;
		
		this.horizontalScaleChangeListener = new ConfigChangeListener<>(Config.Client.Advanced.Graphics.Quality.horizontalQuality, (newHorizontalScale) -> this.onHorizontalQualityChange());
	}
	
	
	
	//=============//
	// tick update //
	//=============//
	
	/**
	 * This function updates the quadTree based on the playerPos and the current game configs (static and global)
	 *
	 * @param playerPos the reference position for the player
	 */
	public void tick(DhBlockPos2D playerPos)
	{
		if (this.level == null)
		{
			// the level hasn't finished loading yet
			// TODO sometimes null pointers still happen, when logging back into a world (maybe the old level isn't null but isn't valid either?)
			return;
		}
		
		
		
		// this shouldn't be updated while the tree is being iterated through
		this.updateDetailLevelVariables();
		
		// don't traverse the tree if it is being modified
		if (this.treeReadWriteLock.tryLock())
		{
			try
			{
				// recenter if necessary, removing out of bounds sections
				this.setCenterBlockPos(playerPos, LodRenderSection::close);
				
				this.updateAllRenderSections(playerPos);
			}
			catch (Exception e)
			{
				LOGGER.error("Quad Tree tick exception for dimension: " + this.level.getClientLevelWrapper().getDimensionType().getDimensionName() + ", exception: " + e.getMessage(), e);
			}
			finally
			{
				this.treeReadWriteLock.unlock();
			}
		}
	}
	private void updateAllRenderSections(DhBlockPos2D playerPos)
	{
		if (Config.Client.Advanced.Debugging.DebugWireframe.showQuadTreeRenderStatus.get())
		{
			try
			{
				// lock to prevent accidentally rendering an array that's being populated/cleared
				this.debugRenderSectionLock.lock();
				
				// swap the debug arrays
				this.debugRenderSections.clear();
				ArrayList<LodRenderSection> temp = this.debugRenderSections;
				this.debugRenderSections = this.altDebugRenderSections;
				this.altDebugRenderSections = temp;
			}
			finally
			{
				this.debugRenderSectionLock.unlock();
			}
		}
		
		
		// reload any sections that need it
		Long pos;
		while ((pos = this.sectionsToReload.poll()) != null)
		{
			// walk up the tree until we hit the root node
			// this is done so any high detail changes flow up to the lower detail render sections as well
			while (DhSectionPos.getDetailLevel(pos) <= this.treeMinDetailLevel)
			{
				try
				{
					LodRenderSection renderSection = this.getValue(pos);
					if (renderSection != null && renderSection.renderingEnabled)
					{
						renderSection.uploadRenderDataToGpuAsync();
					}
				}
				catch (IndexOutOfBoundsException e)
				{ /* the section is now out of bounds, it doesn't need to be reloaded */ }
				
				pos = DhSectionPos.getParentPos(pos);
			}
		}
		
		
		// walk through each root node
		ArrayList<LodRenderSection> nodesNeedingRetrieval = new ArrayList<>();
		ArrayList<LodRenderSection> nodesNeedingLoading = new ArrayList<>();
		LongIterator rootPosIterator = this.rootNodePosIterator();
		while (rootPosIterator.hasNext())
		{
			// make sure all root nodes have been created
			long rootPos = rootPosIterator.nextLong();
			if (this.getNode(rootPos) == null)
			{
				this.setValue(rootPos, new LodRenderSection(rootPos, this, this.level, this.fullDataSourceProvider));
			}
			
			QuadNode<LodRenderSection> rootNode = this.getNode(rootPos);
			this.recursivelyUpdateRenderSectionNode(playerPos, rootNode, rootNode, rootNode.sectionPos, false, nodesNeedingRetrieval, nodesNeedingLoading);
		}
		
		
		// full data retrieval (world gen)
		if (!this.fullDataRetrievalQueueRunning.get())
		{
			this.fullDataRetrievalQueueRunning.set(true);
			FULL_DATA_RETRIEVAL_QUEUE_THREAD.execute(() -> this.queueFullDataRetrievalTasks(playerPos, nodesNeedingRetrieval));
		}
		
		
		nodesNeedingLoading.sort((a, b) ->
		{
			int aDist = DhSectionPos.getManhattanBlockDistance(a.pos, playerPos);
			int bDist = DhSectionPos.getManhattanBlockDistance(b.pos, playerPos);
			return Integer.compare(aDist, bDist);
		});
		
		for (int i = 0; i < nodesNeedingLoading.size(); i++)
		{
			LodRenderSection renderSection = nodesNeedingLoading.get(i);
			if (!renderSection.gpuUploadInProgress() && renderSection.renderBuffer == null)
			{
				renderSection.uploadRenderDataToGpuAsync();
			}
		}
		
	}
	/** @return whether the current position is able to render (note: not if it IS rendering, just if it is ABLE to.) */
	private boolean recursivelyUpdateRenderSectionNode(
			DhBlockPos2D playerPos, 
			QuadNode<LodRenderSection> rootNode, QuadNode<LodRenderSection> quadNode, long sectionPos, 
			boolean parentSectionIsRendering,
			ArrayList<LodRenderSection> nodesNeedingRetrieval,
			ArrayList<LodRenderSection> nodesNeedingLoading)
	{
		//===============================//
		// node and render section setup //
		//===============================//
		
		// make sure the node is created
		if (quadNode == null && this.isSectionPosInBounds(sectionPos)) // the position bounds should only fail when at the edge of the user's render distance
		{
			rootNode.setValue(sectionPos, new LodRenderSection(sectionPos, this, this.level, this.fullDataSourceProvider));
			quadNode = rootNode.getNode(sectionPos);
		}
		if (quadNode == null)
		{
			// this node must be out of bounds, or there was an issue adding it to the tree
			return false;
		}
		
		// make sure the render section is created
		LodRenderSection renderSection = quadNode.value;
		// create a new render section if missing
		if (renderSection == null)
		{
			LodRenderSection newRenderSection = new LodRenderSection(sectionPos, this, this.level, this.fullDataSourceProvider);
			rootNode.setValue(sectionPos, newRenderSection);
			
			renderSection = newRenderSection; // TODO this never seemed to be called, is it necessary?
		}
		
		
		
		//===============================//
		// handle enabling, loading,     //
		// and disabling render sections //
		//===============================//

		//byte expectedDetailLevel = DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL + 3; // can be used instead of the following logic for testing
		byte expectedDetailLevel = this.calculateExpectedDetailLevel(playerPos, sectionPos);
		expectedDetailLevel = (byte) Math.min(expectedDetailLevel, this.minRenderDetailLevel);
		expectedDetailLevel += DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL;
		
		
		if (DhSectionPos.getDetailLevel(sectionPos) > expectedDetailLevel)
		{
			// section detail level too high //
			boolean thisPosIsRendering = renderSection.renderingEnabled;
			boolean allChildrenSectionsAreLoaded = true;
			
			// recursively update all child render sections
			LongIterator childPosIterator = quadNode.getChildPosIterator();
			while (childPosIterator.hasNext())
			{
				long childPos = childPosIterator.nextLong();
				QuadNode<LodRenderSection> childNode = rootNode.getNode(childPos);
				
				boolean childSectionLoaded = this.recursivelyUpdateRenderSectionNode(playerPos, rootNode, childNode, childPos, thisPosIsRendering || parentSectionIsRendering, nodesNeedingRetrieval, nodesNeedingLoading);
				allChildrenSectionsAreLoaded = childSectionLoaded && allChildrenSectionsAreLoaded;
			}
			
			if (!allChildrenSectionsAreLoaded)
			{
				// not all child positions are loaded yet, or this section is out of render range
				return thisPosIsRendering;
			}
			else
			{
				if (renderSection.renderingEnabled
					&& Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionStatus.get())
				{
					// show that this position has just been disabled
					DebugRenderer.makeParticle(
						new DebugRenderer.BoxParticle(
							new DebugRenderer.Box(renderSection.pos, 128f, 156f, 0.09f, Color.CYAN.darker()),
							0.2, 32f
						)
					);
				}
				
				// all child positions are loaded, disable this section and enable its children.
				renderSection.renderingEnabled = false;
				
				// walk back down the tree and enable the child sections //TODO there are probably more efficient ways of doing this, but this will work for now
				childPosIterator = quadNode.getChildPosIterator();
				while (childPosIterator.hasNext())
				{
					long childPos = childPosIterator.nextLong();
					QuadNode<LodRenderSection> childNode = rootNode.getNode(childPos);
					
					boolean childSectionLoaded = this.recursivelyUpdateRenderSectionNode(playerPos, rootNode, childNode, childPos, parentSectionIsRendering, nodesNeedingRetrieval, nodesNeedingLoading);
					allChildrenSectionsAreLoaded = childSectionLoaded && allChildrenSectionsAreLoaded;
				}
				if (!allChildrenSectionsAreLoaded)
				{
					// FIXME having world generation enabled in a pre-generated world that doesn't have any DH data can cause this to happen
					//  surprisingly reloadPos() doesn't appear to be the culprit, maybe there is an issue with reloading/changing the full data source?
					//LOGGER.warn("Potential QuadTree concurrency issue. All child sections should be enabled and ready to render for pos: "+DhSectionPos.toString(sectionPos));
				}
				
				// this section is now being rendered via its children
				return allChildrenSectionsAreLoaded;
			}
		}
		// TODO this should only equal the expected detail level, the (expectedDetailLevel-1) is a temporary fix to prevent corners from being cut out 
		else if (DhSectionPos.getDetailLevel(sectionPos) == expectedDetailLevel || DhSectionPos.getDetailLevel(sectionPos) == expectedDetailLevel - 1)
		{
			// this is the detail level we want to render //
			
			
			/* Can be uncommented to easily debug a single render section. */ 
			/* Don't forget the disableRendering() at the bottom though. */
			//if (sectionPos.getDetailLevel() == 10
			//	&&
			//	(
			//			sectionPos.getX() == 0 &&
			//			sectionPos.getZ() == -4
			//	))
			{
				// prepare this section for rendering
				// TODO this should fire for the lowest detail level first to improve loading speed
				if (!renderSection.gpuUploadInProgress() && renderSection.renderBuffer == null)
				{
					nodesNeedingLoading.add(renderSection);
				}
				
				if (Config.Client.Advanced.Debugging.DebugWireframe.showQuadTreeRenderStatus.get())
				{
					this.debugRenderSections.add(renderSection);
				}
				
				// wait for the parent to disable before enabling this section, so we don't overdraw/overlap render sections
				if (!parentSectionIsRendering && renderSection.canRender())
				{
					// if rendering is already enabled we don't have to re-enable it
					if (!renderSection.renderingEnabled)
					{
						renderSection.renderingEnabled = true;
						
						// delete/disable children, all of them will be a lower detail level than requested
						quadNode.deleteAllChildren((childRenderSection) ->
						{
							if (childRenderSection != null)
							{
								if (childRenderSection.renderingEnabled)
								{
									// show that this position's rendering has been disabled due to a parent rendering
									DebugRenderer.makeParticle(
										new DebugRenderer.BoxParticle(
											new DebugRenderer.Box(childRenderSection.pos, 128f, 156f, 0.09f, Color.MAGENTA.darker()),
											0.2, 32f
										)
									);
								}
								
								childRenderSection.renderingEnabled = false;
								childRenderSection.close();
							}
						});
					}
				}
				
				if (!renderSection.isFullyGenerated())
				{
					nodesNeedingRetrieval.add(renderSection);
				}
			}
			//else
			//{
			//	renderSection.disableRendering();
			//}
			
			return renderSection.canRender();
		}
		else
		{
			throw new IllegalStateException("LodQuadTree shouldn't be updating renderSections below the expected detail level: [" + expectedDetailLevel + "].");
		}
	}
	
	
	
	//====================//
	// detail level logic //
	//====================//
	
	/**
	 * This method will compute the detail level based on player position and section pos
	 * Override this method if you want to use a different algorithm
	 *
	 * @param playerPos player position as a reference for calculating the detail level
	 * @param sectionPos section position
	 * @return detail level of this section pos
	 */
	public byte calculateExpectedDetailLevel(DhBlockPos2D playerPos, long sectionPos) { return this.getDetailLevelFromDistance(playerPos.dist(DhSectionPos.getCenterBlockPosX(sectionPos), DhSectionPos.getCenterBlockPosZ(sectionPos))); }
	private byte getDetailLevelFromDistance(double distance)
	{
		double maxDetailDistance = this.getDrawDistanceFromDetail(Byte.MAX_VALUE - 1);
		if (distance > maxDetailDistance)
		{
			return Byte.MAX_VALUE - 1;
		}
		
		
		int detailLevel = (int) (Math.log(distance / this.detailDropOffDistanceUnit) / this.detailDropOffLogBase);
		return (byte) MathUtil.clamp(this.maxRenderDetailLevel, detailLevel, Byte.MAX_VALUE - 1);
	}
	
	private double getDrawDistanceFromDetail(int detail)
	{
		if (detail <= this.maxRenderDetailLevel)
		{
			return 0;
		}
		else if (detail >= Byte.MAX_VALUE)
		{
			return this.blockRenderDistanceDiameter * 2;
		}
		
		
		double base = Config.Client.Advanced.Graphics.Quality.horizontalQuality.get().quadraticBase;
		return Math.pow(base, detail) * this.detailDropOffDistanceUnit;
	}
	
	private void updateDetailLevelVariables()
	{
		this.detailDropOffDistanceUnit = Config.Client.Advanced.Graphics.Quality.horizontalQuality.get().distanceUnitInBlocks * LodUtil.CHUNK_WIDTH;
		this.detailDropOffLogBase = Math.log(Config.Client.Advanced.Graphics.Quality.horizontalQuality.get().quadraticBase);
		
		this.maxRenderDetailLevel = Config.Client.Advanced.Graphics.Quality.maxHorizontalResolution.get().detailLevel;
		
		// The minimum detail level is done to prevent single corner sections rendering 1 detail level lower than the others.
		// If not done corners may not be flush with the other LODs, which looks bad.
		byte minSectionDetailLevel = this.getDetailLevelFromDistance(this.blockRenderDistanceDiameter); // get the minimum allowed detail level
		minSectionDetailLevel -= 1; // -1 so corners can't render lower than their adjacent neighbors. space
		minSectionDetailLevel = (byte) Math.min(minSectionDetailLevel, this.treeMinDetailLevel); // don't allow rendering lower detail sections than what the tree contains
		this.minRenderDetailLevel = (byte) Math.max(minSectionDetailLevel, this.maxRenderDetailLevel); // respect the user's selected max resolution if it is lower detail (IE they want 2x2 block, but minSectionDetailLevel is specifically for 1x1 block render resolution)
	}
	
	
	
	//=============//
	// render data //
	//=============//
	
	/**
	 * Re-creates the color, render data.
	 * This method should be called after resource packs are changed or LOD settings are modified.
	 */
	public void clearRenderDataCache()
	{
		if (this.treeReadWriteLock.tryLock()) // TODO make async, can lock render thread
		{
			try
			{
				LOGGER.info("Disposing render data...");
				
				// clear the tree
				Iterator<QuadNode<LodRenderSection>> nodeIterator = this.nodeIterator();
				while (nodeIterator.hasNext())
				{
					QuadNode<LodRenderSection> quadNode = nodeIterator.next();
					if (quadNode.value != null)
					{
						quadNode.value.close();
						quadNode.value = null;
					}
				}
				
				LOGGER.info("Render data cleared, please wait a moment for everything to reload...");
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected error when clearing LodQuadTree render cache: " + e.getMessage(), e);
			}
			finally
			{
				this.treeReadWriteLock.unlock();
			}
		}
	}
	
	/**
	 * Can be called whenever a render section's data needs to be refreshed. <br>
	 * This should be called whenever a world generation task is completed or if the connected server has new data to show.
	 */
	public void reloadPos(long pos)
	{
		this.sectionsToReload.add(pos);
		
		// the adjacent locations also need to be updated to make sure lighting
		// and water updates correctly, otherwise oceans may have walls
		// and lights may not show up over LOD borders
		for (EDhDirection direction : EDhDirection.ADJ_DIRECTIONS)
		{
			this.sectionsToReload.add(DhSectionPos.getAdjacentPos(pos, direction));
		}
	}
	
	
	
	//=================================//
	// full data retrieval (world gen) //
	//=================================//
	
	private void queueFullDataRetrievalTasks(DhBlockPos2D playerPos, ArrayList<LodRenderSection> nodesNeedingRetrieval)
	{
		try
		{
			// add a slight delay since we don't need to check the world gen queue every tick
			Thread.sleep(WORLD_GEN_QUEUE_UPDATE_DELAY_IN_MS);
			
			// sort the nodes from nearest to farthest
			nodesNeedingRetrieval.sort((a, b) ->
			{
				int aDist = DhSectionPos.getManhattanBlockDistance(a.pos, playerPos);
				int bDist = DhSectionPos.getManhattanBlockDistance(b.pos, playerPos);
				return Integer.compare(aDist, bDist);
			});
			
			// add retrieval tasks to the queue
			for (int i = 0; i < nodesNeedingRetrieval.size(); i++)
			{
				LodRenderSection renderSection = nodesNeedingRetrieval.get(i);
				if (!this.fullDataSourceProvider.canQueueRetrieval())
				{
					break;
				}
				
				renderSection.tryQueuingMissingLodRetrieval();
			}
			
			// calculate an estimate for the max number of tasks for the queue
			int totalWorldGenCount = 0;
			for (int i = 0; i < nodesNeedingRetrieval.size(); i++)
			{
				LodRenderSection renderSection = nodesNeedingRetrieval.get(i);
				if (!renderSection.missingPositionsCalculated())
				{
					// may be higher than the actual amount
					totalWorldGenCount += this.fullDataSourceProvider.getMaxPossibleRetrievalPositionCountForPos(renderSection.pos);
				}
				else
				{
					totalWorldGenCount += renderSection.ungeneratedPositionCount();
				}
			}
			this.fullDataSourceProvider.setTotalRetrievalPositionCount(totalWorldGenCount);
		}
		catch (Exception e)
		{
			LOGGER.error("Unexpected error: "+e.getMessage(), e);
		}
		finally
		{
			this.fullDataRetrievalQueueRunning.set(false);
		}
	}
	
	
	//==================//
	// config listeners //
	//==================//
	
	private void onHorizontalQualityChange() { this.clearRenderDataCache(); }
	
	
	//===========//
	// debugging //
	//===========//
	
	@Override
	public void debugRender(DebugRenderer debugRenderer)
	{
		try
		{
			// lock to prevent accidentally rendering the array that's being cleared
			this.debugRenderSectionLock.lock();
			
			
			for (int i = 0; i < this.debugRenderSections.size(); i++)
			{
				LodRenderSection renderSection = this.debugRenderSections.get(i);
				
				Color color = Color.BLACK;
				if (renderSection.gpuUploadInProgress())
				{
					color = Color.ORANGE;
				}
				else if (renderSection.renderBuffer == null)
				{
					// uploaded but the buffer is missing
					color = Color.PINK;
				}
				else if (renderSection.renderBuffer.hasNonNullVbos())
				{
					if (renderSection.renderBuffer.vboBufferCount() != 0)
					{
						color = Color.GREEN;
					}
					else
					{
						// This section is probably rendering an empty chunk
						color = Color.RED;
					}
				}
				
				debugRenderer.renderBox(new DebugRenderer.Box(renderSection.pos, 400, 400f, Objects.hashCode(this), 0.05f, color));
			}
		}
		finally
		{
			this.debugRenderSectionLock.unlock();
		}
	}
	
	
	
	//==============//
	// base methods //
	//==============//
	
	@Override
	public void close()
	{
		LOGGER.info("Shutting down " + LodQuadTree.class.getSimpleName() + "...");
		
		this.horizontalScaleChangeListener.close();
		
		DebugRenderer.unregister(this, Config.Client.Advanced.Debugging.DebugWireframe.showQuadTreeRenderStatus);
		
		Iterator<QuadNode<LodRenderSection>> nodeIterator = this.nodeIterator();
		while (nodeIterator.hasNext())
		{
			QuadNode<LodRenderSection> quadNode = nodeIterator.next();
			if (quadNode.value != null)
			{
				quadNode.value.close();
				quadNode.value = null;
			}
		}
		
		LOGGER.info("Finished shutting down " + LodQuadTree.class.getSimpleName());
	}
	
}
