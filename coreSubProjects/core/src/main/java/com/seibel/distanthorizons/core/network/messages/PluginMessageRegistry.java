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

package com.seibel.distanthorizons.core.network.messages;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.seibel.distanthorizons.core.network.messages.plugin.CurrentLevelKeyMessage;
import com.seibel.distanthorizons.core.network.messages.plugin.base.CancelMessage;
import com.seibel.distanthorizons.core.network.messages.plugin.base.CloseReasonMessage;
import com.seibel.distanthorizons.core.network.messages.plugin.base.ExceptionMessage;
import com.seibel.distanthorizons.core.network.messages.plugin.fullData.FullDataPartialUpdateMessage;
import com.seibel.distanthorizons.core.network.messages.plugin.fullData.FullDataSourceRequestMessage;
import com.seibel.distanthorizons.core.network.messages.plugin.fullData.FullDataSourceResponseMessage;
import com.seibel.distanthorizons.core.network.messages.plugin.session.RemotePlayerConfigMessage;
import com.seibel.distanthorizons.core.network.plugin.PluginChannelMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class PluginMessageRegistry
{
	public static final PluginMessageRegistry INSTANCE = new PluginMessageRegistry();
	
	private final Map<Integer, Supplier<? extends PluginChannelMessage>> idToSupplier = new HashMap<>();
	private final BiMap<Class<? extends PluginChannelMessage>, Integer> classToId = HashBiMap.create();
	
	
	
	private PluginMessageRegistry()
	{
		// Note: Messages must have parameterless constructors
		
		// When the communication is about to be stopped, either side can send this message
		this.registerMessage(CloseReasonMessage.class, CloseReasonMessage::new);
		
		// Level keys
		this.registerMessage(CurrentLevelKeyMessage.class, CurrentLevelKeyMessage::new);
		
		// Config (for full DH support)
		this.registerMessage(RemotePlayerConfigMessage.class, RemotePlayerConfigMessage::new);
		
		// Requests
		this.registerMessage(CancelMessage.class, CancelMessage::new);
		this.registerMessage(ExceptionMessage.class, ExceptionMessage::new);
		
		// Full data requests & updates
		this.registerMessage(FullDataSourceRequestMessage.class, FullDataSourceRequestMessage::new);
		this.registerMessage(FullDataSourceResponseMessage.class, FullDataSourceResponseMessage::new);
		this.registerMessage(FullDataPartialUpdateMessage.class, FullDataPartialUpdateMessage::new);
	}
	
	
	
	protected <T extends PluginChannelMessage> void registerMessage(Class<T> clazz, Supplier<T> supplier)
	{
		int id = this.idToSupplier.size() + 1;
		this.idToSupplier.put(id, supplier);
		this.classToId.put(clazz, id);
	}
	
	public PluginChannelMessage createMessage(int messageId) throws IllegalArgumentException
	{
		try
		{
			return this.idToSupplier.get(messageId).get();
		}
		catch (NullPointerException e)
		{
			throw new IllegalArgumentException("Invalid message ID: " + messageId);
		}
	}
	
	public int getMessageId(PluginChannelMessage message)
	{
		return this.getMessageId(message.getClass());
	}
	
	public int getMessageId(Class<? extends PluginChannelMessage> messageClass)
	{
		try
		{
			return this.classToId.get(messageClass);
		}
		catch (NullPointerException e)
		{
			throw new IllegalArgumentException("Message does not have ID assigned to it: " + messageClass.getSimpleName());
		}
	}
	
}