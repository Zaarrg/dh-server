package com.seibel.distanthorizons.core.util;

import java.util.Timer;

/**
 * Handles creating timers.
 * Used to prevent accidentally creating timers with the wrong format.
 * 
 * @see ThreadUtil
 */
public class TimerUtil
{
	
	public static Timer CreateTimer(String timerName) 
	{ 
		// isDaemon = true is necessary to allow MC to stop running even if the timer hasn't finished
		return new Timer(ThreadUtil.THREAD_NAME_PREFIX+timerName, true); 
	}
	
}
