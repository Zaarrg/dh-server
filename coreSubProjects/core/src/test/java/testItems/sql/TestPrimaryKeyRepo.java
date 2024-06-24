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

package testItems.sql;

import com.seibel.distanthorizons.core.sql.repo.AbstractDhRepo;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

public class TestPrimaryKeyRepo extends AbstractDhRepo<Integer, TestSingleKeyDto>
{
	
	public TestPrimaryKeyRepo(String databaseType, String databaseLocation) throws SQLException
	{
		super(databaseType, databaseLocation, TestSingleKeyDto.class);
		
		// note: this should only ever be done with the test repo.
		// All long term tables should be created using a sql Script.
		String createTableSql = 
				"CREATE TABLE IF NOT EXISTS "+this.getTableName()+"(\n" +
				"Id INT NOT NULL PRIMARY KEY\n" +
				"\n" +
				",Value TEXT NULL\n" +
				",LongValue BIGINT NULL\n" +
				",ByteValue TINYINT NULL\n" +
				");";
		this.queryDictionaryFirst(createTableSql);
	}
	
	
	
	@Override
	public String getTableName() { return "Test"; }
	@Override 
	public String createWhereStatement(Integer keyString) { return "Id = '"+keyString+"'"; }
	
	
	@Override 
	public TestSingleKeyDto convertDictionaryToDto(Map<String, Object> objectMap) throws ClassCastException
	{
		int id = (int) objectMap.get("Id");
		String value = (String) objectMap.get("Value");
		long longValue = (Long) objectMap.get("LongValue");
		byte byteValue = (Byte) objectMap.get("ByteValue");
		
		return new TestSingleKeyDto(id, value, longValue, byteValue);
	}
	
	@Override
	public PreparedStatement createInsertStatement(TestSingleKeyDto dto) throws SQLException
	{
		String sql = 
			"INSERT INTO "+this.getTableName()+" \n" +
				"(Id, Value, LongValue, ByteValue) \n" +
			"VALUES(?,?,?,?);";
		PreparedStatement statement = this.createPreparedStatement(sql);
		
		int i = 1; // post-increment for the win!
		statement.setObject(i++, dto.id);
		
		statement.setObject(i++, dto.value);
		statement.setObject(i++, dto.longValue);
		statement.setObject(i++, dto.byteValue);
		
		return statement;
	}
	
	@Override
	public PreparedStatement createUpdateStatement(TestSingleKeyDto dto) throws SQLException
	{
		String sql =
			"UPDATE "+this.getTableName()+" \n" +
			"SET \n" +
			"   Value = ? \n" +
			"   ,LongValue = ? \n" +
			"   ,ByteValue = ? \n" +
			"WHERE Id = ?";
		PreparedStatement statement = this.createPreparedStatement(sql);
		
		int i = 1;
		statement.setObject(i++, dto.value);
		statement.setObject(i++, dto.longValue);
		statement.setObject(i++, dto.byteValue);
		
		statement.setObject(i++, dto.id);
		
		return statement;
	}
	
}
