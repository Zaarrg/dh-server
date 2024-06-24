package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiCullingFrustum;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IOverrideInjector;
import com.seibel.distanthorizons.coreapi.util.math.Mat4f;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;


public class DhFrustumBounds implements IDhApiCullingFrustum
{
	private final FrustumIntersection frustum;
	private final Vector3f boundsMin = new Vector3f();
	private final Vector3f boundsMax = new Vector3f();
	public float worldMinY;
	public float worldMaxY;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DhFrustumBounds()
	{
		this.frustum = new FrustumIntersection();
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public void update(int worldMinBlockY, int worldMaxBlockY, Mat4f dhWorldViewProjection)
	{
		this.worldMinY = worldMinBlockY;
		this.worldMaxY = worldMaxBlockY;
		
		Matrix4f worldViewProjection = new Matrix4f(dhWorldViewProjection.createJomlMatrix());
		this.frustum.set(worldViewProjection);
		
		Matrix4fc matWorldViewProjectionInv = new Matrix4f(worldViewProjection).invert();
		matWorldViewProjectionInv.frustumAabb(this.boundsMin, this.boundsMax);
	}
	
	@Override
	public boolean intersects(int lodBlockPosMinX, int lodBlockPosMinZ, int lodBlockWidth, int lodDetailLevel)
	{
		Vector3f lodMin = new Vector3f(lodBlockPosMinX, this.worldMinY, lodBlockPosMinZ);
		Vector3f lodMax = new Vector3f(lodBlockPosMinX + lodBlockWidth, this.worldMaxY, lodBlockPosMinZ + lodBlockWidth);
		
		if (lodMax.x < this.boundsMin.x || lodMin.x > this.boundsMax.x) return false;
		if (lodMax.z < this.boundsMin.z || lodMin.z > this.boundsMax.z) return false;
		if (this.worldMaxY < this.boundsMin.y || this.worldMinY > this.boundsMax.y) return false;
		
		return this.frustum.testAab(lodMin, lodMax);
	}
	
	
	
	//=====================//
	// overridable methods //
	//=====================//
	
	@Override 
	public int getPriority() { return IOverrideInjector.CORE_PRIORITY; }
	
}
