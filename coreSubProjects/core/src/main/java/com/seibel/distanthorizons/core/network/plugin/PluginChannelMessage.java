package com.seibel.distanthorizons.core.network.plugin;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.core.network.protocol.INetworkObject;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;

public abstract class PluginChannelMessage implements INetworkObject
{
	public PluginChannelSession session = null;
	public IServerPlayerWrapper serverPlayer() { return this.session.serverPlayer; }
	
	public boolean warnWhenUnhandled() { return true; }
	
	public PluginChannelSession getConnection()
	{
		return this.session;
	}
	
	public void setSession(PluginChannelSession connection)
	{
		if (this.session != null)
		{
			throw new IllegalStateException("Session object cannot be changed after initialization.");
		}
		this.session = connection;
	}
	
	
	@Override
	public String toString()
	{
		return this.toStringHelper().toString();
	}
	
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return MoreObjects.toStringHelper(this);
	}
	
}