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

package com.seibel.distanthorizons.core.wrapperInterfaces.world;

import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.core.pos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

/**
 * Can be either a Server world or a Client world.
 *
 * @author James Seibel
 * @version 2023-6-17
 */
public interface ILevelWrapper extends IDhApiLevelWrapper, IBindable
{
	@Override
	IDimensionTypeWrapper getDimensionType();
	
	@Override
	boolean hasCeiling();
	
	@Override
	boolean hasSkyLight();
	
	@Override
	int getHeight();
	
	@Override
	default int getMinHeight() { return 0; }
	
	default IChunkWrapper tryGetChunk(DhChunkPos pos) { return null; }
	
	boolean hasChunkLoaded(int chunkX, int chunkZ);
	
	@Deprecated
	IBlockStateWrapper getBlockState(DhBlockPos pos);
	
	@Deprecated
	IBiomeWrapper getBiome(DhBlockPos pos);
	
	/** Fired when the level is being unloaded. Doesn't unload the level. */
	void onUnload();
	
}