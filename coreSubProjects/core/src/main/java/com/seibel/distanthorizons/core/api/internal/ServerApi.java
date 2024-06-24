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

package com.seibel.distanthorizons.core.api.internal;

import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelUnloadEvent;
import com.seibel.distanthorizons.core.network.plugin.PluginChannelMessage;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import com.seibel.distanthorizons.core.world.DhClientServerWorld;
import com.seibel.distanthorizons.core.world.DhServerWorld;
import com.seibel.distanthorizons.core.world.IDhServerWorld;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * This holds the methods that should be called by the host mod loader (Fabric,
 * Forge, etc.). Specifically server events.
 */
public class ServerApi
{
	public static final ServerApi INSTANCE = new ServerApi();
	
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private ServerApi() { }
	
	
	
	//=============//
	// tick events //
	//=============//
	
	public void serverTickEvent()
	{
		try
		{
			IDhServerWorld serverWorld = SharedApi.getIDhServerWorld();
			if (serverWorld != null)
			{
				serverWorld.serverTick();
				SharedApi.worldGenTick(serverWorld::doWorldGen);
			}
		}
		catch (Exception e)
		{
			// try catch is necessary to prevent crashing the internal server when an exception is thrown
			LOGGER.error("ServerTickEvent error: " + e.getMessage(), e);
		}
	}
	
	
	
	//===============//
	// server events //
	//===============//
	
	public void serverLoadEvent(boolean isDedicatedEnvironment)
	{
		LOGGER.debug("Server World loading with (dedicated?:" + isDedicatedEnvironment + ")");
		SharedApi.setDhWorld(isDedicatedEnvironment ? new DhServerWorld() : new DhClientServerWorld());
	}
	
	public void serverUnloadEvent()
	{
		LOGGER.debug("Server World " + SharedApi.getAbstractDhWorld() + " unloading");
		
		// shutdown the world if it isn't already
		AbstractDhWorld dhWorld = SharedApi.getAbstractDhWorld();
		if (dhWorld != null)
		{
			dhWorld.close();
			SharedApi.setDhWorld(null);
		}
	}
	
	
	
	//==============//
	// level events //
	//==============//
	
	public void serverLevelLoadEvent(IServerLevelWrapper level)
	{
		LOGGER.debug("Server Level " + level + " loading");
		
		AbstractDhWorld serverWorld = SharedApi.getAbstractDhWorld();
		if (serverWorld != null)
		{
			serverWorld.getOrLoadLevel(level);
			ApiEventInjector.INSTANCE.fireAllEvents(DhApiLevelLoadEvent.class, new DhApiLevelLoadEvent.EventParam(level));
		}
	}
	public void serverLevelUnloadEvent(IServerLevelWrapper level)
	{
		LOGGER.debug("Server Level " + level + " unloading");
		
		AbstractDhWorld serverWorld = SharedApi.getAbstractDhWorld();
		if (serverWorld != null)
		{
			serverWorld.unloadLevel(level);
			ApiEventInjector.INSTANCE.fireAllEvents(DhApiLevelUnloadEvent.class, new DhApiLevelUnloadEvent.EventParam(level));
		}
	}
	
	
	
	//=======================//
	// chunk modified events //
	//=======================//
	
	public void serverChunkLoadEvent(IChunkWrapper chunkWrapper, ILevelWrapper level) { SharedApi.INSTANCE.applyChunkUpdate(chunkWrapper, level, false); }
	public void serverChunkSaveEvent(IChunkWrapper chunkWrapper, ILevelWrapper level) { SharedApi.INSTANCE.applyChunkUpdate(chunkWrapper, level, false); }
	
	
	
	//===============//
	// player events //
	//===============//
	
	public void serverPlayerJoinEvent(IServerPlayerWrapper player)
	{
		IDhServerWorld serverWorld = SharedApi.getIDhServerWorld();
		if (serverWorld instanceof DhServerWorld) // TODO add support for DhClientServerWorld's (lan worlds) as well
		{
			LOGGER.info("Creating state for player: " + player.getName());
			((DhServerWorld) serverWorld).addPlayer(player);
		}
	}
	public void serverPlayerDisconnectEvent(IServerPlayerWrapper player)
	{
		IDhServerWorld serverWorld = SharedApi.getIDhServerWorld();
		if (serverWorld instanceof DhServerWorld) // TODO add support for DhClientServerWorld's (lan worlds) as well
		{
			LOGGER.info("Destroying state for player: " + player.getName());
			((DhServerWorld) serverWorld).removePlayer(player);
		}
	}
	public void serverPlayerLevelChangeEvent(IServerPlayerWrapper player, IServerLevelWrapper origin, IServerLevelWrapper dest)
	{
		IDhServerWorld serverWorld = SharedApi.getIDhServerWorld();
		if (serverWorld instanceof DhServerWorld) // TODO add support for DhClientServerWorld's (lan worlds) as well
		{
			LOGGER.info("Player changed level: " + player.getName());
			((DhServerWorld) serverWorld).changePlayerLevel(player, origin, dest);
		}
	}
	
	public void pluginMessageReceived(IServerPlayerWrapper player, @NotNull PluginChannelMessage message)
	{
		IDhServerWorld serverWorld = SharedApi.getIDhServerWorld();
		if (serverWorld instanceof DhServerWorld) // TODO add support for DhClientServerWorld's (lan worlds) as well
		{
			((DhServerWorld) serverWorld).remotePlayerConnectionHandler.handlePluginMessage(player, message);
		}
	}
	
}