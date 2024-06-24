package com.seibel.distanthorizons.core.network.messages.plugin;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.core.network.plugin.PluginChannelMessage;
import io.netty.buffer.ByteBuf;

public class CurrentLevelKeyMessage extends PluginChannelMessage
{
	public String levelKey;
	
	public CurrentLevelKeyMessage() { }
	public CurrentLevelKeyMessage(String levelKey)
	{
		this.levelKey = levelKey;
	}
	
	@Override
	public void encode(ByteBuf out)
	{
		this.writeString(this.levelKey, out);
	}
	
	@Override
	public void decode(ByteBuf in)
	{
		this.levelKey = this.readString(in);
	}
	
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("levelKey", this.levelKey);
	}
	
}