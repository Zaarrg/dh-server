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

package com.seibel.distanthorizons.core.world;

import com.seibel.distanthorizons.core.file.structure.LocalSaveStructure;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.level.DhClientServerLevel;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import com.seibel.distanthorizons.core.util.objects.EventLoop;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class DhClientServerWorld extends AbstractDhWorld implements IDhClientWorld, IDhServerWorld
{
	private final HashMap<ILevelWrapper, DhClientServerLevel> levelWrapperByDhLevel = new HashMap<>();
	private final HashSet<DhClientServerLevel> dhLevels = new HashSet<>();
	public final LocalSaveStructure saveStructure = new LocalSaveStructure();
	
	public ExecutorService dhTickerThread = ThreadUtil.makeSingleThreadPool("Client Server World Ticker Thread", 2);
	public EventLoop eventLoop = new EventLoop(this.dhTickerThread, this::_clientTick); //TODO: Rate-limit the loop
	
	public F3Screen.DynamicMessage f3Message;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DhClientServerWorld()
	{
		super(EWorldEnvironment.Client_Server);
		
		LOGGER.info("Started DhWorld of type " + this.environment);
		
		this.f3Message = new F3Screen.DynamicMessage(() -> LodUtil.formatLog(this.environment + " World with " + this.dhLevels.size() + " levels"));
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public DhClientServerLevel getOrLoadLevel(@NotNull ILevelWrapper wrapper)
	{
		if (wrapper instanceof IServerLevelWrapper)
		{
			return this.levelWrapperByDhLevel.computeIfAbsent(wrapper, (levelWrapper) ->
			{
				File levelFile = this.saveStructure.getLevelFolder(levelWrapper);
				LodUtil.assertTrue(levelFile != null);
				DhClientServerLevel level = new DhClientServerLevel(this.saveStructure, (IServerLevelWrapper) levelWrapper);
				this.dhLevels.add(level);
				return level;
			});
		}
		else
		{
			return this.levelWrapperByDhLevel.computeIfAbsent(wrapper, (levelWrapper) ->
			{
				IClientLevelWrapper clientLevelWrapper = (IClientLevelWrapper) levelWrapper;
				IServerLevelWrapper serverLevelWrapper = clientLevelWrapper.tryGetServerSideWrapper();
				LodUtil.assertTrue(serverLevelWrapper != null);
				if (!clientLevelWrapper.getDimensionType().equals(serverLevelWrapper.getDimensionType()))
				{
					LodUtil.assertNotReach("tryGetServerSideWrapper returned a level for a different dimension. ClientLevelWrapper dim: " + clientLevelWrapper.getDimensionType().getDimensionName() + " ServerLevelWrapper dim: " + serverLevelWrapper.getDimensionType().getDimensionName());
				}
				
				
				DhClientServerLevel level = this.levelWrapperByDhLevel.get(serverLevelWrapper);
				if (level == null)
				{
					return null;
				}
				
				level.startRenderer(clientLevelWrapper);
				return level;
			});
		}
	}
	
	@Override
	public DhClientServerLevel getLevel(@NotNull ILevelWrapper wrapper) { return this.levelWrapperByDhLevel.get(wrapper); }
	
	@Override
	public Iterable<? extends IDhLevel> getAllLoadedLevels() { return this.dhLevels; }
	
	@Override
	public void unloadLevel(@NotNull ILevelWrapper wrapper)
	{
		if (this.levelWrapperByDhLevel.containsKey(wrapper))
		{
			if (wrapper instanceof IServerLevelWrapper)
			{
				LOGGER.info("Unloading level " + this.levelWrapperByDhLevel.get(wrapper));
				wrapper.onUnload();
				
				DhClientServerLevel clientServerLevel = this.levelWrapperByDhLevel.remove(wrapper);
				clientServerLevel.close();
				this.dhLevels.remove(clientServerLevel);
			}
			else
			{
				// If the level wrapper is a Client Level Wrapper, then that means the client side leaves the level,
				// but note that the server side still has the level loaded. So, we don't want to unload the level,
				// we just want to stop rendering it.
				this.levelWrapperByDhLevel.remove(wrapper).stopRenderer(); // Ignore resource warning. The level obj is referenced elsewhere.
			}
		}
	}
	
	private void _clientTick()
	{
		//LOGGER.info("Client world tick with {} levels", levels.size());
		this.dhLevels.forEach(DhClientServerLevel::clientTick);
	}
	
	public void clientTick()
	{
		//LOGGER.info("Client world tick");
		this.eventLoop.tick();
	}
	
	public void serverTick() { this.dhLevels.forEach(DhClientServerLevel::serverTick); }
	
	public void doWorldGen() { this.dhLevels.forEach(DhClientServerLevel::doWorldGen); }
	
	/** synchronized to prevent a rare issue where the server tries closing the same world multiple times in rapid succession. */
	@Override
	public synchronized void close()
	{
		this.f3Message.close();
		
		
		// clear dhLevels to prevent concurrent modification errors
		HashSet<DhClientServerLevel> levelsToClose = new HashSet<>(this.dhLevels);
		this.dhLevels.clear();
		// close each level
		for (DhClientServerLevel level : levelsToClose)
		{
			LOGGER.info("Unloading level " + level.getServerLevelWrapper().getDimensionType().getDimensionName());
			
			// level wrapper shouldn't be null, but just in case
			IServerLevelWrapper serverLevelWrapper = level.getServerLevelWrapper();
			if (serverLevelWrapper != null)
			{
				serverLevelWrapper.onUnload();
			}
			
			level.close();
		}
		
		this.levelWrapperByDhLevel.clear();
		this.eventLoop.close();
		LOGGER.info("Closed DhWorld of type " + this.environment);
	}
	
}
