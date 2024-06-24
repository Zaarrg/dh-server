package com.seibel.distanthorizons.core.wrapperInterfaces.misc;

import com.seibel.distanthorizons.core.network.plugin.PluginChannelMessage;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;
import io.netty.buffer.ByteBuf;

import java.util.function.Consumer;

public interface IPluginPacketSender extends IBindable
{
	void sendPluginPacketClient(PluginChannelMessage message);
	void sendPluginPacketServer(IServerPlayerWrapper serverPlayer, PluginChannelMessage message);
	
}