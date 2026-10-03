package dev.yoanndev90.statusbarhider.data

import android.content.Context
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.StatusBarOverlayService

/**
 * Single writer for the custom bar's on/off flag: persists it through
 * [OverlayPrefsRepository] and keeps the foreground service in sync.
 *
 * Shared by the in-app switches, [dev.yoanndev90.statusbarhider.tiles.CustomBarTile]
 * and BootReceiver, so the service can never disagree with the stored state.
 */
object OverlayController {
	/**
	 * Turns the custom bar on or off. The caller decides whether the change is
	 * worth a log line (the tiles and boot stay quiet).
	 */
	fun setEnabled(
		context: Context,
		enabled: Boolean
	) {
		val app = context.applicationContext
		OverlayPrefsRepository.getInstance(app).update { copy(enabled = enabled) }
		if (enabled) {
			StatusBarOverlayService.start(app)
		} else {
			StatusBarOverlayService.stop(app)
		}
	}

	/**
	 * Applies [transform] and aligns the service with the result.
	 *
	 * Sliders emit on every frame: restarting an already-running service each
	 * time is a `startForegroundService()` binder round-trip per tick. Only the
	 * off -> on edge needs it; [setEnabled], BootReceiver and the tile own the
	 * other transitions.
	 */
	fun update(
		context: Context,
		transform: OverlayPrefs.() -> OverlayPrefs
	) {
		val app = context.applicationContext
		val repo = OverlayPrefsRepository.getInstance(app)
		val wasEnabled = repo.state.value.enabled
		val updated = repo.update(transform)
		if (updated.enabled && !wasEnabled) StatusBarOverlayService.start(app)
	}
}
