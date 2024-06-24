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

import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

import com.seibel.distanthorizons.api.enums.config.EDhApiVanillaOverdraw;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.Pos2D;
import com.seibel.distanthorizons.core.render.vertexFormat.DefaultLodVertexFormats;
import com.seibel.distanthorizons.core.render.vertexFormat.LodVertexFormat;
import com.seibel.distanthorizons.core.util.gridList.EdgeDistanceBooleanGrid;
import com.seibel.distanthorizons.core.util.objects.UncheckedInterruptedException;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IDimensionTypeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import org.apache.logging.log4j.Logger;

/**
 * This class holds methods and constants that may be used in multiple places.
 *
 * @author James Seibel
 * @version 2022-12-5
 */
public class LodUtil
{
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	/**
	 * Vanilla render distances less than or equal to this will not allow partial
	 * overdraw. The VanillaOverdraw will either be ALWAYS or NEVER.
	 */
	public static final int MINIMUM_RENDER_DISTANCE_FOR_PARTIAL_OVERDRAW = 4;
	
	/**
	 * Vanilla render distances less than or equal to this will cause the overdraw to
	 * run at a smaller fraction of the vanilla render distance.
	 */
	public static final int MINIMUM_RENDER_DISTANCE_FOR_FAR_OVERDRAW = 11;
	
	
	
	
	/** The maximum number of LODs that can be rendered vertically */
	public static final int MAX_NUMBER_OF_VERTICAL_LODS = 32;
	
	/**
	 * alpha used when drawing chunks in debug mode
	 */
	public static final int DEBUG_ALPHA = 255; // 0 - 25;
	
	public static final int COLOR_DEBUG_BLACK = ColorUtil.rgbToInt(DEBUG_ALPHA, 0, 0, 0);
	public static final int COLOR_DEBUG_WHITE = ColorUtil.rgbToInt(DEBUG_ALPHA, 255, 255, 255);
	public static final int COLOR_INVISIBLE = ColorUtil.rgbToInt(0, 0, 0, 0);
	
	//FIXME: WE NEED MORE COLORS!!!!
	/**
	 * In order of nearest to farthest: <br>
	 * Red, Orange, Yellow, Green, Cyan, Blue, Magenta, white, gray, black
	 */
	public static final int[] DEBUG_DETAIL_LEVEL_COLORS = new int[]{
			ColorUtil.rgbToInt(255, 0, 0), ColorUtil.rgbToInt(255, 127, 0),
			ColorUtil.rgbToInt(255, 255, 0), ColorUtil.rgbToInt(127, 255, 0),
			ColorUtil.rgbToInt(0, 255, 0), ColorUtil.rgbToInt(0, 255, 127),
			ColorUtil.rgbToInt(0, 255, 255), ColorUtil.rgbToInt(0, 127, 255),
			ColorUtil.rgbToInt(0, 0, 255), ColorUtil.rgbToInt(127, 0, 255),
			ColorUtil.rgbToInt(255, 0, 255), ColorUtil.rgbToInt(255, 127, 255),
			ColorUtil.rgbToInt(255, 255, 255)};
	
	
	/** 512 blocks wide */
	public static final byte REGION_DETAIL_LEVEL = 9;
	/** 16 blocks wide */
	public static final byte CHUNK_DETAIL_LEVEL = 4;
	/** 1 block wide */
	public static final byte BLOCK_DETAIL_LEVEL = 0;
	
	/**
	 * measured in Blocks <br>
	 * detail level 9
	 * 512 x 512 blocks
	 */
	public static final short REGION_WIDTH = 512;
	/**
	 * measured in Blocks <br>
	 * detail level 4
	 * 16 x 16 blocks
	 */
	public static final short CHUNK_WIDTH = 16;
	
	
	/** number of chunks wide */
	public static final int REGION_WIDTH_IN_CHUNKS = REGION_WIDTH / CHUNK_WIDTH;
	
	
	/** maximum possible light level handled by Minecraft */
	public static final byte MAX_MC_LIGHT = 15;
	/** lowest possible light level handled by Minecraft */
	public static final byte MIN_MC_LIGHT = 0;
	
	
	
	/**
	 * This regex finds any characters that are invalid for use in a windows
	 * (and by extension mac and linux) file path
	 */
	public static final String INVALID_FILE_CHARACTERS_REGEX = "[\\\\/:*?\"<>|]";
	
	/**
	 * 64 MB by default is the maximum amount of memory that
	 * can be directly allocated. <br><br>
	 * <p>
	 * James knows there are commands to change that amount
	 * (specifically "-XX:MaxDirectMemorySize"), but
	 * He has no idea how to access that amount. <br>
	 * So for now this will be the hard limit. <br><br>
	 * <p>
	 * https://stackoverflow.com/questions/50499238/bytebuffer-allocatedirect-and-xmx
	 */
	public static final int MAX_ALLOCATABLE_DIRECT_MEMORY = 64 * 1024 * 1024;
	
