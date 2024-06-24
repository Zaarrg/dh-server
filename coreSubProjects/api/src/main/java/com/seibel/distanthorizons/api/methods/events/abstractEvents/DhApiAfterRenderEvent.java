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

package com.seibel.distanthorizons.api.methods.events.abstractEvents;

import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

/**
 * Fired after Distant Horizons finishes rendering a frame. <br>
 * At this point DH will have also finished cleaning up any modifications it
 * did to the OpenGL state, so the state should be back to Minecraft's defaults.
 *
 * @author James Seibel
 * @version 2024-1-31
 * @see DhApiRenderParam
 * @since API 1.0.0
 */
public abstract class DhApiAfterRenderEvent implements IDhApiEvent<DhApiRenderParam>
{
	/** Fired after Distant Horizons finishes rendering fake chunks. */
	public abstract void afterRender(DhApiEventParam<DhApiRenderParam> event);
	
	
	//=========================//
	// internal DH API methods //
	//=========================//
	
	@Override
	public final void fireEvent(DhApiEventParam<DhApiRenderParam> event) { this.afterRender(event); }
	
}