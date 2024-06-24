package com.seibel.distanthorizons.core.file;

import com.seibel.distanthorizons.core.level.IDhLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Data sources are often very large objects and aren't used for very long.
 * This means their frequent construction and garbage collection can result in quite a bit of GC pressure.
 * By pooling said data sources and reusing them we can drastically reduce this GC pressure and improve
 * performance significantly.
 */
public class DataSourcePool<TDataSource extends IDataSource<TDhLevel>, TDhLevel extends IDhLevel>
{
	/** 
	 * James tested with a static 25 on a 8 core 16 processor machine and didn't have any issues.
	 * In most cases the number of pooled sources won't probably even get close to the number of processors,
	 * but just in case the user has a overkill CPU (or config) this should hopefully prevent thrashing.
	 */
	private static final int MAX_POOLED_SOURCES = Runtime.getRuntime().availableProcessors() * 2;
	
	private final ArrayList<TDataSource> pooledDataSources = new ArrayList<>();
	private final ReentrantLock poolLock = new ReentrantLock();
	
	private final Function<Long, TDataSource> createEmptyDatasourceFunc;
	@Nullable
	private final IPrepPooledDataSourceFunc<TDataSource, TDhLevel> prepDatasourceFunc;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DataSourcePool(Function<Long, TDataSource> createEmptyDatasourceFunc, @Nullable IPrepPooledDataSourceFunc<TDataSource, TDhLevel> prepDatasourceFunc)
	{
		this.createEmptyDatasourceFunc = createEmptyDatasourceFunc;
		this.prepDatasourceFunc = prepDatasourceFunc;
	}
	
	
	
	//===============//
	// pool handlers //
	//===============//
	
	/** 
	 * Returns a cleared data source.
	 * @see DataSourcePool#getPooledSource(long, boolean) 
	 */
	public TDataSource getPooledSource(long pos) { return this.getPooledSource(pos, true);}
	
	/** @return an empty data source if non are cached */
	public TDataSource getPooledSource(long pos, boolean clearData)
	{
		try
		{
			this.poolLock.lock();
			
			int index = this.pooledDataSources.size() - 1;
			if (index == -1)
			{
				// no pooled sources exist
				return this.createEmptyDatasourceFunc.apply(pos);
			}
			else
			{
				TDataSource dataSource = this.pooledDataSources.remove(index);
				
				// some data sources may want to handle prep themselves 
				// (due to needing additional inputs than what this pool keeps track of)
				if (this.prepDatasourceFunc != null)
				{
					this.prepDatasourceFunc.prepDataSource(pos, clearData, dataSource);
				}
				
				return dataSource;
			}
		}
		finally
		{
			this.poolLock.unlock();
		}
	}
	
	/**
	 * Doesn't have to be called, if a data source isn't returned, nothing will be leaked. 
	 * It just means a new source must be constructed next time {@link DataSourcePool#getPooledSource} is called.
	 */
	public void returnPooledDataSource(TDataSource dataSource)
	{
		if (dataSource == null)
		{
			return;
		}
		else if (this.pooledDataSources.size() > MAX_POOLED_SOURCES)
		{
			return;
		}
		
		try
		{
			this.poolLock.lock();
			this.pooledDataSources.add(dataSource);
		}
		finally
		{
			this.poolLock.unlock();
		}
	}
	
	
	
	//===============//
	// debug methods //
	//===============//
	
	/** Returns how many data sources are in the pool */
	public int size() { return this.pooledDataSources.size(); }
	
	
	
	//================//
	// helper classes //
	//================//
	
	@FunctionalInterface
	public interface IPrepPooledDataSourceFunc<TDataSource extends IDataSource<TDhLevel>, TDhLevel extends IDhLevel>
	{
		/** @param clearData will be false if the data will be immediately overwritten anyway */
		void prepDataSource(long pos, boolean clearData, TDataSource dataSource);
	}
	
}
