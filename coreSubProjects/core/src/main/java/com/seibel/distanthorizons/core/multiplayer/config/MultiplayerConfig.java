package com.seibel.distanthorizons.core.multiplayer.config;

import com.seibel.distanthorizons.core.config.Config;
import io.netty.buffer.ByteBuf;

public class MultiplayerConfig extends AbstractMultiplayerConfig
{
	// IMPORTANT: Once you added/removed config fields, modify MultiplayerConfigChangeListener accordingly.
	
	public int renderDistanceRadius = Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get();
	@Override public int getRenderDistanceRadius() { return this.renderDistanceRadius; }
	
	public boolean distantGenerationEnabled = Config.Client.Advanced.WorldGenerator.enableDistantGeneration.get();
	@Override public boolean isDistantGenerationEnabled() { return this.distantGenerationEnabled; }
	
	public int fullDataRequestConcurrencyLimit = Config.Client.Advanced.Multiplayer.ServerNetworking.generationRequestRCLimit.get();
	@Override public int getFullDataRequestConcurrencyLimit() { return this.fullDataRequestConcurrencyLimit; }
	
	public boolean realTimeUpdatesEnabled = Config.Client.Advanced.Multiplayer.ServerNetworking.enableRealTimeUpdates.get();
	@Override public boolean isRealTimeUpdatesEnabled() { return this.realTimeUpdatesEnabled; }
	
	public boolean loginDataSyncEnabled = Config.Client.Advanced.Multiplayer.ServerNetworking.enableLoginDataSync.get();
	@Override public boolean isLoginDataSyncEnabled() { return this.loginDataSyncEnabled; }
	
	public int loginDataSyncRCLimit = Config.Client.Advanced.Multiplayer.ServerNetworking.loginDataSyncRCLimit.get();
	@Override public int getLoginDataSyncRCLimit() { return this.loginDataSyncRCLimit; }
	
	
	@Override
	public void decode(ByteBuf in)
	{
		this.renderDistanceRadius = in.readInt();
		this.distantGenerationEnabled = in.readBoolean();
		this.fullDataRequestConcurrencyLimit = in.readInt();
		this.realTimeUpdatesEnabled = in.readBoolean();
		this.loginDataSyncEnabled = in.readBoolean();
		this.loginDataSyncRCLimit = in.readInt();
	}
	
}