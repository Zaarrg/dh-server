package com.seibel.distanthorizons.core.multiplayer.config;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;

import java.io.Closeable;
import java.util.ArrayList;

@SuppressWarnings({"rawtypes", "unchecked"})
public class MultiplayerConfigChangeListener implements Closeable
{
	private static final ConfigEntry[] CONFIG_ENTRIES = new ConfigEntry[] {
			Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius,
			Config.Client.Advanced.WorldGenerator.enableDistantGeneration,
			Config.Client.Advanced.Multiplayer.ServerNetworking.generationRequestRCLimit,
			Config.Client.Advanced.Multiplayer.ServerNetworking.genTaskPriorityRequestRateLimit,
			Config.Client.Advanced.Multiplayer.ServerNetworking.enableRealTimeUpdates,
			Config.Client.Advanced.Multiplayer.ServerNetworking.enableLoginDataSync,
			Config.Client.Advanced.Multiplayer.ServerNetworking.loginDataSyncRCLimit,
	};
	
	private final ArrayList<ConfigChangeListener> changeListeners;
	
	public MultiplayerConfigChangeListener(Runnable runnable)
	{
		this.changeListeners = new ArrayList<>(CONFIG_ENTRIES.length);
		for (ConfigEntry entry : CONFIG_ENTRIES)
		{
			this.changeListeners.add(new ConfigChangeListener(entry, ignored -> runnable.run()));
		}
	}
	
	@Override
	public void close()
	{
		for (ConfigChangeListener changeListener : this.changeListeners)
		{
			changeListener.close();
		}
		this.changeListeners.clear();
	}
	
}