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
import com.seibel.distanthorizons.core.network.exceptions.InvalidLevelException;
import com.seibel.distanthorizons.core.network.exceptions.InvalidSectionPosException;
import com.seibel.distanthorizons.core.network.exceptions.RateLimitedException;
import com.seibel.distanthorizons.core.network.exceptions.RequestRejectedException;
import com.seibel.distanthorizons.core.network.plugin.TrackableMessage;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

// TODO appears to be useless yelling at user
public class ExceptionMessage extends TrackableMessage
{
	private static final List<Class<? extends Exception>> exceptionMap = new ArrayList<Class<? extends Exception>>()
	{{
		// All exceptions here must include constructor: (String)
		this.add(RateLimitedException.class);
		this.add(InvalidLevelException.class);
		this.add(InvalidSectionPosException.class);
		this.add(RequestRejectedException.class);
	}};
	
	public Exception exception;
	
	public ExceptionMessage() { }
	public ExceptionMessage(Exception exception)
	{
		this.exception = exception;
	}
	
	@Override protected void encode0(ByteBuf out)
	{
		out.writeInt(exceptionMap.indexOf(this.exception.getClass()));
		this.writeString(this.exception.getMessage(), out);
	}
	
	@Override protected void decode0(ByteBuf in) throws Exception
	{
		int id = in.readInt();
		String message = this.readString(in);
		this.exception = exceptionMap.get(id).getDeclaredConstructor(String.class).newInstance(message);
	}
	
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("exception", this.exception);
	}
	
}