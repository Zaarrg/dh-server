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

package com.seibel.distanthorizons.api.interfaces.world;

import com.seibel.distanthorizons.api.interfaces.IDhApiUnsafeWrapper;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiLevelType;

/**
 * Can be either a Server or Client level.<br>
 * A level is equivalent to a dimension in vanilla Minecraft.
 *
 * @author James Seibel
 * @version 2022-7-14
 * @since API 1.0.0
 */
public interface IDhApiLevelWrapper extends IDhApiUnsafeWrapper
{
	IDhApiDimensionTypeWrapper getDimensionType();
	
	EDhApiLevelType getLevelType();
	
	boolean hasCeiling();
	
	boolean hasSkyLight();
	
	/** Returns the max block height of the level(?) */
	int getHeight();
	
	/**
	 * Returns the lowest possible block position for the level. <br>
	 * For MC versions before 1.18 this will return 0.
	 */
	default int getMinHeight() { return 0; }
	
}
