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

package com.seibel.distanthorizons.core.file.subDimMatching;

import com.seibel.distanthorizons.core.config.Config;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Contains data used to compare different sub LodDimensions.
 * Sub Dimensions are the different folders under a dimension.
 * For example: "\Distant_Horizons_server_data\server_1\dim_the_nether\6fb97c01-e4c7-4634-87db-36b1e490e4c3"
 * is a sub dimension for the server "server_1" in the nether.
 *
 * @author James Seibel
 * @version 2022-12-17
 */
public class SubDimCompare implements Comparable<SubDimCompare>
{
	/**
	 * the maximum distance in blocks a player can be away from the
	 * given dimension and still be considered in the same place.
	 */
	public static int MAX_SIMILAR_PLAYER_POS_DISTANCE_IN_BLOCKS = 3;
	
	public int equalDataPoints = 0;
	public int totalDataPoints = 0;
	public int playerPosDist = 0;
	public File folder = null;
	
	
	public SubDimCompare(int newEqualDataPoints, int newTotalDataPoints, int newPlayerPosDistance, File newSubDimFolder)
	{
		this.equalDataPoints = newEqualDataPoints;
		this.totalDataPoints = newTotalDataPoints;
		this.playerPosDist = newPlayerPosDistance;
		
		this.folder = newSubDimFolder;
	}
	
	/** returns a number between 0 (no equal datapoint) and 1 (totally equal) */
	public double getPercentEqual() 
	{
		// its possible the comparison didn't find any data points
		if (this.totalDataPoints != 0)
		{
			return (double) this.equalDataPoints / (double) this.totalDataPoints;
		}
		else
		{
			return 0;
		}
	}
	
	
	@Override
	public int compareTo(@NotNull SubDimCompare other)
	{
		if (this.equalDataPoints != other.equalDataPoints)
		{
			// compare based on data points
			return Double.compare(this.getPercentEqual(), other.getPercentEqual());
		}
		else
		{
			// break ties based on player position
			return Integer.compare(this.playerPosDist, other.playerPosDist);
		}
	}
	
	/** Returns true if this sub dimension is close enough to be considered a valid sub dimension */
	public boolean isValidSubDim()
	{
		double minimumSimilarityRequired = Config.Client.Advanced.Multiplayer.multiverseSimilarityRequiredPercent.get();
		return this.getPercentEqual() >= minimumSimilarityRequired
				|| this.playerPosDist <= MAX_SIMILAR_PLAYER_POS_DISTANCE_IN_BLOCKS;
	}
	
	@Override
	public String toString() { return this.equalDataPoints + "/" + this.totalDataPoints + ": " + this.getPercentEqual() + " playerPos: " + this.playerPosDist; }
	
}
