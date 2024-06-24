package com.seibel.distanthorizons.core.multiplayer.config;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.core.network.protocol.INetworkObject;
import io.netty.buffer.ByteBuf;

public abstract class AbstractMultiplayerConfig implements INetworkObject
{
	public abstract int getRenderDistanceRadius();
	public abstract boolean isDistantGenerationEnabled();
	public abstract int getFullDataRequestConcurrencyLimit();
	public abstract boolean isRealTimeUpdatesEnabled();
	public abstract boolean isLoginDataSyncEnabled();
	public abstract int getLoginDataSyncRCLimit();
	
	@Override
	public void encode(ByteBuf out)
	{
		out.writeInt(this.getRenderDistanceRadius());
		out.writeBoolean(this.isDistantGenerationEnabled());
		out.writeInt(this.getFullDataRequestConcurrencyLimit());
		out.writeBoolean(this.isRealTimeUpdatesEnabled());
		out.writeBoolean(this.isLoginDataSyncEnabled());
		out.writeInt(this.getLoginDataSyncRCLimit());
	}
	
	
	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("renderDistanceRadius", this.getRenderDistanceRadius())
				.add("distantGenerationEnabled", this.isDistantGenerationEnabled())
				.add("fullDataRequestConcurrencyLimit", this.getFullDataRequestConcurrencyLimit())
				.add("realTimeUpdatesEnabled", this.isRealTimeUpdatesEnabled())
				.add("loginDataSyncEnabled", this.isLoginDataSyncEnabled())
				.add("loginDataSyncRCLimit", this.getLoginDataSyncRCLimit())
				.toString();
	}
	
}