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

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.sql.DatabaseUpdater;
import com.seibel.distanthorizons.core.sql.DbConnectionClosedException;
import com.seibel.distanthorizons.core.sql.dto.IBaseDTO;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Handles interfacing with SQL databases.
 *
 * @param <TDTO> DTO stands for "Data Transfer Object" 
 */
public abstract class AbstractDhRepo<TKey, TDTO extends IBaseDTO<TKey>> implements AutoCloseable
{
	/** a value of 0 means there's no timeout */
	public static final int TIMEOUT_SECONDS = 0;
	
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final ConcurrentHashMap<String, Connection> CONNECTIONS_BY_CONNECTION_STRING = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<AbstractDhRepo<?, ?>, String> ACTIVE_CONNECTION_STRINGS_BY_REPO = new ConcurrentHashMap<>();
	
	private final String connectionString;
	private final Connection connection;
	
	public final String databaseType;
	public final String databaseLocation;
	
	public final Class<? extends TDTO> dtoClass;
	
	protected final ReentrantLock[] saveLockArray;
	/** Based on the stack overflow post: https://stackoverflow.com/a/45909920 */
	protected ReentrantLock getSaveLockForKey(TKey key) { return this.saveLockArray[Math.abs(key.hashCode()) % this.saveLockArray.length]; }
	
	
	
	
	//=============//
	// constructor //
	//=============//
	
	/** @throws SQLException if the repo is unable to access the database or has trouble updating said database. */
	public AbstractDhRepo(String databaseType, String databaseLocation, Class<? extends TDTO> dtoClass) throws SQLException
	{
		this.databaseType = databaseType;
		this.databaseLocation = databaseLocation;
		this.dtoClass = dtoClass;
		
		
		// the lock array's length is 2x the number of CPU cores so the number of collisions
		// should be relatively low without having too many extra locks
		int lockCount = Runtime.getRuntime().availableProcessors() * 2;
		this.saveLockArray = new ReentrantLock[lockCount];
		for (int i = 0; i < lockCount; i++)
		{
			this.saveLockArray[i] = new ReentrantLock();
		}
		
		
		try
		{
			// needed by Forge to load the Java database connection
			Class.forName("org.sqlite.JDBC");	
		}
		catch (ClassNotFoundException e)
		{
			throw new RuntimeException(e);
		}
		
		
		// get or create the connection,
		// reusing existing connections reduces the chance of locking the database during trivial queries
		this.connectionString = this.databaseType+":"+this.databaseLocation;
		
		
		this.connection = CONNECTIONS_BY_CONNECTION_STRING.computeIfAbsent(this.connectionString, (connectionString) ->
			{
				try
				{
					return DriverManager.getConnection(connectionString);
				}
				catch (SQLException e)
				{
					LOGGER.error("Unable to connect to database with the connection string: ["+connectionString+"]");
					return null;
				}
			});
		if (this.connection == null)
		{
			throw new SQLException("Unable to get repo with connection string ["+this.connectionString+"]");
		}
		
		ACTIVE_CONNECTION_STRINGS_BY_REPO.put(this, this.connectionString);
		
		DatabaseUpdater.runAutoUpdateScripts(this);
	}
	
	
	
	//===============//
	// high level DB //
	//===============//
	
	public TDTO getByKey(TKey primaryKey)
	{
		Map<String, Object> objectMap = this.queryDictionaryFirst(this.createSelectByKeySql(primaryKey));
		if (objectMap != null && !objectMap.isEmpty())
		{
			return this.convertDictionaryToDto(objectMap);
		}
		else
		{
			return null;
		}
	}
	
	
	public void save(TDTO dto)
	{
		// a lock is necessary to prevent concurrent modification between
		// existsWithKey and insert/update,
		// otherwise another thread might cause the insert/update to fail.
		ReentrantLock saveLock = this.getSaveLockForKey(dto.getKey());
		
		try
		{
			saveLock.lock();
			
			if (this.existsWithKey(dto.getKey()))
			{
				this.update(dto);
			}
			else
			{
				this.insert(dto);
			}
		}
		finally
		{
			saveLock.unlock();
		}
	}
	private void insert(TDTO dto) 
	{
		try(PreparedStatement statement = this.createInsertStatement(dto))
		{
			this.query(statement);
		}
		catch (DbConnectionClosedException ignored) 
		{
			LOGGER.warn("Attempted to insert [" + this.dtoClass.getSimpleName() + "] with primary key [" + (dto != null ? dto.getKey() : "NULL") + "] on closed repo [" + this.connectionString + "].");
		}
		catch (SQLException e)
		{
			String message = "Unexpected insert statement error: ["+e.getMessage()+"].";
			LOGGER.error(message);
			throw new RuntimeException(message, e);
		}
	}
	private void update(TDTO dto)
	{
		try(PreparedStatement statement = this.createUpdateStatement(dto))
		{
			this.query(statement);
		}
		catch (DbConnectionClosedException e)
		{
			LOGGER.warn("Attempted to update [" + this.dtoClass.getSimpleName() + "] with primary key [" + (dto != null ? dto.getKey() : "NULL") + "] on closed repo [" + this.connectionString + "].");
		}
		catch (SQLException e)
		{
			String message = "Unexpected update statement error: ["+e.getMessage()+"].";
			LOGGER.error(message);
			throw new RuntimeException(message, e);
		}
	}
	
	
	public void delete(TDTO dto) { this.deleteWithKey(dto.getKey()); }
	public void deleteWithKey(TKey key)
	{
		String whereEqualStatement = this.createWhereStatement(key);
		this.queryDictionaryFirst("DELETE FROM "+this.getTableName()+" WHERE "+whereEqualStatement); 
	}
	
