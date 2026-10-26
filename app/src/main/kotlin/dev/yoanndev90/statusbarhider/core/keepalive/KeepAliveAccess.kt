package dev.yoanndev90.statusbarhider.core.keepalive

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.oem.OemDeepLink

private const val TAG = "KeepAliveAccess"

/**
 * Opens the screens that decide whether the app may run in the background,
 * plus the one state that *can* be read: the battery optimization exemption.
 *
 * Every OEM autostart / "no restriction" / sleeping-apps toggle is a private
 * AppOps with no public API, so nothing here can report whether it is set -
 * the checklist opens the screen and the user flips it. What the code owns is
 * reaching a screen at all: a skin that renamed its activity falls through the
 * remaining [OemDeepLink.components] and then to [OemDeepLink.action], and a
 * total miss is logged once instead of crashing a settings screen.
 */
object KeepAliveAccess {
	/** Whether Doze treats the app as exempt; the only keep-alive state readable. */
	fun isExempt(context: Context): Boolean =
		try {
			context
				.getSystemService(PowerManager::class.java)
				?.isIgnoringBatteryOptimizations(context.packageName) == true
		} catch (e: Exception) {
			// A vendor can strip the method; the row then reads as "missing",
			// which is the honest answer anyway.
			Log.w(TAG, "isExempt", e)
			false
		}

	/**
	 * Opens the exemption prompt for this package, falling back to the full
	 * list of exempt apps. The prompt needs
	 * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; the list does not, and is where
	 * skins that swallow the prompt still land the user.
	 */
	fun openBatteryOptimization(context: Context): Boolean {
		val prompt =
			Intent(
				Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
				Uri.parse("package:${context.packageName}")
			)
		if (tryStart(context, "battery prompt", prompt)) return true
		return tryStart(
			context,
			"battery list",
			Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
		)
	}

	/**
	 * Opens the first screen of [link] this device actually has. Returns false
	 * when none resolved, which the caller shows as a log line - the row stays,
	 * because the same tap is worth retrying after a firmware update.
	 */
	fun open(
		context: Context,
		link: OemDeepLink
	): Boolean {
		for (spec in link.components) {
			val component = parseComponent(spec)
			if (component == null) {
				// A typo in an OEM file, caught by OemConfigParseTest before it
				// ships; reaching here means the test and the file disagree.
				Log.w(TAG, "Malformed component '$spec' in keepAlive.${link.name}")
				continue
			}
			// try/catch rather than resolveActivity(): predicting whether the
			// screen exists means a PackageManager query, which package
			// visibility answers "no" for anything undeclared in <queries>.
			// Letting startActivity answer directly is one call instead of two.
			// A miss is the normal case here - candidates exist to cover renamed
			// skins - so it stays on logcat instead of the in-app log.
			if (tryStart(context, "keepAlive.${link.name} <$spec>", Intent().setComponent(component), quiet = true)) {
				return true
			}
		}
		if (link.action.isNotBlank() && tryStart(context, "keepAlive.${link.name} ${link.action}", Intent(link.action))) {
			return true
		}
		val where = "open#${link.name}"
		Log.w(TAG, "No screen reachable for keepAlive.${link.name}")
		LogStore.appendOnce(
			context,
			"$TAG#$where",
			"ERROR: no keep-alive screen for ${link.name} on this device"
		)
		return false
	}

	/**
	 * Splits a flattened `package/class` into its two halves. Android's own
	 * `ComponentName.unflattenFromString` cannot be called from a unit test
	 * (android.jar throws "Stub!" on the JVM), so the rule is spelled out here
	 * and checked directly by `OemConfigParseTest`: exactly one slash, and
	 * neither half empty - a bare package or a trailing slash would otherwise
	 * build a target that only ever fails as an unhelpful ActivityNotFound.
	 */
	private fun parseComponent(spec: String): ComponentName? {
		val parts = spec.split('/')
		if (parts.size != 2 || parts.any { it.isEmpty() }) return null
		return ComponentName(parts[0], parts[1])
	}

	private fun tryStart(
		context: Context,
		where: String,
		intent: Intent,
		quiet: Boolean = false
	): Boolean =
		try {
			context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
			true
		} catch (e: Exception) {
			// The in-app log is append-once over the whole file, so a probe that
			// is expected to miss on most devices must not reach it.
			if (quiet) {
				Log.i(TAG, "$where not available: ${e.message}")
			} else {
				Log.w(TAG, where, e)
				LogStore.appendOnce(
					context,
					"$TAG#$where",
					"ERROR: $where: ${e.message}"
				)
			}
			false
		}
}
