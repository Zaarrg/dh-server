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

package com.seibel.distanthorizons.core.network.messages.plugin.base;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.core.network.plugin.PluginChannelMessage;
import io.netty.buffer.ByteBuf;

public class CloseReasonMessage extends PluginChannelMessage
{
	public String reason;

	public CloseReasonMessage() { }
	public CloseReasonMessage(String reason) { this.reason = reason; }

	@Override
	public void encode(ByteBuf out)
	{
		this.writeString(this.reason, out);
	}

	@Override
	public void decode(ByteBuf in) { this.reason = this.readString(in); }
	
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("reason", this.reason);
	}
	
}