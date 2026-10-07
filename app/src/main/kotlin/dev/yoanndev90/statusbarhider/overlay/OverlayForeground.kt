package dev.yoanndev90.statusbarhider.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.log.LogStore

private const val TAG = "CustomBar"
private const val NOTIF_ID = 1001
private const val CHANNEL_ID = "overlay"

/**
 * The ongoing notification that keeps the overlay process alive, posted on
 * the first start so the channel exists before the framework checks it.
 *
 * A failure here is recorded and the service is dropped: the caller got a
 * successful `startForegroundService()`, so without this the framework kills
 * the app ~5 s later for ForegroundServiceDidNotStartInTime with the cause
 * visible only in logcat.
 */
internal fun Service.startOverlayForeground() {
	val nm = getSystemService(NotificationManager::class.java) ?: return
	if (nm.getNotificationChannel(CHANNEL_ID) == null) {
		nm.createNotificationChannel(
			NotificationChannel(
				CHANNEL_ID,
				getString(R.string.notif_channel_name),
				NotificationManager.IMPORTANCE_MIN
			)
		)
	}
	val notif =
		Notification
			.Builder(this, CHANNEL_ID)
			.setContentTitle(getString(R.string.notif_content_title))
			.setContentText(getString(R.string.notif_content_text))
			.setSmallIcon(R.drawable.ic_tile_bar)
			.build()
	try {
		if (Build.VERSION.SDK_INT >= 34) {
			startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
		} else {
			startForeground(NOTIF_ID, notif)
		}
	} catch (e: Exception) {
		Log.w(TAG, "startForeground failed", e)
		LogStore.append(
			this,
			getString(R.string.log_start_foreground_failed, e.message ?: e.toString())
		)
		stopSelf()
	}
}
