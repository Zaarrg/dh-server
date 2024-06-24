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

package com.seibel.distanthorizons.core.file.structure;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * Abstract class for determining where LOD data should be saved to.
 *
 * @version 2022-12-17
 */
public abstract class AbstractSaveStructure implements AutoCloseable
{
	public static final String DATABASE_NAME = "DistantHorizons.sqlite";
	
	protected static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	/**
	 * Attempts to return the folder that contains LOD data for the given {@link ILevelWrapper}.
	 * If no appropriate folder exists, one will be created. <br><br>
	 *
	 * This will always return a folder, however that folder may not be the best match
	 * if multiverse support is enabled.
	 */
	public abstract File getLevelFolder(ILevelWrapper wrapper);
	
	/** Will return null if no parent folder exists for the given {@link ILevelWrapper}. */
	public abstract File getRenderCacheFolder(ILevelWrapper world);
	/** Will return null if no parent folder exists for the given {@link ILevelWrapper}. */
	public abstract File getFullDataFolder(ILevelWrapper world);
	
}

