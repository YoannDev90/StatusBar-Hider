package dev.yoanndev90.statusbarhider.overlay

import android.app.Notification
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
			val best = pickProgress(actives)
			val pm = packageManager
			val seen = LinkedHashSet<String>()
			val out = mutableListOf<NotifIcons.Entry>()
			for (sbn in actives) {
				val pkg = sbn.packageName ?: continue
				if (pkg == packageName) continue
				if (!seen.add(pkg)) continue
				if (out.size >= MAX_ICON_ENTRIES) break
				out += NotifIcons.Entry(pkg, loadIcon(pm, sbn, pkg))
			}
			NotifIcons.update(out, best)
		} catch (_: Exception) {
		}
	}

	/**
	 * The single progress to show: ongoing notifications win over non-ongoing
	 * ones, then the most recent update. Scans every active notification, unlike
	 * the icon list which stops after [MAX_ICON_ENTRIES] packages.
	 */
	private fun pickProgress(actives: List<StatusBarNotification>): NotifIcons.Progress? {
		var best: NotifIcons.Progress? = null
		var bestOngoing = false
		var bestWhen = Long.MIN_VALUE
		for (sbn in actives) {
			val pkg = sbn.packageName ?: continue
			if (pkg == packageName) continue
			val candidate = extractProgress(sbn) ?: continue
			val candidateWhen = sbn.notification?.`when` ?: 0L
			val better =
				best == null ||
					(sbn.isOngoing && !bestOngoing) ||
					(sbn.isOngoing == bestOngoing && candidateWhen > bestWhen)
			if (better) {
				best = candidate
				bestOngoing = sbn.isOngoing
				bestWhen = candidateWhen
			}
		}
		return best
	}

	/**
	 * Progress of a determinate / indeterminate notification, or null when the
	 * notification carries none (or its determinate progress already finished).
	 */
	private fun extractProgress(sbn: StatusBarNotification): NotifIcons.Progress? {
		val extras = sbn.notification?.extras ?: return null
		val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
		val current = extras.getInt(Notification.EXTRA_PROGRESS, 0)
		val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
		if (!indeterminate && max <= 0) return null
		if (!indeterminate && current >= max) return null
		val fraction = if (!indeterminate && max > 0) (current.toFloat() / max).coerceIn(0f, 1f) else 0f
		return NotifIcons.Progress(sbn.packageName, fraction, indeterminate)
	}

	private fun loadIcon(
		pm: PackageManager,
		sbn: StatusBarNotification,
		pkg: String
	): android.graphics.drawable.Drawable? {
		// Icon.loadDrawable() logs an E/Icon stacktrace *before* throwing when the
		// emitting package is gone (uninstalled apps can keep stale notifications),
		// and that log can't be caught. Only resolve the notification icon while
		// the package still exists.
		if (isInstalled(pm, pkg)) {
			try {
				sbn.notification?.smallIcon?.let { icon ->
					icon.loadDrawable(this)?.let { return it }
				}
			} catch (_: Exception) {
			}
		}
		return try {
			pm.getApplicationIcon(pkg)
		} catch (_: Exception) {
			null
		}
	}

	/** True when [pkg] is still installed (its `getApplicationInfo` resolves). */
	private fun isInstalled(
		pm: PackageManager,
		pkg: String
	): Boolean =
		try {
			pm.getApplicationInfo(pkg, 0)
			true
		} catch (_: Exception) {
			false
		}

	companion object {
		/** Icon list cap: one entry per package, most relevant packages first. */
		private const val MAX_ICON_ENTRIES = 8

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
