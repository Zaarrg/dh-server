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

package com.seibel.distanthorizons.core.api.external.methods.config.client;

import com.seibel.distanthorizons.api.enums.config.*;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiTransparency;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiAmbientOcclusionConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiFogConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiNoiseTextureConfig;
import com.seibel.distanthorizons.api.objects.config.DhApiConfigValue;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.core.config.Config;

public class DhApiGraphicsConfig implements IDhApiGraphicsConfig
{
	public static DhApiGraphicsConfig INSTANCE = new DhApiGraphicsConfig();
	
	private DhApiGraphicsConfig() { }
	
	
	
	//==============//
	// inner layers //
	//==============//
	
	public IDhApiFogConfig fog() { return DhApiFogConfig.INSTANCE; }
	public IDhApiAmbientOcclusionConfig ambientOcclusion() { return DhApiAmbientOcclusionConfig.INSTANCE; }
	public IDhApiNoiseTextureConfig noiseTexture() { return DhApiNoiseTextureConfig.INSTANCE; }
	
	
	
	//========================//
	// basic graphic settings //
	//========================//
	
	@Override
	public IDhApiConfigValue<Integer> chunkRenderDistance()
	{ return new DhApiConfigValue<Integer, Integer>(Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius); }
	
	@Override
	public IDhApiConfigValue<Boolean> renderingEnabled()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.quickEnableRendering); }
	
	@Override
	public IDhApiConfigValue<EDhApiRendererMode> renderingMode()
	{ return new DhApiConfigValue<EDhApiRendererMode, EDhApiRendererMode>(Config.Client.Advanced.Debugging.rendererMode); }
	
	
	
	//==================//
	// graphic settings //
	//==================//
	
	@Override
	public IDhApiConfigValue<EDhApiMaxHorizontalResolution> maxHorizontalResolution()
	{ return new DhApiConfigValue<EDhApiMaxHorizontalResolution, EDhApiMaxHorizontalResolution>(Config.Client.Advanced.Graphics.Quality.maxHorizontalResolution); }
	
	@Override
	public IDhApiConfigValue<EDhApiVerticalQuality> verticalQuality()
	{ return new DhApiConfigValue<EDhApiVerticalQuality, EDhApiVerticalQuality>(Config.Client.Advanced.Graphics.Quality.verticalQuality); }
	
	@Override
	public IDhApiConfigValue<EDhApiHorizontalQuality> horizontalQuality()
	{ return new DhApiConfigValue<EDhApiHorizontalQuality, EDhApiHorizontalQuality>(Config.Client.Advanced.Graphics.Quality.horizontalQuality); }
	
	@Override
	public IDhApiConfigValue<EDhApiTransparency> transparency()
	{ return new DhApiConfigValue<EDhApiTransparency, EDhApiTransparency>(Config.Client.Advanced.Graphics.Quality.transparency); }
	
	@Override
	public IDhApiConfigValue<EDhApiBlocksToAvoid> blocksToAvoid()
	{ return new DhApiConfigValue<EDhApiBlocksToAvoid, EDhApiBlocksToAvoid>(Config.Client.Advanced.Graphics.Quality.blocksToIgnore); }
	
	@Override
	public IDhApiConfigValue<Boolean> tintWithAvoidedBlocks()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Graphics.Quality.tintWithAvoidedBlocks); }
	
	// TODO re-implement
//	@Override
//	public IDhApiConfigValue<Integer> getBiomeBlending()
//	{ return new DhApiConfigValue<Integer, Integer>(Quality.lodBiomeBlending); }
	
	
	
	//===========================//
	// advanced graphic settings //
	//===========================//

	@Override
	public IDhApiConfigValue<Double> overdrawPreventionRadius()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.AdvancedGraphics.overdrawPrevention); }
	
	@Override
	public IDhApiConfigValue<Double> brightnessMultiplier()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.AdvancedGraphics.brightnessMultiplier); }
	
	@Override
	public IDhApiConfigValue<Double> saturationMultiplier()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.AdvancedGraphics.saturationMultiplier); }
	
	@Override
	public IDhApiConfigValue<Boolean> caveCullingEnabled()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Graphics.AdvancedGraphics.enableCaveCulling); }
	
	@Override
	public IDhApiConfigValue<Integer> caveCullingHeight()
	{ return new DhApiConfigValue<Integer, Integer>(Config.Client.Advanced.Graphics.AdvancedGraphics.caveCullingHeight); }
	
	@Override
	public IDhApiConfigValue<Integer> earthCurvatureRatio()
	{ return new DhApiConfigValue<Integer, Integer>(Config.Client.Advanced.Graphics.AdvancedGraphics.earthCurveRatio); }
	
	@Override
	public IDhApiConfigValue<Boolean> lodOnlyMode()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Debugging.lodOnlyMode); }
	
	@Override
	public IDhApiConfigValue<Double> lodBias()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.AdvancedGraphics.lodBias); }
	
	@Override
	public IDhApiConfigValue<EDhApiLodShading> lodShading()
	{ return new DhApiConfigValue<EDhApiLodShading, EDhApiLodShading>(Config.Client.Advanced.Graphics.AdvancedGraphics.lodShading); }
	
	@Override
	public IDhApiConfigValue<Boolean> disableFrustumCulling()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Graphics.AdvancedGraphics.disableFrustumCulling); }
	
	@Override
	public IDhApiConfigValue<Boolean> disableShadowFrustumCulling()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Graphics.AdvancedGraphics.disableShadowPassFrustumCulling); }
	
	
	
}
