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

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.file.fullDatafile.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.file.structure.AbstractSaveStructure;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;

import java.util.concurrent.CompletableFuture;

public interface IDhLevel extends AutoCloseable
{
	int getMinY();
	
	/**
	 * May return either a client or server level wrapper. <br>
	 * Should not return null
	 */
	ILevelWrapper getLevelWrapper();
	
	void updateChunkAsync(IChunkWrapper chunk);
	
	FullDataSourceProviderV2 getFullDataProvider();
	
	AbstractSaveStructure getSaveStructure();
	
	boolean hasSkyLight();
	
	CompletableFuture<Void> updateDataSourcesAsync(FullDataSourceV2 data);
	
	/**
	 * this number is generally related to how many data sources have been updated
	 * due to chunk modifications or loads.
	 */
	int getUnsavedDataSourceCount();
	
}