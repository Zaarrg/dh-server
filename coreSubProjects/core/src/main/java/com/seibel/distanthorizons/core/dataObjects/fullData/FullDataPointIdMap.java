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

package com.seibel.distanthorizons.core.dataObjects.fullData;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * WARNING: This is not THREAD-SAFE! <br><br>
 * 
 * Used to map a numerical IDs to a Biome/BlockState pair. <br><br>
 * 
 * TODO the serializing of this map might be really big
 *  since it stringifies every block and biome name, which is quite bulky.
 *  It might be worth while to have a biome and block ID that then both get mapped
 *  to the data point ID to reduce file size.
 *  And/or it would be good to dynamically remove IDs that aren't currently in use.  
 * 
 * @author Leetom
 */
public class FullDataPointIdMap
{
	private static final Logger LOGGER = LogManager.getLogger();
	/**
	 * Should only be enabled when debugging.
	 * Has the system check if any duplicate Entries were read/written
	 * when (de)serializing.
	 */
	private static final boolean RUN_SERIALIZATION_DUPLICATE_VALIDATION = false;
	/** Distant Horizons - Block State Wrapper */
	private static final String BLOCK_STATE_SEPARATOR_STRING = "_DH-BSW_";
	
	
	/** used when the data point map is running normally */
	private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
	
	/** should only be used for debugging */
	private long pos;
	
	/** The index should be the same as the Entry's ID */
	private final ArrayList<Entry> entryList = new ArrayList<>();
	private final HashMap<Entry, Integer> idMap = new HashMap<>();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public FullDataPointIdMap(long pos) { this.pos = pos; }
	
	
	
	//=========//
	// getters //
	//=========//
	
	/** @throws IndexOutOfBoundsException if the given ID isn't in the {@link FullDataPointIdMap#entryList} */
	private Entry getEntry(int id) throws IndexOutOfBoundsException
	{
		try
		{
			this.readWriteLock.readLock().lock();
			Entry entry;
			try
			{
				entry = this.entryList.get(id);
			}
			catch (IndexOutOfBoundsException e)
			{
				throw new IndexOutOfBoundsException("FullData ID Map out of sync for pos: "+this.pos+". ID: ["+id+"] greater than the number of known ID's: ["+this.entryList.size()+"].");
			}
			
			return entry;
		}
		finally
		{
			this.readWriteLock.readLock().unlock();
		}
	}
	
	/** @see FullDataPointIdMap#getEntry(int) */
	public IBiomeWrapper getBiomeWrapper(int id) throws IndexOutOfBoundsException { return this.getEntry(id).biome; }
	/** @see FullDataPointIdMap#getEntry(int) */
	public IBlockStateWrapper getBlockStateWrapper(int id) throws IndexOutOfBoundsException { return this.getEntry(id).blockState; }
	
	
	/** @return -1 if the list is empty */
	public int getMaxValidId() { return this.entryList.size() - 1; }
	public int size() { return this.entryList.size(); }
	
	public boolean isEmpty() { return this.entryList.isEmpty(); }
	
	public long getPos() { return this.pos; }
	
	
	
	//=========//
	// setters //
	//=========//
	
	/**
	 * If an entry with the given values already exists nothing will
	 * be added but the existing item's ID will still be returned.
	 */
	public int addIfNotPresentAndGetId(IBiomeWrapper biome, IBlockStateWrapper blockState) { return this.addIfNotPresentAndGetId(Entry.getEntry(biome, blockState), true); }
	/** @param useWriteLocks should only be false if this method is already in a write lock to prevent unlocking at the wrong time */
	private int addIfNotPresentAndGetId(Entry biomeBlockStateEntry, boolean useWriteLocks)
	{
		try
		{
			if (useWriteLocks)
			{
				this.readWriteLock.writeLock().lock();
			}
			
			
			int id;
			if (this.idMap.containsKey(biomeBlockStateEntry))
			{
				// use the existing ID
				id = this.idMap.get(biomeBlockStateEntry);
			}
			else
			{
				// Add the new ID
				id = this.entryList.size();
				this.entryList.add(biomeBlockStateEntry);
				this.idMap.put(biomeBlockStateEntry, id);
			}
			
			return id;
		}
		finally
		{
			if (useWriteLocks)
			{
				this.readWriteLock.writeLock().unlock();
			}
		}
	}
	
