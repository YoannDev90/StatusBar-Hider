package dev.yoanndev90.statusbarhider.overlay

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener
import android.media.session.PlaybackState

/**
 * Active media sessions behind the "now playing" widget.
 *
 * Registration needs notification access, so [ensure] is retried on every
 * query until the listener component is allowed: flipping the toggle on then
 * starts the stream without a service restart.
 *
 * @param dispatch posts a block on the overlay thread (screen-on gated)
 * @param onChanged called whenever a session event may have changed the text
 */
internal class MediaSessions(
	private val context: Context,
	private val dispatch: (block: () -> Unit) -> Unit,
	private val onChanged: () -> Unit
) {
	private var registered = false
	private var manager: MediaSessionManager? = null
	private var controllers: List<MediaController> = emptyList()

	private val callback =
		object : MediaController.Callback() {
			override fun onMetadataChanged(metadata: MediaMetadata?) = dispatch { onChanged() }

			override fun onPlaybackStateChanged(state: PlaybackState?) = dispatch { onChanged() }

			override fun onSessionDestroyed() = dispatch { onChanged() }
		}

	private val listener =
		OnActiveSessionsChangedListener { sessions ->
			dispatch { sync(sessions) }
		}

	/** Registers the session listener; a no-op until notification access is granted. */
	fun ensure() {
		if (registered) return
		runSafely {
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
		runSafely { manager?.removeOnActiveSessionsChangedListener(listener) }
		controllers.forEach { c -> runSafely { c.unregisterCallback(callback) } }
		controllers = emptyList()
	}

	/** Title / artist of the first playing (or paused) session, null when none. */
	fun nowPlaying(): String? {
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
		controllers.forEach { c -> runSafely { c.unregisterCallback(callback) } }
		controllers = sessions.orEmpty()
		controllers.forEach { c -> runSafely { c.registerCallback(callback) } }
		onChanged()
	}
}
