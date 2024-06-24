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

package com.seibel.distanthorizons.common.wrappers.world;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.IDimensionTypeWrapper;

import net.minecraft.world.level.dimension.DimensionType;

/**
 * @author James Seibel
 * @version 2022-9-16
 */
public class DimensionTypeWrapper implements IDimensionTypeWrapper
{
	private static final ConcurrentMap<DimensionType, DimensionTypeWrapper> dimensionTypeWrapperMap = new ConcurrentHashMap<>();
	private final DimensionType dimensionType;
	
	public DimensionTypeWrapper(DimensionType dimensionType)
	{
		this.dimensionType = dimensionType;
	}
	
	public static DimensionTypeWrapper getDimensionTypeWrapper(DimensionType dimensionType)
	{
		//first we check if the biome has already been wrapped
		if (dimensionTypeWrapperMap.containsKey(dimensionType) && dimensionTypeWrapperMap.get(dimensionType) != null)
		{
			return dimensionTypeWrapperMap.get(dimensionType);
		}
		
		
		//if it hasn't been created yet, we create it and save it in the map
		DimensionTypeWrapper dimensionTypeWrapper = new DimensionTypeWrapper(dimensionType);
		dimensionTypeWrapperMap.put(dimensionType, dimensionTypeWrapper);
		
		//we return the newly created wrapper
		return dimensionTypeWrapper;
	}
	
	public static void clearMap()
	{
		dimensionTypeWrapperMap.clear();
	}
	
	
	@Override
	public String getDimensionName()
	{
		#if MC_VER >= MC_1_17_1
		return this.dimensionType.effectsLocation().getPath();
		#else // < 1.17.1
		// effectsLocation() is marked as client only, so using the backing field directly
		return dimensionType.effectsLocation.getPath();
		#endif
	}
	
	@Override
	public boolean hasCeiling()
	{
		return this.dimensionType.hasCeiling();
	}
	
	@Override
	public boolean hasSkyLight()
	{
		return this.dimensionType.hasSkyLight();
	}
	
	@Override
	public Object getWrappedMcObject()
	{
		return this.dimensionType;
	}
	
	@Override
	public double getTeleportationScale(IDimensionTypeWrapper to)
	{
		return DimensionType.getTeleportationScale(this.dimensionType, (DimensionType) to.getWrappedMcObject());
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (obj.getClass() != DimensionTypeWrapper.class)
		{
			return false;
		}
		else
		{
			DimensionTypeWrapper other = (DimensionTypeWrapper) obj;
			return other.getDimensionName().equals(this.getDimensionName());
		}
	}
	
	
}
