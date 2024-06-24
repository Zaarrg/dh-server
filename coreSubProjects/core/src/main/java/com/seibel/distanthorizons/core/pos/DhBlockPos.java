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

package com.seibel.distanthorizons.core.pos;

import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.util.LodUtil;

import org.jetbrains.annotations.Nullable;
import java.util.Objects;

public class DhBlockPos
{
	public static final boolean DO_CHECKS = false;
	
	// 26 bits wide as that just encompasses the maximum possible value
	// of +- 30,000,000 blocks in each direction. Yes this packing method
	// is how Minecraft packs it.
	
	// NOTE: Remember to ALWAYS check that DHBlockPos packing is EXACTLY
	// the same as Minecraft's!!!!
	public static final int PACKED_X_LENGTH = 26;
	public static final int PACKED_Z_LENGTH = 26;
	public static final int PACKED_Y_LENGTH = 12;
	public static final long PACKED_X_MASK = (1L << PACKED_X_LENGTH) - 1L;
	public static final long PACKED_Y_MASK = (1L << PACKED_Y_LENGTH) - 1L;
	public static final long PACKED_Z_MASK = (1L << PACKED_Z_LENGTH) - 1L;
	public static final int PACKED_Y_OFFSET = 0;
	public static final int PACKED_Z_OFFSET = PACKED_Y_LENGTH;
	public static final int PACKED_X_OFFSET = PACKED_Y_LENGTH + PACKED_Z_LENGTH;
	
	/** Useful for methods that need a position passed in but won't actually be used */
	public static final DhBlockPos ZERO = new DhBlockPos(0, 0, 0);
	
	
	public int x;
	public int y;
	public int z;
	
	
	
	public DhBlockPos(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	public DhBlockPos()
	{
		this(0, 0, 0);
	}
	public DhBlockPos(DhBlockPos pos)
	{
		this(pos.x, pos.y, pos.z);
	}
	
	public DhBlockPos(DhBlockPos2D pos, int y)
	{
		this(pos.x, y, pos.z);
	}
	
	public static long asLong(int x, int y, int z)
	{
		if (DO_CHECKS)
		{
			if ((x & ~PACKED_X_MASK) != 0)
			{
				throw new IllegalArgumentException("x is out of range: " + x);
			}
			if ((y & ~PACKED_Y_MASK) != 0)
			{
				throw new IllegalArgumentException("y is out of range: " + y);
			}
			if ((z & ~PACKED_Z_MASK) != 0)
			{
				throw new IllegalArgumentException("z is out of range: " + z);
			}
		}
		return ((long) x & PACKED_X_MASK) << PACKED_X_OFFSET |
				((long) y & PACKED_Y_MASK) << PACKED_Y_OFFSET |
				((long) z & PACKED_Z_MASK) << PACKED_Z_OFFSET;
	}
	
	public static int getX(long packed)
	{ // X is at the top
		return (int) (packed << (64 - PACKED_X_OFFSET - PACKED_X_LENGTH) >> (64 - PACKED_X_LENGTH));
	}
	public static int getY(long packed)
	{ // Y is at the bottom
		return (int) (packed << (64 - PACKED_Y_OFFSET - PACKED_Y_LENGTH) >> (64 - PACKED_Y_LENGTH));
	}
	public static int getZ(long packed)
	{ // Z is at the middle
		return (int) (packed << (64 - PACKED_Z_OFFSET - PACKED_Z_LENGTH) >> (64 - PACKED_Z_LENGTH));
	}
	public DhBlockPos(long packed)
	{
		this(getX(packed), getY(packed), getZ(packed));
	}
	
	public long asLong()
	{
		return asLong(x, y, z);
	}
	
	/** creates a new {@link DhBlockPos} with the given offset from the current pos. */
	public DhBlockPos offset(EDhDirection direction) { return this.mutateOffset(direction, null); }
	/** if not null, mutates "mutablePos" so it matches the current pos after being offset. Otherwise creates a new {@link DhBlockPos}. */
	public DhBlockPos mutateOffset(EDhDirection direction, @Nullable DhBlockPos mutablePos) { return this.mutateOffset(direction.getNormal().x, direction.getNormal().y, direction.getNormal().z, mutablePos); }
	
	public DhBlockPos offset(int x, int y, int z) { return this.mutateOffset(x,y,z, null); }
	public DhBlockPos mutateOffset(int x, int y, int z, @Nullable DhBlockPos mutablePos) 
	{
		int newX = this.x + x;
		int newY = this.y + y;
		int newZ = this.z + z;
		
		if (mutablePos != null)
		{
			mutablePos.x = newX;
			mutablePos.y = newY;
			mutablePos.z = newZ;
			
			return mutablePos;
		}
		else
		{
			return new DhBlockPos(newX, newY, newZ);
		}
	}
	
	/** Returns a new {@link DhBlockPos} limits to a value between 0 and 15 (inclusive) */
	public DhBlockPos convertToChunkRelativePos() { return this.mutateToChunkRelativePos(null); }
	/** 
	 * Limits the block position to a value between 0 and 15 (inclusive) 
	 * If not null, mutates "mutableBlockPos" 
	 */
	public DhBlockPos mutateToChunkRelativePos(@Nullable DhBlockPos mutableBlockPos)
	{
		// move the position into the range -15 and +15
		int relX = (this.x % LodUtil.CHUNK_WIDTH);
		// if the position is negative move it into the range 0 and 15
		relX = (relX < 0) ? (relX + LodUtil.CHUNK_WIDTH) : relX;
		
		int relZ = (this.z % LodUtil.CHUNK_WIDTH);
		relZ = (relZ < 0) ? (relZ + LodUtil.CHUNK_WIDTH) : relZ;
		
		// the y value shouldn't need to be changed
		
		
		if (mutableBlockPos != null)
		{
			mutableBlockPos.x = relX;
			mutableBlockPos.y = this.y;
			mutableBlockPos.z = relZ;
			
			return mutableBlockPos;
		}
		else
		{
			return new DhBlockPos(relX, this.y, relZ);
		}
	}
	
	/**
	 * Can be used to quickly determine the rough distance between two points<Br>
	 * or determine the taxi cab (manhattan) distance between two points. <Br><Br>
	 *
	 * Manhattan distance is equivalent to determining the distance between two street intersections,
	 * where you can only drive along each street, instead of directly to the other point.
	 */
	public int getManhattanDistance(DhBlockPos otherPos)
	{
		return Math.abs(this.x - otherPos.x) + Math.abs(this.y - otherPos.y) + Math.abs(this.z - otherPos.z);
	}
	
	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		DhBlockPos that = (DhBlockPos) o;
		return x == that.x && y == that.y && z == that.z;
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hash(x, y, z);
	}
	@Override
	public String toString()
	{
		return "DHBlockPos[" +
				"" + x +
				", " + y +
				", " + z +
				']';
	}
	
	public static void _DebugCheckPacker(int x, int y, int z, long expected)
	{
		long packed = asLong(x, y, z);
		if (packed != expected)
		{
			throw new IllegalArgumentException("Packed values don't match: " + packed + " != " + expected);
		}
		DhBlockPos pos = new DhBlockPos(packed);
		if (pos.x != x || pos.y != y || pos.z != z)
		{
			throw new IllegalArgumentException("Values after decode don't match: " + pos + " != " + x + ", " + y + ", " + z);
		}
	}
	
}
