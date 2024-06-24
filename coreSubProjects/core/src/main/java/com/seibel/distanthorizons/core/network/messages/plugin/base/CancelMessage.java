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

import com.seibel.distanthorizons.core.network.plugin.TrackableMessage;
import io.netty.buffer.ByteBuf;

public class CancelMessage extends TrackableMessage
{
	public CancelMessage() { }
	
	@Override
	public void encode0(ByteBuf out)
	{
	}
	
	@Override
	public void decode0(ByteBuf in)
	{
	}
}