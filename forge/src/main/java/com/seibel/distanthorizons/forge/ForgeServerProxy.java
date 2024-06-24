package com.seibel.distanthorizons.forge;

import com.seibel.distanthorizons.common.AbstractModInitializer;
import com.seibel.distanthorizons.common.util.ProxyUtil;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.misc.ServerPlayerWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;
import com.seibel.distanthorizons.core.api.internal.ServerApi;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
#if MC_VER < MC_1_19_2
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
#else
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
#endif
import net.minecraftforge.eventbus.api.SubscribeEvent;

#if MC_VER >= MC_1_19_4
import net.minecraft.core.registries.Registries;
#else // < 1.19.4
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
#endif

#if MC_VER == MC_1_16_5
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
#elif MC_VER == MC_1_17_1
import net.minecraftforge.fmlserverevents.FMLServerAboutToStartEvent;
import net.minecraftforge.fmlserverevents.FMLServerStoppingEvent;
#else
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
#endif


import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

public class ForgeServerProxy implements AbstractModInitializer.IEventProxy
{
	#if MC_VER < MC_1_19_2
	private static LevelAccessor GetEventLevel(WorldEvent e) { return e.getWorld(); }
	#else
	private static LevelAccessor GetEventLevel(LevelEvent e) { return e.getLevel(); }
    #endif
	
	private final ServerApi serverApi = ServerApi.INSTANCE;
	private final boolean isDedicated;
	public static Supplier<Boolean> isGenerationThreadChecker = null;
	
	
	
	@Override
	public void registerEvents()
	{
		MinecraftForge.EVENT_BUS.register(this);
		if (this.isDedicated)
		{
			ForgePluginPacketSender.setPacketHandler(ServerApi.INSTANCE::pluginMessageReceived);
		}
	}
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ForgeServerProxy(boolean isDedicated)
	{
		this.isDedicated = isDedicated;
		isGenerationThreadChecker = BatchGenerationEnvironment::isCurrentThreadDistantGeneratorThread;
	}
	
	
	
	//========//
	// events //
	//========//
	
	// ServerTickEvent (at end)
	@SubscribeEvent
	public void serverTickEvent(TickEvent.ServerTickEvent event)
	{
		if (event.phase == TickEvent.Phase.END)
		{
			this.serverApi.serverTickEvent();
		}
	}
	
	// ServerWorldLoadEvent
	@SubscribeEvent
	public void dedicatedWorldLoadEvent(#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1 FMLServerAboutToStartEvent #else ServerAboutToStartEvent #endif event)
	{
		this.serverApi.serverLoadEvent(this.isDedicated);
	}
	
	// ServerWorldUnloadEvent
	@SubscribeEvent
	public void serverWorldUnloadEvent(#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1 FMLServerStoppingEvent #else ServerStoppingEvent #endif event)
	{
		this.serverApi.serverUnloadEvent();
	}
	
	// ServerLevelLoadEvent
	@SubscribeEvent
	#if MC_VER < MC_1_19_2
	public void serverLevelLoadEvent(WorldEvent.Load event)
	#else
	public void serverLevelLoadEvent(LevelEvent.Load event)
	#endif
	{
		if (GetEventLevel(event) instanceof ServerLevel)
		{
			this.serverApi.serverLevelLoadEvent(getServerLevelWrapper((ServerLevel) GetEventLevel(event)));
		}
	}
	
	// ServerLevelUnloadEvent
	@SubscribeEvent
	#if MC_VER < MC_1_19_2
	public void serverLevelUnloadEvent(WorldEvent.Unload event)
	#else
	public void serverLevelUnloadEvent(LevelEvent.Unload event)
    #endif
	{
		if (GetEventLevel(event) instanceof ServerLevel)
		{
			this.serverApi.serverLevelUnloadEvent(getServerLevelWrapper((ServerLevel) GetEventLevel(event)));
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
	
	private static ServerPlayerWrapper getServerPlayerWrapper(PlayerEvent event) {
		return ServerPlayerWrapper.getWrapper(
				#if MC_VER >= MC_1_19_2
				(ServerPlayer) event.getEntity()
				#else
				(ServerPlayer) event.getPlayer()
				#endif
		);
	}
	
}
