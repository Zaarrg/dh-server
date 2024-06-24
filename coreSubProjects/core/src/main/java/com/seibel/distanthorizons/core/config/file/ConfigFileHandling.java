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

package com.seibel.distanthorizons.core.config.file;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.config.ConfigBase;
import com.seibel.distanthorizons.core.config.types.AbstractConfigType;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles reading and writing config files.
 *
 * @author coolGi
 * @version 2023-8-26
 */
public class ConfigFileHandling
{
	public final ConfigBase configBase;
	public final Path configPath;
	
	private final Logger LOGGER;
	
	/** This is the object for night-config */
	private final CommentedFileConfig nightConfig;
	
	public ConfigFileHandling(ConfigBase configBase, Path configPath)
	{
		this.LOGGER = LogManager.getLogger(this.getClass().getSimpleName() + ", " + configBase.modID);
		this.configBase = configBase;
		this.configPath = configPath;
		
		this.nightConfig = CommentedFileConfig.builder(this.configPath.toFile()).build();
	}
	
	/** Saves the entire config to the file */
	public void saveToFile()
	{
		saveToFile(this.nightConfig);
	}
	/** Saves the entire config to the file */
	public void saveToFile(CommentedFileConfig nightConfig)
	{
		if (!Files.exists(configPath)) // Try to check if the config exists
		{
			reCreateFile(configPath);
		}
		
		
		loadNightConfig(nightConfig);
		
		
		for (AbstractConfigType<?, ?> entry : this.configBase.entries)
		{
			if (ConfigEntry.class.isAssignableFrom(entry.getClass()))
			{
				createComment((ConfigEntry<?>) entry, nightConfig);
				saveEntry((ConfigEntry<?>) entry, nightConfig);
			}
		}
		
		
		try
		{
			nightConfig.save();
		}
		catch (Exception e)
		{
			// If it fails to save, crash game
			SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class).crashMinecraft("Failed to save config at [" + configPath.toString() + "]", e);
		}
	}
	
	/**
	 * Loads the entire config from the file
	 *
	 * @apiNote This overwrites any value currently stored in the config
	 */
	public void loadFromFile()
	{
		int currentCfgVersion = configBase.configVersion;
		try
		{
			// Dont load the real `this.nightConfig`, instead create a tempoary one
			CommentedFileConfig tmpNightConfig = CommentedFileConfig.builder(this.configPath.toFile()).build();
			tmpNightConfig.load();
			// Attempt to get the version number
			currentCfgVersion = (Integer) tmpNightConfig.get("_version");
			tmpNightConfig.close();
		} catch (Exception ignored) { }
		
		if (currentCfgVersion == configBase.configVersion)
		{}
		else if (currentCfgVersion > configBase.configVersion)
		{
			LOGGER.warn("Found config version [" + String.valueOf(currentCfgVersion) + "] which is newer than current mods config version of [" + String.valueOf(configBase.configVersion) + "]. You may have downgraded the mod and items may have been moved, you have been warned");
		}
		else // if (currentCfgVersion < configBase.configVersion)
		{
			LOGGER.warn(configBase.modName +" config is of an older version, currently there is no config updater... so resetting config");
			try
			{
				Files.delete(configPath);
			}
			catch (Exception e)
			{
				LOGGER.error(e);
			}
		}
		
		loadFromFile(nightConfig);
		nightConfig.set("_version", configBase.configVersion);
	}
	/**
	 * Loads the entire config from the file
	 *
	 * @apiNote This overwrites any value currently stored in the config
	 */
	public void loadFromFile(CommentedFileConfig nightConfig)
	{
		// Attempt to load the file and if it fails then save config to file
		if (Files.exists(configPath))
		{
			loadNightConfig(nightConfig);
		}
		else
		{
			reCreateFile(configPath);
		}
		
		
		// Load all the entries
		for (AbstractConfigType<?, ?> entry : this.configBase.entries)
		{
			if (
					ConfigEntry.class.isAssignableFrom(entry.getClass()) &&
					entry.getAppearance().showInFile
			)
			{
				createComment((ConfigEntry<?>) entry, nightConfig);
				loadEntry((ConfigEntry<?>) entry, nightConfig);
			}
		}
		
		
		try
		{
			nightConfig.save();
		}
		catch (Exception e)
		{
			// If it fails to save, crash game
			SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class).crashMinecraft("Failed to save config at [" + configPath.toString() + "]", e);
		}
	}
	
	
	
	
	// Save an entry when only given the entry
	public void saveEntry(ConfigEntry<?> entry)
	{
		saveEntry(entry, nightConfig);
		nightConfig.save();
	}
	/** Save an entry */
	public void saveEntry(ConfigEntry<?> entry, CommentedFileConfig workConfig)
	{
		if (!entry.getAppearance().showInFile) return;
		if (SingletonInjector.INSTANCE.get(IMinecraftSharedWrapper.class).isDedicatedServer() && entry.getServersideShortName() == null)
			return;
		if (entry.getTrueValue() == null)
			throw new IllegalArgumentException("Entry [" + entry.getNameWCategory() + "] is null, this may be a problem with [" + configBase.modName + "]. Please contact the authors");
		
		workConfig.set(entry.getNameWCategory(), ConfigTypeConverters.attemptToConvertToString(entry.getType(), entry.getTrueValue()));
	}
	
	/** Loads an entry when only given the entry */
	public void loadEntry(ConfigEntry<?> entry)
	{
		loadEntry(entry, nightConfig);
	}
	/** Loads an entry */
	@SuppressWarnings("unchecked")
	public <T> void loadEntry(ConfigEntry<T> entry, CommentedFileConfig nightConfig)
	{
		if (!entry.getAppearance().showInFile)
			return;
		
		if (!nightConfig.contains(entry.getNameWCategory()))
		{
			saveEntry(entry, nightConfig);
			return;
		}
		
		
		try
		{
			if (entry.getType().isEnum())
			{
				entry.pureSet((T) (nightConfig.getEnum(entry.getNameWCategory(), (Class<? extends Enum>) entry.getType())));
				return;
			}
			
			// try converting the value if necessary
			Class<?> expectedValueClass = entry.getType();
			Object value = nightConfig.get(entry.getNameWCategory());
			Object convertedValue = ConfigTypeConverters.attemptToConvertFromString(expectedValueClass, value);
			if (!convertedValue.getClass().equals(expectedValueClass))
			{
				LOGGER.error("Unable to convert config value ["+value+"] from ["+(value != null ? value.getClass() : "NULL")+"] to ["+expectedValueClass+"] for config ["+entry.name+"], " +
						"the default config value will be used instead ["+entry.getDefaultValue()+"]. " +
						"Make sure a converter is defined in ["+ConfigTypeConverters.class.getSimpleName()+"].");
				convertedValue = entry.getDefaultValue();
			}
			entry.pureSet((T) convertedValue);
			
			if (entry.getTrueValue() == null) 
			{
				LOGGER.warn("Entry [" + entry.getNameWCategory() + "] returned as null from the config. Using default value.");
				entry.pureSet(entry.getDefaultValue());
			}
		}
		catch (Exception e)
		{
//                e.printStackTrace();
			LOGGER.warn("Entry [" + entry.getNameWCategory() + "] had an invalid value when loading the config. Using default value.");
			entry.pureSet(entry.getDefaultValue());
		}
	}
	
	// Creates the comment for an entry when only given the entry
	public void createComment(ConfigEntry<?> entry)
	{
		createComment(entry, nightConfig);
	}
	// Creates a comment for an entry
	public void createComment(ConfigEntry<?> entry, CommentedFileConfig nightConfig)
	{
		if (
				!entry.getAppearance().showInFile || 
				entry.getComment() == null
		)
			return;
		
		if (SingletonInjector.INSTANCE.get(IMinecraftSharedWrapper.class).isDedicatedServer() && entry.getServersideShortName() == null)
			return;
		
		String comment = entry.getComment().replaceAll("\n", "\n ").trim();
		// the new line makes it easier to read and separate configs
		// the space makes sure the first word of a comment isn't directly in line with the "#" 
		comment = "\n " + comment;
		nightConfig.setComment(entry.getNameWCategory(), comment);
	}
	
	
	
	
	
	/**
	 * Uses {@link ConfigFileHandling#nightConfig} to do {@link CommentedFileConfig#load()} but with error checking
	 *
	 * @apiNote This overwrites any value currently stored in the config
	 */
	public void loadNightConfig()
	{
		loadNightConfig(this.nightConfig);
	}
	/**
	 * Does {@link CommentedFileConfig#load()} but with error checking
	 *
	 * @apiNote This overwrites any value currently stored in the config
	 */
	public void loadNightConfig(CommentedFileConfig nightConfig)
	{
		try
		{
			try
			{
				if (!Files.exists(this.configPath))
					Files.createFile(this.configPath);
				nightConfig.load();
			}
			catch (Exception e)
			{
				LOGGER.warn("Loading file failed because of this expectation:\n" + e);
				
				reCreateFile(this.configPath);
				
				nightConfig.load();
			}
		}
		catch (Exception ex)
		{
			System.out.println("Creating file failed");
			LOGGER.error(ex);
			SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class).crashMinecraft("Loading file and resetting config file failed at path [" + configPath + "]. Please check the file is ok and you have the permissions", ex);
		}
	}
	
	
	
	public static void reCreateFile(Path path)
	{
		try
		{
			Files.deleteIfExists(path);
			
			if (!path.getParent().toFile().exists())
			{
				Files.createDirectory(path.getParent());
			}
			Files.createFile(path);
		}
		catch (IOException ex)
		{
			ex.printStackTrace();
		}
	}
	
	
	
	
	// ========== API (server) STUFF ========== //
	/* * ALWAYS CLEAR WHEN NOT ON SERVER!!!! */
	// We are not using this stuff, so comment it out for now (if we ever do need it then we can uncomment it)
    /*
    @SuppressWarnings("unchecked")
    public static void clearApiValues() {
        for (AbstractConfigType<?, ?> entry : ConfigBase.entries) {
            if (ConfigEntry.class.isAssignableFrom(entry.getClass()) && ((ConfigEntry) entry).allowApiOverride) {
                ((ConfigEntry) entry).setApiValue(null);
            }
        }
    }
    @SuppressWarnings("unchecked")
    public static String exportApiValues() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("configVersion", ConfigBase.configVersion);
        for (AbstractConfigType<?, ?> entry : ConfigBase.entries) {
            if (ConfigEntry.class.isAssignableFrom(entry.getClass()) && ((ConfigEntry) entry).allowApiOverride) {
                if (ConfigTypeConverters.convertObjects.containsKey(entry.getType())) {
                    jsonObject.put(entry.getNameWCategory(), ConfigTypeConverters.convertToString(entry.getType(), ((ConfigEntry<?>) entry).getTrueValue()));
                } else {
                    jsonObject.put(entry.getNameWCategory(), ((ConfigEntry<?>) entry).getTrueValue());
                }
            }
        }
        return jsonObject.toJSONString();
    }
    @SuppressWarnings("unchecked") // Suppress due to its always safe
    public static void importApiValues(String values) {
        JSONObject jsonObject = null;
        try {
            jsonObject = (JSONObject) new JSONParser().parse(values);
        } catch (ParseException p) {
            p.printStackTrace();
        }

        // Importing code
        for (AbstractConfigType<?, ?> entry : ConfigBase.entries) {
            if (ConfigEntry.class.isAssignableFrom(entry.getClass()) && ((ConfigEntry) entry).allowApiOverride) {
                Object jsonItem = jsonObject.get(entry.getNameWCategory());
                if (entry.getType().isEnum()) {
                    ((ConfigEntry) entry).setApiValue(Enum.valueOf((Class<? extends Enum>) entry.getType(), jsonItem.toString()));
                } else if (ConfigTypeConverters.convertObjects.containsKey(entry.getType())) {
                    ((ConfigEntry) entry).setApiValue(ConfigTypeConverters.convertFromString(entry.getType(), jsonItem.toString()));
                } else {
                    ((ConfigEntry) entry).setApiValue(jsonItem);
                }
            }
        }
    }
     */
}