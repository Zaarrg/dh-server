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

package com.seibel.distanthorizons.core.util.gridList;

import com.seibel.distanthorizons.core.pos.Pos2D;
import com.seibel.distanthorizons.core.util.objects.BoolType;

import java.util.Iterator;
import java.util.function.IntPredicate;

public class EdgeDistanceBooleanGrid extends PosArrayGridList<BoolType>
{
	ArrayGridList<Integer> edgeCache = null;
	
	public EdgeDistanceBooleanGrid(Iterator<Pos2D> posIter, int offsetX, int offsetY, int gridSize)
	{
		super(gridSize, offsetX, offsetY);
		while (posIter.hasNext())
		{
			Pos2D p = posIter.next();
			this.set(p, BoolType.TRUE);
		}
	}
	
	// Return false if it is indeed updated
	private static boolean updatePos(ArrayGridList<Integer> grid, int ox, int oy)
	{
		if (grid.get(ox, oy) < 0) return true;
		if (ox == 0 || oy == 0 || ox == grid.gridSize - 1 || oy == grid.gridSize - 1)
		{
			return true;
		}
		
		int v = grid.get(ox, oy);
		if (
				grid.get(ox, oy + 1) < v ||
						grid.get(ox, oy - 1) < v ||
						grid.get(ox + 1, oy) < v ||
						grid.get(ox - 1, oy) < v
		)
		{
			return true;
		}
		else
		{
			grid.set(ox, oy, v + 1);
			return false;
		}
	}
	
	//FIXME: This is slow and expensive. Use queue to make this skip recheck done pos
	private void computeEdgeCache()
	{
		if (edgeCache != null) return;
		
		edgeCache = new ArrayGridList<Integer>(gridSize, (ox, oy) -> {
			BoolType b = get(ox + getOffsetX(), oy + getOffsetY());
			return b == null ? -1 : 0;
		});
		
		final boolean[] isDone = {false};
		while (!isDone[0])
		{
			isDone[0] = true;
			edgeCache.forEachPos((ox, oy) -> {
				isDone[0] &= updatePos(edgeCache, ox, oy);
			});
		}
	}
	
	// 0 means right on the edge, while 1 means 1 ceil away. Uses Manhattan Distance
	public <T extends ArrayGridList<BoolType>> void flagAllWithDistance(T list, IntPredicate predicate)
	{
		computeEdgeCache();
		edgeCache.forEachPos((ox, oy) -> {
			int v = edgeCache.get(ox, oy);
			if (v < 0 || !predicate.test(v)) return;
			list.set(ox + getOffsetX(), oy + getOffsetY(), BoolType.TRUE);
		});
	}
	
	
}
