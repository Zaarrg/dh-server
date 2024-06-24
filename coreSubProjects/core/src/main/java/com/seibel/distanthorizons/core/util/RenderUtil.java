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

package com.seibel.distanthorizons.core.util;

import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.pos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.world.IDhClientWorld;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.util.MathUtil;
import com.seibel.distanthorizons.coreapi.util.math.Mat4f;
import com.seibel.distanthorizons.coreapi.util.math.Vec3f;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;

/**
 * This holds miscellaneous helper code
 * to be used in the rendering process.
 *
 * @author James Seibel
 * @version 2022-8-21
 */
public class RenderUtil
{
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	
	
	//=================//
	// culling methods //
	//=================//
	
	/**
	 * Returns if the given ChunkPos is in the loaded area of the world.
	 *
	 * @param center the center of the loaded world (probably the player's ChunkPos)
	 */
	public static boolean isChunkPosInLoadedArea(DhChunkPos pos, DhChunkPos center)
	{
		return (pos.x >= center.x - MC_RENDER.getRenderDistance()
				&& pos.x <= center.x + MC_RENDER.getRenderDistance())
				&&
				(pos.z >= center.z - MC_RENDER.getRenderDistance()
				&& pos.z <= center.z + MC_RENDER.getRenderDistance());
	}
	
	/**
	 * Returns if the given coordinate is in the loaded area of the world.
	 *
	 * @param centerCoordinate the center of the loaded world
	 */
	public static boolean isCoordinateInLoadedArea(int x, int z, int centerCoordinate)
	{
		return (x >= centerCoordinate - MC_RENDER.getRenderDistance()
				&& x <= centerCoordinate + MC_RENDER.getRenderDistance())
				&&
				(z >= centerCoordinate - MC_RENDER.getRenderDistance()
				&& z <= centerCoordinate + MC_RENDER.getRenderDistance());
	}
	
	/**
	 * Find the coordinates that are in the center half of the given
	 * 2D matrix, starting at (0,0) and going to (2 * lodRadius, 2 * lodRadius).
	 */
	public static boolean isCoordinateInNearFogArea(int i, int j, int lodRadius)
	{
		int halfRadius = lodRadius / 2;
		
		return (i >= lodRadius - halfRadius
				&& i <= lodRadius + halfRadius)
				&&
				(j >= lodRadius - halfRadius
				&& j <= lodRadius + halfRadius);
	}
	
	/**
	 * Returns true if one of the region's 4 corners is in front
	 * of the camera.
	 */
	public static boolean isRegionInViewFrustum(DhBlockPos playerBlockPos, Vec3f cameraDir, int vboRegionX, int vboRegionZ)
	{
		// convert the vbo position into a direction vector
		// starting from the player's position
		Vec3f vboVec = new Vec3f(vboRegionX * LodUtil.REGION_WIDTH, 0, vboRegionZ * LodUtil.REGION_WIDTH);
		Vec3f playerVec = new Vec3f(playerBlockPos.x, playerBlockPos.y, playerBlockPos.z);
		
		vboVec.subtract(playerVec);
		
		// calculate the 4 corners
		Vec3f vboSeVec = new Vec3f(vboVec.x + LodUtil.REGION_WIDTH, vboVec.y, vboVec.z + LodUtil.REGION_WIDTH);
		Vec3f vboSwVec = new Vec3f(vboVec.x, vboVec.y, vboVec.z + LodUtil.REGION_WIDTH);
		Vec3f vboNwVec = new Vec3f(vboVec.x, vboVec.y, vboVec.z);
		Vec3f vboNeVec = new Vec3f(vboVec.x + LodUtil.REGION_WIDTH, vboVec.y, vboVec.z);
		
		// if any corner is visible, this region should be rendered
		return isNormalizedVectorInViewFrustum(vboSeVec, cameraDir) ||
				isNormalizedVectorInViewFrustum(vboSwVec, cameraDir) ||
				isNormalizedVectorInViewFrustum(vboNwVec, cameraDir) ||
				isNormalizedVectorInViewFrustum(vboNeVec, cameraDir);
	}
	
	/**
	 * Currently takes the dot product of the two vectors,
	 * but in the future could do more complicated frustum culling tests.
	 */
	private static boolean isNormalizedVectorInViewFrustum(Vec3f objectVector, Vec3f cameraDir)
	{
		// the -0.1 is to offer a slight buffer, so we are
		// more likely to render LODs and thus, hopefully prevent
		// flickering or odd disappearances
		return objectVector.dotProduct(cameraDir) > -0.1;
	}
	
	
	
	//=====================//
	// matrix manipulation //
	//=====================//
	
	/**
	 * create and return a new projection matrix based on MC's modelView and projection matrices
	 *
	 * @param mcProjMat Minecraft's current projection matrix
	 */
	public static Mat4f createLodProjectionMatrix(Mat4f mcProjMat, float partialTicks)
	{
		// in James' testing a near clip plane distance of 2 blocks is enough to allow the fragment
		// culling to take effect instead of seeing the near clip plane.
		float nearClipDist = RenderUtil.getNearClipPlaneDistanceInBlocks(partialTicks);
		float farClipDist = (float) RenderUtil.getFarClipPlaneDistanceInBlocks();
		
		// Create a copy of the current matrix, so it won't be modified.
		Mat4f lodProj = mcProjMat.copy();
		// Set new far and near clip plane values.
		lodProj.setClipPlanes(nearClipDist, farClipDist);
		return lodProj;
	}
	
