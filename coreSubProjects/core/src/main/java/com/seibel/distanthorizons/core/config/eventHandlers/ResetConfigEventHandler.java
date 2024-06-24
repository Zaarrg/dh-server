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

package com.seibel.distanthorizons.core.config.eventHandlers;

import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.core.config.Config;

public class ResetConfigEventHandler
{
	public static ResetConfigEventHandler INSTANCE = new ResetConfigEventHandler();
	public final ConfigChangeListener<Boolean> configChangeListener;
	
	
	
	/** private since we only ever need one handler at a time */
	private ResetConfigEventHandler()
	{
		this.configChangeListener = new ConfigChangeListener<>(Config.Client.ResetConfirmation.resetAllSettings, (resetSettings) -> { doStuff(resetSettings); });
		
	}
	
	private void doStuff(boolean resetSettings)
	{
		if (!resetSettings)
		{
			return;
		}
		
		
		Config.Client.ResetConfirmation.resetAllSettings.set(false);
	}
	
}
