package dev.yoanndev90.statusbarhider.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * External control API: accepts explicit broadcasts carrying one of the
 * [ControlActions] actions.
 *
 * No intent filter on purpose: only broadcasts aimed at this component are
 * taken, e.g.
 * `am broadcast -n <pkg>/.control.ControlReceiver -a <pkg>.SHOW_BAR --es token <TOKEN>`
 *
 * Every broadcast must carry the install's token, checked by [ControlAuth]
 * before anything is dispatched.
 */
class ControlReceiver : BroadcastReceiver() {
	companion object {
		private const val TAG = "ControlReceiver"

		/** Outlives the broadcast; the dispatch itself is short. */
		private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	}

	override fun onReceive(
		context: Context,
		intent: Intent
	) {
		// Auth reads (and possibly creates) SharedPreferences and the app log,
		// and dispatch reaches the Shizuku binder plus startForegroundService:
		// none of that may run on the main thread of a broadcast, whose budget
		// is 10 s before the app is ANR'd.
		val pending = goAsync()
		scope.launch {
			try {
				if (!ControlAuth.accept(context, intent)) return@launch
				if (!ControlActions.dispatch(context, intent.action)) {
					Log.w(TAG, "Ignoring unexpected action: ${intent.action}")
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.w(TAG, "Control dispatch failed", e)
			} finally {
				pending.finish()
			}
		}
	}
}