	/** With great power comes great responsibility... */
	public void deleteAll() { this.queryDictionaryFirst("DELETE FROM "+this.getTableName()); }
	
	
	public boolean exists(TDTO dto) { return this.existsWithKey(dto.getKey()); }
	public boolean existsWithKey(TKey key)
	{
		String whereEqualStatement = this.createWhereStatement(key);
		Map<String, Object> result = this.queryDictionaryFirst("SELECT EXISTS(SELECT 1 FROM "+this.getTableName()+" WHERE "+whereEqualStatement+") as 'existingCount';"); 
		return result != null && (int)result.get("existingCount") != 0;
	}
	
	
	//==============//
	// low level DB //
	//==============//
	
	public List<Map<String, Object>> queryDictionary(String sql)
	{
		try
		{
			return this.query(sql);
		}
		catch (DbConnectionClosedException e)
		{
			return new ArrayList<>();
		}
	}
	@Nullable
	public Map<String, Object> queryDictionaryFirst(String sql) 
	{
		try
		{
			List<Map<String, Object>> objectList = this.query(sql);
			return !objectList.isEmpty() ? objectList.get(0) : null;
		}
		catch (DbConnectionClosedException e)
		{
			return null;
		}
	}
	
	
	/** note: this can only handle 1 command at a time */
	public List<Map<String, Object>> query(PreparedStatement statement) throws RuntimeException, DbConnectionClosedException
	{
		try
		{
			statement.setQueryTimeout(TIMEOUT_SECONDS);
			
			// Note: this can only handle 1 command at a time
			boolean resultSetPresent = statement.execute();
			try (ResultSet resultSet = statement.getResultSet())
			{
				return this.parseQueryResult(resultSet, resultSetPresent);
			}
		}
		catch(SQLException e)
		{
			// SQL exceptions generally only happen when something is wrong with 
			// the database or the query and should cause the system to blow up to notify the developer
			
			if (DbConnectionClosedException.IsClosedException(e))
			{
				throw new DbConnectionClosedException(e);
			}
			else
			{
				String message = "Unexpected Query error: [" + e.getMessage() + "], for prepared statement: [" + statement + "].";
				LOGGER.error(message);
				throw new RuntimeException(message, e);
			}
		}
	}
	/** note: this can only handle 1 command at a time */
	private List<Map<String, Object>> query(String sql) throws RuntimeException, DbConnectionClosedException
	{
		try (Statement statement = this.connection.createStatement())
		{
			statement.setQueryTimeout(TIMEOUT_SECONDS);
			
			// Note: this can only handle 1 command at a time
			boolean resultSetPresent = statement.execute(sql);
			try (ResultSet resultSet = statement.getResultSet())
			{
				return this.parseQueryResult(resultSet, resultSetPresent);
			}
		}
		catch(SQLException e)
		{
			// SQL exceptions generally only happen when something is wrong with 
			// the database or the query and should cause the system to blow up to notify the developer
			
			if (DbConnectionClosedException.IsClosedException(e))
			{
				throw new DbConnectionClosedException(e);
			}
			else
			{
				String message = "Unexpected Query error: [" + e.getMessage() + "], for script: [" + sql + "].";
				LOGGER.error(message);
				throw new RuntimeException(message, e);
			}
		}
	}
	private List<Map<String, Object>> parseQueryResult(ResultSet resultSet, boolean resultSetPresent) throws SQLException
	{
		if (resultSetPresent)
		{
			List<Map<String, Object>> resultList = convertResultSetToDictionaryList(resultSet);
			resultSet.close();
			return resultList;
		}
		else
		{
			if (resultSet != null)
			{
				resultSet.close();
			}
			
			return new ArrayList<>();
		}
	}
	
	
	public PreparedStatement createPreparedStatement(String sql) throws DbConnectionClosedException
	{
		try
		{
			PreparedStatement statement = this.connection.prepareStatement(sql);
			statement.setQueryTimeout(TIMEOUT_SECONDS);
			return statement;
		}
		catch(SQLException e)
		{
			if (DbConnectionClosedException.IsClosedException(e))
			{
				throw new DbConnectionClosedException(e);
			}
			else
			{
				// SQL exceptions generally only happen when something is wrong with 
				// the database or the query and should cause the system to blow up to notify the developer
				
				String message = "Unexpected error: [" + e.getMessage() + "], preparing statement: [" + sql + "].";
				LOGGER.error(message);
				throw new RuntimeException(message, e);
			}
		}
	}
	
	
	
