package dev.yoanndev90.statusbarhider.overlay

import android.content.Context
import android.util.Log
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.log.LogStore

private const val TAG = "Safely"

/**
 * Runs [block], swallowing any platform error. Used on paths where a failure
 * only costs a refresh (unregistering a receiver, removing a window view…)
 * instead of crashing the overlay service. The error still reaches logcat,
 * and the first repeat also reaches the in-app log.
 */
internal inline fun runSafely(
	context: Context,
	block: () -> Unit
) {
	try {
		block()
	} catch (e: Exception) {
		val line = e.toString()
		Log.w(TAG, line)
		LogStore.appendOnce(context, "$TAG#$line", context.getString(R.string.log_error, line))
	}
}
