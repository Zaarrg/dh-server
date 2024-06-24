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

package com.seibel.distanthorizons.core.sql.dto;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.network.protocol.INetworkObject;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.zip.Adler32;
import java.util.zip.CheckedOutputStream;

/** handles storing {@link FullDataSourceV2}'s in the database. */
public class FullDataSourceV2DTO implements IBaseDTO<Long>, INetworkObject
{
	public static final boolean VALIDATE_INPUT_DATAPOINTS = true;
	
	
	public long pos;
	
	public int levelMinY;
	
	/** only for the data array */
	public int dataChecksum;
	
	public byte[] compressedDataByteArray;
	
	/** @see EDhApiWorldGenerationStep */
	public byte[] compressedColumnGenStepByteArray;
	/** @see EDhApiWorldCompressionMode */
	public byte[] compressedWorldCompressionModeByteArray;
	
	public byte[] compressedMappingByteArray;
	
	public byte dataFormatVersion;
	public byte compressionModeValue;
	
	public boolean applyToParent;
	
	public long lastModifiedUnixDateTime;
	public long createdUnixDateTime;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public static FullDataSourceV2DTO CreateFromDataSource(FullDataSourceV2 dataSource, EDhApiDataCompressionMode compressionModeEnum) throws IOException
	{
		CheckedByteArray checkedDataPointArray = writeDataSourceDataArrayToBlob(dataSource.dataPoints, compressionModeEnum);
		byte[] compressedWorldGenStepByteArray = writeGenerationStepsToBlob(dataSource.columnGenerationSteps, compressionModeEnum);
		byte[] compressedWorldCompressionModeByteArray = writeWorldCompressionModeToBlob(dataSource.columnWorldCompressionMode, compressionModeEnum);
		byte[] mappingByteArray = writeDataMappingToBlob(dataSource.mapping, compressionModeEnum);
		
		return new FullDataSourceV2DTO(
				dataSource.getPos(),
				checkedDataPointArray.checksum, compressedWorldGenStepByteArray, compressedWorldCompressionModeByteArray, FullDataSourceV2.DATA_FORMAT_VERSION, compressionModeEnum.value, checkedDataPointArray.byteArray,
				dataSource.lastModifiedUnixDateTime, dataSource.createdUnixDateTime,
				mappingByteArray, dataSource.applyToParent,
				dataSource.levelMinY
		);
	}
	
	/** Should only be used for subsequent decoding */
	public FullDataSourceV2DTO() { }
	
	public FullDataSourceV2DTO(
			long pos, 
			int dataChecksum, byte[] compressedColumnGenStepByteArray, byte[] compressedWorldCompressionModeByteArray, byte dataFormatVersion, byte compressionModeValue, byte[] compressedDataByteArray,
			long lastModifiedUnixDateTime, long createdUnixDateTime,
			byte[] compressedMappingByteArray, boolean applyToParent,
			int levelMinY)
	{
		this.pos = pos;
		this.dataChecksum = dataChecksum;
		this.compressedColumnGenStepByteArray = compressedColumnGenStepByteArray;
		this.compressedWorldCompressionModeByteArray = compressedWorldCompressionModeByteArray;
		
		this.dataFormatVersion = dataFormatVersion;
		this.compressionModeValue = compressionModeValue;
		
		this.compressedDataByteArray = compressedDataByteArray;
		this.compressedMappingByteArray = compressedMappingByteArray;
		
		this.applyToParent = applyToParent;
		
		this.lastModifiedUnixDateTime = lastModifiedUnixDateTime;
		this.createdUnixDateTime = createdUnixDateTime;
		
		this.levelMinY = levelMinY;
	}
	
	
	
	//========================//
	// data source population //
	//========================//
	
	public FullDataSourceV2 createPooledDataSource(@NotNull ILevelWrapper levelWrapper) throws IOException, InterruptedException, DataCorruptedException
	{
		FullDataSourceV2 dataSource = FullDataSourceV2.DATA_SOURCE_POOL.getPooledSource(this.pos, false);
		return this.populateDataSource(dataSource, levelWrapper);
	}
	
