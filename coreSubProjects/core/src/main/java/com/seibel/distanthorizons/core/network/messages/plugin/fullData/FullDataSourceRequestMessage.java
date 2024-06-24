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
import com.seibel.distanthorizons.core.network.messages.plugin.ILevelRelatedMessage;
import com.seibel.distanthorizons.core.network.plugin.TrackableMessage;
import com.seibel.distanthorizons.core.network.protocol.INetworkObject;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import io.netty.buffer.ByteBuf;

import javax.annotation.Nullable;

public class FullDataSourceRequestMessage extends TrackableMessage implements ILevelRelatedMessage
{
	private String levelName;
	
	public long sectionPos;
	
	/** Only present when requesting for changes. */
	@Nullable
	public Long clientTimestamp;
	
	@Override
	public String getLevelName() { return this.levelName; }
	
	public FullDataSourceRequestMessage() {}
	public FullDataSourceRequestMessage(ILevelWrapper levelWrapper, long sectionPos, @Nullable Long clientTimestamp)
	{
		this.levelName = levelWrapper.getDimensionType().getDimensionName();
		this.sectionPos = sectionPos;
		this.clientTimestamp = clientTimestamp;
	}

    @Override
    public void encode0(ByteBuf out)
	{
		this.writeString(this.levelName, out);
		out.writeLong(this.sectionPos);
		if (this.writeOptional(out, this.clientTimestamp))
		{
			out.writeLong(this.clientTimestamp);
		}
    }

    @Override
    public void decode0(ByteBuf in)
	{
		this.levelName = this.readString(in);
		this.sectionPos = in.readLong();
		this.clientTimestamp = this.readOptional(in, in::readLong);
    }
	
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("levelName", this.levelName)
				.add("sectionPos", this.sectionPos)
				.add("clientTimestamp", this.clientTimestamp);
	}
	
}