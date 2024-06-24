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

package com.seibel.distanthorizons.core.file.structure;

import com.google.common.net.PercentEscaper;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.file.subDimMatching.SubDimensionLevelMatcher;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.api.enums.config.EDhApiServerFolderNameMode;
import com.seibel.distanthorizons.core.level.IServerKeyedClientLevel;
import com.seibel.distanthorizons.core.util.objects.ParsedIp;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IDimensionTypeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;

import java.io.File;
import java.util.*;

/**
 * Designed for the Client_Only environment.
 */
public class ClientOnlySaveStructure extends AbstractSaveStructure
{
	final File folder;
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftSharedWrapper MC_SHARED = SingletonInjector.INSTANCE.get(IMinecraftSharedWrapper.class);
	public static final String INVALID_FILE_CHARACTERS_REGEX = "[\\\\/:*?\"<>|]";
	
	SubDimensionLevelMatcher subDimMatcher = null;
	final HashMap<ILevelWrapper, File> levelWrapperToFileMap = new HashMap<>();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ClientOnlySaveStructure()
	{
		this.folder = new File(getSaveStructureFolderPath());
		
		if (!this.folder.exists())
		{
			if (!this.folder.mkdirs())
			{
				LOGGER.warn("Unable to create folder [" + this.folder.getPath() + "]");
				//TODO: Deal with errors
			}
		}
	}
	
	
	
	//================//
	// folder methods //
	//================//
	
	@Override
	public File getLevelFolder(ILevelWrapper levelWrapper)
	{
		return this.levelWrapperToFileMap.computeIfAbsent(levelWrapper, (newLevelWrapper) ->
		{
			// Use the server provided key if one was provided
			if (newLevelWrapper instanceof IServerKeyedClientLevel)
			{
				IServerKeyedClientLevel keyedClientLevel = (IServerKeyedClientLevel) newLevelWrapper;
				LOGGER.info("Loading level " + newLevelWrapper.getDimensionType().getDimensionName() + " with key: " + keyedClientLevel.getServerLevelKey());
				// This world was identified by the server directly, so we can know for sure which folder to use.
				return new File(getSaveStructureFolderPath() + File.separatorChar + keyedClientLevel.getServerLevelKey());
			}
			
			
			// use multiverse matching if enabled and in multiplayer (the server should already know where the player is)
			if (newLevelWrapper instanceof IClientLevelWrapper && Config.Client.Advanced.Multiplayer.multiverseSimilarityRequiredPercent.get() != 0)
			{
				IClientLevelWrapper newClientLevelWrapper = (IClientLevelWrapper) newLevelWrapper;
				
				// create the matcher if one doesn't exist
				if (this.subDimMatcher == null || !this.subDimMatcher.isFindingLevel(newClientLevelWrapper))
				{
					LOGGER.info("Loading level " + newClientLevelWrapper.getDimensionType().getDimensionName());
					
					List<File> levelFolders = this.getDhDataFoldersForDimension(newClientLevelWrapper.getDimensionType());
					this.subDimMatcher = new SubDimensionLevelMatcher(newClientLevelWrapper, this.folder, levelFolders);
				}
				
				File levelFile = this.subDimMatcher.tryGetLevel();
				if (levelFile != null)
				{
					this.subDimMatcher.close();
					this.subDimMatcher = null;
				}
				return levelFile;
			}
			
			// we aren't using multiverse matching, shut down the matcher
			// TODO this additional call may not be needed
			if (this.subDimMatcher != null)
			{
				this.subDimMatcher.close();
				this.subDimMatcher = null;
			}
			
			
			// get the default folder
			return this.getLevelFolderWithoutSimilarityMatching(newLevelWrapper);
		});
	}
	
	private File getLevelFolderWithoutSimilarityMatching(ILevelWrapper level)
	{
		List<File> folders = this.getDhDataFoldersForDimension(level.getDimensionType());
		if (!folders.isEmpty() && folders.get(0) != null)
		{
			// use the first existing sub-dimension
			String folderName = folders.get(0).getName();
			LOGGER.info("Default Sub Dimension set to: [" + LodUtil.shortenString(folderName, 8) + "...]");
			return folders.get(0);
		}
		else
		{
			// no valid sub dimension was found, create a new one
			LOGGER.info("Default Sub Dimension not found. Creating: [" + level.getDimensionType().getDimensionName() + "]");
			return new File(this.folder, level.getDimensionType().getDimensionName());
		}
	}
	
