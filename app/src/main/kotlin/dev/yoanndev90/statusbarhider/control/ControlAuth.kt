package dev.yoanndev90.statusbarhider.control

import android.content.Context
import android.content.Intent
import android.util.Log
import dev.yoanndev90.statusbarhider.data.AppSettings
import java.security.MessageDigest

/**
 * Gate for the control API: a broadcast is dispatched only when it carries
 * this install's secret in the [EXTRA_TOKEN] extra.
 *
 * Identifying the sender is not an option. A manifest receiver is never told
 * who sent a broadcast: on API 35 [android.content.BroadcastReceiver.getSentFromUid]
 * returned INVALID_UID with a null package for adb (shell uid) and for a
 * third-party app alike, with and without the senders listed in `<queries>`,
 * so any uid-based check rejected everyone, including our own widget.
 *
 * The token keeps every documented caller working without exposing a secret
 * wider than needed:
 *
 * - the widget adds it in-process (see `BarWidgetProvider`);
 * - `adb shell am broadcast ... --es token <TOKEN>` with the token shown on
 *   the Status tab and copyable from there;
 * - Tasker passes it as an extra.
 *
 * A third-party app cannot read app-private prefs, so it cannot learn the
 * token. A missing or wrong token drops the broadcast before dispatch; the
 * rejection goes to logcat only, never to the on-disk app log, so a probing
 * app gets no disk-write primitive through this receiver.
 */
object ControlAuth {
	private const val TAG = "ControlActions"

	/** String extra name: `--es token <value>` on adb, Extra `token` in Tasker. */
	const val EXTRA_TOKEN = "token"

	/** True when [intent] carries this install's token and may be dispatched. */
	fun accept(
		context: Context,
		intent: Intent
	): Boolean {
		val ok = matches(AppSettings.controlToken(context), intent.getStringExtra(EXTRA_TOKEN))
		if (!ok) Log.w(TAG, "Rejecting control broadcast: missing or invalid token")
		return ok
	}

	/**
	 * Pure comparison, extracted so unit tests can cover it without a
	 * broadcast. Byte-wise constant time: the token is short-lived knowledge
	 * that must not leak through response timing.
	 */
	internal fun matches(
		expected: String?,
		provided: String?
	): Boolean {
		if (expected.isNullOrEmpty() || provided == null) return false
		return MessageDigest.isEqual(expected.toByteArray(), provided.toByteArray())
	}
}
