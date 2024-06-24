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
import com.seibel.distanthorizons.core.level.DhServerLevel;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.multiplayer.server.RemotePlayerConnectionHandler;
import com.seibel.distanthorizons.core.multiplayer.server.ServerPlayerState;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;

public class DhServerWorld extends AbstractDhWorld implements IDhServerWorld
{
	private final HashMap<IServerLevelWrapper, DhServerLevel> levels;
	public final LocalSaveStructure saveStructure;
	
	public final RemotePlayerConnectionHandler remotePlayerConnectionHandler;
	
	
	//==============//
	// constructors //
	//==============//
	
	public DhServerWorld()
	{
		super(EWorldEnvironment.Server_Only);
		
		this.saveStructure = new LocalSaveStructure();
		this.levels = new HashMap<>();
		
		this.remotePlayerConnectionHandler = new RemotePlayerConnectionHandler();

		LOGGER.info("Started "+DhServerWorld.class.getSimpleName()+" of type "+this.environment);
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	public void addPlayer(IServerPlayerWrapper serverPlayer)
	{
		ServerPlayerState playerState = this.remotePlayerConnectionHandler.registerJoinedPlayer(serverPlayer);
		this.getLevel(serverPlayer.getLevel()).addPlayer(serverPlayer);
		
		for (DhServerLevel level : this.levels.values())
		{
			level.registerNetworkHandlers(playerState);
		}
	}
	
	public void removePlayer(IServerPlayerWrapper serverPlayer)
	{
		this.getLevel(serverPlayer.getLevel()).removePlayer(serverPlayer);
		this.remotePlayerConnectionHandler.unregisterLeftPlayer(serverPlayer);
		
		// If player's left, session is already closed
	}
	
	public void changePlayerLevel(IServerPlayerWrapper player, IServerLevelWrapper origin, IServerLevelWrapper dest)
	{
		this.getLevel(dest).addPlayer(player);
		this.getLevel(origin).removePlayer(player);
	}
	
	
	
	@Override
	public DhServerLevel getOrLoadLevel(@NotNull ILevelWrapper wrapper)
	{
		if (!(wrapper instanceof IServerLevelWrapper))
		{
			return null;
		}
		
		return this.levels.computeIfAbsent((IServerLevelWrapper) wrapper, (serverLevelWrapper) ->
		{
			File levelFile = this.saveStructure.getLevelFolder(wrapper);
			LodUtil.assertTrue(levelFile != null);
			return new DhServerLevel(this.saveStructure, serverLevelWrapper, this.remotePlayerConnectionHandler);
		});
	}
	
	@Override
	public DhServerLevel getLevel(@NotNull ILevelWrapper wrapper)
	{
		if (!(wrapper instanceof IServerLevelWrapper))
		{
			return null;
		}
		
		return this.levels.get(wrapper);
	}
	
	@Override
	public Iterable<? extends IDhLevel> getAllLoadedLevels() { return this.levels.values(); }
	
	@Override
	public void unloadLevel(@NotNull ILevelWrapper wrapper)
	{
		if (!(wrapper instanceof IServerLevelWrapper))
		{
			return;
		}
		
		if (this.levels.containsKey(wrapper))
		{
			LOGGER.info("Unloading level {} ", this.levels.get(wrapper));
			wrapper.onUnload();
			this.levels.remove(wrapper).close();
		}
	}
	
	@Override public void serverTick()
	{
		this.levels.values().forEach(DhServerLevel::serverTick);
	}
	
	@Override public void doWorldGen()
	{
		this.levels.values().forEach(DhServerLevel::doWorldGen);
	}
	
	@Override
	public void close()
	{
		for (DhServerLevel level : this.levels.values())
		{
			LOGGER.info("Unloading level " + level.getLevelWrapper().getDimensionType().getDimensionName());
			
			// level wrapper shouldn't be null, but just in case
			IServerLevelWrapper serverLevelWrapper = level.getServerLevelWrapper();
			if (serverLevelWrapper != null)
			{
				serverLevelWrapper.onUnload();
			}
			
			level.close();
		}
		
		this.levels.clear();
		LOGGER.info("Closed DhWorld of type " + this.environment);
	}
	
}