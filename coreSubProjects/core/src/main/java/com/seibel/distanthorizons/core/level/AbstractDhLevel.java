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

import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkModifiedEvent;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.transformers.ChunkToLodBuilder;
import com.seibel.distanthorizons.core.file.fullDatafile.DelayedFullDataSourceSaveCache;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractDhLevel implements IDhLevel
{
	public final ChunkToLodBuilder chunkToLodBuilder;
	
	protected final DelayedFullDataSourceSaveCache delayedFullDataSourceSaveCache = new DelayedFullDataSourceSaveCache(this::onDataSourceSave, 2_000);
	/** contains the {@link DhChunkPos} for each {@link DhSectionPos} that are queued to save via {@link AbstractDhLevel#delayedFullDataSourceSaveCache} */
	protected final ConcurrentHashMap<Long, HashSet<DhChunkPos>> updatedChunkPosSetBySectionPos = new ConcurrentHashMap<>();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	protected AbstractDhLevel() { this.chunkToLodBuilder = new ChunkToLodBuilder(); }
	
	
	
	//=================//
	// default methods //
	//=================//
	
	@Override
	public int getUnsavedDataSourceCount() { return this.delayedFullDataSourceSaveCache.getUnsavedCount(); }
	
	@Override
	public void updateChunkAsync(IChunkWrapper chunkWrapper)
	{
		FullDataSourceV2 dataSource = FullDataSourceV2.createFromChunk(chunkWrapper);
		if (dataSource == null)
		{
			// This can happen if, among other reasons, a chunk save is superseded by a later event
			return;
		}
		
		
		this.updatedChunkPosSetBySectionPos.compute(dataSource.getPos(), (dataSourcePos, chunkPosSet) ->
		{
			if (chunkPosSet == null)
			{
				chunkPosSet = new HashSet<>();
			}
			chunkPosSet.add(chunkWrapper.getChunkPos());
			return chunkPosSet;
		});
		
		// batch updates to reduce overhead when flying around or breaking/placing a lot of blocks in an area
		this.delayedFullDataSourceSaveCache.queueDataSourceForUpdateAndSave(dataSource);
	}
	
	private void onDataSourceSave(FullDataSourceV2 fullDataSource)
	{
		this.updateDataSourcesAsync(fullDataSource).thenRun(() ->
		{
			HashSet<DhChunkPos> updatedChunkPosSet = this.updatedChunkPosSetBySectionPos.remove(fullDataSource.getPos());
			if (updatedChunkPosSet != null)
			{
				for (DhChunkPos chunkPos : updatedChunkPosSet)
				{
					ApiEventInjector.INSTANCE.fireAllEvents(
							DhApiChunkModifiedEvent.class,
							new DhApiChunkModifiedEvent.EventParam(this.getLevelWrapper(), chunkPos.x, chunkPos.z));
				}
			}
		});
	}
	
	
	@Override
	public void close() { this.chunkToLodBuilder.close(); }
	
}