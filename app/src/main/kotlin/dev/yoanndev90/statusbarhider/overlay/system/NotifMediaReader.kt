package dev.yoanndev90.statusbarhider.overlay.system

import android.content.ComponentName
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.model.OverlayBarState
import dev.yoanndev90.statusbarhider.overlay.model.OverlayNotifIcon
import dev.yoanndev90.statusbarhider.overlay.model.OverlayProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads what the notification listener can see into [OverlayBarState]: the
 * icon row (with the progress ring it feeds) and the now-playing text.
 *
 * Notification access is reached through [isListenerEnabled] rather than the
 * listener class: `NotifListenerService` already imports this package for its
 * icons, so referencing it back would close a package cycle. Prefs and the bar
 * state are read through lambdas for the reason given in [SystemStatusReader]
 * - each read has to happen at the point the original field read did, before
 * or after a suspension, exactly as before.
 */
internal class NotifMediaReader(
	private val prefs: () -> OverlayPrefs,
	private val readBarState: () -> OverlayBarState,
	private val updateBarState: ((OverlayBarState) -> OverlayBarState) -> Unit,
	private val isListenerEnabled: () -> Boolean,
	private val mediaSessions: MediaSessions,
	private val notifListenerComponent: () -> ComponentName
) {
	suspend fun updateNotifs() {
		val p = prefs()
		val locked = readBarState().locked
		// Privacy filter: while the keyguard is up the icons (and the progress
		// ring they feed) would leak what the notifications are about.
		val privateFilter = locked && p.hideNotifsOnLock
		// Settings read + icon rasterization are IPC/CPU: off the main thread.
		val (enabled, icons, progress) =
			withContext(Dispatchers.IO) {
				val listenerOn = isListenerEnabled()
				val on = p.showNotifs && listenerOn && !privateFilter
				val list: List<OverlayNotifIcon> =
					if (!on) {
						emptyList()
					} else {
						NotifIcons.snapshot().take(p.maxNotifs.coerceIn(1, 8)).mapNotNull { e ->
							try {
								val bitmap = e.icon?.toBitmap()?.asImageBitmap()
								OverlayNotifIcon(bitmap, e.pkg)
							} catch (_: Exception) {
								null
							}
						}
					}
				val ring =
					NotifIcons
						.progress()
						?.let { OverlayProgress(it.fraction, it.indeterminate, it.pkg) }
						?.takeIf { listenerOn && !privateFilter }
				Triple(on, list, ring)
			}
		updateBarState { it.copy(notifsEnabled = enabled, notifs = icons, progress = progress) }
	}

	/** Now playing from media sessions; hidden when notification access is off. */
	suspend fun updateMedia() {
		// The notification-access check is a Settings read: off the main thread.
		// ensure() stays out of the withContext - it registers a framework
		// listener, a one-off that must land on the thread the manager expects.
		val listenerOn = withContext(Dispatchers.IO) { isListenerEnabled() }
		if (!prefs().showMedia || !listenerOn) {
			updateBarState { it.copy(mediaText = null) }
			return
		}
		mediaSessions.ensure(notifListenerComponent())
		updateBarState { it.copy(mediaText = mediaSessions.nowPlaying.value) }
	}
}
