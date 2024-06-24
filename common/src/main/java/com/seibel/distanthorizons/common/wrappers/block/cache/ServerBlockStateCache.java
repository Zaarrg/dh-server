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

package com.seibel.distanthorizons.common.wrappers.block.cache;

import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhBlockPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

/**
 * @version 2022-9-16
 */
public class ServerBlockStateCache
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public final BlockState state;
	public final LevelReader level;
	public final BlockPos pos;
	
	public ServerBlockStateCache(BlockState blockState, ILevelWrapper samplingLevel, DhBlockPos samplingPos)
	{
		state = blockState;
		level = (LevelReader) samplingLevel.getWrappedMcObject();
		pos = McObjectConverter.Convert(samplingPos);
		resolveShapes();
		//LOGGER.info("ServerBlockState created for {}", blockState);
	}
	
	boolean noCollision = false;
	boolean[] occludeFaces = null;
	boolean[] fullFaces = null;
	boolean isShapeResolved = false;
	public void resolveShapes()
	{
		if (isShapeResolved) return;
		if (state.getFluidState().isEmpty())
		{
			noCollision = state.getCollisionShape(level, pos).isEmpty();
			occludeFaces = new boolean[6];
			if (state.canOcclude())
			{
				for (Direction dir : Direction.values())
				{
					// Note: isEmpty() isn't quite correct... best would be a isFull() or something...
					occludeFaces[McObjectConverter.Convert(dir).ordinal()]
							= !state.getFaceOcclusionShape(level, pos, dir).isEmpty();
				}
			}
			
			VoxelShape voxelShape = state.getShape(level, pos);
			fullFaces = new boolean[6];
			if (!voxelShape.isEmpty())
			{
				for (Direction dir : Direction.values())
				{
					VoxelShape faceShape = voxelShape.getFaceShape(dir);
					AABB aabb = faceShape.bounds();
					boolean xFull = aabb.minX <= 0.01 && aabb.maxX >= 0.99;
					boolean yFull = aabb.minY <= 0.01 && aabb.maxY >= 0.99;
					boolean zFull = aabb.minZ <= 0.01 && aabb.maxZ >= 0.99;
					fullFaces[McObjectConverter.Convert(dir).ordinal()] =
							(xFull || dir.getAxis().equals(Direction.Axis.X))
									&& (yFull || dir.getAxis().equals(Direction.Axis.Y))
									&& (zFull || dir.getAxis().equals(Direction.Axis.Z));
				}
			}
		}
		else
		{ // Liquid Block. Treat as full block
			occludeFaces = new boolean[6];
			Arrays.fill(occludeFaces, true);
			fullFaces = new boolean[6];
			Arrays.fill(fullFaces, true);
		}
	}
	
}