	//=============//
	// connections //
	//=============//
	
	public Connection getConnection() { return this.connection; }
	
	public boolean isConnected() 
	{
		try
		{
			return this.connection != null && this.connection.isClosed();
		}
		catch (SQLException e)
		{
			return false;
		}
	}
	
	@Override
	public void close()
	{
		try
		{
			// mark this repo as deactivated
			ACTIVE_CONNECTION_STRINGS_BY_REPO.remove(this);
			
			// check if any other repos are using this connection
			if (!ACTIVE_CONNECTION_STRINGS_BY_REPO.containsValue(this.connectionString)) // not a fast operation, but we shouldn't have more than 10 repos active at a time, so it shouldn't be a problem
			{
				if(this.connection != null)
				{
					CONNECTIONS_BY_CONNECTION_STRING.remove(this.connectionString);
					
					if (!this.connection.isClosed())
					{
						LOGGER.info("Closing database connection: [" + this.connectionString + "]");
						this.connection.close();
					}
					else
					{
						LOGGER.warn("Attempting to close already closed database connection: [" + this.connectionString + "]");
					}
				}
				ACTIVE_CONNECTION_STRINGS_BY_REPO.remove(this);
			}
		}
		catch(SQLException e)
		{
			// connection close failed.
			LOGGER.error("Unable to close the connection ["+this.connectionString+"], error: ["+e.getMessage()+"]");
		}
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	public String createWhereStatement(TDTO dto) { return this.createWhereStatement(dto.getKey()); }
	
	public static List<Map<String, Object>> convertResultSetToDictionaryList(ResultSet resultSet) throws SQLException
	{
		List<Map<String, Object>> list = new ArrayList<>();
		
		ResultSetMetaData resultMetaData = resultSet.getMetaData();
		int resultColumnCount = resultMetaData.getColumnCount();
		
		while (resultSet.next())
		{
			HashMap<String, Object> object = new HashMap<>();
			for (int columnIndex = 1; columnIndex <= resultColumnCount; columnIndex++) // column indices start at 1
			{
				String columnName = resultMetaData.getColumnName(columnIndex);
				if (columnName == null || columnName.equals(""))
				{
					throw new RuntimeException("SQL result set is missing a column name for column ["+resultMetaData.getTableName(columnIndex)+"."+columnIndex+"].");
				}
				
				
				// some values need explicit conversion
				// Example: Long values that are within the bounds of an int would automatically be incorrectly returned as "Integer" objects
				String columnType = resultMetaData.getColumnTypeName(columnIndex).toUpperCase();
				Object columnValue;
				switch (columnType)
				{
					case "BIGINT":
						columnValue = resultSet.getLong(columnIndex);
						break;
					case "SMALLINT":
						columnValue = resultSet.getShort(columnIndex);
						break;
					case "TINYINT":
						columnValue = resultSet.getByte(columnIndex);
						break;
					default:
						columnValue = resultSet.getObject(columnIndex);
						break;
				}
				
				
				object.put(columnName, columnValue);
			}
			
			list.add(object);
		}
		
		return list;
	}
	
	
	
	//==================//
	// abstract methods //
	//==================//
	
	public abstract String getTableName();
	
	@Nullable
	public abstract TDTO convertDictionaryToDto(Map<String, Object> objectMap) throws ClassCastException;
	
	public String createSelectByKeySql(TKey key) { return "SELECT * FROM " + this.getTableName() + " WHERE " + this.createWhereStatement(key); }
	/**
	 * Example:
	 * <code> Id = '0' </code>
	 * <code> ColOne = '0' AND ColTwo = '2' </code>
	 */
	public abstract String createWhereStatement(TKey key);
	
	public abstract PreparedStatement createInsertStatement(TDTO dto) throws SQLException;
	public abstract PreparedStatement createUpdateStatement(TDTO dto) throws SQLException;
	
	
}