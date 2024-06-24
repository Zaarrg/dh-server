package com.seibel.distanthorizons.neoforge;

import com.seibel.distanthorizons.common.AbstractModInitializer;
import com.seibel.distanthorizons.common.util.ProxyUtil;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.misc.ServerPlayerWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;
import com.seibel.distanthorizons.core.api.internal.ServerApi;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;

import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

#if MC_VER < MC_1_20_6
import net.neoforged.neoforge.event.TickEvent;
#else
import net.neoforged.neoforge.event.tick.ServerTickEvent;
#endif


public class NeoforgeServerProxy implements AbstractModInitializer.IEventProxy
{
	private static LevelAccessor GetEventLevel(LevelEvent e) { return e.getLevel(); }
	
	private final ServerApi serverApi = ServerApi.INSTANCE;
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private final boolean isDedicated;
	public static Supplier<Boolean> isGenerationThreadChecker = null;
	
	
	//=============//
	// constructor //
	//=============//
	
	public NeoforgeServerProxy(boolean isDedicated)
	{
		this.isDedicated = isDedicated;
		isGenerationThreadChecker = BatchGenerationEnvironment::isCurrentThreadDistantGeneratorThread;
	}
	
	@Override
	public void registerEvents()
	{
		NeoForge.EVENT_BUS.register(this);
	}
	
	
	
	
	//========//
	// events //
	//========//
	
	#if MC_VER < MC_1_20_6
	@SubscribeEvent
	public void serverTickEvent(TickEvent.ServerTickEvent event)
	{
		if (event.phase == TickEvent.Phase.END)
		{
			this.serverApi.serverTickEvent();
		}
	}
	#else
	@SubscribeEvent
	public void serverTickEvent(ServerTickEvent.Post event)
	{
		this.serverApi.serverTickEvent();
	}
	#endif
	
	// ServerWorldLoadEvent
	@SubscribeEvent
	public void dedicatedWorldLoadEvent(ServerAboutToStartEvent event)
	{
		this.serverApi.serverLoadEvent(this.isDedicated);
	}
	
	// ServerWorldUnloadEvent
	@SubscribeEvent
	public void serverWorldUnloadEvent(ServerStoppingEvent event)
	{
		this.serverApi.serverUnloadEvent();
	}
	
	// ServerLevelLoadEvent
	@SubscribeEvent
	public void serverLevelLoadEvent(LevelEvent.Load event)
	{
		if (GetEventLevel(event) instanceof ServerLevel)
		{
			this.serverApi.serverLevelLoadEvent(this.getServerLevelWrapper((ServerLevel) GetEventLevel(event)));
		}
	}
	
	// ServerLevelUnloadEvent
	@SubscribeEvent
	public void serverLevelUnloadEvent(LevelEvent.Unload event)
	{
		if (GetEventLevel(event) instanceof ServerLevel)
		{
			this.serverApi.serverLevelUnloadEvent(this.getServerLevelWrapper((ServerLevel) GetEventLevel(event)));
		}
	}
	
	@SubscribeEvent
	public void serverChunkLoadEvent(ChunkEvent.Load event)
	{
		ILevelWrapper levelWrapper = ProxyUtil.getLevelWrapper(GetEventLevel(event));
		
		IChunkWrapper chunk = new ChunkWrapper(event.getChunk(), GetEventLevel(event), levelWrapper);
		this.serverApi.serverChunkLoadEvent(chunk, levelWrapper);
	}
	@SubscribeEvent
	public void serverChunkSaveEvent(ChunkEvent.Unload event)
	{
		ILevelWrapper levelWrapper = ProxyUtil.getLevelWrapper(GetEventLevel(event));
		
		IChunkWrapper chunk = new ChunkWrapper(event.getChunk(), GetEventLevel(event), levelWrapper);
		this.serverApi.serverChunkSaveEvent(chunk, levelWrapper);
	}
	
	@SubscribeEvent
	public void playerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event)
	{
		this.serverApi.serverPlayerJoinEvent(getServerPlayerWrapper(event));
	}
	@SubscribeEvent
	public void playerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event)
	{
		this.serverApi.serverPlayerDisconnectEvent(getServerPlayerWrapper(event));
	}
	@SubscribeEvent
	public void playerChangedDimensionEvent(PlayerEvent.PlayerChangedDimensionEvent event)
	{
		this.serverApi.serverPlayerLevelChangeEvent(
				getServerPlayerWrapper(event),
				getServerLevelWrapper(event.getFrom(), event),
				getServerLevelWrapper(event.getTo(), event)
		);
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	private static ServerLevelWrapper getServerLevelWrapper(ServerLevel level) { return ServerLevelWrapper.getWrapper(level); }
	private static ServerLevelWrapper getServerLevelWrapper(ResourceKey<Level> resourceKey, PlayerEvent event)
	{
		//noinspection DataFlowIssue (possible NPE after getServer())
		return getServerLevelWrapper(event.getEntity().getServer().getLevel(resourceKey));
	}
	
	private static ServerPlayerWrapper getServerPlayerWrapper(PlayerEvent event)
	{
		return ServerPlayerWrapper.getWrapper((ServerPlayer) event.getEntity());
	}
	
}