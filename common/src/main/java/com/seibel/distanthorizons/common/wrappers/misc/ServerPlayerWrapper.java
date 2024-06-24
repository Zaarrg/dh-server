package com.seibel.distanthorizons.common.wrappers.misc;

import com.google.common.collect.MapMaker;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import com.seibel.distanthorizons.coreapi.util.math.Vec3d;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

public class ServerPlayerWrapper implements IServerPlayerWrapper
{
	private static final ConcurrentMap<ServerPlayer, ServerPlayerWrapper>
			serverPlayerWrapperMap = new MapMaker().weakKeys().makeMap();

	private final ServerPlayer serverPlayer;

	public static ServerPlayerWrapper getWrapper(ServerPlayer serverPlayer)
	{
		return serverPlayerWrapperMap.computeIfAbsent(serverPlayer, ServerPlayerWrapper::new);
	}

	private ServerPlayerWrapper(ServerPlayer serverPlayer)
	{
		this.serverPlayer = serverPlayer;
	}
	
	@Override
	public String getName()
	{
		return this.serverPlayer.getName().getString();
	}
	
	@Override
	public IServerLevelWrapper getLevel()
	{
		#if MC_VER < MC_1_20_1
		return ServerLevelWrapper.getWrapper(this.serverPlayer.getLevel());
		#else
		return ServerLevelWrapper.getWrapper(this.serverPlayer.serverLevel());
		#endif
    }
	
	@Override
	public Vec3d getPosition()
	{
		Vec3 position = this.serverPlayer.position();
        return new Vec3d(position.x, position.y, position.z);
    }
	
	@Override
	public int getViewDistance()
	{
		return this.serverPlayer.server.getPlayerList().getViewDistance();
	}
	
	@Override
	public SocketAddress getRemoteAddress()
	{
		#if MC_VER >= MC_1_19_4
		return this.serverPlayer.connection.getRemoteAddress();
		#else // < 1.19.4
		return this.serverPlayer.connection.connection.getRemoteAddress();
		#endif
	}
	
	@Override
	public Object getWrappedMcObject()
	{
		return this.serverPlayer;
    }

    @Override
    public String toString() {
	    return "Wrapped{" + this.serverPlayer.toString() + "}";
    }
}