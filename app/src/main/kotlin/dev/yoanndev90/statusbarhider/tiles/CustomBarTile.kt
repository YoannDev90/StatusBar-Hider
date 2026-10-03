package dev.yoanndev90.statusbarhider.tiles

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.data.OverlayController
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository

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
		if (next && !Settings.canDrawOverlays(this)) {
			// Without the permission the overlay window cannot be attached, so
			// enabling the switch would do nothing: send the user to the system
			// screen and leave the state untouched.
			unlockAndRun {
				val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				startActivity(intent)
			}
			return
		}
		OverlayController.setEnabled(applicationContext, next)
		refresh()
	}

	private fun refresh() {
		val tile = qsTile ?: return
		val repo = OverlayPrefsRepository.getInstance(this)
		val enabled = repo.state.value.enabled
		tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
		if (Build.VERSION.SDK_INT >= 29) {
			tile.subtitle = if (enabled) getString(R.string.tile_tap_to_hide) else getString(R.string.tile_tap_to_show)
		}
		tile.updateTile()
	}
}
