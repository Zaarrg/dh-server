package com.seibel.distanthorizons.core.network.messages.plugin;

import com.seibel.distanthorizons.core.network.plugin.PluginChannelMessage;
import com.seibel.distanthorizons.core.network.protocol.INetworkObject;
import io.netty.buffer.ByteBuf;

/**
 * This is not a "real" message, and only used to indicate a disconnection.
 */
public class PluginCloseEvent extends PluginChannelMessage
{
	@Override
	public void encode(ByteBuf out) { throw new UnsupportedOperationException(this.getClass().getSimpleName() + " is not a real message, and cannot be sent."); }
	@Override
	public void decode(ByteBuf in) { throw new UnsupportedOperationException(this.getClass().getSimpleName() + " is not a real message, and cannot be received."); }
	
}