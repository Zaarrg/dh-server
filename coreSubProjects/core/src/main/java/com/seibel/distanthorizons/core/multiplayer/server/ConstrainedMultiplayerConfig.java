package com.seibel.distanthorizons.core.multiplayer.server;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.multiplayer.config.AbstractMultiplayerConfig;
import com.seibel.distanthorizons.core.multiplayer.config.MultiplayerConfig;
import io.netty.buffer.ByteBuf;

/**
 * Used for constraining the client config to the server config.
 */
public class ConstrainedMultiplayerConfig extends AbstractMultiplayerConfig
{
	public MultiplayerConfig clientConfig = new MultiplayerConfig();
	
	@Override
	public int getRenderDistanceRadius()
	{
		return Math.min(this.clientConfig.renderDistanceRadius, Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get());
	}
	
	@Override
	public boolean isDistantGenerationEnabled()
	{
		return this.clientConfig.distantGenerationEnabled && Config.Client.Advanced.WorldGenerator.enableDistantGeneration.get();
	}
	
	@Override
	public int getFullDataRequestConcurrencyLimit()
	{
		return Math.min(this.clientConfig.fullDataRequestConcurrencyLimit, Config.Client.Advanced.Multiplayer.ServerNetworking.generationRequestRCLimit.get());
	}
	
	@Override
	public boolean isRealTimeUpdatesEnabled()
	{
		return this.clientConfig.realTimeUpdatesEnabled && Config.Client.Advanced.Multiplayer.ServerNetworking.enableRealTimeUpdates.get();
	}
	
	@Override
	public boolean isLoginDataSyncEnabled()
	{
		return this.clientConfig.loginDataSyncEnabled && Config.Client.Advanced.Multiplayer.ServerNetworking.enableLoginDataSync.get();
	}
	
	@Override
	public int getLoginDataSyncRCLimit()
	{
		return Math.min(this.clientConfig.loginDataSyncRCLimit, Config.Client.Advanced.Multiplayer.ServerNetworking.loginDataSyncRCLimit.get());
	}
	
	@Override
	public void decode(ByteBuf in)
	{
		throw new UnsupportedOperationException("Decoding is not supported for server-only class.");
	}
	
}