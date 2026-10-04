package dev.yoanndev90.statusbarhider.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * External control API: accepts explicit broadcasts carrying one of the
 * [ControlActions] actions.
 *
 * No intent filter on purpose: only broadcasts aimed at this component are
 * taken, e.g.
 * `am broadcast -n dev.yoanndev90.statusbarhider/.control.ControlReceiver -a dev.yoanndev90.statusbarhider.SHOW_BAR`
 */
class ControlReceiver : BroadcastReceiver() {
	override fun onReceive(
		context: Context,
		intent: Intent
	) {
		if (!ControlActions.dispatch(context, intent.action)) {
			Log.w("ControlActions", "Ignoring unexpected action: ${intent.action}")
		}
	}
}
