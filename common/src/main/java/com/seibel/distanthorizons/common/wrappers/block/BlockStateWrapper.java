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

package com.seibel.distanthorizons.common.wrappers.block;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
#elif MC_VER == MC_1_18_2 || MC_VER == MC_1_19_2
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.EmptyBlockGetter;
#else
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.EmptyBlockGetter;
#endif

public class BlockStateWrapper implements IBlockStateWrapper
{
	/** example "minecraft:water" */
	public static final String RESOURCE_LOCATION_SEPARATOR = ":";
	/** example "minecraft:water_STATE_{level:0}" */
	public static final String STATE_STRING_SEPARATOR = "_STATE_";
	
	
	// must be defined before AIR, otherwise a null pointer will be thrown
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
    public static final ConcurrentHashMap<BlockState, BlockStateWrapper> WRAPPER_BY_BLOCK_STATE = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, BlockStateWrapper> WRAPPER_BY_RESOURCE_LOCATION = new ConcurrentHashMap<>();
	
	public static final String AIR_STRING = "AIR";
	public static final BlockStateWrapper AIR = new BlockStateWrapper(null, null);
	
	public static final String DIRT_RESOURCE_LOCATION_STRING = "minecraft:dirt";
	
	// TODO: Make this changeable through the config
	public static final String[] RENDERER_IGNORED_BLOCKS_RESOURCE_LOCATIONS = { AIR_STRING, "minecraft:barrier", "minecraft:structure_void", "minecraft:light", "minecraft:tripwire" };
	public static HashSet<IBlockStateWrapper> rendererIgnoredBlocks = null;
	
	/** keep track of broken blocks so we don't log every time */
	private static final HashSet<ResourceLocation> BrokenResourceLocations = new HashSet<>();
	
	
	
	// properties //
	
	public final BlockState blockState;
	/** technically final, but since it requires a method call to generate it can't be marked as such */
	private String serialString;
	private final int hashCode;
	/** 
	 * Cached opacity value, -1 if not populated. <br>
	 * Should be between {@link IBlockStateWrapper#FULLY_OPAQUE} and {@link IBlockStateWrapper#FULLY_OPAQUE}
	 */
	private int opacity = -1;
	/** used by the Iris shader mod to determine how each LOD should be rendered */
	private byte irisBlockMaterialId = 0;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public static BlockStateWrapper fromBlockState(BlockState blockState, ILevelWrapper levelWrapper)
	{
		if (blockState == null || blockState.isAir())
		{
			return AIR;
		}
		
		
		if (WRAPPER_BY_BLOCK_STATE.containsKey(blockState))
		{
			return WRAPPER_BY_BLOCK_STATE.get(blockState);
		}
		else
		{
			BlockStateWrapper newWrapper = new BlockStateWrapper(blockState, levelWrapper);
			WRAPPER_BY_BLOCK_STATE.put(blockState, newWrapper);
			return newWrapper;
		}
	}
	
