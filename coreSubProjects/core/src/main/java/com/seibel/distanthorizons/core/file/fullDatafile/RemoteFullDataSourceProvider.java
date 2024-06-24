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

package com.seibel.distanthorizons.core.file.fullDatafile;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.file.structure.AbstractSaveStructure;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.multiplayer.client.FullDataRefreshQueue;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

public class RemoteFullDataSourceProvider extends GeneratedFullDataSourceProvider
{
	@Nullable
	private final FullDataRefreshQueue dataRefreshQueue;
	
	public RemoteFullDataSourceProvider(IDhLevel level, AbstractSaveStructure saveStructure, @Nullable File saveDirOverride, @Nullable FullDataRefreshQueue dataRefreshQueue)
	{
		super(level, saveStructure, saveDirOverride);
		this.dataRefreshQueue = dataRefreshQueue;
	}
	
	@Override
	@Nullable
	public FullDataSourceV2 get(long pos)
	{
		FullDataSourceV2 fullDataSource = super.get(pos);
		if (fullDataSource == null || this.dataRefreshQueue == null)
		{
			return fullDataSource;
		}
		
		int posToMinimumDetailScale = (DhSectionPos.getDetailLevel(pos) - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL + 1);
		Map<Long, Long> timestamps = this.getTimestampsForRange(
				DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL,
				DhSectionPos.getX(pos) * posToMinimumDetailScale,
				DhSectionPos.getZ(pos) * posToMinimumDetailScale,
				(DhSectionPos.getX(pos) + 1) * posToMinimumDetailScale - 1,
				(DhSectionPos.getZ(pos) + 1) * posToMinimumDetailScale - 1
		);
		for (Map.Entry<Long, Long> entry : timestamps.entrySet())
		{
			this.dataRefreshQueue.submitRequest(entry.getKey(), entry.getValue(), this.delayedFullDataSourceSaveCache::queueDataSourceForUpdateAndSave);
		}
		
		return fullDataSource;
	}
	
	@Override
	public void close()
	{
		if (this.dataRefreshQueue != null)
		{
			this.dataRefreshQueue.close();
		}
		super.close();
	}
	
}