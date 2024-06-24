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

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * VANILLA_CHUNKS, <br>
 * API_CHUNKS <br>
 * 
 * @author Builderb0y, James Seibel
 * @version 2023-12-21
 * @since API 2.0.0
 */
public enum EDhApiWorldGeneratorReturnType
{
	/**
	 * when this constant is returned by {@link IDhApiWorldGenerator#getReturnType()},
	 * {@link IDhApiWorldGenerator#generateChunks(int, int, byte, byte, EDhApiDistantGeneratorMode, ExecutorService, Consumer)}
	 * will be used when generating terrain.
	 * 
	 * @since API 2.0.0
	 */
	VANILLA_CHUNKS,
	
	/**
	 * when this constant is returned by {@link IDhApiWorldGenerator#getReturnType()},
	 * {@link IDhApiWorldGenerator#generateApiChunks(int, int, byte, byte, EDhApiDistantGeneratorMode, ExecutorService, Consumer)}
	 * will be used when generating terrain.
	 * 
	 * @since API 2.0.0
	 */
	API_CHUNKS;
	
}
