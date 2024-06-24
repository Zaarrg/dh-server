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

package tests;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhLodPos;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Assert;
import org.junit.Test;

public class SquareIntersectTest
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	static
	{
		Configurator.setRootLevel(Level.ALL);
	}
	
	
	
	public static boolean DoSquaresOverlap(DhLodPos rect1Min, int rect1Width, DhLodPos rect2Min, int rect2Width)
	{
		// Determine the coordinates of the rectangles
		float rect1MinX = rect1Min.x;
		float rect1MaxX = rect1Min.x + rect1Width;
		float rect1MinZ = rect1Min.z;
		float rect1MaxZ = rect1Min.z + rect1Width;
		
		float rect2MinX = rect2Min.x;
		float rect2MaxX = rect2Min.x + rect2Width;
		float rect2MinZ = rect2Min.z;
		float rect2MaxZ = rect2Min.z + rect2Width;
		
		// Check if the rectangles overlap
		return rect1MinX < rect2MaxX && rect1MaxX > rect2MinX && rect1MinZ < rect2MaxZ && rect1MaxZ > rect2MinZ;
	}
	
	
	
	// The first test case checks that two overlapping rectangles are detected as overlapping.
	@Test
	public void TestOverlappingSquares()
	{
		DhLodPos rect1Min = new DhLodPos((byte) 0, 1, 1);
		int rect1Width = 4;
		
		DhLodPos rect2Min = new DhLodPos((byte) 0, 3, 3);
		int rect2Width = 4;
		
		boolean result = DoSquaresOverlap(rect1Min, rect1Width, rect2Min, rect2Width);
		Assert.assertTrue(result);
	}
	
	// The second test case checks that two non-overlapping rectangles are detected as not overlapping.
	@Test
	public void TestNonOverlappingSquares()
	{
		DhLodPos rect1Min = new DhLodPos((byte) 0, 1, 1);
		int rect1Width = 2;
		
		DhLodPos rect2Min = new DhLodPos((byte) 0, 4, 4);
		int rect2Width = 2;
		
		boolean result = DoSquaresOverlap(rect1Min, rect1Width, rect2Min, rect2Width);
		Assert.assertFalse(result);
	}
	
	// The third test case checks that two rectangles with different sizes and overlapping are detected as overlapping.
	@Test
	public void TestSquaresWithDifferentSizes()
	{
		DhLodPos rect1Min = new DhLodPos((byte) 0, 1, 1);
		int rect1Width = 4;
		
		DhLodPos rect2Min = new DhLodPos((byte) 0, 3, 3);
		int rect2Width = 3;
		
		boolean result = DoSquaresOverlap(rect1Min, rect1Width, rect2Min, rect2Width);
		Assert.assertTrue(result);
	}
	
	// The fourth test case checks that a rectangle that contains another rectangle is detected as overlapping.
	@Test
	public void TestOneRectangleContainsTheOther()
	{
		DhLodPos rect1Min = new DhLodPos((byte) 0, 1, 1);
		int rect1Width = 9;
		
		DhLodPos rect2Min = new DhLodPos((byte) 0, 3, 3);
		int rect2Width = 3;
		
		boolean result = DoSquaresOverlap(rect1Min, rect1Width, rect2Min, rect2Width);
		Assert.assertTrue(result);
	}
	
	// The fifth test case checks that the same as the fourth, but with the rectangles swapped to make sure the method can detect overlapping in any configuration.
	@Test
	public void TestOneRectangleContainsTheOtherInverted()
	{
		DhLodPos rect1Min = new DhLodPos((byte) 0, 3, 3);
		int rect1Width = 3;
		
		DhLodPos rect2Min = new DhLodPos((byte) 0, 1, 1);
		int rect2Width = 9;
		
		boolean result = DoSquaresOverlap(rect1Min, rect1Width, rect2Min, rect2Width);
		Assert.assertTrue(result);
	}
	
}
