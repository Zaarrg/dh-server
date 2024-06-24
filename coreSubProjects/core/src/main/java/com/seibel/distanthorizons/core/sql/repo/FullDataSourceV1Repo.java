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

package com.seibel.distanthorizons.core.sql.repo;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV1DTO;
import com.seibel.distanthorizons.coreapi.util.StringUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FullDataSourceV1Repo extends AbstractDhRepo<Long, FullDataSourceV1DTO>
{
	public static final String TABLE_NAME = "Legacy_FullData_V1";
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public FullDataSourceV1Repo(String databaseType, String databaseLocation) throws SQLException
	{
		super(databaseType, databaseLocation, FullDataSourceV1DTO.class);
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	public String getTableName() { return TABLE_NAME; }
	
	@Override
	public String createWhereStatement(Long pos) { return "DhSectionPos = '"+serializeSectionPos(pos)+"'"; }
	
	
	
	//=======================//
	// repo required methods //
	//=======================//
	
	@Override 
	public FullDataSourceV1DTO convertDictionaryToDto(Map<String, Object> objectMap) throws ClassCastException
	{
		String posString = (String) objectMap.get("DhSectionPos");
		Long pos = deserializeSectionPos(posString);
		
		// meta data
		int checksum = (Integer) objectMap.get("Checksum");
		byte dataDetailLevel = (Byte) objectMap.get("DataDetailLevel");
		String worldGenStepString = (String) objectMap.get("WorldGenStep");
		EDhApiWorldGenerationStep worldGenStep = EDhApiWorldGenerationStep.fromName(worldGenStepString);
		
		String dataType = (String) objectMap.get("DataType");
		byte binaryDataFormatVersion = (Byte) objectMap.get("BinaryDataFormatVersion");
		
		// binary data
		byte[] dataByteArray = (byte[]) objectMap.get("Data");
		
		FullDataSourceV1DTO dto = new FullDataSourceV1DTO(
				pos,
				checksum, dataDetailLevel, worldGenStep,
				dataType, binaryDataFormatVersion, 
				dataByteArray);
		return dto;
	}
	
	@Override
	public PreparedStatement createInsertStatement(FullDataSourceV1DTO dto) throws SQLException
	{
		String sql =
			"INSERT INTO "+this.getTableName() + "\n" +
			"  (DhSectionPos, \n" +
			"Checksum, DataVersion, DataDetailLevel, WorldGenStep, DataType, BinaryDataFormatVersion, \n" +
			"Data) \n" +
			"   VALUES( \n" +
			"    ? \n" +
			"   ,? ,? ,? ,? ,? ,? \n" +
			"   ,? \n" +
			// created/lastModified are automatically set by Sqlite
			");";
		PreparedStatement statement = this.createPreparedStatement(sql);
		
		int i = 1;
		statement.setObject(i++, serializeSectionPos(dto.pos));
		
		statement.setObject(i++, dto.checksum);
		statement.setObject(i++, 0 /*dto.dataVersion*/);
		statement.setObject(i++, dto.dataDetailLevel);
		statement.setObject(i++, dto.worldGenStep);
		statement.setObject(i++, dto.dataType);
		statement.setObject(i++, dto.binaryDataFormatVersion);
		
		statement.setObject(i++, dto.dataArray);
		
		return statement;
	}
	
	@Override
	public PreparedStatement createUpdateStatement(FullDataSourceV1DTO dto) throws SQLException
	{
		String sql =
			"UPDATE "+this.getTableName()+" \n" +
			"SET \n" +
			"    Checksum = ? \n" +
			"   ,DataVersion = ? \n" +
			"   ,DataDetailLevel = ? \n" +
			"   ,WorldGenStep = ? \n" +
			"   ,DataType = ? \n" +
			"   ,BinaryDataFormatVersion = ? \n" +
					
			"   ,Data = ? \n" +
			
			"   ,LastModifiedDateTime = CURRENT_TIMESTAMP \n" +
			"WHERE DhSectionPos = ?";
		PreparedStatement statement = this.createPreparedStatement(sql);
		
		int i = 1;
		statement.setObject(i++, dto.checksum);
		statement.setObject(i++, 0 /*dto.dataVersion*/);
		statement.setObject(i++, dto.dataDetailLevel);
		statement.setObject(i++, dto.worldGenStep);
		statement.setObject(i++, dto.dataType);
		statement.setObject(i++, dto.binaryDataFormatVersion);
		
		statement.setObject(i++, dto.dataArray);
		
		statement.setObject(i++, serializeSectionPos(dto.pos));
		
		return statement;
	}
	
	
	
	//=====================//
	// data source methods //
	//=====================//
	
	/** 
	 * Returns the highest numerical detail level in this table. <Br>
	 * Returns {@link DhSectionPos#SECTION_MINIMUM_DETAIL_LEVEL} if no data is present.
	 */
	public int getMaxSectionDetailLevel()
	{
		Map<String, Object> resultMap = this.queryDictionaryFirst("select MAX(DataDetailLevel) as maxDetailLevel from "+this.getTableName()+";");
		int maxDetailLevel;
		if (resultMap == null || resultMap.get("maxDetailLevel") == null)
		{
			maxDetailLevel = 0;
		}
		else
		{
			maxDetailLevel = (int)resultMap.get("maxDetailLevel");
		}
		
		return maxDetailLevel + DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
	}
	
	
	
	//===========//
	// migration //
	//===========//
	
	/** Returns how many positions need to be migrated over to the new version */
	public long getMigrationCount()
	{
		Map<String, Object> resultMap = this.queryDictionaryFirst(
				"select COUNT(*) as itemCount from "+this.getTableName()+" where MigrationFailed <> 1");
		
		if (resultMap == null)
		{
			return 0;
		}
		else
		{
			Number resultNumber = (Number) resultMap.get("itemCount");
			long count = resultNumber.longValue();
			return count;
		}
	}
	
	/** Returns the new "returnCount" positions that need to be migrated */
	public LongArrayList getPositionsToMigrate(int returnCount)
	{
		LongArrayList list = new LongArrayList();
		
		List<Map<String, Object>> resultMapList = this.queryDictionary(
				"select DhSectionPos " +
						"from "+this.getTableName()+" " +
						"WHERE MigrationFailed <> 1 " +
						"LIMIT "+returnCount+";");
		
		for (Map<String, Object> resultMap : resultMapList)
		{
			// returned in the format [sectionDetailLevel,x,z] IE [6,0,0]
			long sectionPos = deserializeSectionPos((String) resultMap.get("DhSectionPos"));
			list.add(sectionPos);
		}
		
		return list;
	}
	
	public void markMigrationFailed(long pos)
	{
		String sql =
				"UPDATE "+this.getTableName()+" \n" +
						"SET MigrationFailed = 1 \n" +
						"WHERE DhSectionPos = '"+serializeSectionPos(pos)+"'";
		
		this.queryDictionaryFirst(sql);
	}
	
	
	
	//======================//
	// migration - deletion //
	//======================//
	
	/** returns the number of data sources that should be deleted */
	public long getUnusedDataSourceCount()
	{
		Map<String, Object> resultMap = this.queryDictionaryFirst(
				"select Count(*) as unusedCount from "+this.getTableName()+" where DataDetailLevel <> 0 or DataType <> 'CompleteFullDataSource'");
		
		if (resultMap != null)
		{
			// Number cast is necessary because the returned number can be an int or long
			Number resultNumber = (Number) resultMap.get("unusedCount");
			long count = resultNumber.longValue();
			return count;
		}
		else
		{
			return 0;
		}
	}
	
	/** Returns single quote surrounded {@link DhSectionPos} serailzed values */
	public ArrayList<String> getUnusedDataSourcePositionStringList(int deleteCount)
	{
		List<Map<String, Object>> deletePosResultMapList = this.queryDictionary(
				"select DhSectionPos from "+this.getTableName()+" where DataDetailLevel <> 0 or DataType <> 'CompleteFullDataSource' limit "+deleteCount);
		
		ArrayList<String> deletePosList = new ArrayList<>();
		for (Map<String, Object> deletePosMap : deletePosResultMapList)
		{
			String posString = (String) deletePosMap.get("DhSectionPos");
			deletePosList.add("'"+posString+"'");
		}
		
		return deletePosList;
	}
	
	/** Expects positions to already be surrounded in single quotes */
	public void deleteUnusedLegacyData(ArrayList<String> deletePosList)
	{
		String sectionPosCsv = StringUtil.join(",", deletePosList);
		this.queryDictionaryFirst("delete from " + this.getTableName() + " where DhSectionPos in (" + sectionPosCsv + ")");
	}
	
	
	
	//=====================//
	// section pos helpers //
	//=====================//
	
	private static String serializeSectionPos(long pos) { return "[" + DhSectionPos.getDetailLevel(pos) + ',' + DhSectionPos.getX(pos) + ',' + DhSectionPos.getZ(pos) + ']'; }
	
	
	@Nullable
	private static Long deserializeSectionPos(String value)
	{
		if (value.charAt(0) != '[' || value.charAt(value.length() - 1) != ']')
		{
			return null;
		}
		
		String[] split = value.substring(1, value.length() - 1).split(",");
		if (split.length != 3)
		{
			return null;
		}
		
		return DhSectionPos.encode(Byte.parseByte(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
	}
	
}