	/** create and return a new projection matrix based on MC's modelView and projection matrices */
	public static Mat4f createLodModelViewMatrix(Mat4f mcModelViewMat)
	{
		// nothing beyond copying needs to be done to MC's MVM currently,
		// this method is just here in case that changes in the future
		return mcModelViewMat.copy();
	}
	
	/**
	 * create and return a new combined modelView/projection matrix based on MC's modelView and projection matrices
	 *
	 * @param mcProjMat Minecraft's current projection matrix
	 * @param mcModelViewMat Minecraft's current model view matrix
	 */
	public static Mat4f createCombinedModelViewProjectionMatrix(Mat4f mcProjMat, Mat4f mcModelViewMat, float partialTicks)
	{
		Mat4f lodProj = createLodProjectionMatrix(mcProjMat, partialTicks);
		lodProj.multiply(createLodModelViewMatrix(mcModelViewMat));
		return lodProj;
	}
	
	public static float getNearClipPlaneDistanceInBlocks(float partialTicks)
	{
		int chunkRenderDistance = MC_RENDER.getRenderDistance();
		int vanillaBlockRenderedDistance = chunkRenderDistance * LodUtil.CHUNK_WIDTH;
		
		float nearClipPlane;
		if (Config.Client.Advanced.Debugging.lodOnlyMode.get())
		{
			nearClipPlane = 0.1f;
		}
		else
		{
			// TODO make this option dependent on player speed.
			//  If the player is flying quickly, lower the near clip plane to account for slow chunk loading.
			//  If the player is moving quickly they are less likely to notice overdraw.
			
			nearClipPlane = Config.Client.Advanced.Graphics.AdvancedGraphics.overdrawPrevention.get().floatValue();
			nearClipPlane *= vanillaBlockRenderedDistance; 
			
			// the near clip plane should never be closer than 1/10th of a block,
			// otherwise Z-fighting and other issues may occur
			if (nearClipPlane < 0.1f)
			{
				nearClipPlane = 0.1f;
			}
		}
		
		
		// TODO move into method and use to override discard value in shader program
		float heightOverride = getHeightBasedNearClipOverride();
		if (heightOverride != -1.0f)
		{
			nearClipPlane = heightOverride;
		}
		
		
		// modify based on the player's FOV
		double fov = MC_RENDER.getFov(partialTicks);
		double aspectRatio = (double) MC_RENDER.getScreenWidth() / MC_RENDER.getScreenHeight();
		
		// source: https://stackoverflow.com/questions/8101119/how-do-i-methodically-choose-the-near-clip-plane-distance-for-a-perspective-proj/8101234#8101234
		return (float) (nearClipPlane
				/ Math.sqrt(1d + MathUtil.pow2(Math.tan(fov / 180d * Math.PI / 2d))
				* (MathUtil.pow2(aspectRatio) + 1d)));
	}
	public static int getFarClipPlaneDistanceInBlocks()
	{
		int lodChunkDist = Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get();
		int lodBlockDist = lodChunkDist * LodUtil.CHUNK_WIDTH;
		// sqrt 2 to prevent the corners from being cut off
		return (int)((lodBlockDist + LodUtil.REGION_WIDTH) * Math.sqrt(2));
	}
	
	/** @return -1 if no override is necessary */
	public static float getHeightBasedNearClipOverride()
	{
		// TODO always using the client level like this might cause issues with immersive portals and the like,
		//  but for now it should work well enough
		IClientLevelWrapper level = MC.getWrappedClientLevel();
		// a level should always be loaded, but just in case
		if (level != null)
		{
			// if the player is a significant distance above the work, increase the
			// near clip plane to fix Z imprecision issues
			int playerHeight = MC.getPlayerBlockPos().y;
			int levelMaxHeight = level.getHeight();
			if (playerHeight > levelMaxHeight + 1_000)
			{
				return playerHeight - (levelMaxHeight + 1000);
			}
		}
		
		return -1.0f;
	}
	
	/** @return false if LODs shouldn't be rendered for any reason */
	public static boolean shouldLodsRender(ILevelWrapper levelWrapper)
	{
		if (!MC.playerExists())
		{
			return false;
		}
		
		if (levelWrapper == null)
		{
			return false;
		}
		
		IDhClientWorld clientWorld = SharedApi.getIDhClientWorld();
		if (clientWorld == null)
		{
			return false;
		}
		
		IDhClientLevel level = (IDhClientLevel) clientWorld.getLevel(levelWrapper);
		if (level == null)
		{
			return false; //Level is not ready yet.
		}
		
		/* if (MC_RENDER.isFogStateSpecial())
		{
			// if the player is blind/under-water, don't render LODs,
			// and don't change minecraft's fog
			// which blindness relies on.
			return false;
		} */
		
		if (MC_RENDER.getLightmapWrapper(levelWrapper) == null)
		{
			return false;
		}
		
		return true;
	}
	
}
