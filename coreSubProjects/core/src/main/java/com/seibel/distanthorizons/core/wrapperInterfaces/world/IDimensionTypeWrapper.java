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

package com.seibel.distanthorizons.core.wrapperInterfaces.world;

import com.seibel.distanthorizons.api.interfaces.world.IDhApiDimensionTypeWrapper;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

public interface IDimensionTypeWrapper extends IDhApiDimensionTypeWrapper, IBindable
{
	@Override
	String getDimensionName();
	
	@Override
	boolean hasCeiling();
	
	@Override
	boolean hasSkyLight();
	
	// there's definitely a better way of doing this, but it should work well enough for now
	default boolean isTheEnd() { return this.getDimensionName().equalsIgnoreCase("the_end"); }
	
	double getTeleportationScale(IDimensionTypeWrapper to);
	
}
