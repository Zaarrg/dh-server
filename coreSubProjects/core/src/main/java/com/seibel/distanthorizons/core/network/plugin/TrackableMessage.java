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

package com.seibel.distanthorizons.core.network.plugin;

import com.google.common.base.MoreObjects;
import com.google.common.collect.MapMaker;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.network.messages.plugin.base.ExceptionMessage;
import com.seibel.distanthorizons.core.world.EWorldEnvironment;
import io.netty.buffer.ByteBuf;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class TrackableMessage extends PluginChannelMessage
{
	private static final AtomicInteger lastId = new AtomicInteger();
	// 32 bits - Context ID (not transmitted)
	// 1 bit - Requesting side (client - 0, server - 1)
	// 31 bits - Request ID
	public long futureId = lastId.getAndIncrement()
			| ((Objects.requireNonNull(SharedApi.getEnvironment()) == EWorldEnvironment.Server_Only ? 1 : 0) << 31);
	
	public void sendResponse(TrackableMessage responseMessage)
	{
		responseMessage.futureId = this.futureId;
		this.session.sendMessage(responseMessage);
	}
	
	public void sendResponse(Exception e)
	{
		this.sendResponse(new ExceptionMessage(e));
	}
	
	@Override
	public final void encode(ByteBuf out)
	{
		try
		{
			out.writeInt((int) this.futureId);
			this.encode0(out);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public final void decode(ByteBuf in)
	{
		try
		{
			this.futureId = in.readInt();
			this.decode0(in);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
	
	protected abstract void encode0(ByteBuf out) throws Exception;
	protected abstract void decode0(ByteBuf in) throws Exception;
	
	
	@Override public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("futureId", this.futureId);
	}
	
}