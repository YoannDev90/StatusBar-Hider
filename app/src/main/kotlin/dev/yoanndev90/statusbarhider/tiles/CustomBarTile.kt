package dev.yoanndev90.statusbarhider.tiles

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.overlay.StatusBarOverlayService

/**
 * Quick Settings tile: shows or hides the custom Compose status bar.
 *
 * Reads the shared [OverlayPrefsRepository], so the tile and the in-app switch
 * are always driven by the same state.
 */
class CustomBarTile : TileService() {
	override fun onStartListening() {
		refresh()
	}

	override fun onClick() {
		val repo = OverlayPrefsRepository.getInstance(applicationContext)
		val next = !repo.state.value.enabled
		repo.update { copy(enabled = next) }
		if (next) {
			StatusBarOverlayService.start(applicationContext)
		} else {
			StatusBarOverlayService.stop(applicationContext)
		}
		refresh()
	}

	private fun refresh() {
		val tile = qsTile ?: return
		val repo = OverlayPrefsRepository.getInstance(this)
		val enabled = repo.state.value.enabled
		tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
		if (Build.VERSION.SDK_INT >= 29) {
			tile.subtitle = if (enabled) "Tap to hide" else "Tap to show"
		}
		tile.updateTile()
	}
}
