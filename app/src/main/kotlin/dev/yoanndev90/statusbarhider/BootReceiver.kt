package dev.yoanndev90.statusbarhider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.yoanndev90.statusbarhider.data.OemRepository
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.overlay.StatusBarOverlayService
import rikka.shizuku.Shizuku

class BootReceiver : BroadcastReceiver() {
	companion object {
		private const val TAG = "StatusBarHider"
		private const val LISTENER_TIMEOUT_MS = 30_000L
	}

	override fun onReceive(
		context: Context,
		intent: Intent
	) {
		if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
		Log.i(TAG, "Boot completed - scheduling status bar hide")

		val handler = Handler(Looper.getMainLooper())
		val oemRepo = OemRepository.getInstance(context)
		val prefsRepo = OverlayPrefsRepository.getInstance(context)

		val listener =
			object : Shizuku.OnBinderReceivedListener {
				override fun onBinderReceived() {
					handler.removeCallbacksAndMessages(null)
					Shizuku.removeBinderReceivedListener(this)
					if (!ShizukuCmd.granted()) {
						Log.w(TAG, "Shizuku not authorized - skipping auto-hide")
						return
					}
					Log.i(TAG, "Shizuku ready - applying hide")
					val oem = oemRepo.refresh()
					for (cmd in oem.hide) {
						val (_, out) = ShizukuCmd.run(cmd.cmd)
						Log.i(TAG, "${cmd.name} -> $out")
					}
					Log.i(TAG, "Auto-hide done")
					if (prefsRepo.state.value.enabled) {
						Log.i(TAG, "Restarting custom overlay")
						StatusBarOverlayService.start(context)
					}
				}
			}

		// If binder is already available, run immediately
		if (Shizuku.pingBinder()) {
			listener.onBinderReceived()
			return
		}

		Shizuku.addBinderReceivedListener(listener)

		// Safety timeout: remove listener if binder never arrives
		handler.postDelayed({
			Shizuku.removeBinderReceivedListener(listener)
			Log.w(TAG, "Shizuku binder not received within ${LISTENER_TIMEOUT_MS / 1000}s - aborting auto-hide")
		}, LISTENER_TIMEOUT_MS)
	}
}
