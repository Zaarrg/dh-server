/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020-2023 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.fabric.wrappers.modAccessor;

import java.util.HashSet;
import java.util.stream.Collectors;

import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.coreapi.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.ISodiumAccessor;


import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Minecraft;
#if MC_VER < MC_1_17_1
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
#else
import net.minecraft.world.level.LevelHeightAccessor;
#endif

public class SodiumAccessor implements ISodiumAccessor
{
	private final IWrapperFactory factory = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
	private final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);

	public IClientLevelWrapper levelWrapper;
	public Mat4f mcModelViewMatrix;
	public Mat4f mcProjectionMatrix;
	public float partialTicks;

	@Override
	public String getModName()
	{
		return "Sodium-Fabric";
	}

	#if MC_VER >= MC_1_17_1
	@Override
	public HashSet<DhChunkPos> getNormalRenderedChunks()
	{
		SodiumWorldRenderer renderer = SodiumWorldRenderer.instance();
		LevelHeightAccessor height = Minecraft.getInstance().level;

		#if MC_VER >= MC_1_20_1
		// TODO: This is just a tmp solution, use a proper solution later
		return MC_RENDER.getMaximumRenderedChunks().stream().filter((DhChunkPos chunk) -> {
			return (renderer.isBoxVisible(
					chunk.getMinBlockX() + 1, height.getMinBuildHeight() + 1, chunk.getMinBlockZ() + 1,
					chunk.getMinBlockX() + 15, height.getMaxBuildHeight() - 1, chunk.getMinBlockZ() + 15));
		}).collect(Collectors.toCollection(HashSet::new));
		#elif MC_VER >= MC_1_18_2
		// 0b11 = Lighted chunk & loaded chunk
		return renderer.getChunkTracker().getChunks(0b00).filter(
				(long l) -> {
					return true;
				}).mapToObj(DhChunkPos::new).collect(Collectors.toCollection(HashSet::new));
		#else
		// TODO: Maybe use a mixin to make this more efficient, and maybe ignore changes behind the camera
		return MC_RENDER.getMaximumRenderedChunks().stream().filter((DhChunkPos chunk) -> {
			return (renderer.isBoxVisible(
					chunk.getMinBlockX() + 1, height.getMinBuildHeight() + 1, chunk.getMinBlockZ() + 1,
					chunk.getMinBlockX() + 15, height.getMaxBuildHeight() - 1, chunk.getMinBlockZ() + 15));
		}).collect(Collectors.toCollection(HashSet::new));
		#endif
	}
	#else
	@Override
	public HashSet<DhChunkPos> getNormalRenderedChunks() {
		SodiumWorldRenderer renderer = SodiumWorldRenderer.getInstance();
		LevelAccessor height = Minecraft.getInstance().level;
		// TODO: Maybe use a mixin to make this more efficient
		return MC_RENDER.getMaximumRenderedChunks().stream().filter((DhChunkPos chunk) -> {
			FakeChunkEntity AABB = new FakeChunkEntity(chunk.x, chunk.z, height.getMaxBuildHeight());
			return (renderer.isEntityVisible(AABB));
		}).collect(Collectors.toCollection(HashSet::new));
	}

	private static class FakeChunkEntity extends Entity {
		public int cx;
		public int cz;
		public int my;
		public FakeChunkEntity(int chunkX, int chunkZ, int maxHeight) {
			super(EntityType.AREA_EFFECT_CLOUD, null);
			cx = chunkX;
			cz = chunkZ;
			my = maxHeight;
		}
		@Override
		public AABB getBoundingBoxForCulling() {
			return new AABB(cx*16+1, 1, cz*16+1,
					cx*16+15, my-1, cz*16+15);
		}
		@Override
		protected void defineSynchedData() {}
		@Override
		protected void readAdditionalSaveData(CompoundTag paramCompoundTag) {}
		@Override
		protected void addAdditionalSaveData(CompoundTag paramCompoundTag) {}
		@Override
		public Packet<?> getAddEntityPacket() {
			throw new UnsupportedOperationException("This is a FAKE CHUNK ENTITY... For tricking the Sodium to check a AABB.");
		}
	}
	#endif

	/** A temporary overwrite for a config in sodium 0.5 to fix their terrain from showing, will be removed once a proper fix is added */
	// FIXME
	@Override
	public void setFogOcclusion(boolean b)
	{
		#if MC_VER >= MC_1_20_1
		me.jellysquid.mods.sodium.client.SodiumClientMod.options().performance.useFogOcclusion = b;
		#endif
	}

}
