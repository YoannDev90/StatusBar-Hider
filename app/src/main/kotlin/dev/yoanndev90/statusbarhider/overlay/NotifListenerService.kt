package dev.yoanndev90.statusbarhider.overlay

import android.content.ComponentName
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Collects one icon per notifying package for the custom overlay bar.
 * Requires the user to enable notification access in system settings.
 */
class NotifListenerService : NotificationListenerService() {
	override fun onListenerConnected() {
		super.onListenerConnected()
		rebuild()
	}

	override fun onNotificationPosted(sbn: StatusBarNotification?) {
		rebuild()
	}

	override fun onNotificationRemoved(sbn: StatusBarNotification?) {
		rebuild()
	}

	override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
		rebuild()
	}

	private fun rebuild() {
		try {
			val actives =
				try {
					activeNotifications?.toList() ?: emptyList()
				} catch (_: Exception) {
					emptyList()
				}
			val pm = packageManager
			val seen = LinkedHashSet<String>()
			val out = mutableListOf<NotifIcons.Entry>()
			for (sbn in actives) {
				val pkg = sbn.packageName ?: continue
				if (pkg == packageName) continue
				if (!seen.add(pkg)) continue
				if (out.size >= 8) break
				out += NotifIcons.Entry(pkg, loadIcon(pm, sbn, pkg))
			}
			NotifIcons.update(out)
		} catch (_: Exception) {
		}
	}

	private fun loadIcon(
		pm: PackageManager,
		sbn: StatusBarNotification,
		pkg: String
	): android.graphics.drawable.Drawable? {
		try {
			sbn.notification?.smallIcon?.let { icon ->
				icon.loadDrawable(this)?.let { return it }
			}
		} catch (_: Exception) {
		}
		return try {
			pm.getApplicationIcon(pkg)
		} catch (_: Exception) {
			null
		}
	}

	companion object {
		/** True when the user enabled notification access for this app. */
		fun isEnabled(context: android.content.Context): Boolean {
			val flat =
				android.provider.Settings.Secure.getString(
					context.contentResolver,
					"enabled_notification_listeners"
				) ?: return false
			val me = ComponentName(context, NotifListenerService::class.java).flattenToString()
			return flat.split(":").any { it.equals(me, ignoreCase = true) }
		}
	}
}
