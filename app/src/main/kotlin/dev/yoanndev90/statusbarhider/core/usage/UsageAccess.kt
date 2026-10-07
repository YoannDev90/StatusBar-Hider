package dev.yoanndev90.statusbarhider.core.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.log.LogStore

/**
 * Support for the foreground-app blacklist: [granted] reports the "Usage
 * access" special permission it needs (Settings > Special app access > Usage
 * access), [foregroundPackage] reads the app that permission is granted for.
 */
object UsageAccess {
	private const val TAG = "UsageAccess"

	/**
	 * Trailing window of the foreground-app query. It must outlive a service
	 * restart (the bar would otherwise show over a blacklisted app until the
	 * next switch), but stay small: it is scanned every 2 s.
	 */
	private const val FOREGROUND_WINDOW_MS = 10 * 60 * 1000L

	fun granted(context: Context): Boolean =
		try {
			val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
			val mode =
				if (Build.VERSION.SDK_INT >= 29) {
					appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
				} else {
					@Suppress("DEPRECATION")
					appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
				}
			mode == AppOpsManager.MODE_ALLOWED
		} catch (e: Exception) {
			Log.w(TAG, "granted", e)
			LogStore.appendOnce(
				context,
				"$TAG#granted",
				context.getString(R.string.log_error, "usage access check: ${e.message}")
			)
			false
		}

	/** Package of the last resumed activity in the trailing window, or null when unknown. */
	fun foregroundPackage(context: Context): String? =
		try {
			val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
			val now = System.currentTimeMillis()
			val events = usm.queryEvents(now - FOREGROUND_WINDOW_MS, now)
			val event = UsageEvents.Event()
			var pkg: String? = null
			while (events.hasNextEvent()) {
				events.getNextEvent(event)
				if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
					pkg = event.packageName
				}
			}
			pkg
		} catch (e: Exception) {
			Log.w(TAG, "foregroundPackage", e)
			LogStore.appendOnce(
				context,
				"$TAG#foregroundPackage",
				context.getString(R.string.log_error, "foreground package query: ${e.message}")
			)
			null
		}
}
