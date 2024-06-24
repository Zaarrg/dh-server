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

package com.seibel.distanthorizons.core.network.messages.plugin.fullData;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.network.plugin.TrackableMessage;
import com.seibel.distanthorizons.core.network.protocol.INetworkObject;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Response message, containing the requested full data source,
 * or nothing if requested in updates-only mode and the data was not updated.
 */
public class FullDataSourceResponseMessage extends TrackableMessage
{
	@Nullable
	public FullDataSourceV2DTO dataSourceDto;
	
	public FullDataSourceResponseMessage() { }
	public FullDataSourceResponseMessage(@Nullable FullDataSourceV2 fullDataSource)
	{
		try
		{
			if (fullDataSource != null)
			{
				EDhApiDataCompressionMode compressionMode = Config.Client.Advanced.LodBuilding.dataCompression.get();
				this.dataSourceDto = FullDataSourceV2DTO.CreateFromDataSource(fullDataSource, compressionMode);
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void encode0(ByteBuf out)
	{
		if (this.writeOptional(out, this.dataSourceDto))
		{
			this.dataSourceDto.encode(out);
		}
	}
	
	@Override
	public void decode0(ByteBuf in)
	{
		this.dataSourceDto = this.readOptional(in, () -> INetworkObject.decodeToInstance(new FullDataSourceV2DTO(), in));
	}
	
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("dataSourceDto", this.dataSourceDto);
	}
	
}