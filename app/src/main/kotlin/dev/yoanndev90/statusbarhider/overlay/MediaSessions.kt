package dev.yoanndev90.statusbarhider.overlay

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener
import android.media.session.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Active media sessions behind the "now playing" widget.
 *
 * Registration needs notification access, so [ensure] is retried on every
 * query until the listener component is allowed: flipping the toggle on then
 * starts the stream without a service restart.
 *
 * Every framework callback hops onto [scope] (the overlay's main scope)
 * instead of a pushed lambda, and the latest text is exposed as [nowPlaying].
 */
internal class MediaSessions(
	private val context: Context,
	private val scope: CoroutineScope
) {
	private var registered = false
	private var manager: MediaSessionManager? = null
	private var controllers: List<MediaController> = emptyList()

	private val _nowPlaying = MutableStateFlow<String?>(null)

	/** Latest "Title — Artist", null when nothing plays; emits on any session change. */
	val nowPlaying: StateFlow<String?> = _nowPlaying.asStateFlow()

	private val callback =
		object : MediaController.Callback() {
			override fun onMetadataChanged(metadata: MediaMetadata?) {
				scope.launch { publish() }
			}

			override fun onPlaybackStateChanged(state: PlaybackState?) {
				scope.launch { publish() }
			}

			override fun onSessionDestroyed() {
				scope.launch { publish() }
			}
		}

	private val listener =
		OnActiveSessionsChangedListener { sessions ->
			scope.launch { sync(sessions) }
		}

	/** Registers the session listener; a no-op until notification access is granted. */
	fun ensure() {
		if (registered) return
		runSafely(context) {
			val msm = context.getSystemService(MediaSessionManager::class.java) ?: return
			val cn = ComponentName(context, NotifListenerService::class.java)
			msm.addOnActiveSessionsChangedListener(listener, cn)
			registered = true
			manager = msm
			sync(msm.getActiveSessions(cn))
		}
	}

	/** Unregisters the listener and every controller callback. */
	fun dispose() {
		runSafely(context) { manager?.removeOnActiveSessionsChangedListener(listener) }
		controllers.forEach { c -> runSafely(context) { c.unregisterCallback(callback) } }
		controllers = emptyList()
	}

	private fun publish() {
		_nowPlaying.value = compute()
	}

	private fun compute(): String? {
		val ctrl =
			controllers.firstOrNull {
				it.playbackState?.state == PlaybackState.STATE_PLAYING
			} ?: controllers.firstOrNull {
				it.playbackState?.state == PlaybackState.STATE_PAUSED
			}
		val md = ctrl?.metadata
		val title = md
			?.getText(MediaMetadata.METADATA_KEY_TITLE)
			?.toString()
			?.trim()
			.orEmpty()
		val artist = md
			?.getText(MediaMetadata.METADATA_KEY_ARTIST)
			?.toString()
			?.trim()
			.orEmpty()
		if (title.isEmpty()) return null
		return if (artist.isEmpty()) title else "$title — $artist"
	}

	private fun sync(sessions: List<MediaController>?) {
		controllers.forEach { c -> runSafely(context) { c.unregisterCallback(callback) } }
		controllers = sessions.orEmpty()
		controllers.forEach { c -> runSafely(context) { c.registerCallback(callback) } }
		publish()
	}
}