	public FullDataSourceV2 populateDataSource(FullDataSourceV2 dataSource, @NotNull ILevelWrapper levelWrapper) throws IOException, InterruptedException, DataCorruptedException 
	{ return this.internalPopulateDataSource(dataSource, levelWrapper, false); }
	
	/**
	 * May be missing one or more data fields. <br>
	 * Designed to be used without access to Minecraft or any supporting objects.
	 */
	public FullDataSourceV2 createUnitTestDataSource() throws IOException, InterruptedException, DataCorruptedException 
	{ return this.internalPopulateDataSource(FullDataSourceV2.createEmpty(this.pos), null, true); }
	
	private FullDataSourceV2 internalPopulateDataSource(FullDataSourceV2 dataSource, ILevelWrapper levelWrapper, boolean unitTest) throws IOException, InterruptedException, DataCorruptedException
	{
		if (FullDataSourceV2.DATA_FORMAT_VERSION != this.dataFormatVersion)
		{
			throw new IllegalStateException("There should only be one data format [" + FullDataSourceV2.DATA_FORMAT_VERSION + "].");
		}
		
		
		EDhApiDataCompressionMode compressionModeEnum;
		try
		{
			compressionModeEnum = this.getCompressionMode();
		}
		catch (IllegalArgumentException e)
		{
			// may happen if ZStd was used (which was added and removed during the nightly builds)
			// or if the compressor value is changed to an invalid option
			throw new DataCorruptedException(e);
		}
		
		
		dataSource.columnGenerationSteps = readBlobToGenerationSteps(this.compressedColumnGenStepByteArray, compressionModeEnum);
		dataSource.columnWorldCompressionMode = readBlobToGenerationSteps(this.compressedWorldCompressionModeByteArray, compressionModeEnum);
		dataSource.dataPoints = readBlobToDataSourceDataArray(this.compressedDataByteArray, compressionModeEnum);
		
		dataSource.mapping.clear(dataSource.getPos());
		// should only be null when used in a unit test
		if (!unitTest)
		{
			if (levelWrapper == null)
			{
				throw new NullPointerException("No level wrapper present, unable to deserialize data map. This should only be used for unit tests.");
			}
			
			FullDataPointIdMap newMap = readBlobToDataMapping(this.compressedMappingByteArray, dataSource.getPos(), levelWrapper,  compressionModeEnum);
			dataSource.mapping.addAll(newMap);
			if (dataSource.mapping.size() != newMap.size())
			{
				// if the mappings are out of sync then the LODs will render incorrectly due to IDs being wrong
				LodUtil.assertNotReach("ID maps out of sync for pos: "+this.pos);
			}
		}
		
		dataSource.lastModifiedUnixDateTime = this.lastModifiedUnixDateTime;
		dataSource.createdUnixDateTime = this.createdUnixDateTime;
		
		dataSource.levelMinY = this.levelMinY;
		
		dataSource.isEmpty = false;
		
		return dataSource;
	}
	
	
	
	//=================//
	// (de)serializing //
	//=================//
	
