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
 * `am broadcast -n <pkg>/.control.ControlReceiver -a <pkg>.SHOW_BAR --es token <TOKEN>`
 *
 * Every broadcast must carry the install's token, checked by [ControlAuth]
 * before anything is dispatched.
 */
class ControlReceiver : BroadcastReceiver() {
	override fun onReceive(
		context: Context,
		intent: Intent
	) {
		if (!ControlAuth.accept(context, intent)) return
		if (!ControlActions.dispatch(context, intent.action)) {
			Log.w("ControlActions", "Ignoring unexpected action: ${intent.action}")
		}
	}
}
