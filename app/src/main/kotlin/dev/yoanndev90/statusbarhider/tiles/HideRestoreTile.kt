package dev.yoanndev90.statusbarhider.tiles

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import dev.yoanndev90.statusbarhider.ShizukuCmd
import dev.yoanndev90.statusbarhider.hide.HideController
import kotlinx.coroutines.runBlocking
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Quick Settings tile: hides or restores the system status bar in one tap.
 *
 * Shizuku commands block, so they run on a single worker thread; the tile only
 * touches [Tile] on the main thread. State is refreshed whenever SystemUI asks
 * for listening (panel opened) and after each tap.
 */
class HideRestoreTile : TileService() {
	companion object {
		private const val TAG = "QSTile"
		private val executor = Executors.newSingleThreadExecutor()
		private val busy = AtomicBoolean(false)
	}

	override fun onStartListening() {
		refresh()
	}

	override fun onClick() {
		if (!busy.compareAndSet(false, true)) return
		if (!ShizukuCmd.granted()) {
			busy.set(false)
			requestShizuku()
			return
		}
		val app = applicationContext
		executor.execute {
			val result =
				runBlocking {
					try {
						if (HideController.isHidden(app)) {
							HideController.restore(app)
						} else {
							HideController.applyHide(app)
						}
					} catch (e: Exception) {
						Log.e(TAG, "tile command failed", e)
						HideController.Result(false, listOf("ERROR: ${e.message}"))
					}
				}
			result.lines.forEach { Log.i(TAG, it) }
			android.os.Handler(android.os.Looper.getMainLooper()).post {
				refresh()
				busy.set(false)
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
		val hidden = HideController.isHidden(this)
		tile.state = if (hidden) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
		if (Build.VERSION.SDK_INT >= 29) {
			tile.subtitle = if (hidden) "Tap to restore" else "Tap to hide"
		}
		tile.updateTile()
	}
}
