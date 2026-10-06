package dev.yoanndev90.statusbarhider.tiles

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.hide.HideInteractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Quick Settings tile: hides or restores the system status bar in one tap.
 *
 * Shizuku commands run on a coroutine scope; the tile only touches [Tile] on
 * the main thread. State is refreshed whenever SystemUI asks for listening
 * (panel opened) and after each tap.
 */
class HideRestoreTile : TileService() {
	companion object {
		private const val TAG = "QSTile"
		private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
		private val busy = AtomicBoolean(false)
	}

	override fun onStartListening() {
		refresh()
	}

	override fun onClick() {
		if (!busy.compareAndSet(false, true)) return
		if (!ShellRunner.granted()) {
			busy.set(false)
			requestShizuku()
			return
		}
		val app = applicationContext
		val hide = !HideInteractor.isHidden(app)
		scope.launch {
			try {
				// Gated: a tile tap cannot interleave with a Status screen action
				// or the unlock re-apply, and the lines reach the app log too.
				val result =
					HideInteractor.applyAndLogGated(
						app,
						hide = hide,
						labelRes = if (hide) R.string.log_applying_hide else R.string.log_restoring
					)
				result?.lines?.forEach { Log.i(TAG, it) }
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "tile command failed", e)
			}
			withContext(Dispatchers.Main) {
				try {
					refresh()
				} finally {
					busy.set(false)
				}
			}
		}
	}

	private fun requestShizuku() {
		try {
			Shizuku.requestPermission(0)
		} catch (e: Exception) {
			Log.w(TAG, "Shizuku permission request failed", e)
		}
	}

	private fun refresh() {
		val tile = qsTile ?: return
		val hidden = HideInteractor.isHidden(this)
		tile.state = if (hidden) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
		if (Build.VERSION.SDK_INT >= 29) {
			tile.subtitle =
				if (hidden) getString(R.string.tile_tap_to_restore) else getString(R.string.tile_tap_to_hide)
		}
		tile.updateTile()
	}
}
