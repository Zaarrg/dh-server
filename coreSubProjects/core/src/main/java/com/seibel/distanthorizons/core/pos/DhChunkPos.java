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

import com.seibel.distanthorizons.coreapi.util.math.Vec3d;

public class DhChunkPos
{
	public final int x; // Low 32 bits
	public final int z; // High 32 bits
	
	/** cached to improve hashing speed */
	public final int hashCode;
	
	
	
	public DhChunkPos(int x, int z)
	{
		this.x = x;
		this.z = z;
		
		// custom hash, 7309 is a random prime
		this.hashCode = this.x * 7309 + this.z;
	}
	public DhChunkPos(DhBlockPos blockPos)
	{
		// >> 4 is the Same as div 16
		this(blockPos.x >> 4, blockPos.z >> 4);
	}
	public DhChunkPos(DhBlockPos2D blockPos)
	{
		// >> 4 is the Same as div 16
		this(blockPos.x >> 4, blockPos.z >> 4);
	}
	public DhChunkPos(Vec3d pos)
	{
		this(((int)pos.x) >> 4, ((int)pos.z) >> 4);
	}
	public DhChunkPos(long packed) { this(getXFromPackedLong(packed), getZFromPackedLong(packed)); }
	
	
	
	public DhBlockPos center() { return new DhBlockPos(8 + this.x << 4, 0, 8 + this.z << 4); }
	public DhBlockPos corner() { return new DhBlockPos(this.x << 4, 0, this.z << 4); }
	
	public static long toLong(int x, int z) { return ((long) x & 0xFFFFFFFFL) << 32 | (long) z & 0xFFFFFFFFL; }
	
	private static int getXFromPackedLong(long chunkPos) { return (int) (chunkPos >> 32); }
	private static int getZFromPackedLong(long chunkPos) { return (int) (chunkPos & 0xFFFFFFFFL); }
	
	public int getMinBlockX() { return this.x << 4; }
	public int getMinBlockZ() { return this.z << 4; }
	
	public DhBlockPos2D getMinBlockPos() { return new DhBlockPos2D(this.x << 4, this.z << 4); }
	
	public long getLong() { return toLong(this.x, this.z); }
	
	public double distance(DhChunkPos other)
	{
		return Math.sqrt(Math.pow(x - other.x, 2) + Math.pow(z - other.z, 2));
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		else if (obj == null || this.getClass() != obj.getClass())
		{
			return false;
		}
		else
		{
			DhChunkPos that = (DhChunkPos) obj;
			return this.x == that.x && this.z == that.z;
		}
	}
	
	@Override
	public int hashCode() { return this.hashCode; }
	
	@Override
	public String toString() { return "C[" + this.x + "," + this.z + "]"; }
	
	
	
	//=======================//
	// static helper methods //
	//=======================//
	
	public static void _DebugCheckPacker(int x, int z, long expected)
	{
		long packed = toLong(x, z);
		if (packed != expected)
		{
			throw new IllegalArgumentException("Packed values don't match: " + packed + " != " + expected);
		}
		
		DhChunkPos pos = new DhChunkPos(packed);
		if (pos.x != x || pos.z != z)
		{
			throw new IllegalArgumentException("Values after decode don't match: " + pos + " != " + x + ", " + z);
		}
	}
	
	/** @return true if testPos is within the area defined by the min and max positions. */
	public static boolean isChunkPosBetween(DhChunkPos minChunkPos, DhChunkPos testPos, DhChunkPos maxChunkPos)
	{
		int minChunkX = Math.min(minChunkPos.x, maxChunkPos.x);
		int minChunkZ = Math.min(minChunkPos.z, maxChunkPos.z);
		
		int maxChunkX = Math.max(minChunkPos.x, maxChunkPos.x);
		int maxChunkZ = Math.max(minChunkPos.z, maxChunkPos.z);
		
		return minChunkX <= testPos.x && testPos.x <= maxChunkX &&
				minChunkZ <= testPos.z && testPos.z <= maxChunkZ;
	}
	
	
}
