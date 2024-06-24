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
import com.seibel.distanthorizons.core.network.messages.plugin.ILevelRelatedMessage;
import com.seibel.distanthorizons.core.network.plugin.PluginChannelMessage;
import com.seibel.distanthorizons.core.network.protocol.INetworkObject;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import io.netty.buffer.ByteBuf;

import java.io.IOException;

public class FullDataPartialUpdateMessage extends PluginChannelMessage implements ILevelRelatedMessage
{
	private String levelName;
	@Override
	public String getLevelName() { return this.levelName; }
	
	public FullDataSourceV2DTO dataSourceDto;
	
	
	public FullDataPartialUpdateMessage() { }
	public FullDataPartialUpdateMessage(ILevelWrapper level, FullDataSourceV2 fullDataSource)
	{
		this.levelName = level.getDimensionType().getDimensionName();
		
		try
		{
			EDhApiDataCompressionMode compressionMode = Config.Client.Advanced.LodBuilding.dataCompression.get();
			this.dataSourceDto = FullDataSourceV2DTO.CreateFromDataSource(fullDataSource, compressionMode);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	
	@Override
	public boolean warnWhenUnhandled() { return false; }
	
	@Override
	public void encode(ByteBuf out)
	{
		this.writeString(this.levelName, out);
		this.dataSourceDto.encode(out);
	}
	
	@Override
	public void decode(ByteBuf in)
	{
		this.levelName = this.readString(in);
		this.dataSourceDto = INetworkObject.decodeToInstance(new FullDataSourceV2DTO(), in);
	}
	
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("levelName", this.levelName)
				.add("dataSourceDto", this.dataSourceDto);
	}
	
}