	private static CheckedByteArray writeDataSourceDataArrayToBlob(LongArrayList[] dataArray, EDhApiDataCompressionMode compressionModeEnum) throws IOException
	{
		// write the outputs to a stream to prep for writing to the database
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		
		// the order of these streams is important, otherwise the checksum won't be calculated
		CheckedOutputStream checkedOut = new CheckedOutputStream(byteArrayOutputStream, new Adler32());
		// normally a DhStream should be the topmost stream to prevent closing the stream accidentally, 
		// but since this stream will be closed immediately after writing anyway, it won't be an issue
		DhDataOutputStream compressedOut = new DhDataOutputStream(checkedOut, compressionModeEnum);
		
		
		// write the data
		int dataArrayLength = FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH;
		for (int xz = 0; xz < dataArrayLength; xz++)
		{
			LongArrayList dataColumn = dataArray[xz];
			
			// write column length
			short columnLength = (dataColumn != null) ? (short) dataColumn.size() : 0;
			// a short is used instead of an int because at most we store 4096 vertical slices and a 
			// short fits that with less wasted spaces vs an int (short has max value of 32,767 vs int's max of 2 billion)
			compressedOut.writeShort(columnLength);
			
			// write column data (will be skipped if no data was present)
			for (int y = 0; y < columnLength; y++)
			{
				compressedOut.writeLong(dataColumn.getLong(y));
			}
		}
		
		
		// generate the checksum
		compressedOut.flush();
		int checksum = (int) checkedOut.getChecksum().getValue();
		byteArrayOutputStream.close();
		
		return new CheckedByteArray(checksum, byteArrayOutputStream.toByteArray());
	}
	private static LongArrayList[] readBlobToDataSourceDataArray(byte[] compressedDataByteArray, EDhApiDataCompressionMode compressionModeEnum) throws IOException, DataCorruptedException
	{
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedDataByteArray);
		DhDataInputStream compressedIn = new DhDataInputStream(byteArrayInputStream, compressionModeEnum);
		
		
		// read the data
		int dataArrayLength = FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH;
		LongArrayList[] dataArray = new LongArrayList[dataArrayLength];
		for (int xz = 0; xz < dataArray.length; xz++)
		{
			// read the column length
			short dataColumnLength = compressedIn.readShort(); // separate variables are used for debugging and in case validation wants to be added later 
			if (dataColumnLength < 0)
			{
				throw new DataCorruptedException("Read DataSource Blob data at index ["+xz+"], column length ["+dataColumnLength+"] should be greater than zero.");
			}
			
			LongArrayList dataColumn = new LongArrayList(new long[dataColumnLength]);
			
			// read column data (will be skipped if no data was present)
			for (int y = 0; y < dataColumnLength; y++)
			{
				long dataPoint = compressedIn.readLong();
				if (VALIDATE_INPUT_DATAPOINTS)
				{
					FullDataPointUtil.validateDatapoint(dataPoint);
				}
				dataColumn.set(y, dataPoint);
			}
			
			dataArray[xz] = dataColumn;
		}
		
		
		return dataArray;
	}
	
	
	private static byte[] writeGenerationStepsToBlob(byte[] columnGenStepByteArray, EDhApiDataCompressionMode compressionModeEnum) throws IOException
	{
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		DhDataOutputStream compressedOut = new DhDataOutputStream(byteArrayOutputStream, compressionModeEnum);
		
		compressedOut.write(columnGenStepByteArray);
		
		compressedOut.flush();
		byteArrayOutputStream.close();
		
		return byteArrayOutputStream.toByteArray();
	}
	private static byte[] readBlobToGenerationSteps(byte[] dataByteArray, EDhApiDataCompressionMode compressionModeEnum) throws IOException, DataCorruptedException
	{
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(dataByteArray);
		DhDataInputStream compressedIn = new DhDataInputStream(byteArrayInputStream, compressionModeEnum);
		
		try
		{
			byte[] columnGenStepByteArray = new byte[FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH];
			compressedIn.readFully(columnGenStepByteArray);
			
			return columnGenStepByteArray;
		}
		catch (EOFException e)
		{
			throw new DataCorruptedException(e);
		}
	}
	
	
	private static byte[] writeWorldCompressionModeToBlob(byte[] worldCompressionModeByteArray, EDhApiDataCompressionMode compressionModeEnum) throws IOException
	{
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		DhDataOutputStream compressedOut = new DhDataOutputStream(byteArrayOutputStream, compressionModeEnum);
		
		compressedOut.write(worldCompressionModeByteArray);
		
		compressedOut.flush();
		byteArrayOutputStream.close();
		
		return byteArrayOutputStream.toByteArray();
	}
	private static byte[] readBlobToWorldCompressionMode(byte[] dataByteArray, EDhApiDataCompressionMode compressionModeEnum) throws IOException, InterruptedException
	{
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(dataByteArray);
		DhDataInputStream compressedIn = new DhDataInputStream(byteArrayInputStream, compressionModeEnum);
		
		byte[] worldCompressionModeByteArray = new byte[FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH];
		compressedIn.readFully(worldCompressionModeByteArray);
		
		return worldCompressionModeByteArray;
	}
	
	
	private static byte[] writeDataMappingToBlob(FullDataPointIdMap mapping, EDhApiDataCompressionMode compressionModeEnum) throws IOException
	{
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		DhDataOutputStream compressedOut = new DhDataOutputStream(byteArrayOutputStream, compressionModeEnum);
		
		mapping.serialize(compressedOut);
		
		compressedOut.flush();
		byteArrayOutputStream.close();
		
		return byteArrayOutputStream.toByteArray();
	}
	private static FullDataPointIdMap readBlobToDataMapping(byte[] compressedMappingByteArray, long pos, @NotNull ILevelWrapper levelWrapper, EDhApiDataCompressionMode compressionModeEnum) throws IOException, InterruptedException, DataCorruptedException
	{
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedMappingByteArray);
		DhDataInputStream compressedIn = new DhDataInputStream(byteArrayInputStream, compressionModeEnum);
		
		FullDataPointIdMap mapping = FullDataPointIdMap.deserialize(compressedIn, pos, levelWrapper);
		return mapping;
	}
	
	@Override
	public void encode(ByteBuf out)
	{
		out.writeLong(this.pos);
		out.writeInt(this.dataChecksum);
		
		out.writeInt(this.compressedDataByteArray.length);
		out.writeBytes(this.compressedDataByteArray);
		
		out.writeInt(this.compressedColumnGenStepByteArray.length);
		out.writeBytes(this.compressedColumnGenStepByteArray);
		out.writeInt(this.compressedWorldCompressionModeByteArray.length);
		out.writeBytes(this.compressedWorldCompressionModeByteArray);
		
		out.writeInt(this.compressedMappingByteArray.length);
		out.writeBytes(this.compressedMappingByteArray);
		
		out.writeByte(this.dataFormatVersion);
		out.writeByte(this.compressionModeValue);
		
		out.writeBoolean(this.applyToParent);
		
		out.writeLong(this.lastModifiedUnixDateTime);
		out.writeLong(this.createdUnixDateTime);
	}
	
	@Override
	public void decode(ByteBuf in)
	{
		this.pos = in.readLong();
		this.dataChecksum = in.readInt();
		
		this.compressedDataByteArray = new byte[in.readInt()];
		in.readBytes(this.compressedDataByteArray);
		
		this.compressedColumnGenStepByteArray = new byte[in.readInt()];
		in.readBytes(this.compressedColumnGenStepByteArray);
		this.compressedWorldCompressionModeByteArray = new byte[in.readInt()];
		in.readBytes(this.compressedWorldCompressionModeByteArray);
		
		this.compressedMappingByteArray = new byte[in.readInt()];
		in.readBytes(this.compressedMappingByteArray);
		
		this.dataFormatVersion = in.readByte();
		this.compressionModeValue = in.readByte();
		
		this.applyToParent = in.readBoolean();
		
		this.lastModifiedUnixDateTime = in.readLong();
		this.createdUnixDateTime = in.readLong();
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	public Long getKey() { return this.pos; }
	
	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("levelMinY", this.levelMinY)
				.add("pos", this.pos)
				.add("dataChecksum", this.dataChecksum)
				.add("compressedDataByteArray length", this.compressedDataByteArray.length)
				.add("compressedColumnGenStepByteArray length", this.compressedColumnGenStepByteArray.length)
				.add("compressedWorldCompressionModeByteArray length", this.compressedWorldCompressionModeByteArray.length)
				.add("compressedMappingByteArray length", this.compressedMappingByteArray.length)
				.add("dataFormatVersion", this.dataFormatVersion)
				.add("compressionModeValue", this.compressionModeValue)
				.add("applyToParent", this.applyToParent)
				.add("lastModifiedUnixDateTime", this.lastModifiedUnixDateTime)
				.add("createdUnixDateTime", this.createdUnixDateTime)
				.toString();
	}
	
	//================//
	// helper methods //
	//================//
	
	public EDhApiDataCompressionMode getCompressionMode() throws IllegalArgumentException { return EDhApiDataCompressionMode.getFromValue(this.compressionModeValue); }
	
	
	//================//
	// helper classes //
	//================//
	
	private static class CheckedByteArray
	{
		public final int checksum;
		public final byte[] byteArray;
		
		public CheckedByteArray(int checksum, byte[] byteArray)
		{
			this.checksum = checksum;
			this.byteArray = byteArray;
		}
		
	}
	
}