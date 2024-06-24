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

package com.seibel.distanthorizons.api.enums.worldGeneration;

/**
 * PRE_EXISTING_ONLY <br>
 * SURFACE <br>
 * FEATURES <br><br>
 *
 * In order of fastest to slowest.
 *
 * @author James Seibel
 * @author Leonardo Amato
 * @version 2022-12-10
 * @since API 1.0.0
 */
public enum EDhApiDistantGeneratorMode
{
	// Reminder:
	// when adding items up the API minor version
	// when removing items up the API major version
	
	
	/** Don't generate any new terrain, just generate LODs for already generated chunks. */
	PRE_EXISTING_ONLY((byte) 1),
	
	/*
	 * Not currently implemented <br><br>
	 * 
	 * Only generate the biomes and use biome
	 * grass/foliage color, water color, or ice color
	 * to generate the color. <br>
	 * Doesn't generate height, everything is shown at sea level.
	 */
	//BIOME_ONLY((byte) 2),
	
	/*
	 * Not currently implemented <br><br>
	 * 
	 * Same as BIOME_ONLY, except instead
	 * of always using sea level as the LOD height
	 * different biome types (mountain, ocean, forest, etc.)
	 * use predetermined heights to simulate having height data.
	 */
	//BIOME_ONLY_SIMULATE_HEIGHT((byte) 3),
	
	/**
	 * Generate the world surface,
	 * this does NOT include caves, trees,
	 * or structures.
	 */
	SURFACE((byte) 4),
	
	/**
	 * Generate including structures.
	 * NOTE: This may cause world generation bugs or instability,
	 * since some features can cause concurrentModification exceptions.
	 */
	FEATURES((byte) 5);
	
	
	
	/** The higher the number the more complete the generation is. */
	public final byte complexity;
	
	
	EDhApiDistantGeneratorMode(byte complexity) { this.complexity = complexity; }
	
}