	public List<File> getDhDataFoldersForDimension(IDimensionTypeWrapper dimensionType)
	{
		File[] folders = this.folder.listFiles();
		if (folders == null)
		{
			return new ArrayList<>(0);
		}
		
		// filter by dimension name
		String expectedDimName = dimensionType.getDimensionName();
		ArrayList<File> possibleDimFolders = new ArrayList<>();
		for (File dimFolder : folders)
		{
			if (dimFolder.isDirectory() && dimFolder.getName().equals(expectedDimName))
			{
				possibleDimFolders.addAll(getValidDhDimensionFolders(dimFolder));
			}
		}
		
		return possibleDimFolders;
	}
	
	
	@Override
	public File getRenderCacheFolder(ILevelWrapper level)
	{
		File levelFolder = this.levelWrapperToFileMap.get(level);
		if (levelFolder == null)
		{
			return null;
		}
		
		return levelFolder;
	}
	
	@Override
	public File getFullDataFolder(ILevelWrapper level)
	{
		File levelFolder = this.levelWrapperToFileMap.get(level);
		if (levelFolder == null)
		{
			return null;
		}
		
		return levelFolder;
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	/** Returns true if the given folder holds valid Lod Dimension data */
	private static ArrayList<File> getValidDhDimensionFolders(File potentialFolder)
	{
		ArrayList<File> subDimSaveFolders = new ArrayList<>();
		
		if (!potentialFolder.isDirectory())
		{
			// a valid level folder needs to be a folder
			return subDimSaveFolders;
		}
		
		
		File[] potentialLevelFolders = potentialFolder.listFiles();
		if (potentialLevelFolders != null)
		{
			// check each level folder
			for (File potentialFile : potentialLevelFolders)
			{
				if (potentialFile.isDirectory())
				{
					// check if this is a valid DH level folder
					File[] dataFolders = potentialFile.listFiles();
					if (dataFolders != null)
					{
						boolean isValidDhLevelFolder = false;
						for (File dataFolder : dataFolders)
						{
							// look for the DH database file
							if (dataFolder.getName().equalsIgnoreCase(AbstractSaveStructure.DATABASE_NAME))
							{
								isValidDhLevelFolder = true;
								break;
							}
						}
						
						if (isValidDhLevelFolder)
						{
							subDimSaveFolders.add(potentialFile);
						}
					}
				}
			}
		}
		
		return subDimSaveFolders;
	}
	
	
	private static String getSaveStructureFolderPath()
	{
		String path = MC_SHARED.getInstallationDirectory().getPath() + File.separatorChar
				+ "Distant_Horizons_server_data" + File.separatorChar
				+ getServerFolderName();
		return path;
	}
	
	/** Generated from the server the client is currently connected to. */
	private static String getServerFolderName()
	{
		// parse the current server's IP
		ParsedIp parsedIp = new ParsedIp(MC_CLIENT.getCurrentServerIp());
		String serverIpCleaned = parsedIp.ip.replaceAll(INVALID_FILE_CHARACTERS_REGEX, "");
		String serverPortCleaned = parsedIp.port != null ? parsedIp.port.replaceAll(INVALID_FILE_CHARACTERS_REGEX, "") : "";
		
		
		// determine the auto folder name format
		EDhApiServerFolderNameMode folderNameMode = Config.Client.Advanced.Multiplayer.serverFolderNameMode.get();
		String serverName = MC_CLIENT.getCurrentServerName().replaceAll(INVALID_FILE_CHARACTERS_REGEX, "");
		String serverMcVersion = MC_CLIENT.getCurrentServerVersion().replaceAll(INVALID_FILE_CHARACTERS_REGEX, "");
		
		
		// generate the folder name
		String folderName;
		switch (folderNameMode)
		{
			default:
			case NAME_ONLY:
				folderName = serverName;
				break;
			case IP_ONLY:
				folderName = serverIpCleaned;
				break;
			
			case NAME_IP:
				folderName = serverName + ", IP " + serverIpCleaned;
				break;
			case NAME_IP_PORT:
				folderName = serverName + ", IP " + serverIpCleaned + (serverPortCleaned.length() != 0 ? ("-" + serverPortCleaned) : "");
				break;
			case NAME_IP_PORT_MC_VERSION:
				folderName = serverName + ", IP " + serverIpCleaned + (serverPortCleaned.length() != 0 ? ("-" + serverPortCleaned) : "") + ", GameVersion " + serverMcVersion;
				break;
		}
		
		// PercentEscaper makes the characters all part of the standard alphameric character set
		// This fixes some issues when the server is named something in other languages
		return new PercentEscaper("", true).escape(folderName);
	}
	
	
	
	//==================//
	// override methods //
	//==================//
	
	@Override
	public void close() { this.subDimMatcher.close(); }
	
	@Override
	public String toString() { return "[" + this.getClass().getSimpleName() + "@" + this.folder.getName() + "]"; }
	
}
