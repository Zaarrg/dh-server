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

package testItems.worldGeneratorInjection.objects;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiLevelType;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiDimensionTypeWrapper;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;

/**
 * Stub implementation of a Level wrapper for basic unit testing.
 *
 * @author James Seibel
 * @version 2022-9-8
 */
public class LevelWrapperTest implements IDhApiLevelWrapper
{
	@Override
	public Object getWrappedMcObject() { return null; }
	
	@Override
	public IDhApiDimensionTypeWrapper getDimensionType() { return null; }
	
	@Override
	public EDhApiLevelType getLevelType() { return null; }
	
	@Override
	public boolean hasCeiling() { return false; }
	
	@Override
	public boolean hasSkyLight() { return false; }
	
	@Override
	public int getHeight() { return 0; }
	
	@Override
	public int getMinHeight() { return IDhApiLevelWrapper.super.getMinHeight(); }
	
}