	/** allows for adding duplicate {@link Entry} */
	private void add(Entry biomeBlockStateEntry, boolean useWriteLocks)
	{
		try
		{
			if (useWriteLocks)
			{
				this.readWriteLock.writeLock().lock();
			}
			
			
			int id = this.entryList.size();
			this.entryList.add(biomeBlockStateEntry);
			this.idMap.put(biomeBlockStateEntry, id);
		}
		finally
		{
			if (useWriteLocks)
			{
				this.readWriteLock.writeLock().unlock();
			}
		}
	}
	
	
	/**
	 * Adds every {@link Entry} from inputMap into this map. <br>
	 * Allows duplicate entries. <br><br>
	 * 
	 * Allowing duplicate entries should be done if a datasource is just being read in and 
	 * a merge step isn't being done afterwards. If duplicates are removed it may cause 
	 * the ID's to get out of sync since everything will be shifted down after the removed
	 * ID(s).
	 */
	public void addAll(FullDataPointIdMap inputMap)
	{
		try
		{
			//LOGGER.trace("adding {" + this.pos + ", " + this.entryList.size() + "} and {" + inputMap.pos + ", " + inputMap.entryList.size() + "}");
			
			inputMap.readWriteLock.readLock().lock();
			this.readWriteLock.writeLock().lock();
			
			ArrayList<Entry> entriesToMerge = inputMap.entryList;
			for (int i = 0; i < entriesToMerge.size(); i++)
			{
				Entry entity = entriesToMerge.get(i);
				this.add(entity, false);
			}
		}
		finally
		{
			this.readWriteLock.writeLock().unlock();
			inputMap.readWriteLock.readLock().unlock();
			
			//LOGGER.trace("finished merging {" + this.pos + ", " + this.entryList.size() + "} and {" + inputMap.pos + ", " + inputMap.entryList.size() + "}");
		}
	}
	
	/**
	 * Adds each entry from the given map to this map. <br><br>
	 * 
	 * Note: when using this function be careful about re-mapping the
	 * same data source multiple times.
	 * Doing so may cause indexOutOfBounds issues.
	 *
	 * @return an array of each added entry's ID in this map in order
	 */
	public int[] mergeAndReturnRemappedEntityIds(FullDataPointIdMap inputMap)
	{
		try
		{
			//LOGGER.trace("merging {" + this.pos + ", " + this.entryList.size() + "} and {" + inputMap.pos + ", " + inputMap.entryList.size() + "}");
			
			inputMap.readWriteLock.readLock().lock();
			this.readWriteLock.writeLock().lock();
			
			ArrayList<Entry> entriesToMerge = inputMap.entryList;
			int[] remappedEntryIds = new int[entriesToMerge.size()];
			for (int i = 0; i < entriesToMerge.size(); i++)
			{
				Entry entity = entriesToMerge.get(i);
				int id = this.addIfNotPresentAndGetId(entity, false);
				remappedEntryIds[i] = id;
			}
			
			return remappedEntryIds;
		}
		finally
		{
			this.readWriteLock.writeLock().unlock();
			inputMap.readWriteLock.readLock().unlock();
			
			//LOGGER.trace("finished merging {" + this.pos + ", " + this.entryList.size() + "} and {" + inputMap.pos + ", " + inputMap.entryList.size() + "}");
		}
	}
	
	/** Should only be used if this map is going to be reused, otherwise bad things will happen. */
	public void clear(long pos)
	{
		this.pos = pos;
		this.entryList.clear();
		this.idMap.clear();
	}
	
	
	
	//=============//
	// serializing //
	//=============//
	
