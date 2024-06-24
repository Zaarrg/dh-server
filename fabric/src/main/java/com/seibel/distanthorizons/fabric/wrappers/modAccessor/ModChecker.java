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

import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModChecker;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;

public class ModChecker implements IModChecker
{
	public static final ModChecker INSTANCE = new ModChecker();
	
	@Override
	public boolean isModLoaded(String modid)
	{
		return FabricLoader.getInstance().isModLoaded(modid);
	}
	
	@Override
	public File modLocation(String modid)
	{
		return new File(FabricLoader.getInstance().getModContainer(modid).get().getOrigin().getPaths().get(0).toUri());
	}
	
}
