package dev.yoanndev90.statusbarhider.overlay

import android.app.Notification
import android.content.ComponentName
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.log.LogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

/**
 * Collects one icon per notifying package for the custom overlay bar.
 * Requires the user to enable notification access in system settings.
 *
 * The rebuild does binder + package-manager IPC (`activeNotifications`,
 * icon loading), so it never runs on the callback thread: every event is
 * conflated into a single IO consumer ([rebuilds]) - a notification storm
 * collapses to one rebuild instead of a queue.
 */
class NotifListenerService : NotificationListenerService() {
	private val rebuildScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val rebuilds = Channel<Unit>(Channel.CONFLATED)

	override fun onCreate() {
		super.onCreate()
		rebuildScope.launch { rebuilds.consumeEach { rebuild() } }
	}

	override fun onDestroy() {
		rebuildScope.cancel()
		rebuilds.close()
		super.onDestroy()
	}

	override fun onListenerConnected() {
		super.onListenerConnected()
		requestRebuild()
	}

	override fun onNotificationPosted(sbn: StatusBarNotification?) {
		requestRebuild()
	}

	override fun onNotificationRemoved(sbn: StatusBarNotification?) {
		requestRebuild()
	}

	override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
		requestRebuild()
	}

	private fun requestRebuild() {
		rebuilds.trySend(Unit)
	}

	private fun rebuild() {
		try {
			val actives =
				try {
					activeNotifications?.toList() ?: emptyList()
				} catch (e: Exception) {
					Log.w(TAG, "activeNotifications", e)
					LogStore.appendOnce(
						this,
						"$TAG#activeNotifications",
						getString(R.string.log_error, "activeNotifications: ${e.message}")
					)
					emptyList()
				}
			val best = pickProgress(actives)
			val pm = packageManager
			// One icon per notifying package, ours excluded, first
			// MAX_ICON_ENTRIES wins. Sequence keeps loadIcon() lazy, so the
			// cap is honoured before any IPC beyond it.
			val out =
				actives
					.asSequence()
					.mapNotNull { sbn -> sbn.packageName?.let { pkg -> pkg to sbn } }
					.filter { (pkg, _) -> pkg != packageName }
					.distinctBy { (pkg, _) -> pkg }
					.take(MAX_ICON_ENTRIES)
					.map { (pkg, sbn) -> NotifIcons.Entry(pkg, loadIcon(pm, sbn, pkg)) }
					.toList()
			NotifIcons.update(out, best)
		} catch (e: Exception) {
			Log.w(TAG, "rebuild", e)
			LogStore.appendOnce(
				this,
				"$TAG#rebuild",
				getString(R.string.log_error, "notif rebuild: ${e.message}")
			)
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
			val candidate = relevantProgress(sbn) ?: continue
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
	 * Progress of a notification this bar would also show an icon for, or null
	 * when the notification is ours or carries no progress worth displaying.
	 * Split out so [pickProgress] has a single skip path.
	 */
	private fun relevantProgress(sbn: StatusBarNotification): NotifIcons.Progress? {
		val pkg = sbn.packageName ?: return null
		if (pkg == packageName) return null
		return extractProgress(sbn)
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

		private const val TAG = "NotifListener"

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
