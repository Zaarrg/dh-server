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

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.network.messages.PluginMessageRegistry;
import com.seibel.distanthorizons.core.network.messages.plugin.base.CancelMessage;
import com.seibel.distanthorizons.core.network.messages.plugin.base.ExceptionMessage;
import com.seibel.distanthorizons.core.network.messages.plugin.PluginCloseEvent;
import com.seibel.distanthorizons.core.network.plugin.PluginChannelMessage;
import com.seibel.distanthorizons.core.network.plugin.PluginChannelSession;
import com.seibel.distanthorizons.core.network.plugin.TrackableMessage;
import com.seibel.distanthorizons.coreapi.ModInfo;
import io.netty.channel.ChannelException;
import org.apache.logging.log4j.LogManager;

import java.io.InvalidClassException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public abstract class NetworkEventSource
{
	private static final ConfigBasedLogger LOGGER = new ConfigBasedLogger(LogManager.getLogger(),
			() -> Config.Client.Advanced.Logging.logNetworkEvent.get());
	protected final ConcurrentMap<Class<? extends PluginChannelMessage>, Set<Consumer<PluginChannelMessage>>> handlers = new ConcurrentHashMap<>();
	private final ConcurrentMap<Long, FutureResponseData> pendingFutures = new ConcurrentHashMap<>();
	
	protected boolean hasHandler(Class<? extends PluginChannelMessage> handlerClass)
	{
		return this.handlers.containsKey(handlerClass);
	}
	
	
	protected void handleMessage(PluginChannelMessage message)
	{
		boolean handled = false;
		
		Set<Consumer<PluginChannelMessage>> handlerList = this.handlers.get(message.getClass());
		if (handlerList != null)
		{
			for (Consumer<PluginChannelMessage> handler : handlerList)
			{
				handled = true;
				handler.accept(message);
			}
		}
		
		if (message instanceof TrackableMessage)
		{
			TrackableMessage trackableMessage = (TrackableMessage) message;
			FutureResponseData responseData = this.pendingFutures.get(trackableMessage.futureId);
			if (responseData != null)
			{
				handled = true;
				
				if (message instanceof ExceptionMessage)
				{
					responseData.future.completeExceptionally(((ExceptionMessage) message).exception);
				}
				else if (message.getClass() != responseData.responseClass)
				{
					responseData.future.completeExceptionally(new InvalidClassException("Response with invalid type: expected " + responseData.responseClass.getSimpleName() + ", got:" + message));
				}
				else
				{
					responseData.future.complete(trackableMessage);
				}
			}
		}
		
		if (!handled && ModInfo.IS_DEV_BUILD && message.warnWhenUnhandled())
		{
			LOGGER.warn("Unhandled message: " + message);
		}
	}
	
	public <T extends PluginChannelMessage> void registerHandler(Class<T> handlerClass, Consumer<T> handlerImplementation)
	{
		//noinspection unchecked
		this.handlers.computeIfAbsent(handlerClass, missingHandlerClass ->
				{
					// Will throw if the handler class is not found
					if (handlerClass != PluginCloseEvent.class)
					{
						PluginMessageRegistry.INSTANCE.getMessageId(handlerClass);
					}
					return new HashSet<>();
				})
				.add((Consumer<PluginChannelMessage>) handlerImplementation);
	}
	
	protected <T extends PluginChannelMessage> void removeHandler(Class<T> handlerClass, Consumer<T> handlerImplementation)
	{
		this.handlers.computeIfAbsent(handlerClass, missingHandlerClass -> new HashSet<>())
				.remove(handlerImplementation);
	}
	
	
	protected <TResponse extends TrackableMessage> CompletableFuture<TResponse> createRequest(TrackableMessage msg, Class<TResponse> responseClass)
	{
		CompletableFuture<TResponse> responseFuture = new CompletableFuture<>();
		responseFuture.whenComplete((response, throwable) ->
		{
			if (!(throwable instanceof ChannelException))
			{
				this.pendingFutures.remove(msg.futureId);
			}
			
			if (throwable instanceof CancellationException)
			{
				msg.sendResponse(new CancelMessage());
			}
		});
		
		this.pendingFutures.put(msg.futureId, new FutureResponseData(responseClass, responseFuture));
		
		return responseFuture;
	}
	
	protected final void completeAllFuturesExceptionally(Throwable cause)
	{
		for (FutureResponseData responseData : this.pendingFutures.values())
		{
			responseData.future.completeExceptionally(cause);
		}
	}
	
	public void close()
	{
		this.handlers.clear();
		this.completeAllFuturesExceptionally(new ChannelException(this.getClass().getSimpleName() + " is closed."));
	}
	
	private static class FutureResponseData
	{
		public final Class<? extends TrackableMessage> responseClass;
		public final CompletableFuture<TrackableMessage> future;
		
		private <T extends TrackableMessage> FutureResponseData(Class<T> responseClass, CompletableFuture<T> future)
		{
			this.responseClass = responseClass;
			//noinspection unchecked
			this.future = (CompletableFuture<TrackableMessage>) future;
		}
		
	}
	
}