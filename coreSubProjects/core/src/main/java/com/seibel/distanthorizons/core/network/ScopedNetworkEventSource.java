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

package com.seibel.distanthorizons.core.network;

import com.seibel.distanthorizons.core.network.plugin.PluginChannelMessage;
import com.seibel.distanthorizons.core.network.protocol.INetworkObject;

import java.util.function.Consumer;

/** Provides a way to register network message handlers which are expected to be removed later. */
public final class ScopedNetworkEventSource extends NetworkEventSource
{
	public final NetworkEventSource parent;
	private boolean isClosed = false;
	
	public ScopedNetworkEventSource(NetworkEventSource parent)
	{
		this.parent = parent;
	}
	
	@Override
	public <T extends PluginChannelMessage> void registerHandler(Class<T> handlerClass, Consumer<T> handlerImplementation)
	{
		if (this.isClosed)
		{
			return;
		}
		
		if (!this.hasHandler(handlerClass))
		{
			this.parent.registerHandler(handlerClass, this::handleMessage);
		}
		
		super.registerHandler(handlerClass, handlerImplementation);
	}
	
	@Override
	public void close()
	{
		this.isClosed = true;
		for (Class<? extends PluginChannelMessage> handlerClass : this.handlers.keySet())
		{
			this.parent.removeHandler(handlerClass, this::handleMessage);
		}
	}
}