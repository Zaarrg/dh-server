package com.seibel.distanthorizons.common.wrappers.world;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiLevelType;
import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.block.cache.ClientBlockDetailMap;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftClientWrapper;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.level.IKeyedClientLevelManager;
import com.seibel.distanthorizons.core.level.IServerKeyedClientLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IDimensionTypeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

#if MC_VER <= MC_1_20_4
import net.minecraft.world.level.chunk.ChunkStatus;
#else
import net.minecraft.world.level.chunk.status.ChunkStatus;
#endif

public class ClientLevelWrapper implements IClientLevelWrapper
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger(ClientLevelWrapper.class.getSimpleName());
	private static final ConcurrentHashMap<ClientLevel, ClientLevelWrapper> LEVEL_WRAPPER_BY_CLIENT_LEVEL = new ConcurrentHashMap<>();
	private static final IKeyedClientLevelManager KEYED_CLIENT_LEVEL_MANAGER = SingletonInjector.INSTANCE.get(IKeyedClientLevelManager.class);
	
	private final ClientLevel level;
	private final ClientBlockDetailMap blockMap = new ClientBlockDetailMap(this);
	
	private BlockStateWrapper dirtBlockWrapper;
	private BiomeWrapper plainsBiomeWrapper;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	protected ClientLevelWrapper(ClientLevel level) { this.level = level; }
	
	
	
	//===============//
	// wrapper logic //
	//===============//
	
	public static IClientLevelWrapper getWrapper(@NotNull ClientLevel level)
	{
		return getWrapper(level, false);
	}
	
	@Nullable
	public static IClientLevelWrapper getWrapper(@Nullable ClientLevel level, boolean bypassMultiverse)
	{
		if (!bypassMultiverse)
		{
			if (level == null)
			{
				return null;
			}
			
			// used if the client is connected to a server that defines the currently loaded level
			IServerKeyedClientLevel overrideLevel = KEYED_CLIENT_LEVEL_MANAGER.getServerKeyedLevel();
			if (overrideLevel != null)
			{
				return overrideLevel;
			}
		}
		
		return LEVEL_WRAPPER_BY_CLIENT_LEVEL.computeIfAbsent(level, ClientLevelWrapper::new);
	}
	
	@Nullable
	@Override
	public IServerLevelWrapper tryGetServerSideWrapper()
	{
		try
		{
			Iterable<ServerLevel> serverLevels = MinecraftClientWrapper.INSTANCE.mc.getSingleplayerServer().getAllLevels();
			
			// attempt to find the server level with the same dimension type
			// TODO this assumes only one level per dimension type, the SubDimensionLevelMatcher will need to be added for supporting multiple levels per dimension
			ServerLevelWrapper foundLevelWrapper = null;
			
			// TODO: Surely there is a more efficient way to write this code
			for (ServerLevel serverLevel : serverLevels)
			{
				if (serverLevel.dimension() == this.level.dimension())
				{
					foundLevelWrapper = ServerLevelWrapper.getWrapper(serverLevel);
					break;
				}
			}
			
			return foundLevelWrapper;
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to get server side wrapper for client level: " + this.level);
			return null;
		}
	}
	
	//====================//
	// base level methods //
	//====================//
	
	@Override
	public int computeBaseColor(DhBlockPos pos, IBiomeWrapper biome, IBlockStateWrapper blockState)
	{
		return this.blockMap.getColor(((BlockStateWrapper) blockState).blockState, (BiomeWrapper) biome, pos);
	}
	
	@Override
	public int getDirtBlockColor()
	{
		if (this.dirtBlockWrapper == null)
		{
			try
			{
				this.dirtBlockWrapper = (BlockStateWrapper) BlockStateWrapper.deserialize(BlockStateWrapper.DIRT_RESOURCE_LOCATION_STRING, this);
			}
			catch (IOException e)
			{
				// shouldn't happen, but just in case
				LOGGER.warn("Unable to get dirt color with resource location ["+BlockStateWrapper.DIRT_RESOURCE_LOCATION_STRING+"] with level ["+this+"].", e);
				return -1;
			}
		}
		
		return this.blockMap.getColor(this.dirtBlockWrapper.blockState, BiomeWrapper.EMPTY_WRAPPER, DhBlockPos.ZERO);
	}
	
	@Override
	public IBiomeWrapper getPlainsBiomeWrapper()
	{
		if (this.plainsBiomeWrapper == null)
		{
			try
			{
				this.plainsBiomeWrapper = (BiomeWrapper) BiomeWrapper.deserialize(BiomeWrapper.PLAINS_RESOURCE_LOCATION_STRING, this);
			}
			catch (IOException e)
			{
				// shouldn't happen, but just in case
				LOGGER.warn("Unable to get planes biome with resource location ["+BiomeWrapper.PLAINS_RESOURCE_LOCATION_STRING+"] with level ["+this+"].", e);
				return null;
			}
		}
		
		return this.plainsBiomeWrapper;
	}
	
	@Override
	public IDimensionTypeWrapper getDimensionType() { return DimensionTypeWrapper.getDimensionTypeWrapper(this.level.dimensionType()); }
	
	@Override
	public EDhApiLevelType getLevelType() { return EDhApiLevelType.CLIENT_LEVEL; }
	
	public ClientLevel getLevel() { return this.level; }
	
	@Override
	public boolean hasCeiling() { return this.level.dimensionType().hasCeiling(); }
	
	@Override
	public boolean hasSkyLight() { return this.level.dimensionType().hasSkyLight(); }
	
	@Override
	public int getHeight() { return this.level.getHeight(); }
	
	@Override
	public int getMinHeight()
	{
        #if MC_VER < MC_1_17_1
        return 0;
        #else
		return this.level.getMinBuildHeight();
        #endif
	}
	
	@Override
	public IChunkWrapper tryGetChunk(DhChunkPos pos)
	{
		if (!this.level.hasChunk(pos.x, pos.z))
		{
			return null;
		}
		
		ChunkAccess chunk = this.level.getChunk(pos.x, pos.z, ChunkStatus.EMPTY, false);
		if (chunk == null)
		{
			return null;
		}
		
		return new ChunkWrapper(chunk, this.level, this);
	}
	
	@Override
	public boolean hasChunkLoaded(int chunkX, int chunkZ)
	{
		ChunkSource source = this.level.getChunkSource();
		return source.hasChunk(chunkX, chunkZ);
	}
	
	@Override
	public IBlockStateWrapper getBlockState(DhBlockPos pos)
	{
		return BlockStateWrapper.fromBlockState(this.level.getBlockState(McObjectConverter.Convert(pos)), this);
	}
	
	@Override
	public IBiomeWrapper getBiome(DhBlockPos pos) { return BiomeWrapper.getBiomeWrapper(this.level.getBiome(McObjectConverter.Convert(pos)), this); }
	
	@Override
	public ClientLevel getWrappedMcObject() { return this.level; }
	
	@Override
	public void onUnload() { LEVEL_WRAPPER_BY_CLIENT_LEVEL.remove(this.level); }
	
	@Override
	public String toString()
	{
		if (this.level == null)
		{
			return "Wrapped{null}";
		}
		
		return "Wrapped{" + this.level.toString() + "@" + this.getDimensionType().getDimensionName() + "}";
	}
	
}
