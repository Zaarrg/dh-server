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

package com.seibel.distanthorizons.core.file.structure;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;

import java.io.File;

/**
 * Designed for Client_Server & Server_Only environments.
 *
 * @version 2022-12-17
 */
public class LocalSaveStructure extends AbstractSaveStructure
{
	private File debugPath = new File("");
	
	
	
	public LocalSaveStructure() { }
	
	
	
	//================//
	// folder methods //
	//================//
	
	@Override
	public File getLevelFolder(ILevelWrapper wrapper)
	{
		IServerLevelWrapper serverSide = (IServerLevelWrapper) wrapper;
		this.debugPath = serverSide.getSaveFolder();
		return serverSide.getSaveFolder();
	}
	
	@Override
	public File getRenderCacheFolder(ILevelWrapper level)
	{
		IServerLevelWrapper serverSide = (IServerLevelWrapper) level;
		this.debugPath = serverSide.getSaveFolder();
		return serverSide.getSaveFolder();
	}
	
	@Override
	public File getFullDataFolder(ILevelWrapper level)
	{
		IServerLevelWrapper serverLevelWrapper = (IServerLevelWrapper) level;
		this.debugPath = serverLevelWrapper.getSaveFolder();
		return serverLevelWrapper.getSaveFolder();
	}
	
	
	
	//==================//
	// override methods //
	//==================//
	
	@Override
	public void close() throws Exception { }
	
	@Override
	public String toString() { return "[" + this.getClass().getSimpleName() + "@" + this.debugPath + "]"; }
	
}
