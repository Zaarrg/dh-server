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

package com.seibel.distanthorizons.core.level;

import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.core.config.AppliedConfigState;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.file.fullDatafile.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.file.fullDatafile.RemoteFullDataSourceProvider;
import com.seibel.distanthorizons.core.file.structure.AbstractSaveStructure;
import com.seibel.distanthorizons.core.generation.WorldRemoteGenerationQueue;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.multiplayer.client.ClientNetworkState;
import com.seibel.distanthorizons.core.multiplayer.client.FullDataRefreshQueue;
import com.seibel.distanthorizons.core.network.ScopedNetworkEventSource;
import com.seibel.distanthorizons.core.network.messages.plugin.fullData.FullDataPartialUpdateMessage;
import com.seibel.distanthorizons.core.pos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckForNull;
import java.awt.*;
import java.io.File;
import java.util.concurrent.CompletableFuture;

/** The level used when connected to a server */
public class DhClientLevel extends AbstractDhLevel implements IDhClientLevel
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	private static class WorldGenState extends WorldGenModule.AbstractWorldGenState
	{
		WorldGenState(IDhClientLevel level, ClientNetworkState networkState)
		{
			this.worldGenerationQueue = new WorldRemoteGenerationQueue(networkState, level);
		}
	}
	
	public final ClientLevelModule clientside;
	public final IClientLevelWrapper levelWrapper;
	public final AbstractSaveStructure saveStructure;
	public final RemoteFullDataSourceProvider dataFileHandler;
	
	@CheckForNull
	private final ClientNetworkState networkState;
	@Nullable
	private final ScopedNetworkEventSource eventSource;
	
	public final WorldGenModule worldGenModule;
	public final AppliedConfigState<Boolean> worldGeneratorEnabledConfig;
	
	@Nullable
	private final FullDataRefreshQueue dataRefreshQueue;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DhClientLevel(AbstractSaveStructure saveStructure, IClientLevelWrapper clientLevelWrapper, @Nullable ClientNetworkState networkState) { this(saveStructure, clientLevelWrapper, null, true, networkState); }
	public DhClientLevel(AbstractSaveStructure saveStructure, IClientLevelWrapper clientLevelWrapper, @Nullable File fullDataSaveDirOverride, boolean enableRendering, @Nullable ClientNetworkState networkState)
	{
		if (saveStructure.getFullDataFolder(clientLevelWrapper).mkdirs())
		{
			LOGGER.warn("unable to create data folder.");
		}
		this.levelWrapper = clientLevelWrapper;
		this.saveStructure = saveStructure;
		
		this.networkState = networkState;
		if (networkState != null)
		{
			this.eventSource = new ScopedNetworkEventSource(networkState.getSession());
			this.dataRefreshQueue = new FullDataRefreshQueue(this, networkState);
			this.registerNetworkHandlers();
		}
		else
		{
			this.eventSource = null;
			this.dataRefreshQueue = null;
		}
		
		this.dataFileHandler = new RemoteFullDataSourceProvider(this, saveStructure, fullDataSaveDirOverride, this.dataRefreshQueue);
		this.worldGeneratorEnabledConfig = new AppliedConfigState<>(Config.Client.Advanced.WorldGenerator.enableDistantGeneration);
		this.worldGenModule = new WorldGenModule(this);
		
		this.clientside = new ClientLevelModule(this);
		if (enableRendering)
		{
			this.clientside.startRenderer(clientLevelWrapper);
			LOGGER.info("Started DHLevel for " + this.levelWrapper + " with saves at " + this.saveStructure);
		}
	}
	
	private void registerNetworkHandlers()
	{
		assert this.eventSource != null;
		
		this.eventSource.registerHandler(FullDataPartialUpdateMessage.class, msg ->
		{
			try
			{
				if (msg.dataSourceDto == null)
				{
					return;
				}
				
				this.updateDataSourcesAsync(msg.dataSourceDto.createPooledDataSource(this.levelWrapper));
			}
			catch (Exception e)
			{
				LOGGER.error("Error while updating full data source", e);
			}
		});
	}
	
	//==============//
	// tick methods //
	//==============//
	
	@Override
	public void clientTick()
	{
		try
		{
			this.chunkToLodBuilder.tick();
			this.clientside.clientTick();
			
			if (this.dataRefreshQueue != null)
			{
				this.dataRefreshQueue.tick(new DhBlockPos2D(MC_CLIENT.getPlayerBlockPos()));
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Unexpected clientTick Exception: "+e.getMessage(), e);
		}
	}
	
	@Override
	public void doWorldGen()
	{
		ClientNetworkState networkState = this.networkState;
		
		boolean isClientUsable = false, isAllowedDimension = false;
		if (networkState != null)
		{
			isClientUsable = !networkState.getSession().isClosed();
			isAllowedDimension = MC_CLIENT.getWrappedClientLevel() == this.levelWrapper;
		}
		
		boolean shouldDoWorldGen = isClientUsable
				&& networkState.config.distantGenerationEnabled
				&& isAllowedDimension
				&& this.clientside.isRendering();
		
		boolean isWorldGenRunning = this.worldGenModule.isWorldGenRunning();
		if (shouldDoWorldGen && !isWorldGenRunning)
		{
			// start world gen
			this.worldGenModule.startWorldGen(this.dataFileHandler, new WorldGenState(this, networkState));
			
			// populate the queue based on the current rendering tree
			ClientLevelModule.ClientRenderState renderState = this.clientside.ClientRenderStateRef.get();
			renderState.quadtree.leafNodeIterator().forEachRemaining(node -> {
				this.dataFileHandler.getAsync(node.sectionPos);
			});
		}
		else if (!shouldDoWorldGen && isWorldGenRunning)
		{
			// stop world gen
			this.worldGenModule.stopWorldGen(this.dataFileHandler);
		}
		
		if (this.worldGenModule.isWorldGenRunning())
		{
			this.worldGenModule.worldGenTick(
					new DhBlockPos2D(MC_CLIENT.getPlayerBlockPos())
							.scale(MC_CLIENT.getWrappedClientLevel().getDimensionType().getTeleportationScale(this.getLevelWrapper().getDimensionType()))
			);
		}
	}

	@Override
	public void render(DhApiRenderParam renderEventParam, IProfilerWrapper profiler)
	{ this.clientside.render(renderEventParam, profiler); }
	
	@Override
	public void renderDeferred(DhApiRenderParam renderEventParam, IProfilerWrapper profiler)
	{ this.clientside.renderDeferred(renderEventParam, profiler); }
	
	
	
	//================//
	// level handling //
	//================//
	
	@Override
	public int computeBaseColor(DhBlockPos pos, IBiomeWrapper biome, IBlockStateWrapper block) { return this.levelWrapper.computeBaseColor(pos, biome, block); }
	
	@Override
	public IClientLevelWrapper getClientLevelWrapper() { return this.levelWrapper; }
	
	@Override
	public void clearRenderCache()
	{
		this.clientside.clearRenderCache();
	}
	
	@Override
	public ILevelWrapper getLevelWrapper() { return this.levelWrapper; }
	
	@Override
	public CompletableFuture<Void> updateDataSourcesAsync(FullDataSourceV2 data) { return this.clientside.updateDataSourcesAsync(data); }
	
	@Override
	public int getMinY() { return this.levelWrapper.getMinHeight(); }
	
	@Override
	public void close()
	{
		if (this.worldGenModule != null)
		{
			this.worldGenModule.close();
		}
		this.clientside.close();
		super.close();
		this.dataFileHandler.close();
		LOGGER.info("Closed " + DhClientLevel.class.getSimpleName() + " for " + this.levelWrapper);
	}
	
	@Override
	public FullDataSourceProviderV2 getFullDataProvider() { return this.dataFileHandler; }
	
	@Override
	public AbstractSaveStructure getSaveStructure() { return this.saveStructure; }
	
	@Override
	public boolean hasSkyLight() { return this.levelWrapper.hasSkyLight(); }
	
	
	@Override
	public void onWorldGenTaskComplete(long pos)
	{
		DebugRenderer.makeParticle(
				new DebugRenderer.BoxParticle(
						new DebugRenderer.Box(pos, 128f, 156f, 0.09f, Color.red.darker()),
						0.2, 32f
				)
		);
		
		this.clientside.reloadPos(pos);
	}
}