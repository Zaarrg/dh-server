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

package com.seibel.distanthorizons.core.wrapperInterfaces.block;

import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;

/** A Minecraft version independent way of handling Blocks. */
public interface IBlockStateWrapper extends IDhApiBlockStateWrapper
{
	//===========//
	// constants //
	//===========//
	
	int FULLY_TRANSPARENT = 0; 
	int FULLY_OPAQUE = 16;
	
	/** contains the indices used by Iris to determine how different block types should be rendered */
	class IrisBlockMaterial
	{
		public static final byte UNKOWN = 0;
		public static final byte LEAVES = 1;
		public static final byte STONE = 2;
		public static final byte WOOD = 3;
		public static final byte METAL = 4;
		public static final byte DIRT = 5;
		public static final byte LAVA = 6;
		public static final byte DEEPSLATE = 7;
		public static final byte SNOW = 8;
		public static final byte SAND = 9;
		public static final byte TERRACOTTA = 10;
		public static final byte NETHER_STONE = 11;
		public static final byte WATER = 12;
		public static final byte GRASS = 13;
		
		/** shouldn't normally be needed, but just in case */
		public static final byte AIR = 14;
		public static final byte ILLUMINATED = 15; // Max value
	}
	
	
	
	
	
	//=========//
	// methods //
	//=========//
	
	String getSerialString();
	
	/**
	 * Returning a value of 0 means the block is completely transparent. <br.
	 * Returning a value of 15 means the block is completely opaque.
	 *
	 * @see IBlockStateWrapper#FULLY_OPAQUE
	 * @see IBlockStateWrapper#FULLY_TRANSPARENT
	 */
	int getOpacity();
	
	int getLightEmission();
	
	byte getIrisBlockMaterialId();
	
}