	/** Serializes all contained entries into the given stream, formatted in UTF */
	public void serialize(DhDataOutputStream outputStream) throws IOException
	{
		try
		{
			this.readWriteLock.readLock().lock();
			outputStream.writeInt(this.entryList.size());
			
			// only used when debugging
			HashMap<String, FullDataPointIdMap.Entry> dataPointEntryBySerialization = new HashMap<>();
			
			for (Entry entry : this.entryList)
			{
				String entryString = entry.serialize();
				outputStream.writeUTF(entryString);
				
				if (RUN_SERIALIZATION_DUPLICATE_VALIDATION)
				{
					if (dataPointEntryBySerialization.containsKey(entryString))
					{
						LOGGER.error("Duplicate serialized entry found with serial: " + entryString);
					}
					if (dataPointEntryBySerialization.containsValue(entry))
					{
						LOGGER.error("Duplicate serialized entry found with value: " + entry.serialize());
					}
					dataPointEntryBySerialization.put(entryString, entry);
				}
			}
		}
		finally
		{
			this.readWriteLock.readLock().unlock();
			//LOGGER.trace("serialize " + this.pos + " " + this.entryList.size());
		}
	}
	
	/** Creates a new IdBiomeBlockStateMap from the given UTF formatted stream */
	public static FullDataPointIdMap deserialize(DhDataInputStream inputStream, long pos, ILevelWrapper levelWrapper) throws IOException, InterruptedException, DataCorruptedException
	{
		int entityCount = inputStream.readInt();
		if (entityCount < 0)
		{
			throw new DataCorruptedException("FullDataPointIdMap deserialize entry count should have a number greater than or equal to 0, returned value ["+entityCount+"].");
		}
		
		
		// only used when debugging
		HashMap<String, FullDataPointIdMap.Entry> dataPointEntryBySerialization = new HashMap<>();
		
		FullDataPointIdMap newMap = new FullDataPointIdMap(pos);
		for (int i = 0; i < entityCount; i++)
		{
			// necessary to prevent issues with deserializing objects after the level has been closed
			if (Thread.interrupted())
			{
				throw new InterruptedException(FullDataPointIdMap.class.getSimpleName() + " task interrupted.");
			}
			
			
			String entryString = inputStream.readUTF();
			Entry newEntry = Entry.deserialize(entryString, levelWrapper);
			newMap.entryList.add(newEntry);
			
			if (RUN_SERIALIZATION_DUPLICATE_VALIDATION)
			{
				if (dataPointEntryBySerialization.containsKey(entryString))
				{
					LOGGER.error("Duplicate deserialized entry found with serial: " + entryString);
				}
				if (dataPointEntryBySerialization.containsValue(newEntry))
				{
					LOGGER.error("Duplicate deserialized entry found with value: " + newEntry.serialize());
				}
				dataPointEntryBySerialization.put(entryString, newEntry);
			}
		}
		
		if (newMap.size() != entityCount)
		{
			// if the mappings are out of sync then the LODs will render incorrectly due to IDs being wrong
			LodUtil.assertNotReach("ID maps failed to deserialize for pos: ["+ DhSectionPos.toString(pos)+"], incorrect entity count. Expected count ["+entityCount+"], actual count ["+newMap.size()+"]");
		}
		
		return newMap;
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	public boolean equals(Object other)
	{
		if (other == this)
			return true;
/*        if (!(other instanceof FullDataPointIdMap)) return false;
		FullDataPointIdMap otherMap = (FullDataPointIdMap) other;
        if (entries.size() != otherMap.entries.size()) return false;
        for (int i=0; i<entries.size(); i++) {
            if (!entries.get(i).equals(otherMap.entries.get(i))) return false;
        }*/
		return false;
	}
	
	
	
	//==============//
	// helper class //
	//==============//
	
	private static final class Entry
	{
		private static final IWrapperFactory WRAPPER_FACTORY = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
		
		private static final Int2ReferenceOpenHashMap<ArrayList<Entry>> ENTRY_POOL = new Int2ReferenceOpenHashMap<>();
		/** lock is necessary since {@link Int2ReferenceOpenHashMap} isn't concurrent and concurrent threads can cause infinite loops */
		private static final ReentrantReadWriteLock ENTRY_POOL_LOCK = new ReentrantReadWriteLock();
		
		public final IBiomeWrapper biome;
		public final IBlockStateWrapper blockState;
		
		private Integer hashCode = null;
		
		
		
		//=============//
		// constructor //
		//=============//
		
		public static Entry getEntry(IBiomeWrapper biome, IBlockStateWrapper blockState)
		{
			int entryHash = getHashCode(biome, blockState);
			
			// try getting the existing entry
			try
			{
				ENTRY_POOL_LOCK.readLock().lock();
				
				// check if an entry already exists
				ArrayList<Entry> entryList = ENTRY_POOL.get(entryHash);
				if (entryList != null)
				{
					// at least one entry exists with this hash code
					for (int i = 0; i < entryList.size(); i++)
					{
						Entry entry = entryList.get(i);
						if (entry.biome.equals(biome) && entry.blockState.equals(blockState))
						{
							return entry;
						}
					}
					
					// if we got here, then there was a hash collision and this entry wasn't present in the array
				}
			}
			finally
			{
				ENTRY_POOL_LOCK.readLock().unlock();
			}
			
			
			// no entry exists,
			// create a new one
			try
			{
				ENTRY_POOL_LOCK.writeLock().lock();
				
				ArrayList<Entry> entryList = ENTRY_POOL.get(entryHash);
				if (entryList == null)
				{
					// no entries exist for this hash code
					
					// we assume that hash collisions should basically never happen, 
					// so the array starts with an initial capacity of 1.
					// However, since collisions will eventually happen, using an arrayList prevents unexpected bugs caused by collisions.
					entryList = new ArrayList<>(1);
					ENTRY_POOL.put(entryHash, entryList);
				}
				
				Entry newEntry = new Entry(biome, blockState);
				entryList.add(newEntry);
				return newEntry;
			}
			finally
			{
				ENTRY_POOL_LOCK.writeLock().unlock();
			}
		}
		private Entry(IBiomeWrapper biome, IBlockStateWrapper blockState)
		{
			this.biome = biome;
			this.blockState = blockState;
		}
		
		
		
		//===========//
		// overrides //
		//===========//
		
		public static int getHashCode(Entry entry) { return getHashCode(entry.biome, entry.blockState); }
		public static int getHashCode(IBiomeWrapper biome, IBlockStateWrapper blockState)
		{
			final int prime = 31;
			
			int result = 1;
			// the biome and blockstate hashcode should be already calculated by the time
			// we get here, so this operation should be very fast
			result = prime * result + (biome == null ? 0 : biome.hashCode());
			result = prime * result + (blockState == null ? 0 : blockState.hashCode());
			return result;
		}
		@Override
		public int hashCode()
		{
			// cache the hash code to improve speed
			if (this.hashCode == null)
			{
				this.hashCode = getHashCode(this);
			}
			
			return this.hashCode;
		}
		
		@Override
		public boolean equals(Object otherObj)
		{
			if (otherObj == this)
				return true;
			
			if (!(otherObj instanceof Entry))
				return false;
			
			Entry other = (Entry) otherObj;
			return other.biome.getSerialString().equals(this.biome.getSerialString())
					&& other.blockState.getSerialString().equals(this.blockState.getSerialString());
		}
		
		@Override
		public String toString() { return this.serialize(); }
		
		
		
		//=================//
		// (de)serializing //
		//=================//
		
		public String serialize() { return this.biome.getSerialString() + BLOCK_STATE_SEPARATOR_STRING + this.blockState.getSerialString(); }
		
		public static Entry deserialize(String str, ILevelWrapper levelWrapper) throws IOException, DataCorruptedException
		{
			String[] stringArray = str.split(BLOCK_STATE_SEPARATOR_STRING);
			if (stringArray.length != 2)
			{
				throw new DataCorruptedException("Failed to deserialize BiomeBlockStateEntry");
			}
			
			IBiomeWrapper biome = WRAPPER_FACTORY.deserializeBiomeWrapperOrGetDefault(stringArray[0], levelWrapper);
			IBlockStateWrapper blockState = WRAPPER_FACTORY.deserializeBlockStateWrapperOrGetDefault(stringArray[1], levelWrapper);
			return Entry.getEntry(biome, blockState);
		}
		
	}
	
	
}
