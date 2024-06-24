package com.seibel.distanthorizons.core.multiplayer.server;

import com.seibel.distanthorizons.core.level.DhServerLevel;
import com.seibel.distanthorizons.core.multiplayer.config.MultiplayerConfig;
import com.seibel.distanthorizons.core.multiplayer.config.MultiplayerConfigChangeListener;
import com.seibel.distanthorizons.core.network.messages.plugin.CurrentLevelKeyMessage;
import com.seibel.distanthorizons.core.network.messages.plugin.session.RemotePlayerConfigMessage;
import com.seibel.distanthorizons.core.network.messages.plugin.PluginCloseEvent;
import com.seibel.distanthorizons.core.network.exceptions.RateLimitedException;
import com.seibel.distanthorizons.core.network.messages.plugin.fullData.FullDataSourceRequestMessage;
import com.seibel.distanthorizons.core.network.plugin.PluginChannelSession;
import com.seibel.distanthorizons.core.util.ratelimiting.SupplierBasedRateAndConcurrencyLimiter;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;

import static com.seibel.distanthorizons.core.config.Config.Client.Advanced.Multiplayer.ServerNetworking;

public class ServerPlayerState
{
	public final PluginChannelSession session;
	public IServerPlayerWrapper serverPlayer() { return this.session.serverPlayer; }
	
	@NotNull
	public ConstrainedMultiplayerConfig config = new ConstrainedMultiplayerConfig();
	private final MultiplayerConfigChangeListener configChangeListener = new MultiplayerConfigChangeListener(this::onConfigChanged);
	private String lastLevelKey = "";
	
	private final ConcurrentHashMap<DhServerLevel, RateLimiterSet> rateLimiterSets = new ConcurrentHashMap<>();
	public RateLimiterSet getRateLimiterSet(DhServerLevel level)
	{
		return this.rateLimiterSets.computeIfAbsent(level, ignored -> new RateLimiterSet());
	}
	public void clearRateLimiterSets()
	{
		this.rateLimiterSets.clear();
	}
	
	
	
	public ServerPlayerState(IServerPlayerWrapper serverPlayer)
	{
		this.session = new PluginChannelSession(serverPlayer);
		
		this.session.registerHandler(RemotePlayerConfigMessage.class, remotePlayerConfigMessage ->
		{
			this.config.clientConfig = (MultiplayerConfig) remotePlayerConfigMessage.payload;
			
			if (ServerNetworking.sendLevelKeys.get())
			{
				String levelKeyPrefix = ServerNetworking.levelKeyPrefix.get();
				String dimensionName = serverPlayer.getLevel().getDimensionType().getDimensionName();
				
				String levelKey;
				if (!levelKeyPrefix.isEmpty())
				{
					levelKey = levelKeyPrefix + "_" + dimensionName;
				}
				else
				{
					levelKey = dimensionName;
				}
				
				if (!levelKey.equals(this.lastLevelKey))
				{
					this.lastLevelKey = levelKey;
					this.session.sendMessage(new CurrentLevelKeyMessage(levelKey));
				}
			}
			
			this.session.sendMessage(new RemotePlayerConfigMessage(this.config));
		});
		
		this.session.registerHandler(PluginCloseEvent.class, event -> {
			// Noop
		});
	}
	
	
	
	
	private void onConfigChanged()
	{
		this.session.sendMessage(new RemotePlayerConfigMessage(this.config));
	}
	
	public void close()
	{
		this.configChangeListener.close();
		this.session.close();
	}
	
	
	
	public class RateLimiterSet
	{
		public final SupplierBasedRateAndConcurrencyLimiter<FullDataSourceRequestMessage> fullDataRequestConcurrencyLimiter = new SupplierBasedRateAndConcurrencyLimiter<>(
				() -> ServerNetworking.generationRequestRCLimit.get(),
				msg -> {
					msg.sendResponse(new RateLimitedException("Full data request rate/concurrency limit: " + ServerPlayerState.this.config.getFullDataRequestConcurrencyLimit()));
				}
		);
		
		public final SupplierBasedRateAndConcurrencyLimiter<FullDataSourceRequestMessage> loginDataSyncRCLimiter = new SupplierBasedRateAndConcurrencyLimiter<>(
				() -> ServerNetworking.loginDataSyncRCLimit.get(),
				msg -> {
					msg.sendResponse(new RateLimitedException("Data sync rate/concurrency limit: " + ServerPlayerState.this.config.getLoginDataSyncRCLimit()));
				}
		);
		
	}
	
}