	/** the format of data stored in the GPU buffers */
	public static final LodVertexFormat LOD_VERTEX_FORMAT = DefaultLodVertexFormats.POSITION_COLOR_BLOCK_LIGHT_SKY_LIGHT_MATERIAL_ID_NORMAL_INDEX;
	
	
	
	
	/**
	 * Gets the ServerWorld for the relevant dimension.
	 *
	 * @return null if there is no ServerWorld for the given dimension
	 */
	public static ILevelWrapper getServerWorldFromDimension(IDimensionTypeWrapper newDimension)
	{
		if (!MC_CLIENT.hasSinglePlayerServer())
			return null;
		
		Iterable<ILevelWrapper> worlds = MC_CLIENT.getAllServerWorlds();
		ILevelWrapper returnWorld = null;
		
		for (ILevelWrapper world : worlds)
		{
			if (world.getDimensionType() == newDimension)
			{
				returnWorld = world;
				break;
			}
		}
		
		return returnWorld;
	}
	
	
	public static int computeOverdrawOffset()
	{
		int chunkRenderDist = MC_RENDER.getRenderDistance() + 1;
		EDhApiVanillaOverdraw overdraw = EDhApiVanillaOverdraw.ALWAYS; //Config.Client.Advanced.Graphics.AdvancedGraphics.vanillaOverdraw.get();
		if (overdraw == EDhApiVanillaOverdraw.ALWAYS) return Integer.MAX_VALUE;
		
		int offset;
		if (overdraw == EDhApiVanillaOverdraw.NEVER)
		{
			offset = 0; //Config.Client.Advanced.Graphics.AdvancedGraphics.overdrawOffset.get();
		}
		else
		{
			if (chunkRenderDist < MINIMUM_RENDER_DISTANCE_FOR_FAR_OVERDRAW)
			{
				offset = 1;
			}
			else
			{
				offset = chunkRenderDist / 5;
			}
		}
		
		if (chunkRenderDist - offset <= 1)
		{
			return Integer.MAX_VALUE;
		}
		return offset;
	}
	
	/** not currently used since the new rendering system can't easily toggle single chunks to render */
	@Deprecated
	public static EdgeDistanceBooleanGrid readVanillaRenderedChunks()
	{
		int offset = computeOverdrawOffset();
		if (offset == Integer.MAX_VALUE) return null;
		int renderDist = MC_RENDER.getRenderDistance() + 1;
		
		Iterator<DhChunkPos> posIter = MC_RENDER.getVanillaRenderedChunks().iterator();
		
		return new EdgeDistanceBooleanGrid(new Iterator<Pos2D>()
		{
			@Override
			public boolean hasNext()
			{
				return posIter.hasNext();
			}
			
			@Override
			public Pos2D next()
			{
				DhChunkPos pos = posIter.next();
				return new Pos2D(pos.x, pos.z);
			}
		},
				MC_CLIENT.getPlayerChunkPos().x - renderDist,
				MC_CLIENT.getPlayerChunkPos().z - renderDist,
				renderDist * 2 + 1);
	}
	
	
	// True if the requested threshold pass, or false otherwise
	// For details, see:
	// https://stackoverflow.com/questions/3571203/what-are-runtime-getruntime-totalmemory-and-freememory
	public static boolean checkRamUsage(double minFreeMemoryPercent, int minFreeMemoryMB)
	{
		long freeMem = Runtime.getRuntime().freeMemory() + Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory();
		if (freeMem < minFreeMemoryMB * 1024L * 1024L) return false;
		long maxMem = Runtime.getRuntime().maxMemory();
		if (freeMem / (double) maxMem < minFreeMemoryPercent) return false;
		return true;
	}
	
	public static void checkInterrupts() throws InterruptedException
	{
		if (Thread.interrupted()) throw new InterruptedException();
	}
	
	/**
	 * Format a given string with params using log4j's MessageFormat
	 *
	 * @param str The string to format
	 * @param param The parameters to use in the string
	 * @return A message object. Call .toString() to get the string.
	 * @apiNote <b>This 'format' SHOULD ONLY be used for logging and debugging purposes!
	 * Do not use it for deserialization or naming of objects.</b>
	 * @author leetom
	 */
	public static String formatLog(String str, Object... param)
	{
		return LOGGER.getMessageFactory().newMessage(str, param).getFormattedMessage();
	}
	
	/**
	 * Returns a shortened version of the given string that is no longer than maxLength. <br>
	 * If null returns the empty string.
	 */
	public static String shortenString(String str, int maxLength)
	{
		if (str == null)
		{
			return "";
		}
		else
		{
			return str.substring(0, Math.min(str.length(), maxLength));
		}
	}
	
	public static class AssertFailureException extends RuntimeException
	{
		public AssertFailureException(String message)
		{
			super(message);
			debugBreak();
		}
		
	}
	
	public static void debugBreak()
	{
		int a = 0; // Set breakpoint here for auto pause on assert failure
	}
	
	public static void assertTrue(boolean condition)
	{
		if (!condition)
		{
			throw new AssertFailureException("Assertion failed");
		}
	}
	public static void assertTrue(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertFailureException("Assertion failed:\n " + message);
		}
	}
	public static void assertTrue(boolean condition, String message, Object... args)
	{
		if (!condition)
		{
			throw new AssertFailureException("Assertion failed:\n " + formatLog(message, args));
		}
	}
	public static void assertNotReach()
	{
		throw new AssertFailureException("Assert Not Reach failed");
	}
	public static void assertNotReach(String message)
	{
		throw new AssertFailureException("Assert Not Reach failed:\n " + message);
	}
	public static void assertNotReach(String message, Object... args)
	{
		throw new AssertFailureException("Assert Not Reach failed:\n " + formatLog(message, args));
	}
	public static void assertToDo()
	{
		throw new AssertFailureException("TODO!");
	}
	
	public static Throwable ensureUnwrap(Throwable t)
	{
		return t instanceof CompletionException ? ensureUnwrap(t.getCause()) : t;
	}
	
	public static boolean isInterruptOrReject(Throwable t)
	{
		Throwable unwrapped = LodUtil.ensureUnwrap(t);
		return UncheckedInterruptedException.isInterrupt(unwrapped) ||
				unwrapped instanceof RejectedExecutionException ||
				unwrapped instanceof CancellationException;
	}
	
}
