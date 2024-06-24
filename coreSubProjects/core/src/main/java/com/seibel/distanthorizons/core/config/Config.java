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

package com.seibel.distanthorizons.core.config;


import com.seibel.distanthorizons.api.enums.config.*;
import com.seibel.distanthorizons.api.enums.config.quickOptions.*;
import com.seibel.distanthorizons.api.enums.rendering.*;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.core.config.eventHandlers.*;
import com.seibel.distanthorizons.core.config.eventHandlers.presets.*;
import com.seibel.distanthorizons.core.config.types.*;
import com.seibel.distanthorizons.core.config.types.enums.*;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.coreapi.util.StringUtil;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;


/**
 * This handles any configuration the user has access to. <br><br>
 *
 * Note: <br>
 * Only add simpler listeners here (IE listeners that only depend on 1 config entry).
 * For listeners that depend on 2 or more config entries, add them before the config menu is opened.
 * Otherwise, you will have issues where only some of the config entries will exist when your listener is created.
 *
 * @author coolGi
 * @version 2023-7-16
 */

public class Config
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public static ConfigCategory client = new ConfigCategory.Builder().set(Client.class).build();
	
	
	
	public static class Client
	{
		public static ConfigEntry<Boolean> quickEnableRendering = new ConfigEntry.Builder<Boolean>()
				.set(true)
				.comment(""
						+ "If true, Distant Horizons will render LODs beyond the vanilla render distance."
						+ "")
				.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
				.build();
		
		public static ConfigLinkedEntry quickLodChunkRenderDistance = new ConfigLinkedEntry(Advanced.Graphics.Quality.lodChunkRenderDistanceRadius);
		
		public static ConfigEntry<EDhApiQualityPreset> qualityPresetSetting = new ConfigEntry.Builder<EDhApiQualityPreset>()
				.set(EDhApiQualityPreset.EXTREME) // the default value is set via the listener when accessed
				.comment(""
						+ "Changing this setting will modify a number of different settings that will change the \n"
						+ "visual fidelity of the rendered LODs.\n"
						+ "\n"
						+ "Higher settings will improve the graphical quality while increasing GPU and memory use.\n"
						+ "")
				.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
				.addListener(RenderQualityPresetConfigEventHandler.INSTANCE)
				.build();
		
		public static ConfigEntry<EDhApiThreadPreset> threadPresetSetting = new ConfigEntry.Builder<EDhApiThreadPreset>()
				.set(EDhApiThreadPreset.AGGRESSIVE) // the default value is set via the listener when accessed
				.comment(""
						+ "Changing this setting will modify a number of different settings that will change \n"
						+ "the load that Distant Horizons is allowed to put on your CPU. \n"
						+ "\n"
						+ "Higher options will improve LOD generation and loading speed, \n"
						+ "but will increase CPU load and may introduce stuttering.\n"
						+ "\n"
						+ "Note: on CPUs with 4 cores or less these settings will be less effective \n"
						+ "      and some settings will give similar results. \n"
						+ "")
				.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
				.addListener(ThreadPresetConfigEventHandler.INSTANCE)
				.build();
		
		public static ConfigLinkedEntry quickEnableWorldGenerator = new ConfigLinkedEntry(Advanced.WorldGenerator.enableDistantGeneration);
		
		public static ConfigEntry<Boolean> optionsButton = new ConfigEntry.Builder<Boolean>()
				.set(true)
				.setAppearance(EConfigEntryAppearance.ONLY_IN_FILE)
				.comment("" +
						"Should Distant Horizon's config button appear in the options screen next to fov slider?")
				.build();
		
		
		
		public static ConfigCategory advanced = new ConfigCategory.Builder().set(Advanced.class).build();
//		public static ConfigCategory resetSettingsConfirmation = new ConfigCategory.Builder().set(ResetConfirmation.class).build();
		
		public static class Advanced
		{
			public static ConfigCategory graphics = new ConfigCategory.Builder().set(Graphics.class).build();
			public static ConfigCategory worldGenerator = new ConfigCategory.Builder().set(WorldGenerator.class).build();
			public static ConfigCategory multiplayer = new ConfigCategory.Builder().set(Multiplayer.class).build();
			public static ConfigCategory lodBuilding = new ConfigCategory.Builder().set(LodBuilding.class).build();
			public static ConfigCategory multiThreading = new ConfigCategory.Builder().set(MultiThreading.class).build();
			public static ConfigCategory buffers = new ConfigCategory.Builder().set(GpuBuffers.class).build();
			public static ConfigCategory autoUpdater = new ConfigCategory.Builder().set(AutoUpdater.class).build();
			
			public static ConfigCategory logging = new ConfigCategory.Builder().set(Logging.class).build();
			public static ConfigCategory debugging = new ConfigCategory.Builder().set(Debugging.class).build();
			
			
			public static class Graphics
			{
				public static ConfigCategory quality = new ConfigCategory.Builder().set(Quality.class).build();
				public static ConfigCategory fog = new ConfigCategory.Builder().set(Fog.class).build();
				public static ConfigCategory ssao = new ConfigCategory.Builder().set(Ssao.class).build();
				public static ConfigCategory noiseTextureSettings = new ConfigCategory.Builder().set(NoiseTextureSettings.class).build();
				public static ConfigCategory advancedGraphics = new ConfigCategory.Builder().set(AdvancedGraphics.class).build();
				
				
				public static class Quality
				{
					public static ConfigEntry<EDhApiMaxHorizontalResolution> maxHorizontalResolution = new ConfigEntry.Builder<EDhApiMaxHorizontalResolution>()
							.set(EDhApiMaxHorizontalResolution.BLOCK)
							.comment(""
									+ "What is the maximum detail LODs should be drawn at? \n"
									+ "Higher settings will increase memory and GPU usage. \n"
									+ "\n"
									+ EDhApiMaxHorizontalResolution.CHUNK + ": render 1 LOD for each Chunk. \n"
									+ EDhApiMaxHorizontalResolution.HALF_CHUNK + ": render 4 LODs for each Chunk. \n"
									+ EDhApiMaxHorizontalResolution.FOUR_BLOCKS + ": render 16 LODs for each Chunk. \n"
									+ EDhApiMaxHorizontalResolution.TWO_BLOCKS + ": render 64 LODs for each Chunk. \n"
									+ EDhApiMaxHorizontalResolution.BLOCK + ": render 256 LODs for each Chunk (width of one block). \n"
									+ "\n"
									+ "Lowest Quality: " + EDhApiMaxHorizontalResolution.CHUNK + "\n"
									+ "Highest Quality: " + EDhApiMaxHorizontalResolution.BLOCK)
							.setPerformance(EConfigEntryPerformance.MEDIUM)
							.build();
					
					public static ConfigEntry<Integer> lodChunkRenderDistanceRadius = new ConfigEntry.Builder<Integer>()
							.setServersideShortName("renderDistanceRadius")
							.setMinDefaultMax(32, 128, 4096)
							.comment("" +
									"The radius of the mod's render distance. (measured in chunks)\n" +
									"On server changes the distance players will receive real time updates for, if enabled." +
									"\n" +
									"Note for servers:\n" +
									"This setting does not prevent players from generating farther out.\n" +
									"If you want to limit performance impact, change rate/concurrency (RC) limits\n" +
									"and thread count/runtime ratio settings instead.")
							.setPerformance(EConfigEntryPerformance.HIGH)
							.build();
					
					public static ConfigEntry<EDhApiVerticalQuality> verticalQuality = new ConfigEntry.Builder<EDhApiVerticalQuality>()
							.set(EDhApiVerticalQuality.MEDIUM)
							.comment(""
									+ "This indicates how well LODs will represent \n"
									+ "overhangs, caves, floating islands, etc. \n"
									+ "Higher options will make the world more accurate, but"
									+ "will increase memory and GPU usage. \n"
									+ "\n"
									+ "Lowest Quality: " + EDhApiVerticalQuality.HEIGHT_MAP + "\n"
									+ "Highest Quality: " + EDhApiVerticalQuality.EXTREME)
							.setPerformance(EConfigEntryPerformance.VERY_HIGH)
							.build();
					
					public static ConfigEntry<EDhApiHorizontalQuality> horizontalQuality = new ConfigEntry.Builder<EDhApiHorizontalQuality>()
							.set(EDhApiHorizontalQuality.MEDIUM)
							.comment(""
									+ "This indicates how quickly LODs decrease in quality the further away they are. \n"
									+ "Higher settings will render higher quality fake chunks farther away, \n"
									+ "but will increase memory and GPU usage.")
							.setPerformance(EConfigEntryPerformance.MEDIUM)
							.build();
					
					public static ConfigEntry<EDhApiTransparency> transparency = new ConfigEntry.Builder<EDhApiTransparency>()
							.set(EDhApiTransparency.COMPLETE)
							.comment(""
									+ "How should LOD transparency be handled. \n"
									+ "\n"
									+ EDhApiTransparency.COMPLETE + ": LODs will render transparent. \n"
									+ EDhApiTransparency.FAKE + ": LODs will be opaque, but shaded to match the blocks underneath. \n"
									+ EDhApiTransparency.DISABLED + ": LODs will be opaque. \n"
									+ "")
							.setPerformance(EConfigEntryPerformance.MEDIUM)
							.build();
					
					public static ConfigEntry<EDhApiBlocksToAvoid> blocksToIgnore = new ConfigEntry.Builder<EDhApiBlocksToAvoid>()
							.set(EDhApiBlocksToAvoid.NON_COLLIDING)
							.comment(""
									+ "What blocks shouldn't be rendered as LODs? \n"
									+ "\n"
									+ EDhApiBlocksToAvoid.NONE + ": Represent all blocks in the LODs \n"
									+ EDhApiBlocksToAvoid.NON_COLLIDING + ": Only represent solid blocks in the LODs (tall grass, torches, etc. won't count for a LOD's height) \n"
									+ "")
							.setPerformance(EConfigEntryPerformance.NONE)
							.build();
					
					public static ConfigEntry<Boolean> tintWithAvoidedBlocks = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment(""
									+ "Should the blocks underneath avoided blocks gain the color of the avoided block? \n"
									+ "\n"
									+ "True: a red flower will tint the grass below it red. \n"
									+ "False: skipped blocks will not change color of surface below them. "
									+ "")
							.setPerformance(EConfigEntryPerformance.NONE)
							.build();
					
					// TODO fixme
//					public static ConfigEntry<Integer> lodBiomeBlending = new ConfigEntry.Builder<Integer>()
//							.setMinDefaultMax(0,1,7)
//							.comment(""
//									+ "This is the same as vanilla Biome Blending settings for Lod area. \n"
//									+ "    Note that anything other than '0' will greatly effect Lod building time \n"
//									+ "     and increase triangle count. The cost on chunk generation speed is also \n"
//									+ "     quite large if set too high.\n"
//									+ "\n"
//									+ "    '0' equals to Vanilla Biome Blending of '1x1' or 'OFF', \n"
//									+ "    '1' equals to Vanilla Biome Blending of '3x3', \n"
//									+ "    '2' equals to Vanilla Biome Blending of '5x5'...")
//							.build();
				}
				
				public static class Fog
				{
					public static ConfigEntry<EDhApiFogDrawMode> drawMode = new ConfigEntry.Builder<EDhApiFogDrawMode>()
							.set(EDhApiFogDrawMode.FOG_ENABLED)
							.comment(""
									+ "When should fog be drawn? \n"
									+ "\n"
									+ EDhApiFogDrawMode.USE_OPTIFINE_SETTING + ": Use whatever Fog setting Optifine is using.\n"
									+ "If Optifine isn't installed this defaults to " + EDhApiFogDrawMode.FOG_ENABLED + ". \n"
									+ EDhApiFogDrawMode.FOG_ENABLED + ": Never draw fog on the LODs \n"
									+ EDhApiFogDrawMode.FOG_DISABLED + ": Always draw fast fog on the LODs \n"
									+ "\n"
									+ "Disabling fog will improve GPU performance.")
							.setPerformance(EConfigEntryPerformance.VERY_LOW)
							.build();
					
					public static ConfigEntry<EDhApiFogColorMode> colorMode = new ConfigEntry.Builder<EDhApiFogColorMode>()
							.set(EDhApiFogColorMode.USE_WORLD_FOG_COLOR)
							.comment(""
									+ "What color should fog use? \n"
									+ "\n"
									+ EDhApiFogColorMode.USE_WORLD_FOG_COLOR + ": Use the world's fog color. \n"
									+ EDhApiFogColorMode.USE_SKY_COLOR + ": Use the sky's color.")
							.setPerformance(EConfigEntryPerformance.NONE)
							.build();
					
					public static ConfigEntry<Boolean> disableVanillaFog = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment(""
									+ "Should Minecraft's fog be disabled? \n"
									+ "\n"
									+ "Note: Other mods may conflict with this setting.")
							.build();
					
					
					
					public static ConfigCategory advancedFog = new ConfigCategory.Builder().set(AdvancedFog.class).build();
					
					
					public static class AdvancedFog
					{
						private static final Double FOG_RANGE_MIN = 0.0;
						private static final Double FOG_RANGE_MAX = Math.sqrt(2.0);
						
						public static ConfigEntry<Double> farFogStart = new ConfigEntry.Builder<Double>()
								.setMinDefaultMax(FOG_RANGE_MIN, 0.0, FOG_RANGE_MAX)
								.comment(""
										+ "At what distance should the far fog start? \n"
										+ "\n"
										+ "0.0: Fog starts at the player's position. \n"
										+ "1.0: Fog starts at the closest edge of the vanilla render distance. \n"
										+ "1.414: Fog starts at the corner of the vanilla render distance.")
								.build();
						
						public static ConfigEntry<Double> farFogEnd = new ConfigEntry.Builder<Double>()
								.setMinDefaultMax(FOG_RANGE_MIN, 1.0, FOG_RANGE_MAX)
								.comment(""
										+ "Where should the far fog end? \n"
										+ "\n"
										+ "0.0: Fog ends at player's position.\n"
										+ "1.0: Fog ends at the closest edge of the vanilla render distance. \n"
										+ "1.414: Fog ends at the corner of the vanilla render distance.")
								.build();
						
						public static ConfigEntry<Double> farFogMin = new ConfigEntry.Builder<Double>()
								.setMinDefaultMax(-5.0, 0.0, FOG_RANGE_MAX)
								.comment(""
										+ "What is the minimum fog thickness? \n"
										+ "\n"
										+ "0.0: No fog. \n"
										+ "1.0: Fully opaque fog.")
								.build();
						
						public static ConfigEntry<Double> farFogMax = new ConfigEntry.Builder<Double>()
								.setMinDefaultMax(FOG_RANGE_MIN, 1.0, 5.0)
								.comment(""
										+ "What is the maximum fog thickness? \n"
										+ "\n"
										+ "0.0: No fog. \n"
										+ "1.0: Fully opaque fog.")
								.build();
						
						public static ConfigEntry<EDhApiFogFalloff> farFogFalloff = new ConfigEntry.Builder<EDhApiFogFalloff>()
								.set(EDhApiFogFalloff.EXPONENTIAL_SQUARED)
								.comment(""
										+ "How should the fog thickness should be calculated? \n"
										+ "\n"
										+ EDhApiFogFalloff.LINEAR + ": Linear based on distance (will ignore 'density')\n"
										+ EDhApiFogFalloff.EXPONENTIAL + ": 1/(e^(distance*density)) \n"
										+ EDhApiFogFalloff.EXPONENTIAL_SQUARED + ": 1/(e^((distance*density)^2)")
								.build();
						
						public static ConfigEntry<Double> farFogDensity = new ConfigEntry.Builder<Double>()
								.setMinDefaultMax(0.01, 2.5, 50.0)
								.comment(""
										+ "Used in conjunction with the Fog Falloff.")
								.build();
						
						
						
						public static ConfigCategory heightFog = new ConfigCategory.Builder().set(HeightFog.class).build();
						
						
						public static class HeightFog
						{
							public static ConfigEntry<EDhApiHeightFogMixMode> heightFogMixMode = new ConfigEntry.Builder<EDhApiHeightFogMixMode>()
									.set(EDhApiHeightFogMixMode.BASIC)
									.comment(""
											+ "How should height effect the fog thickness? \n"
											+ "Note: height fog is combined with the other fog settings. \n"
											+ "\n"
											+ EDhApiHeightFogMixMode.BASIC + ": No special height fog effect. Fog is calculated based on camera distance \n"
											+ EDhApiHeightFogMixMode.IGNORE_HEIGHT + ": Ignore height completely. Fog is only calculated with horizontal distance \n"
											+ EDhApiHeightFogMixMode.ADDITION + ": heightFog + farFog \n"
											+ EDhApiHeightFogMixMode.MAX + ": max(heightFog, farFog) \n"
											+ EDhApiHeightFogMixMode.MULTIPLY + ": heightFog * farFog \n"
											+ EDhApiHeightFogMixMode.INVERSE_MULTIPLY + ": 1 - (1-heightFog) * (1-farFog) \n"
											+ EDhApiHeightFogMixMode.LIMITED_ADDITION + ": farFog + max(farFog, heightFog) \n"
											+ EDhApiHeightFogMixMode.MULTIPLY_ADDITION + ": farFog + farFog * heightFog \n"
											+ EDhApiHeightFogMixMode.INVERSE_MULTIPLY_ADDITION + ": farFog + 1 - (1-heightFog) * (1-farFog) \n"
											+ EDhApiHeightFogMixMode.AVERAGE + ": farFog*0.5 + heightFog*0.5 \n"
											+ "\n"
											+ "Note: height fog settings are ignored if '" + EDhApiHeightFogMixMode.BASIC + "' or '" + EDhApiHeightFogMixMode.IGNORE_HEIGHT + "' are selected.")
									.build();
							
							public static ConfigEntry<EDhApiHeightFogMode> heightFogMode = new ConfigEntry.Builder<EDhApiHeightFogMode>()
									.set(EDhApiHeightFogMode.ABOVE_AND_BELOW_CAMERA)
									.comment(""
											+ "Where should the height fog start? \n"
											+ "\n"
											+ EDhApiHeightFogMode.ABOVE_CAMERA + ": Height fog starts at the camera and goes towards the sky \n"
											+ EDhApiHeightFogMode.BELOW_CAMERA + ": Height fog starts at the camera and goes towards the void \n"
											+ EDhApiHeightFogMode.ABOVE_AND_BELOW_CAMERA + ": Height fog starts from the camera to goes towards both the sky and void \n"
											+ EDhApiHeightFogMode.ABOVE_SET_HEIGHT + ": Height fog starts from a set height and goes towards the sky \n"
											+ EDhApiHeightFogMode.BELOW_SET_HEIGHT + ": Height fog starts from a set height and goes towards the void \n"
											+ EDhApiHeightFogMode.ABOVE_AND_BELOW_SET_HEIGHT + ": Height fog starts from a set height and goes towards both the sky and void")
									.build();
							
							public static ConfigEntry<Double> heightFogBaseHeight = new ConfigEntry.Builder<Double>()
									.setMinDefaultMax(-4096.0, 70.0, 4096.0)
									.comment("If the height fog is calculated around a set height, what is that height position?")
									.build();
							
							public static ConfigEntry<Double> heightFogStart = new ConfigEntry.Builder<Double>()
									.setMinDefaultMax(FOG_RANGE_MIN, 0.0, FOG_RANGE_MAX)
									.comment(""
											+ "Should the start of the height fog be offset? \n"
											+ "\n"
											+ "0.0: Fog start with no offset.\n"
											+ "1.0: Fog start with offset of the entire world's height. (Includes depth)")
									.build();
							
							public static ConfigEntry<Double> heightFogEnd = new ConfigEntry.Builder<Double>()
									.setMinDefaultMax(FOG_RANGE_MIN, 1.0, FOG_RANGE_MAX)
									.comment(""
											+ "Should the end of the height fog be offset? \n"
											+ "\n"
											+ "0.0: Fog end with no offset.\n"
											+ "1.0: Fog end with offset of the entire world's height. (Include depth)")
									.build();
							
							public static ConfigEntry<Double> heightFogMin = new ConfigEntry.Builder<Double>()
									.setMinDefaultMax(-5.0, 0.0, FOG_RANGE_MAX)
									.comment(""
											+ "What is the minimum fog thickness? \n"
											+ "\n"
											+ "0.0: No fog. \n"
											+ "1.0: Fully opaque fog.")
									.build();
							
							public static ConfigEntry<Double> heightFogMax = new ConfigEntry.Builder<Double>()
									.setMinDefaultMax(FOG_RANGE_MIN, 1.0, 5.0)
									.comment(""
											+ "What is the maximum fog thickness? \n"
											+ "\n"
											+ "0.0: No fog. \n"
											+ "1.0: Fully opaque fog.")
									.build();
							
							public static ConfigEntry<EDhApiFogFalloff> heightFogFalloff = new ConfigEntry.Builder<EDhApiFogFalloff>()
									.set(EDhApiFogFalloff.EXPONENTIAL_SQUARED)
									.comment(""
											+ "How should the height fog thickness should be calculated? \n"
											+ "\n"
											+ EDhApiFogFalloff.LINEAR + ": Linear based on height (will ignore 'density')\n"
											+ EDhApiFogFalloff.EXPONENTIAL + ": 1/(e^(height*density)) \n"
											+ EDhApiFogFalloff.EXPONENTIAL_SQUARED + ": 1/(e^((height*density)^2)")
									.build();
							
							public static ConfigEntry<Double> heightFogDensity = new ConfigEntry.Builder<Double>()
									.setMinDefaultMax(0.01, 2.5, 50.0)
									.comment("What is the height fog's density?")
									.build();
							
						}
						
					}
					
				}
				
				public static class Ssao
				{
					public static ConfigEntry<Boolean> enabled = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment("Enable Screen Space Ambient Occlusion")
							.setPerformance(EConfigEntryPerformance.MEDIUM)
							.build();
					
					public static ConfigEntry<Integer> sampleCount = new ConfigEntry.Builder<Integer>()
							.set(6)
							.comment("" +
									"Determines how many points in space are sampled for the occlusion test. \n" +
									"Higher numbers will improve quality and reduce banding, but will increase GPU load." +
									"")
							.setPerformance(EConfigEntryPerformance.MEDIUM)
							.build();
					
					public static ConfigEntry<Double> radius = new ConfigEntry.Builder<Double>()
							.set(4.0)
							.comment("" +
									"Determines the radius Screen Space Ambient Occlusion is applied, measured in blocks." +
									"")
							.setPerformance(EConfigEntryPerformance.NONE)
							.build();
					
					public static ConfigEntry<Double> strength = new ConfigEntry.Builder<Double>()
							.set(0.2)
							.comment("" +
									"Determines how dark the Screen Space Ambient Occlusion effect will be." +
									"")
							.setPerformance(EConfigEntryPerformance.NONE)
							.build();
					
					public static ConfigEntry<Double> bias = new ConfigEntry.Builder<Double>()
							.set(0.02)
							.comment("" +
									"Increasing the value can reduce banding at the cost of reducing the strength of the effect." +
									"")
							.setPerformance(EConfigEntryPerformance.NONE)
							.build();
					
					public static ConfigEntry<Double> minLight = new ConfigEntry.Builder<Double>()
							.set(0.25)
							.comment("" +
									"Determines how dark the occlusion shadows can be. \n" +
									"0 = totally black at the corners \n" +
									"1 = no shadow" +
									"")
							.setPerformance(EConfigEntryPerformance.NONE)
							.build();
					
					public static ConfigEntry<Integer> blurRadius = new ConfigEntry.Builder<Integer>()
							.set(2)
							.comment("" +
									"The radius, measured in pixels, that blurring is calculated for the SSAO. \n" +
									"Higher numbers will reduce banding at the cost of GPU performance." +
									"")
							.setPerformance(EConfigEntryPerformance.HIGH)
							.build();
					
				}
				
				public static class NoiseTextureSettings
				{
					public static ConfigEntry<Boolean> noiseEnabled = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment(""
									+ "Should a noise texture be applied to LODs? \n"
									+ "\n"
									+ "This is done to simulate textures and make the LODs appear more detailed. \n"
									+ "")
							.build();
					
					public static ConfigEntry<Integer> noiseSteps = new ConfigEntry.Builder<Integer>()
							.setMinDefaultMax(1, 4, null)
							.comment(""
									+ "How many steps of noise should be applied to LODs?")
							.build();
					
					public static ConfigEntry<Double> noiseIntensity = new ConfigEntry.Builder<Double>()    // TODO: Make this a float (the ClassicConfigGUI doesn't support floats)
							.setMinDefaultMax(0d, 5d, 100d)                    // TODO: Once this becomes a float make it 0-1 instead of 0-100 (I did this cus doubles only allow 2 decimal places)
							.comment(""
									+ "How intense should the noise should be?")
							.build();
					
					public static ConfigEntry<Integer> noiseDropoff = new ConfigEntry.Builder<Integer>()    // TODO: Make this a float (the ClassicConfigGUI doesn't support floats)
							.setMinDefaultMax(0, 1024, null)
							.comment(""
									+ "Defines how far should the noise texture render before it fades away. (in blocks) \n"
									+ "Set to 0 to disable noise from fading away")
							.build();
					
				}
				
				public static class AdvancedGraphics
				{
					public static ConfigEntry<Double> overdrawPrevention = new ConfigEntry.Builder<Double>()
							.setMinDefaultMax(0.0, 0.4, 1.0)
							.comment(""
									+ "Determines how far from the camera Distant Horizons will start rendering. \n"
									+ "Measured as a percentage of the vanilla render distance.\n"
									+ "\n"
									+ "Higher values will prevent LODs from rendering behind vanilla blocks at a higher distance,\n"
									+ "but may cause holes to appear in the LODs. \n"
									+ "Holes are most likely to appear when flying through unloaded terrain. \n"
									+ "\n"
									+ "Increasing the vanilla render distance increases the effectiveness of this setting."
									+ "")
							.setPerformance(EConfigEntryPerformance.NONE)
							.build();
					
					// move into "shader compatibility"
					public static ConfigEntry<Double> brightnessMultiplier = new ConfigEntry.Builder<Double>() // TODO: Make this a float (the ClassicConfigGUI doesnt support floats)
							.set(1.0)
							.comment(""
									+ "How bright LOD colors are. \n"
									+ "\n"
									+ "0 = black \n"
									+ "1 = normal \n"
									+ "2 = near white")
							.build();
					
					public static ConfigEntry<Double> saturationMultiplier = new ConfigEntry.Builder<Double>() // TODO: Make this a float (the ClassicConfigGUI doesnt support floats)
							.set(1.0)
							.comment(""
									+ "How saturated LOD colors are. \n"
									+ "\n"
									+ "0 = black and white \n"
									+ "1 = normal \n"
									+ "2 = very saturated")
							.build();
					
					public static ConfigEntry<Boolean> enableCaveCulling = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment(""
									+ "If enabled caves will be culled \n"
									+ "\n"
									+ "NOTE: This feature is under development and \n"
									+ " it is VERY experimental! Please don't report \n"
									+ " any issues related to this feature. \n"
									+ "\n"
									+ "Additional Info: Currently this cull all faces \n"
									+ " with skylight value of 0 in dimensions that \n"
									+ " does not have a ceiling.")
							.build();
					
					public static ConfigEntry<Integer> caveCullingHeight = new ConfigEntry.Builder<Integer>()
							.setMinDefaultMax(-4096, 40, 4096)
							.comment(""
									+ "At what Y value should cave culling start?")
							.build();
					
					public static ConfigEntry<Integer> earthCurveRatio = new ConfigEntry.Builder<Integer>()
							.setMinDefaultMax(0, 0, 5000)
							.comment(""
									+ "This is the earth size ratio when applying the curvature shader effect. \n"
									+ "Note: Enabling this feature may cause rendering bugs. \n"
									+ "\n"
									+ "0 = flat/disabled \n"
									+ "1 = 1 to 1 (6,371,000 blocks) \n"
									+ "100 = 1 to 100 (63,710 blocks) \n"
									+ "10000 = 1 to 10000 (637.1 blocks) \n"
									+ "\n"
									+ "Note: Due to current limitations, the min value is 50 \n"
									+ "and the max value is 5000. Any values outside this range \n"
									+ "will be set to 0 (disabled).")
							.addListener(WorldCurvatureConfigEventHandler.INSTANCE)
							.build();
					
					public static ConfigEntry<Double> lodBias = new ConfigEntry.Builder<Double>()
							.setMinDefaultMax(0d, 0d, null)
							.comment(""
									+ "What the value should vanilla Minecraft's texture LodBias be? \n"
									+ "If set to 0 the mod wont overwrite vanilla's default (which so happens to also be 0)")
							.build();
					
					public static ConfigEntry<EDhApiLodShading> lodShading = new ConfigEntry.Builder<EDhApiLodShading>()
							.set(EDhApiLodShading.AUTO)
							.comment(""
									+ "How should LODs be shaded? \n"
									+ "\n"
									+ EDhApiLodShading.AUTO + ": Uses the same side shading as vanilla Minecraft blocks. \n"
									+ EDhApiLodShading.ENABLED + ": Simulates Minecraft's block shading for LODs. \n"
									+ "              Can be used to force LOD shading when using some shaders. \n"
									+ EDhApiLodShading.DISABLED + ": All LOD sides will be rendered with the same brightness. \n"
									+ "")
							.setPerformance(EConfigEntryPerformance.NONE)
							.build();
					
					public static ConfigEntry<Boolean> disableFrustumCulling = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment(""
									+ "If false LODs outside the player's camera \n"
									+ "aren't drawn, increasing GPU performance. \n"
									+ "\n"
									+ "If true all LODs are drawn, even those behind \n"
									+ "the player's camera, decreasing GPU performance. \n"
									+ "\n"
									+ "Disable this if you see LODs disappearing at the corners of your vision.")
							.build();
					
					public static ConfigEntry<Boolean> disableShadowPassFrustumCulling = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment(""
									+ "Identical to the other frustum culling option\n"
									+ "only used when a shader mod is present using the DH API\n"
									+ "and the shadow pass is being rendered.\n"
									+ "\n"
									+ "Disable this if shadows render incorrectly.")
							.build();
					
					public static ConfigEntry<EDhApiGrassSideRendering> grassSideRendering = new ConfigEntry.Builder<EDhApiGrassSideRendering>()
							.set(EDhApiGrassSideRendering.FADE_TO_DIRT)
							.comment(""
									+ "How should the sides and bottom of grass block LODs render? \n"
									+ "\n"
									+ EDhApiGrassSideRendering.AS_GRASS + ": all sides of dirt LOD's render using the top (green) color. \n"
									+ EDhApiGrassSideRendering.FADE_TO_DIRT + ": sides fade from grass to dirt. \n"
									+ EDhApiGrassSideRendering.AS_DIRT + ": sides render entirely as dirt. \n"
									+ "")
							.setPerformance(EConfigEntryPerformance.NONE)
							.build();
					
				}
				
			}
			
			public static class WorldGenerator
			{
				public static ConfigEntry<Boolean> enableDistantGeneration = new ConfigEntry.Builder<Boolean>()
						.setServersideShortName("enableDistantGeneration")
						.set(true)
						.comment(""
								+ " Should Distant Horizons slowly generate LODs \n"
								+ " outside the vanilla render distance?")
						.build();
				
				public static ConfigEntry<EDhApiDistantGeneratorMode> distantGeneratorMode = new ConfigEntry.Builder<EDhApiDistantGeneratorMode>()
						.setServersideShortName("distantGeneratorMode")
						.set(EDhApiDistantGeneratorMode.FEATURES)
						.comment(""
								+ "How detailed should LODs be generated outside the vanilla render distance? \n"
								+ "\n"
								+ EDhApiDistantGeneratorMode.PRE_EXISTING_ONLY + " \n"
								+ "Only create LOD data for already generated chunks. \n"
								+ "\n"
								//not currently implemented
								//+ EDhApiDistantGeneratorMode.BIOME_ONLY + " \n"
								//+ "Only generate the biomes and use the biome's \n"
								//+ "grass color, water color, or snow color. \n"
								//+ "Doesn't generate height, everything is shown at sea level. \n"
								//+ "- Fastest \n"
								//+ "\n"
								//+ EDhApiDistantGeneratorMode.BIOME_ONLY_SIMULATE_HEIGHT + " \n"
								//+ "Same as " + EDhApiDistantGeneratorMode.BIOME_ONLY + ", except instead \n"
								//+ "of always using sea level as the LOD height \n"
								//+ "different biome types (mountain, ocean, forest, etc.) \n"
								//+ "use predetermined heights to simulate having height data. \n"
								//+ "- Fastest \n"
								+ "\n"
								+ EDhApiDistantGeneratorMode.SURFACE + " \n"
								+ "Generate the world surface, \n"
								+ "this does NOT include trees, \n"
								+ "or structures. \n"
								+ "\n"
								+ EDhApiDistantGeneratorMode.FEATURES + " \n"
								+ "Generate everything except structures. \n"
								+ "WARNING: This may cause world generator bugs or instability when paired with certain world generator mods. \n"
								+ "")
						/*
							// FULL isn't currently implemented
							+ "\n"
							+ EDhApiDistantGeneratorMode.FULL + " \n"
							+ "Ask the local server to generate/load each chunk. \n"
							+ "This is the most compatible, but will cause server/simulation lag. \n"
							+ "- Slow (15-50 ms, with spikes up to 200 ms) \n"
						*/
						.build();
				
				public static ConfigEntry<Integer> worldGenerationTimeoutLengthInSeconds = new ConfigEntry.Builder<Integer>()
						.setServersideShortName("worldGenerationTimeout")
						.setMinDefaultMax(5, 60, 60 * 10/*10 minutes*/ )
						.comment(""
								+ "How long should a world generator thread run for before timing out? \n"
								+ "Note: If you are experiencing timeout errors it is better to lower your CPU usage first \n"
								+ "via the thread config before changing this value. \n"
								+ "")
						.build();
				
			}
			
			public static class LodBuilding
			{
				public static ConfigEntry<Integer> minTimeBetweenChunkUpdatesInSeconds = new ConfigEntry.Builder<Integer>()
						.setServersideShortName("minTimeBetweenChunkUpdates")
						.setMinDefaultMax(0, 1, 60)
						.comment(""
								+ "Determines how long must pass between LOD chunk updates before another. \n"
								+ "update can occur\n"
								+ "\n"
								+ "Increasing this value will reduce CPU load but may may cause \n"
								+ "LODs to become outdated more frequently or for longer. \n"
								+ "")
						.build();
				
				public static ConfigEntry<Boolean> onlyUseDhLightingEngine = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.comment(""
								+ "If false LODs will be lit by Minecraft's lighting engine when possible \n"
								+ "and fall back to the DH lighting engine only when necessary. \n"
								+ "\n"
								+ "If true LODs will only be lit using Distant Horizons' lighting engine. \n"
								+ "\n"
								+ "Generally it is best to leave this disabled and should only be enabled \n"
								+ "if there are lighting issues or for debugging. \n"
								+ "")
						.build();
				
				public static ConfigEntry<EDhApiDataCompressionMode> dataCompression = new ConfigEntry.Builder<EDhApiDataCompressionMode>()
						.set(EDhApiDataCompressionMode.LZMA2)
						.comment(""
								+ "What algorithm should be used to compress new LOD data? \n"
								+ "This setting will only affect new or updated LOD data, \n"
								+ "any data already generated when this setting is changed will be\n"
								+ "unaffected until it needs to be re-written to the database.\n"
								+ "\n"
								+ EDhApiDataCompressionMode.UNCOMPRESSED + " \n"
								+ "Should only be used for testing, is worse in every way vs [" + EDhApiDataCompressionMode.LZ4 + "].\n"
								+ "Expected Compression Ratio: 1.0\n"
								+ "Estimated average DTO read speed: 1.64 milliseconds\n"
								+ "Estimated average DTO write speed: 12.44 milliseconds\n"
								+ "\n"
								+ EDhApiDataCompressionMode.LZ4 + " \n"
								+ "A good option if you're CPU limited and have plenty of hard drive space.\n"
								+ "Expected Compression Ratio: 0.36\n"
								+ "Estimated average DTO read speed: 1.85 ms\n"
								+ "Estimated average DTO write speed: 9.46 ms\n"
								+ "\n"
								+ EDhApiDataCompressionMode.LZMA2 + " \n"
								+ "Slow but very good compression.\n"
								+ "Expected Compression Ratio: 0.14\n"
								+ "Estimated average DTO read speed: 11.89 ms\n"
								+ "Estimated average DTO write speed: 192.01 ms\n"
								+ "")
						.build();
				
				public static ConfigEntry<EDhApiWorldCompressionMode> worldCompression = new ConfigEntry.Builder<EDhApiWorldCompressionMode>()
						.set(EDhApiWorldCompressionMode.VISUALLY_EQUAL)
						.comment(""
								+ "How should block data be compressed when creating LOD data? \n"
								+ "This setting will only affect new or updated LOD data, \n"
								+ "any data already generated when this setting is changed will be\n"
								+ "unaffected until it is modified or re-loaded.\n"
								+ "\n"
								+ EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS + " \n"
								+ "Every block/biome change is recorded in the database. \n"
								+ "This is what DH 2.0 and 2.0.1 all used by default and will store a lot of data. \n"
								+ "Expected Compression Ratio: 1.0\n"
								+ "\n"
								+ EDhApiWorldCompressionMode.VISUALLY_EQUAL + " \n"
								+ "Only visible block/biome changes are recorded in the database. \n"
								+ "Hidden blocks (IE ores) are ignored.  \n"
								+ "Expected Compression Ratio: 0.7\n"
								+ "")
						.build();
				
				public static ConfigEntry<Boolean> showMigrationChatWarning = new ConfigEntry.Builder<Boolean>()
						.set(true)
						.comment(""
								+ "Determines if a message should be displayed in the chat when LOD migration starts. \n"
								+ "")
						.build();
				
			}
			
			public static class Multiplayer
			{
				public static ConfigEntry<EDhApiServerFolderNameMode> serverFolderNameMode = new ConfigEntry.Builder<EDhApiServerFolderNameMode>()
						.set(EDhApiServerFolderNameMode.NAME_ONLY)
						.comment(""
								+ "How should multiplayer save folders should be named? \n"
								+ "\n"
								+ EDhApiServerFolderNameMode.NAME_ONLY + ": Example: \"Minecraft Server\" \n"
								+ EDhApiServerFolderNameMode.IP_ONLY + ": Example: \"192.168.1.40\" \n"
								+ EDhApiServerFolderNameMode.NAME_IP + ": Example: \"Minecraft Server IP 192.168.1.40\" \n"
								+ EDhApiServerFolderNameMode.NAME_IP_PORT + ": Example: \"Minecraft Server IP 192.168.1.40:25565\""
								+ EDhApiServerFolderNameMode.NAME_IP_PORT_MC_VERSION + ": Example: \"Minecraft Server IP 192.168.1.40:25565 GameVersion 1.16.5\"")
						.build();
				
				public static ConfigEntry<Double> multiverseSimilarityRequiredPercent = new ConfigEntry.Builder<Double>()
						.setMinDefaultMax(0.0, 0.0, 1.0)
						.comment(""
								+ "AKA: Multiverse support. \n"
								+ "\n"
								+ "When matching levels (dimensions) of the same type (overworld, nether, etc.) the \n"
								+ "loaded chunks must be at least this percent the same \n"
								+ "in order to be considered the same world. \n"
								+ "\n"
								+ "Note: If you use portals to enter a dimension at two \n"
								+ "different locations the system will think the dimension \n"
								+ "it is two different levels. \n"
								+ "\n"
								+ "1.0 (100%) the chunks must be identical. \n"
								+ "0.5 (50%)  the chunks must be half the same. \n"
								+ "0.0 (0%)   disables multi-dimension support, \n"
								+ "            only one world will be used per dimension. \n"
								+ "\n"
								+ "If multiverse support is needed start with a value of 0.2 \n"
								+ "and tweak the sensitivity from there."
								+ "Lower values mean the matching is less strict.\n"
								+ "Higher values mean the matching is more strict.\n"
								+ "")
						.build();
				
				// not currently implemented
				public static ConfigEntry<Boolean> enableMultiverseNetworking = new ConfigEntry.Builder<Boolean>()
						.set(true)
						.comment(""
								+ "If true Distant Horizons will attempt to communicate with the connected \n"
								+ "server in order to improve multiverse support. \n"
								+ "")
						.build();
				
				public static ConfigCategory serverNetworking = new ConfigCategory.Builder().set(ServerNetworking.class).build();
				
				public static class ServerNetworking
				{
					public static ConfigUIComment generalSectionNote = new ConfigUIComment();
					public static ConfigEntry<Boolean> enableServerNetworking = new ConfigEntry.Builder<Boolean>()
							.setServersideShortName("enableServerNetworking")
							.set(true)
							.comment(""
									+ "WARNING!\n"
									+ "Server-client networking is not yet fully implemented!\n"
									+ "Both the server and client must be running the server-side fork with this option enabled\n"
									+ "for Distant Horizons data to be transceived.\n"
									+ "\n"
									+ "If true, the server and client will attempt to communicate to transceive Distant Horizons data.\n"
									+ "This allows for further distant generation and LOD updates on all clients.\n"
									+ "\n"
									+ "This should only be used on trusted servers with trusted players!\n"
									+ "")
							.build();
					
					
					public static ConfigEntry<Integer> rateLimitHitTolerance = new ConfigEntry.Builder<Integer>()
							.setServersideShortName("rateLimitHitTolerance")
							.setAppearance(EConfigEntryAppearance.ONLY_IN_FILE)
							.setMinDefaultMax(1, 10, 100)
							.comment(""
									+ "The amount of rate/concurrency limit hits a client can make in one second before being disconnected by the server.\n"
									+ "This setting only applies to the server and has no effect on clients."
									+ "")
							.build();
					
					
					
					public static ConfigEntry<Boolean> sendLevelKeys = new ConfigEntry.Builder<Boolean>()
							.setServersideShortName("sendLevelKeys")
							.setAppearance(EConfigEntryAppearance.ONLY_IN_FILE)
							.set(true)
							.comment(""
									+ "Makes the server send level keys for each world.\n"
									+ "Disable this if you use alternative ways to send level keys.\n"
									+ "")
							.build();
					public static ConfigEntry<String> levelKeyPrefix = new ConfigEntry.Builder<String>()
							.setServersideShortName("levelKeyPrefix")
							.setAppearance(EConfigEntryAppearance.ONLY_IN_FILE)
							.set("")
							.comment(""
									+ "Prefix of the level keys sent to the clients.\n"
									+ "Should be set to a unique value for each backend server behind a proxy,\n"
									+ "or empty if you don't use a proxy.\n"
									+ "")
							.build();
					
					
					public static ConfigUIComment generationSectionNote = new ConfigUIComment();
					public static ConfigEntry<Integer> generationRequestRCLimit = new ConfigEntry.Builder<Integer>()
							.setServersideShortName("generationRequestRCLimit")
							.setMinDefaultMax(1, 20, 100)
							.comment(""
									+ "Limits the amount of generation requests the server will handle."
									+ "")
							.build();
					
					public static ConfigEntry<Double> genTaskPriorityDistanceRatio = new ConfigEntry.Builder<Double>()
							.setServersideShortName("genTaskPriorityDistanceRatio")
							.setMinDefaultMax(1d, 3d, 10d)
							// todo: this comment should probably be rewritten, but it's outside my knowledge scope
							// - yeshi
							.comment(""
									+ "Controls the max ratio between distances of nearest unloaded sections of each priority.\n"
									+ "For example, value of 2 means that the nearest lower priority section will be allowed to stay\n"
									+ "unloaded only if it's at most 2x closer than one of a higher priority."
									+ "")
							.build();
					public static ConfigEntry<Integer> genTaskPriorityRequestRateLimit = new ConfigEntry.Builder<Integer>()
							.setServersideShortName("genTaskPriorityRequestRateLimit")
							.setMinDefaultMax(1, 50, 200)
							.comment(""
									+ "Limits the amount of LOD sections the client can request states for per second."
									+ "")
							.build();
					
					
					public static ConfigUIComment realTimeUpdatesSectionNote = new ConfigUIComment();
					public static ConfigEntry<Boolean> enableRealTimeUpdates = new ConfigEntry.Builder<Boolean>()
							.setServersideShortName("enableRealTimeUpdates")
							.set(false)
							.comment(""
									+ "If true, the client will receive real-time LOD updates for chunks outside the client's render distance."
									+ "")
							.build();
					
					
					public static ConfigUIComment loginDataSyncSectionNote = new ConfigUIComment();
					public static ConfigEntry<Boolean> enableLoginDataSync = new ConfigEntry.Builder<Boolean>()
							.setServersideShortName("enableLoginDataSync")
							.set(false)
							.comment(""
									+ "If true, clients will receive updated LODs on join if any changes occured since last join."
									+ "")
							.build();
					
					public static ConfigEntry<Integer> loginDataSyncRCLimit = new ConfigEntry.Builder<Integer>()
							.setServersideShortName("loginDataSyncRCLimit")
							.setMinDefaultMax(1, 50, 100)
							.comment(""
									+ "Limits the amount of sent/processed LOD *update* requests concurrently, per player."
									+ "")
							.build();
				}
				
			}
			
			public static class MultiThreading
			{
				public static final String THREAD_NOTE = ""
						+ "Multi-threading Note:\n"
						+ "If the total thread count in Distant Horizon's config is more threads than your CPU has cores,\n"
						+ "CPU performance may suffer if Distant Horizons has a lot to load or generate.\n"
						+ "This can be an issue when first loading into a world, when flying, and/or when generating new terrain.";
				
				public static final String THREAD_RUN_TIME_RATIO_NOTE = ""
						+ "If this value is less than 1.0, it will be treated as a percentage\n"
						+ "of time each thread can run before going idle.\n"
						+ "\n"
						+ "This can be used to reduce CPU usage if the thread count\n"
						+ "is already set to 1 for the given option, or more finely\n"
						+ "tune CPU performance.";
				
				
				public static final ConfigEntry<Integer> numberOfWorldGenerationThreads = new ConfigEntry.Builder<Integer>()
						.setServersideShortName("numberOfWorldGenerationThreads")
						.setMinDefaultMax(1,
								ThreadPresetConfigEventHandler.getWorldGenDefaultThreadCount(),
								Runtime.getRuntime().availableProcessors())
						.comment(""
								+ "How many threads should be used when generating LOD\n"
								+ "chunks outside the normal render distance?\n"
								+ "\n"
								+ "If you experience stuttering when generating distant LODs,\n"
								+ "decrease this number.\n"
								+ "If you want to increase LOD\n"
								+ "generation speed, increase this number.\n"
								+ "\n"
								+ THREAD_NOTE)
						.build();
				public static final ConfigEntry<Double> runTimeRatioForWorldGenerationThreads = new ConfigEntry.Builder<Double>()
						.setServersideShortName("runTimeRatioForWorldGenerationThreads")
						.setMinDefaultMax(0.01, ThreadPresetConfigEventHandler.getWorldGenDefaultRunTimeRatio(), 1.0)
						.comment(THREAD_RUN_TIME_RATIO_NOTE)
						.build();
				
				public static final ConfigEntry<Integer> numberOfFileHandlerThreads = new ConfigEntry.Builder<Integer>()
						.setServersideShortName("numberOfFileHandlerThreads")
						.setMinDefaultMax(1,
								ThreadPresetConfigEventHandler.getFileHandlerDefaultThreadCount(),
								Runtime.getRuntime().availableProcessors())
						.comment(""
								+ "How many threads should be used when reading/writing LOD data to/from disk? \n"
								+ "\n"
								+ "Increasing this number will cause LODs to load in faster, \n"
								+ "but may cause lag when loading a new world or when \n"
								+ "quickly flying through existing LODs. \n"
								+ "\n"
								+ THREAD_NOTE)
						.build();
				public static final ConfigEntry<Double> runTimeRatioForFileHandlerThreads = new ConfigEntry.Builder<Double>()
						.setServersideShortName("runTimeRatioForFileHandlerThreads")
						.setMinDefaultMax(0.01, ThreadPresetConfigEventHandler.getFileHandlerDefaultRunTimeRatio(), 1.0)
						.comment(THREAD_RUN_TIME_RATIO_NOTE)
						.build();
				
				public static final ConfigEntry<Integer> numberOfUpdatePropagatorThreads = new ConfigEntry.Builder<Integer>()
						.setMinDefaultMax(1,
								ThreadPresetConfigEventHandler.getUpdatePropagatorDefaultThreadCount(),
								Runtime.getRuntime().availableProcessors())
						.comment(""
								+ "How many threads should be used when applying LOD updates? \n"
								+ "An LOD update is the operation of down-sampling a high detail LOD \n"
								+ "into a lower detail one.\n"
								+ "\n"
								+ "This config can have a much higher number of threads \n"
								+ "assigned and much lower run time ratio vs other thread pools \n"
								+ "because the amount of time any particular thread may run is relatively low.\n"
								+ "\n"
								+ "This is because LOD updating only only partially thread safe, \n"
								+ "so between 40% and 60% of the time a given thread may end up \n"
								+ "waiting on another thread to finish updating the same LOD it also wants\n"
								+ "to work on.\n"
								+ "\n"
								+ THREAD_NOTE)
						.build();
				public static final ConfigEntry<Double> runTimeRatioForUpdatePropagatorThreads = new ConfigEntry.Builder<Double>()
						.setMinDefaultMax(0.01, ThreadPresetConfigEventHandler.getUpdatePropagatorDefaultRunTimeRatio(), 1.0)
						.comment(THREAD_RUN_TIME_RATIO_NOTE)
						.build();
				
				public static final ConfigEntry<Integer> numberOfLodBuilderThreads = new ConfigEntry.Builder<Integer>()
						.setServersideShortName("numberOfLodBuilderThreads")
						.setMinDefaultMax(1,
								ThreadPresetConfigEventHandler.getLodBuilderDefaultThreadCount(),
								Runtime.getRuntime().availableProcessors())
						.comment(""
								+ "How many threads should be used when building LODs? \n"
								+ "\n"
								+ "These threads run when terrain is generated, when\n"
								+ "certain graphics settings are changed, and when moving around the world. \n"
								+ "\n"
								+ THREAD_NOTE)
						.build();
				public static final ConfigEntry<Double> runTimeRatioForLodBuilderThreads = new ConfigEntry.Builder<Double>()
						.setServersideShortName("runTimeRatioForLodBuilderThreads")
						.setMinDefaultMax(0.01, ThreadPresetConfigEventHandler.getLodBuilderDefaultRunTimeRatio(), 1.0)
						.comment(THREAD_RUN_TIME_RATIO_NOTE)
						.build();
				public static final ConfigEntry<Boolean> enableLodBuilderThreadLimiting = new ConfigEntry.Builder<Boolean>()
						.setServersideShortName("enableLodBuilderThreadLimiting")
						.set(true)
						.comment(""
								+ "Should only be disabled if deadlock occurs and LODs refuse to update. \n"
								+ "This will cause CPU usage to drastically increase for the Lod Builder threads. \n"
								+ "\n"
								+ "Note that if deadlock did occur restarting MC may be necessary to stop the locked threads. \n"
								+ "")
						.build();
				
			}
			
			public static class GpuBuffers
			{
				public static ConfigEntry<EDhApiGpuUploadMethod> gpuUploadMethod = new ConfigEntry.Builder<EDhApiGpuUploadMethod>()
						.set(EDhApiGpuUploadMethod.AUTO)
						.comment(""
								+ "What method should be used to upload geometry to the GPU? \n"
								+ "\n"
								+ EDhApiGpuUploadMethod.AUTO + ": Picks the best option based on the GPU you have. \n"
								+ "\n"
								+ EDhApiGpuUploadMethod.BUFFER_STORAGE + ": Default if OpenGL 4.5 is supported. \n"
								+ "    Fast rendering, no stuttering. \n"
								+ "\n"
								+ EDhApiGpuUploadMethod.SUB_DATA + ": Backup option for NVIDIA. \n"
								+ "    Fast rendering but may stutter when uploading. \n"
								+ "\n"
								+ EDhApiGpuUploadMethod.BUFFER_MAPPING + ": Slow rendering but won't stutter when uploading. \n"
								+ "    Generally the best option for integrated GPUs. \n"
								+ "    Default option for AMD/Intel if OpenGL 4.5 isn't supported. \n"
								+ "    May end up storing buffers in System memory. \n"
								+ "    Fast rendering if in GPU memory, slow if in system memory, \n"
								+ "    but won't stutter when uploading.  \n"
								+ "\n"
								+ EDhApiGpuUploadMethod.DATA + ": Fast rendering but will stutter when uploading. \n"
								+ "    Backup option for AMD/Intel. \n"
								+ "    Fast rendering but may stutter when uploading. \n"
								+ "\n"
								+ "If you don't see any difference when changing these settings, \n"
								+ "or the world looks corrupted: restart your game."
								+ "")
						.build();
				
				public static ConfigEntry<Integer> gpuUploadPerMegabyteInMilliseconds = new ConfigEntry.Builder<Integer>()
						.setMinDefaultMax(0, 0, 50)
						.comment(""
								+ "How long should a buffer wait per Megabyte of data uploaded? \n"
								+ "Helpful resource for frame times: https://fpstoms.com \n"
								+ "\n"
								+ "Longer times may reduce stuttering but will make LODs \n"
								+ "transition and load slower. Change this to [0] for no timeout. \n"
								+ "\n"
								+ "NOTE:\n"
								+ "Before changing this config, try changing the \"GPU Upload method\" first. \n"
								+ "")
						.build();
				
			}
			
			public static class AutoUpdater
			{
				public static ConfigEntry<Boolean> enableAutoUpdater = new ConfigEntry.Builder<Boolean>()
						.set(
								!SingletonInjector.INSTANCE.get(IMinecraftSharedWrapper.class).getInstallationDirectory().getName().equals("run") // Guesses that a dev would use the directory called "run" as their running directory, and clients wont
						) // disable the updater in dev clients
						.comment(""
								+ "Automatically check for updates on game launch?")
						.build();
				
				public static ConfigEntry<Boolean> enableSilentUpdates = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.comment(""
								+ "Should Distant Horizons silently, automatically download and install new versions?")
						.build();
				
				public static ConfigEntry<EDhApiUpdateBranch> updateBranch = new ConfigEntry.Builder<EDhApiUpdateBranch>()
						.set(EDhApiUpdateBranch.AUTO)
						.comment(""
								+ "If DH should use the nightly (provided by Gitlab), or stable (provided by Modrinth) build. \n"
								+ "If ["+EDhApiUpdateBranch.AUTO+"] is selected DH will update to new stable releases if the current jar is a stable jar \n"
								+ "and will update to new nightly builds if the current jar is a nightly jar (IE the version number ends in '-dev')."
								+ "")
						.build();
			}
			
			public static class Logging
			{
				// TODO add change all option
				// TODO default to error chat and info file
				public static ConfigEntry<EDhApiLoggerMode> logWorldGenEvent = new ConfigEntry.Builder<EDhApiLoggerMode>()
						.setServersideShortName("logWorldGenEvent")
						.set(EDhApiLoggerMode.LOG_WARNING_TO_CHAT_AND_FILE)
						.comment(""
								+ "If enabled, the mod will log information about the world generation process. \n"
								+ "This can be useful for debugging.")
						.build();
				
				public static ConfigEntry<EDhApiLoggerMode> logWorldGenPerformance = new ConfigEntry.Builder<EDhApiLoggerMode>()
						.setServersideShortName("logWorldGenPerformance")
						.set(EDhApiLoggerMode.LOG_WARNING_TO_CHAT_AND_FILE)
						.comment(""
								+ "If enabled, the mod will log performance about the world generation process. \n"
								+ "This can be useful for debugging.")
						.build();
				
				public static ConfigEntry<EDhApiLoggerMode> logWorldGenLoadEvent = new ConfigEntry.Builder<EDhApiLoggerMode>()
						.setServersideShortName("logWorldGenPerformance")
						.set(EDhApiLoggerMode.LOG_WARNING_TO_CHAT_AND_FILE)
						.comment(""
								+ "If enabled, the mod will log information about the world generation process. \n"
								+ "This can be useful for debugging.")
						.build();
				
				public static ConfigEntry<EDhApiLoggerMode> logLodBuilderEvent = new ConfigEntry.Builder<EDhApiLoggerMode>()
						.setServersideShortName("logLodBuilderEvent")
						.set(EDhApiLoggerMode.LOG_WARNING_TO_CHAT_AND_INFO_TO_FILE)
						.comment(""
								+ "If enabled, the mod will log information about the LOD generation process. \n"
								+ "This can be useful for debugging.")
						.build();
				
				public static ConfigEntry<EDhApiLoggerMode> logRendererBufferEvent = new ConfigEntry.Builder<EDhApiLoggerMode>()
						.set(EDhApiLoggerMode.LOG_WARNING_TO_CHAT_AND_INFO_TO_FILE)
						.comment(""
								+ "If enabled, the mod will log information about the renderer buffer process. \n"
								+ "This can be useful for debugging.")
						.build();
				
				public static ConfigEntry<EDhApiLoggerMode> logRendererGLEvent = new ConfigEntry.Builder<EDhApiLoggerMode>()
						.set(EDhApiLoggerMode.LOG_WARNING_TO_CHAT_AND_INFO_TO_FILE)
						.comment(""
								+ "If enabled, the mod will log information about the renderer OpenGL process. \n"
								+ "This can be useful for debugging.")
						.build();
				
				public static ConfigEntry<EDhApiLoggerMode> logFileReadWriteEvent = new ConfigEntry.Builder<EDhApiLoggerMode>()
						.setServersideShortName("logFileReadWriteEvent")
						.set(EDhApiLoggerMode.LOG_WARNING_TO_CHAT_AND_INFO_TO_FILE)
						.comment(""
								+ "If enabled, the mod will log information about file read/write operations. \n"
								+ "This can be useful for debugging.")
						.build();
				
				public static ConfigEntry<EDhApiLoggerMode> logFileSubDimEvent = new ConfigEntry.Builder<EDhApiLoggerMode>()
						.setServersideShortName("logFileSubDimEvent")
						.set(EDhApiLoggerMode.LOG_WARNING_TO_CHAT_AND_INFO_TO_FILE)
						.comment(""
								+ "If enabled, the mod will log information about file sub-dimension operations. \n"
								+ "This can be useful for debugging.")
						.build();
				
				public static ConfigEntry<EDhApiLoggerMode> logNetworkEvent = new ConfigEntry.Builder<EDhApiLoggerMode>()
						.setServersideShortName("logNetworkEvent")
						.set(EDhApiLoggerMode.LOG_WARNING_TO_CHAT_AND_INFO_TO_FILE)
						.comment(""
								+ "If enabled, the mod will log information about network operations. \n"
								+ "This can be useful for debugging.")
						.build();
				
			}
			
			public static class Debugging
			{
				public static ConfigEntry<EDhApiRendererMode> rendererMode = new ConfigEntry.Builder<EDhApiRendererMode>()
						.set(EDhApiRendererMode.DEFAULT)
						.comment(""
								+ "What renderer is active? \n"
								+ "\n"
								+ EDhApiRendererMode.DEFAULT + ": Default lod renderer \n"
								+ EDhApiRendererMode.DEBUG + ": Debug testing renderer \n"
								+ EDhApiRendererMode.DISABLED + ": Disable rendering")
						.build();
				
				public static ConfigEntry<EDhApiDebugRendering> debugRendering = new ConfigEntry.Builder<EDhApiDebugRendering>()
						.set(EDhApiDebugRendering.OFF)
						.comment(""
								+ "Should specialized colors/rendering modes be used? \n"
								+ "\n"
								+ EDhApiDebugRendering.OFF + ": LODs will be drawn with their normal colors. \n"
								+ EDhApiDebugRendering.SHOW_DETAIL + ": LODs' color will be based on their detail level. \n"
								+ EDhApiDebugRendering.SHOW_BLOCK_MATERIAL + ": LODs' color will be based on their material. \n"
								+ EDhApiDebugRendering.SHOW_OVERLAPPING_QUADS + ": LODs will be drawn with total white, but overlapping quads will be drawn with red. \n"
								+ "")
						.build();
				
				public static ConfigEntry<Boolean> renderWireframe = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.comment(""
								+ "If enabled the LODs will render as wireframe."
								+ "")
						.build();
				
				
				// TODO add LOD-only mode to this
				public static ConfigEntry<Boolean> enableDebugKeybindings = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.comment(""
								+ "If true the F8 key can be used to cycle through the different debug modes. \n"
								+ "and the F6 key can be used to enable and disable LOD rendering.")
						.build();
				
				public static ConfigEntry<Boolean> lodOnlyMode = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.comment(""
								+ "If enabled this will disable (most) vanilla Minecraft rendering. \n"
								+ "\n"
								+ "NOTE: Do not report any issues when this mode is on! \n"
								+ "   This setting is only for fun and debugging. \n"
								+ "   Mod compatibility is not guaranteed.")
						.build();
				
				public static ConfigEntry<Boolean> enableWhiteWorld = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.comment(""
								+ "Stops vertex colors from being passed. \n"
								+ "Useful for debugging shaders")
						.build();
				
				public static ConfigEntry<Boolean> showOverlappingQuadErrors = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.comment(""
								+ "If true overlapping quads will be rendered as bright red for easy identification. \n"
								+ "If false the quads will be rendered normally. \n"
								+ "")
						.build();
				
				// Note: This will reset on game restart, and should have a warning on the tooltip
				public static ConfigEntry<Boolean> allowUnsafeValues = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
						.addListener(UnsafeValuesConfigListener.INSTANCE)
						.build();
				
				public static ConfigEntry<Boolean> columnBuilderDebugEnable = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
						.addListener(DebugColumnConfigEventHandler.INSTANCE)
						.build();
				public static ConfigEntry<Integer> columnBuilderDebugDetailLevel = new ConfigEntry.Builder<Integer>()
						.set((int) DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL)
						.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
						.addListener(DebugColumnConfigEventHandler.INSTANCE)
						.build();
				public static ConfigEntry<Integer> columnBuilderDebugXPos = new ConfigEntry.Builder<Integer>()
						.set(0)
						.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
						.addListener(DebugColumnConfigEventHandler.INSTANCE)
						.build();
				public static ConfigEntry<Integer> columnBuilderDebugZPos = new ConfigEntry.Builder<Integer>()
						.set(0)
						.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
						.addListener(DebugColumnConfigEventHandler.INSTANCE)
						.build();
				
				
				
				public static ConfigCategory debugWireframe = new ConfigCategory.Builder()
						.set(DebugWireframe.class)
						.build();
				
				public static class DebugWireframe
				{
					public static ConfigEntry<Boolean> enableRendering = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment(""
									+ "If enabled, various wireframes for debugging internal functions will be drawn. \n"
									+ "\n"
									+ "NOTE: There WILL be performance hit! \n"
									+ "   Additionally, only stuff that's loaded after you enable this \n"
									+ "   will render their debug wireframes.")
							.build();
					
					
					public static ConfigEntry<Boolean> showWorldGenQueue = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment("Render queued world gen tasks?")
							.build();
					
					public static ConfigEntry<Boolean> showRenderSectionStatus = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment("Render LOD section status?")
							.build();
					
					public static ConfigEntry<Boolean> showQuadTreeRenderStatus = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment("Render Quad Tree Rendering status?")
							.build();
					
					public static ConfigEntry<Boolean> showFullDataUpdateStatus = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment("Render full data update/lock status?")
							.build();
					
				}
				
				
				
				public static ConfigCategory openGl = new ConfigCategory.Builder()
						.set(OpenGl.class)
						.build();
				
				public static class OpenGl
				{
					public static ConfigEntry<Boolean> overrideVanillaGLLogger = new ConfigEntry.Builder<Boolean>()
							.set(ModInfo.IS_DEV_BUILD)
							.comment(""
									+ "Requires a reboot to change. \n"
									+ "")
							.build();
					
					public static ConfigEntry<EDhApiGLErrorHandlingMode> glErrorHandlingMode = new ConfigEntry.Builder<EDhApiGLErrorHandlingMode>()
							.set(ModInfo.IS_DEV_BUILD ? EDhApiGLErrorHandlingMode.LOG : EDhApiGLErrorHandlingMode.IGNORE)
							.comment(""
									+ "Defines how OpenGL errors are handled. \n"
									+ "May incorrectly catch OpenGL errors thrown by other mods. \n"
									+ "\n"
									+ EDhApiGLErrorHandlingMode.IGNORE + ": Do nothing. \n"
									+ EDhApiGLErrorHandlingMode.LOG + ": write an error to the log. \n"
									+ EDhApiGLErrorHandlingMode.LOG_THROW + ": write to the log and throw an exception. \n"
									+ "           Warning: this should only be enabled when debugging the LOD renderer \n"
									+ "           as it may break Minecraft's renderer when an exception is thrown. \n"
									+ "")
							.build();
					
					public static ConfigEntry<Integer> glContextMajorVersion = new ConfigEntry.Builder<Integer>()
							.setMinDefaultMax(0, 0, 4)
							.comment("" +
									"Can be changed if you experience crashing when loading into a world.\n" +
									"Note: setting to an invalid version may also cause the game to crash.\n" +
									"\n" +
									"Leaving this value at causes DH to try all supported GL versions. \n" +
									"\n" +
									"Defines the requested OpenGL context major version Distant Horizons will create. \n" +
									"Possible values (DH requires 3.2 or higher at minimum): \n" +
									"4.6, 4.5, 4.4, 4.3, 4.2, 4.1, 4.0 \n" +
									"3.3, 3.2 \n" +
									"")
							.build();
					public static ConfigEntry<Integer> glContextMinorVersion = new ConfigEntry.Builder<Integer>()
							.setMinDefaultMax(0, 0, 6)
							.comment("" +
									"Can be changed if you experience crashing when loading into a world.\n" +
									"Note: setting to an invalid version may also cause the game to crash.\n" +
									"\n" +
									"Defines the requested OpenGL context major version Distant Horizons will create. \n" +
									"Possible values (DH requires 3.2 or higher at minimum): \n" +
									"4.6, 4.5, 4.4, 4.3, 4.2, 4.1, 4.0 \n" +
									"3.3, 3.2 \n" +
									"")
							.build();
					
					public static ConfigEntry<EDhApiGlProfileMode> glProfileMode = new ConfigEntry.Builder<EDhApiGlProfileMode>()
							.set(EDhApiGlProfileMode.CORE)
							.comment("" +
									"Can be changed if you experience crashing when loading into a world.\n" +
									"\n" +
									"Defines the OpenGL context type Distant Horizon's will create. \n" +
									"Generally this should be left as [" + EDhApiGlProfileMode.CORE + "] unless there is an issue with your GPU driver. \n" +
									"Possible values: [" + StringUtil.join("],[", EDhApiGlProfileMode.values()) + "] \n" +
									"")
							.build();
					public static ConfigEntry<Boolean> enableGlForwardCompatibilityMode = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment("" +
									"Can be changed if you experience crashing when loading into a world.\n" +
									"\n" +
									"If true Distant Horizon's OpenGL contexts will be created with legacy OpenGL methods disabled. \n" +
									"Distant Horizons doesn't use any legacy OpenGL methods so normally this should be disabled. \n" +
									"")
							.build();
					public static ConfigEntry<Boolean> enableGlDebugContext = new ConfigEntry.Builder<Boolean>()
							.set(ModInfo.IS_DEV_BUILD)
							.comment("" +
									"Can be changed if you experience crashing when loading into a world.\n" +
									"\n" +
									"If true Distant Horizon's OpenGL contexts will be created with debugging enabled. \n" +
									"This allows for enhanced debugging but may throw warnings for other mods or active overlay software. \n" +
									"")
							.build();
					
				}
				
				
				
				// can be set to public inorder to show in the config file and UI
				public static ConfigCategory exampleConfigScreen = new ConfigCategory.Builder()
						.set(ExampleConfigScreen.class)
						.build();
				
				/** This class is used to debug the different features of the config GUI */
				// FIXME: WARNING: Some of the options in this class dont get show n in the default UI
				// This will throw a warning when opened in the default ui to tell you about it not showing
				public static class ExampleConfigScreen
				{
					// Defined in the lang, just a note about this screen
					public static ConfigUIComment debugConfigScreenNote = new ConfigUIComment();
					
					public static ConfigEntry<Boolean> boolTest = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.build();
					
					public static ConfigEntry<Byte> byteTest = new ConfigEntry.Builder<Byte>()
							.set((byte) 8)
							.build();
					
					public static ConfigEntry<Integer> intTest = new ConfigEntry.Builder<Integer>()
							.set(69420)
							.build();
					
					public static ConfigEntry<Double> doubleTest = new ConfigEntry.Builder<Double>()
							.set(420.69d)
							.build();
					
					public static ConfigEntry<Short> shortTest = new ConfigEntry.Builder<Short>()
							.set((short) 69)
							.build();
					
					public static ConfigEntry<Long> longTest = new ConfigEntry.Builder<Long>()
							.set(42069L)
							.build();
					
					public static ConfigEntry<Float> floatTest = new ConfigEntry.Builder<Float>()
							.set(0.42069f)
							.build();
					
					public static ConfigEntry<String> stringTest = new ConfigEntry.Builder<String>()
							.set("Test input box")
							.build();
					
					public static ConfigEntry<List<String>> listTest = new ConfigEntry.Builder<List<String>>()
							.set(new ArrayList<String>(Arrays.asList("option 1", "option 2", "option 3")))
							.build();
					
					public static ConfigEntry<Map<String, String>> mapTest = new ConfigEntry.Builder<Map<String, String>>()
							.set(new HashMap<String, String>())
							.build();
					
					public static ConfigUIButton uiButtonTest = new ConfigUIButton(() ->
					{
						new Thread(() ->
						{
							if (!GraphicsEnvironment.isHeadless())
							{
								JOptionPane.showMessageDialog(null, "Button pressed!", "UITester dialog", JOptionPane.INFORMATION_MESSAGE);
							}
							else
							{
								LOGGER.info("button pressed!");
							}
						});
					});
					
					public static ConfigCategory categoryTest = new ConfigCategory.Builder().set(CategoryTest.class).build();
					
					public static ConfigEntry<Integer> linkableTest = new ConfigEntry.Builder<Integer>()
							.set(420)
							.build();
					
					
					public static class CategoryTest
					{
						// The name of this can be anything as it will be overwritten by the name of the linked object
						public static ConfigLinkedEntry linkableTest = new ConfigLinkedEntry(ExampleConfigScreen.linkableTest);
						
					}
					
				}
				
			}
			
		}
		
		// TODO implement
		public static class ResetConfirmation
		{
			public static ConfigUIComment resetConfirmationNote = new ConfigUIComment();
			
			// TODO implement
			public static ConfigEntry<Boolean> resetAllSettings = new ConfigEntry.Builder<Boolean>()
					.set(false)
					.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
					//.addListener(null)
					.build();
			
		}
		
	}
	
	
	
	/** the setup should only be called once */
	private static boolean complicatedListenerSetupComplete = false;
	/**
	 * Runs any config setup that needs all (or most) config entries be initialized (not null),
	 * but doesn't necessarily require they have the right values yet. <br><br>
	 *
	 * Specially:
	 * Updates any config values that are UI only
	 * and adds any listeners that depend on multiple config values.
	 */
	public static void completeDelayedSetup()
	{
		if (!complicatedListenerSetupComplete)
		{
			complicatedListenerSetupComplete = true;
			
			try
			{
				// TODO automatically get all instances of AbstractPresetConfigEventHandler and fire "setUiOnlyConfigValues"
				ThreadPresetConfigEventHandler.INSTANCE.setUiOnlyConfigValues();
				RenderQualityPresetConfigEventHandler.INSTANCE.setUiOnlyConfigValues();
				QuickRenderToggleConfigEventHandler.INSTANCE.setUiOnlyConfigValues();
				RenderCacheConfigEventHandler.getInstance();
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected exception when running config delayed UI setup. Error: [" + e.getMessage() + "].", e);
			}
		}
	}
	
}