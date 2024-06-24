package com.seibel.distanthorizons.core.multiplayer.client;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.multiplayer.config.MultiplayerConfig;
import com.seibel.distanthorizons.core.multiplayer.config.MultiplayerConfigChangeListener;
import com.seibel.distanthorizons.core.network.ScopedNetworkEventSource;
import com.seibel.distanthorizons.core.network.messages.plugin.PluginCloseEvent;
import com.seibel.distanthorizons.core.network.messages.plugin.session.RemotePlayerConfigMessage;
import com.seibel.distanthorizons.core.network.plugin.PluginChannelSession;
import org.apache.logging.log4j.LogManager;

import java.io.Closeable;

public class ClientNetworkState implements Closeable
{
	protected static final ConfigBasedLogger LOGGER = new ConfigBasedLogger(LogManager.getLogger(),
			() -> Config.Client.Advanced.Logging.logNetworkEvent.get());
	
	private final PluginChannelSession session = new PluginChannelSession(null);
	private EServerSupportStatus serverSupportStatus = EServerSupportStatus.NONE;
	
	
	public MultiplayerConfig config = new MultiplayerConfig();
	private volatile boolean configReceived = false;
	private final MultiplayerConfigChangeListener configChangeListener = new MultiplayerConfigChangeListener(this::sendConfigMessage);
	public boolean isReady() { return this.configReceived; }
	
	private final F3Screen.NestedMessage f3Message = new F3Screen.NestedMessage(this::f3Log);
	
	/**
	 * Returns the client used by this instance. <p>
	 * If you need to subscribe to any packet events, create an instance of {@link ScopedNetworkEventSource} using the returned instance.
	 */
	public PluginChannelSession getSession() { return this.session; }
	
	/**
	 * Constructs a new instance.
	 */
	public ClientNetworkState()
	{
		this.session.registerHandler(RemotePlayerConfigMessage.class, msg ->
		{
			this.serverSupportStatus = EServerSupportStatus.FULL;
			
			LOGGER.info("Connection config has been changed: " + msg.payload);
			this.config = (MultiplayerConfig) msg.payload;
			this.configReceived = true;
		});
		
		this.session.registerHandler(PluginCloseEvent.class, msg ->
		{
			this.configReceived = false;
		});
	}
	
	public void sendConfigMessage()
	{
		this.configReceived = false;
		this.getSession().sendMessage(new RemotePlayerConfigMessage(new MultiplayerConfig()));
	}
	
	private String[] f3Log()
	{
		if (this.session.isClosed())
		{
			return new String[]{
					"Session closed: " + this.session.getCloseReason().getMessage()
			};
		}
		
		return new String[]{
				this.serverSupportStatus.message
		};
	}
	
	@Override
	public void close()
	{
		this.f3Message.close();
		this.configChangeListener.close();
		this.session.close();
	}
	
	private enum EServerSupportStatus
	{
		NONE("Server does not support DH"),
		LEVELS_ONLY("Server supports shared level keys"),
		FULL("Server has full DH support");
		
		public final String message;
		
		EServerSupportStatus(String message)
		{
			this.message = message;
		}
	}
}