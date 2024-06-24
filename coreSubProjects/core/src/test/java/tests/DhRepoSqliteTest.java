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

package tests;

import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.sql.DatabaseUpdater;
import com.seibel.distanthorizons.core.sql.repo.AbstractDhRepo;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import testItems.sql.TestCompoundKeyRepo;
import testItems.sql.TestCompoundKeyDto;
import testItems.sql.TestPrimaryKeyRepo;
import testItems.sql.TestSingleKeyDto;

import java.io.File;
import java.sql.SQLException;
import java.util.Map;

/**
 * Validates {@link AbstractDhRepo} is set up correctly.
 */
public class DhRepoSqliteTest
{
	public static String DATABASE_TYPE = "jdbc:sqlite";
	public static String DB_FILE_NAME = "test.sqlite";
	
	
	
	@BeforeClass
	public static void testSetup()
	{
		File dbFile = new File(DB_FILE_NAME);
		if (dbFile.exists())
		{
			Assert.assertTrue("unable to delete old test DB File.", dbFile.delete());
		}
	}
	
	
	
	@Test
	public void testPrimaryKeyRepo()
	{
		TestPrimaryKeyRepo primaryKeyRepo = null;
		try
		{
			primaryKeyRepo = new TestPrimaryKeyRepo(DATABASE_TYPE, DB_FILE_NAME);
			
			
			
			//==========================//
			// Auto update script tests //
			//==========================//
			
			// check that the schema table is created
			Map<String, Object> autoUpdateTablePresentResult = primaryKeyRepo.queryDictionaryFirst("SELECT name FROM sqlite_master WHERE type='table' AND name='"+DatabaseUpdater.SCHEMA_TABLE_NAME+"';");
			if (autoUpdateTablePresentResult == null || autoUpdateTablePresentResult.get("name") == null)
			{
				Assert.fail("Auto DB update table missing.");
			}
			
			// check that the update scripts aren't run multiple times
			TestPrimaryKeyRepo altDataRepoOne = new TestPrimaryKeyRepo(DATABASE_TYPE, DB_FILE_NAME);
			TestPrimaryKeyRepo altDataRepoTwo = new TestPrimaryKeyRepo(DATABASE_TYPE, DB_FILE_NAME);
			
			
			
			//===========//
			// DTO tests //
			//===========//
			
			// insert
			TestSingleKeyDto insertDto = new TestSingleKeyDto(0, "a", 0L, (byte) 0);
			primaryKeyRepo.save(insertDto);
			
			// get
			TestSingleKeyDto getDto = primaryKeyRepo.getByKey(0);
			Assert.assertNotNull("get failed, null returned", getDto);
			Assert.assertEquals("get/insert failed, not equal", insertDto, getDto);
			
			// exists - DTO present
			Assert.assertTrue("DTO exists failed", primaryKeyRepo.exists(insertDto));
			Assert.assertTrue("DTO exists failed", primaryKeyRepo.existsWithKey(insertDto.getKey()));
			
			
			// update
			TestSingleKeyDto updateMetaFile = new TestSingleKeyDto(0, "b", Long.MAX_VALUE, Byte.MAX_VALUE);
			primaryKeyRepo.save(updateMetaFile);
			
			// get
			getDto = primaryKeyRepo.getByKey(0);
			Assert.assertNotNull("get failed, null returned", getDto);
			Assert.assertEquals("get/insert failed, not equal", updateMetaFile, getDto);
			
			
			// delete
			primaryKeyRepo.delete(updateMetaFile);
			
			// get
			getDto = primaryKeyRepo.getByKey(0);
			Assert.assertNull("delete failed, not null returned", getDto);
			
			// exists - DTO absent
			Assert.assertFalse("DTO exists failed", primaryKeyRepo.exists(insertDto));
			Assert.assertFalse("DTO exists failed", primaryKeyRepo.existsWithKey(insertDto.getKey()));
			
		}
		catch (SQLException e)
		{
			Assert.fail(e.getMessage());
		}
		finally
		{
			if (primaryKeyRepo != null)
			{
				primaryKeyRepo.close();
			}
		}
	}
	
	@Test
	public void testCompoundKeyRepo()
	{
		TestCompoundKeyRepo compoundKeyRepo = null;
		try
		{
			compoundKeyRepo = new TestCompoundKeyRepo(DATABASE_TYPE, DB_FILE_NAME);
			
			
			
			//===========//
			// DTO tests //
			//===========//
			
			// insert
			TestCompoundKeyDto insertDto = new TestCompoundKeyDto(new DhChunkPos(1, 2), "a");
			compoundKeyRepo.save(insertDto);
			
			// get
			TestCompoundKeyDto getDto = compoundKeyRepo.getByKey(new DhChunkPos(1, 2));
			Assert.assertNotNull("get failed, null returned", getDto);
			Assert.assertEquals("get/insert failed, not equal", insertDto, getDto);
			
			// exists - DTO present
			Assert.assertTrue("DTO exists failed", compoundKeyRepo.exists(insertDto));
			Assert.assertTrue("DTO exists failed", compoundKeyRepo.existsWithKey(insertDto.getKey()));
			
			
			// update
			TestCompoundKeyDto updateMetaFile = new TestCompoundKeyDto(new DhChunkPos(1, 2), "b");
			compoundKeyRepo.save(updateMetaFile);
			
			// get
			getDto = compoundKeyRepo.getByKey(new DhChunkPos(1, 2));
			Assert.assertNotNull("get failed, null returned", getDto);
			Assert.assertEquals("get/insert failed, not equal", updateMetaFile, getDto);
			
			
			// delete
			compoundKeyRepo.delete(updateMetaFile);
			
			// get
			getDto = compoundKeyRepo.getByKey(new DhChunkPos(1, 2));
			Assert.assertNull("delete failed, not null returned", getDto);
			
			// exists - DTO absent
			Assert.assertFalse("DTO exists failed", compoundKeyRepo.exists(insertDto));
			Assert.assertFalse("DTO exists failed", compoundKeyRepo.existsWithKey(insertDto.getKey()));
			
		}
		catch (SQLException e)
		{
			Assert.fail(e.getMessage());
		}
		finally
		{
			if (compoundKeyRepo != null)
			{
				compoundKeyRepo.close();
			}
		}
	}
	
	
}