	private BlockStateWrapper(BlockState blockState, ILevelWrapper levelWrapper)
	{
		this.blockState = blockState;
		this.serialString = this.serialize(levelWrapper);
		this.hashCode = Objects.hash(this.serialString);
		this.irisBlockMaterialId = this.calculateIrisBlockMaterialId();
		
		//LOGGER.trace("Created BlockStateWrapper ["+this.serialString+"] for ["+blockState+"] with material ID ["+this.irisBlockMaterialId+"]");
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	/** 
	 * Requires a {@link ILevelWrapper} since {@link BlockStateWrapper#deserialize(String,ILevelWrapper)} also requires one. 
	 * This way the method won't accidentally be called before the deserialization can be completed.
	 */
	public static HashSet<IBlockStateWrapper> getRendererIgnoredBlocks(ILevelWrapper levelWrapper)
	{
		// use the cached version if possible
		if (rendererIgnoredBlocks != null)
		{
			return rendererIgnoredBlocks;
		}
		
		
		// deserialize each of the given resource locations
		HashSet<IBlockStateWrapper> blockStateWrappers = new HashSet<>();
		for (String blockResourceLocation : RENDERER_IGNORED_BLOCKS_RESOURCE_LOCATIONS)
		{
			try
			{
				BlockStateWrapper DefaultBlockStateToIgnore = (BlockStateWrapper) deserialize(blockResourceLocation, levelWrapper);
				blockStateWrappers.add(DefaultBlockStateToIgnore);
				
				if (DefaultBlockStateToIgnore == AIR)
				{
					continue;
				}
				
				// add all possible blockstates (to account for light blocks with different light values and such)
				List<BlockState> blockStatesToIgnore = DefaultBlockStateToIgnore.blockState.getBlock().getStateDefinition().getPossibleStates();
				for (BlockState blockState : blockStatesToIgnore)
				{
					BlockStateWrapper newBlockToIgnore = BlockStateWrapper.fromBlockState(blockState, levelWrapper);
					blockStateWrappers.add(newBlockToIgnore);
				}
			}
			catch (IOException e)
			{
				LOGGER.warn("Unable to deserialize rendererIgnoredBlock with the resource location: ["+blockResourceLocation+"]. Error: "+e.getMessage(), e);
			}
		}
		
		rendererIgnoredBlocks = blockStateWrappers;
		return rendererIgnoredBlocks;
	}
	
	
	
	//=================//
	// wrapper methods //
	//=================//
	
	@Override
	public int getOpacity()
	{
		// use the cached opacity value if possible
		if (this.opacity != -1)
		{
			return this.opacity;
		}
		
		
		// this method isn't perfect, but works well enough for our use case
		int opacity;
		if (this.isAir())
		{
			opacity = FULLY_TRANSPARENT;
		}
		else if (this.isLiquid() && !this.blockState.canOcclude())
		{
			// probably not a waterlogged block (which should block light entirely)
			
			// +1 to indicate that the block is translucent (in between transparent and opaque) 
			opacity = FULLY_TRANSPARENT + 1;
		}
		else if (this.blockState.propagatesSkylightDown(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
		{
			opacity = FULLY_TRANSPARENT;
		}
		else
		{
			// default for all other blocks
			opacity = FULLY_OPAQUE;
		}
		
		
		this.opacity = opacity;
		return this.opacity;
	}
	
	@Override
	public int getLightEmission() { return (this.blockState != null) ? this.blockState.getLightEmission() : 0; }
	
	@Override
	public String getSerialString() { return this.serialString; }
	
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		
		if (obj == null || this.getClass() != obj.getClass())
		{
			return false;
		}
		
		BlockStateWrapper that = (BlockStateWrapper) obj;
		// the serialized value is used so we can test the contents instead of the references
		return Objects.equals(this.getSerialString(), that.getSerialString());
	}
	
	@Override
	public int hashCode() { return this.hashCode; } 
	
	
	@Override
	public Object getWrappedMcObject() { return this.blockState; }
	
	@Override
	public boolean isAir() { return this.isAir(this.blockState); }
	public boolean isAir(BlockState blockState) { return blockState == null || blockState.isAir(); }
	
	@Override
	public boolean isSolid()
	{
        #if MC_VER < MC_1_20_1
		return this.blockState.getMaterial().isSolid();
        #else
		return !this.blockState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
        #endif
	}
	
	@Override
	public boolean isLiquid()
	{
		if (this.isAir())
		{
			return false;
		}
		
        #if MC_VER < MC_1_20_1
		return this.blockState.getMaterial().isLiquid() || !this.blockState.getFluidState().isEmpty();
        #else
		return !this.blockState.getFluidState().isEmpty();
        #endif
	}
	
	@Override
	public byte getIrisBlockMaterialId() { return this.irisBlockMaterialId; }
	
	@Override
	public String toString() { return this.getSerialString(); }
	
	
	
	//=======================//
	// serialization methods //
	//=======================//
	
	private String serialize(ILevelWrapper levelWrapper)
	{
		if (this.blockState == null)
		{
			return AIR_STRING;
		}
		
		
		
		// older versions of MC have a static registry
		#if MC_VER > MC_1_17_1
		Level level = (Level)levelWrapper.getWrappedMcObject();
		net.minecraft.core.RegistryAccess registryAccess = level.registryAccess();
		#endif
		
		ResourceLocation resourceLocation;
		#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
		resourceLocation = Registry.BLOCK.getKey(this.blockState.getBlock());
		#elif MC_VER == MC_1_18_2 || MC_VER == MC_1_19_2
		resourceLocation = registryAccess.registryOrThrow(Registry.BLOCK_REGISTRY).getKey(this.blockState.getBlock());
		#else
		resourceLocation = registryAccess.registryOrThrow(Registries.BLOCK).getKey(this.blockState.getBlock());
		#endif
		
		
		
		if (resourceLocation == null)
		{
			LOGGER.warn("No ResourceLocation found, unable to serialize: " + this.blockState);
			return AIR_STRING;
		}
		
		this.serialString = resourceLocation.getNamespace() + RESOURCE_LOCATION_SEPARATOR + resourceLocation.getPath()
				+ STATE_STRING_SEPARATOR + serializeBlockStateProperties(this.blockState);
		
		return this.serialString;
	}
	
	
	/** will only work if a level is currently loaded */
	public static IBlockStateWrapper deserialize(String resourceStateString, ILevelWrapper levelWrapper) throws IOException
	{
		// we need the final string for the concurrent hash map later
		final String finalResourceStateString = resourceStateString;
		
		if (finalResourceStateString.equals(AIR_STRING) || finalResourceStateString.equals("")) // the empty string shouldn't normally happen, but just in case
		{
			return AIR;
		}
		
		// attempt to use the existing wrapper
		if (WRAPPER_BY_RESOURCE_LOCATION.containsKey(finalResourceStateString))
		{
			return WRAPPER_BY_RESOURCE_LOCATION.get(finalResourceStateString);
		}
		
		
		
		// if no wrapper is found, default to air
		BlockStateWrapper foundWrapper = AIR;
		try
		{
			// try to parse out the BlockState
			String blockStatePropertiesString = null; // will be null if no properties were included
			int stateSeparatorIndex = resourceStateString.indexOf(STATE_STRING_SEPARATOR);
			if (stateSeparatorIndex != -1)
			{
				// blockstate properties found
				blockStatePropertiesString = resourceStateString.substring(stateSeparatorIndex + STATE_STRING_SEPARATOR.length());
				resourceStateString = resourceStateString.substring(0, stateSeparatorIndex);
			}
			
			// parse the resource location
			int separatorIndex = resourceStateString.indexOf(RESOURCE_LOCATION_SEPARATOR);
			if (separatorIndex == -1)
			{
				throw new IOException("Unable to parse Resource Location out of string: [" + resourceStateString + "].");
			}
			
			ResourceLocation resourceLocation;
			try
			{
				#if MC_VER < MC_1_21
					resourceLocation = new ResourceLocation(resourceStateString.substring(0, separatorIndex), resourceStateString.substring(separatorIndex + 1));
				#else
				resourceLocation = ResourceLocation.fromNamespaceAndPath(resourceStateString.substring(0, separatorIndex), resourceStateString.substring(separatorIndex + 1));
				#endif
			}
			catch (Exception e)
			{
				throw new IOException("No Resource Location found for the string: [" + resourceStateString + "] Error: [" + e.getMessage() + "].");
			}
			
			
			
			// attempt to get the BlockState from all possible BlockStates
			try
			{
				
				#if MC_VER > MC_1_17_1
			Level level = (Level)Objects.requireNonNull(levelWrapper.getWrappedMcObject());
				#endif
				
				Block block;
				#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
				block = Registry.BLOCK.get(resourceLocation);
				#elif MC_VER == MC_1_18_2 || MC_VER == MC_1_19_2
				net.minecraft.core.RegistryAccess registryAccess = level.registryAccess();
				block = registryAccess.registryOrThrow(Registry.BLOCK_REGISTRY).get(resourceLocation);
				#else
				net.minecraft.core.RegistryAccess registryAccess = level.registryAccess();
				block = registryAccess.registryOrThrow(Registries.BLOCK).get(resourceLocation);
				#endif
				
				
				if (block == null)
				{
					// shouldn't normally happen, but here to make the compiler happy
					if (!BrokenResourceLocations.contains(resourceLocation))
					{
						BrokenResourceLocations.add(resourceLocation);
						LOGGER.warn("Unable to find BlockState with the resourceLocation [" + resourceLocation + "] and properties: [" + blockStatePropertiesString + "]. Air will be used instead, some data may be lost.");
					}
					
					return AIR;
				}
				
				
				// attempt to find the blockstate from all possibilities
				BlockState foundState = null;
				if (blockStatePropertiesString != null)
				{
					List<BlockState> possibleStateList = block.getStateDefinition().getPossibleStates();
					for (BlockState possibleState : possibleStateList)
					{
						String possibleStatePropertiesString = serializeBlockStateProperties(possibleState);
						if (possibleStatePropertiesString.equals(blockStatePropertiesString))
						{
							foundState = possibleState;
							break;
						}
					}
				}
				
				// use the default if no state was found or given
				if (foundState == null)
				{
					if (blockStatePropertiesString != null)
					{
						// we should have found a blockstate, but didn't
						if (!BrokenResourceLocations.contains(resourceLocation))
						{
							BrokenResourceLocations.add(resourceLocation);
							LOGGER.warn("Unable to find BlockState for Block [" + resourceLocation + "] with properties: [" + blockStatePropertiesString + "]. Using the default block state.");
						}
					}
					
					foundState = block.defaultBlockState();
				}
				
				foundWrapper = new BlockStateWrapper(foundState, levelWrapper);
				return foundWrapper;
			}
			catch (Exception e)
			{
				throw new IOException("Failed to deserialize the string [" + finalResourceStateString + "] into a BlockStateWrapper: " + e.getMessage(), e);
			}
		}
		finally
		{
			// put if absent in case two threads deserialize at the same time
			// unfortunately we can't put everything in a computeIfAbsent() since we also throw exceptions
			WRAPPER_BY_RESOURCE_LOCATION.putIfAbsent(finalResourceStateString, foundWrapper);
		}
	}
	
	/** used to compare and save BlockStates based on their properties */
	private static String serializeBlockStateProperties(BlockState blockState)
	{
		// get the property list for this block (doesn't contain this block state's values, just the names and possible values)
		java.util.Collection<net.minecraft.world.level.block.state.properties.Property<?>> blockPropertyCollection = blockState.getProperties();
		
		// alphabetically sort the list so they are always in the same order
		List<net.minecraft.world.level.block.state.properties.Property<?>> sortedBlockPropteryList = new ArrayList<>(blockPropertyCollection);
		sortedBlockPropteryList.sort((a, b) -> a.getName().compareTo(b.getName()));
		
		
		StringBuilder stringBuilder = new StringBuilder();
		for (net.minecraft.world.level.block.state.properties.Property<?> property : sortedBlockPropteryList)
		{
			String propertyName = property.getName();
			
			String value = "NULL";
			if (blockState.hasProperty(property))
			{
				value = blockState.getValue(property).toString();
			}
			
			stringBuilder.append("{");
			stringBuilder.append(propertyName).append(RESOURCE_LOCATION_SEPARATOR).append(value);
			stringBuilder.append("}");
		}
		
		return stringBuilder.toString();
	}
	
	
	
	//==============//
	// Iris methods //
	//==============//
	
	private byte calculateIrisBlockMaterialId() 
	{
		if (this.blockState == null)
		{
			return IrisBlockMaterial.AIR;
		}
		
		
		String serialString = this.getSerialString().toLowerCase();
		
		if (this.blockState.is(BlockTags.LEAVES) 
			|| serialString.contains("bamboo") 
			|| serialString.contains("cactus")
			|| serialString.contains("chorus_flower")
			|| serialString.contains("mushroom")
			) 
		{
			return IrisBlockMaterial.LEAVES;
		}
		else if (this.blockState.is(Blocks.LAVA))
		{
			return IrisBlockMaterial.LAVA;
		}
		else if (this.isLiquid() || this.blockState.is(Blocks.WATER))
		{
			return IrisBlockMaterial.WATER;
		}
		else if (this.blockState.getSoundType() == SoundType.WOOD
				|| serialString.contains("root")
				#if MC_VER >= MC_1_19_4
				|| this.blockState.getSoundType() == SoundType.CHERRY_WOOD
				#endif
				) 
		{
			return IrisBlockMaterial.WOOD;
		}
		else if (this.blockState.getSoundType() == SoundType.METAL
				#if MC_VER >= MC_1_19_2
				|| this.blockState.getSoundType() == SoundType.COPPER
				#endif
				#if MC_VER >= MC_1_20_4
				|| this.blockState.getSoundType() == SoundType.COPPER_BULB
				|| this.blockState.getSoundType() == SoundType.COPPER_GRATE
				#endif
				) 
		{
			return IrisBlockMaterial.METAL;
		}
		else if (serialString.contains("grass_block"))
		{
			return IrisBlockMaterial.GRASS;
		}
		else if (
			serialString.contains("dirt")
			|| serialString.contains("gravel")
			|| serialString.contains("mud")
			|| serialString.contains("podzol")
			|| serialString.contains("mycelium")
			)
		{
			return IrisBlockMaterial.DIRT;
		}
		#if MC_VER >= MC_1_17_1
		else if (this.blockState.getSoundType() == SoundType.DEEPSLATE
				|| this.blockState.getSoundType() == SoundType.DEEPSLATE_BRICKS
				|| this.blockState.getSoundType() == SoundType.DEEPSLATE_TILES 
				|| this.blockState.getSoundType() == SoundType.POLISHED_DEEPSLATE
				|| serialString.contains("deepslate") ) 
		{
			return IrisBlockMaterial.DEEPSLATE;
		} 
		#endif
		else if (this.serialString.contains("snow"))
		{
			return IrisBlockMaterial.SNOW;
		} 
		else if (serialString.contains("sand"))
		{
			return IrisBlockMaterial.SAND;
		}
		else if (serialString.contains("terracotta"))
		{
			return IrisBlockMaterial.TERRACOTTA;
		} 
		else if (this.blockState.is(BlockTags.BASE_STONE_NETHER)) 
		{
			return IrisBlockMaterial.NETHER_STONE;
		} 
		else if (serialString.contains("stone")
				|| serialString.contains("ore")) 
		{
			return IrisBlockMaterial.STONE;
		}
		else if (this.blockState.getLightEmission() > 0) 
		{
			return IrisBlockMaterial.ILLUMINATED;
		}
		else
		{
			return IrisBlockMaterial.UNKOWN;
		}
	}
	
}