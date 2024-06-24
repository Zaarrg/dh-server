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

package com.seibel.distanthorizons.api.interfaces.config.client;

import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.enums.config.EDhApiServerFolderNameMode;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigGroup;

/**
 * Distant Horizons' client-side multiplayer configuration.
 *
 * @author James Seibel
 * @version 2023-6-14
 * @since API 1.0.0
 */
public interface IDhApiMultiplayerConfig extends IDhApiConfigGroup
{
	
	/**
	 * Defines how multiplayer server folders are named. <br>
	 * Note: Changing this while connected to a multiplayer world will cause undefined behavior!
	 */
	IDhApiConfigValue<EDhApiServerFolderNameMode> folderSavingMode();
	
	/**
	 * Defines the necessary similarity (as a percent) that two potential levels
	 * need in order to be considered the same. <br> <br>
	 *
	 * Setting this to zero causes every level of a specific dimension type to be considered
	 * the same level. <br>
	 * Setting this to a non-zero value allows for usage in servers that user Multiverse
	 * or similar mods.
	 */
	IDhApiConfigValue<Double> multiverseSimilarityRequirement();
	
}
