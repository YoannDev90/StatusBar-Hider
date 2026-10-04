package dev.yoanndev90.statusbarhider.core.notif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.yoanndev90.statusbarhider.MainActivity
import dev.yoanndev90.statusbarhider.R

/**
 * Test notification behind the Style page's "Send test notification" action.
 *
 * A determinate progress bar is exactly what feeds the notification widget and
 * the camera ring, so the user can watch both react instead of waiting for a
 * real download. Re-sending replaces the same notification id, and it is
 * dismissible like any other.
 */
object TestNotifier {
	private const val TAG = "TestNotifier"
	private const val CHANNEL_ID = "test"
	private const val NOTIFICATION_ID = 0x7E57

	/** Posts the test notification; false when the notification manager refused it. */
	fun send(context: Context): Boolean =
		try {
			val app = context.applicationContext
			val nm = app.getSystemService(NotificationManager::class.java)
			if (nm.getNotificationChannel(CHANNEL_ID) == null) {
				nm.createNotificationChannel(
					NotificationChannel(
						CHANNEL_ID,
						app.getString(R.string.test_notif_channel),
						NotificationManager.IMPORTANCE_LOW
					)
				)
			}
			val contentIntent =
				PendingIntent.getActivity(
					app,
					0,
					Intent(app, MainActivity::class.java),
					PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
				)
			val notification =
				Notification
					.Builder(app, CHANNEL_ID)
					.setSmallIcon(R.drawable.ic_tile_bar)
					.setContentTitle(app.getString(R.string.test_notif_title))
					.setContentText(app.getString(R.string.test_notif_text))
					.setContentIntent(contentIntent)
					.setProgress(100, 42, false)
					.build()
			nm.notify(NOTIFICATION_ID, notification)
			true
		} catch (e: Exception) {
			Log.w(TAG, "Cannot post the test notification", e)
			false
		}
}
