package com.seibel.distanthorizons.core.network.messages.plugin;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;

public interface ILevelRelatedMessage
{
	String getLevelName();
	
	/**
	 * Checks whether the message's level matches the given level.
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	default boolean isSameLevelAs(ILevelWrapper levelWrapper)
	{
		return this.getLevelName().equals(levelWrapper.getDimensionType().getDimensionName());
	}
	
}