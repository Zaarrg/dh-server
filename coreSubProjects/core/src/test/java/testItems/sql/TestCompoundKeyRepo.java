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

import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.sql.repo.AbstractDhRepo;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

public class TestCompoundKeyRepo extends AbstractDhRepo<DhChunkPos, TestCompoundKeyDto>
{
	
	public TestCompoundKeyRepo(String databaseType, String databaseLocation) throws SQLException
	{
		super(databaseType, databaseLocation, TestCompoundKeyDto.class);
		
		// note: this should only ever be done with the test repo.
		// All long term tables should be created using a sql Script.
		String createTableSql = 
				"CREATE TABLE IF NOT EXISTS "+this.getTableName()+"(\n" +
				"XPos INT NOT NULL\n" +
				",ZPos INT NOT NULL\n" +
				"\n" +
				",Value TEXT NULL\n" +
				"\n" +
				",PRIMARY KEY (XPos, ZPos)" +
				");";
		this.queryDictionaryFirst(createTableSql);
	}
	
	
	@Override
	public String getTableName() { return "TestCompound"; }
	@Override 
	public String createWhereStatement(DhChunkPos key) { return "XPos = '"+key.x+"' AND ZPos = '"+key.z+"'"; }
	
	
	@Override 
	public TestCompoundKeyDto convertDictionaryToDto(Map<String, Object> objectMap) throws ClassCastException
	{
		int xPos = (int) objectMap.get("XPos");
		int zPos = (int) objectMap.get("ZPos");
		String value = (String) objectMap.get("Value");
		
		return new TestCompoundKeyDto(new DhChunkPos(xPos, zPos), value);
	}
	
	@Override
	public PreparedStatement createInsertStatement(TestCompoundKeyDto dto) throws SQLException
	{
		String sql = 
			"INSERT INTO "+this.getTableName()+" \n" +
				"(XPos, ZPos, Value) \n" +
			"VALUES(?,?,?);";
		PreparedStatement statement = this.createPreparedStatement(sql);
		
		int i = 1; // post-increment for the win!
		statement.setObject(i++, dto.id.x);
		statement.setObject(i++, dto.id.z);
		
		statement.setObject(i++, dto.value);
		
		return statement;
	}
	
	@Override
	public PreparedStatement createUpdateStatement(TestCompoundKeyDto dto) throws SQLException
	{
		String sql =
			"UPDATE "+this.getTableName()+" \n" +
			"SET \n" +
			"   Value = ? \n" +
			"WHERE XPos = ? AND ZPos = ?";
		PreparedStatement statement = this.createPreparedStatement(sql);
		
		int i = 1;
		statement.setObject(i++, dto.value);
		
		statement.setObject(i++, dto.id.x);
		statement.setObject(i++, dto.id.z);
		
		return statement;
	}
	
}
