package dev.yoanndev90.statusbarhider.core.permission

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.keepalive.KeepAliveAccess
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.usage.UsageAccess
import dev.yoanndev90.statusbarhider.overlay.LockScreenOverlayService
import dev.yoanndev90.statusbarhider.overlay.NotifListenerService

private const val TAG = "PermissionAccess"

/** The system answers behind the permission rows (setup checklist and Debug > Permissions). */
data class PermissionGrants(
	val overlay: Boolean,
	val notifListener: Boolean,
	val calendar: Boolean,
	val postNotifs: Boolean,
	val bluetooth: Boolean,
	val usage: Boolean,
	val lockOverlay: Boolean,
	/** Not a permission: whether Doze already exempts the app from battery optimization. */
	val batteryExempt: Boolean = false
)

/**
 * Shared permission helpers. The reads are binder calls (never on the main
 * thread); the launches open the system screens that grant each access.
 */
object PermissionAccess {
	/** Reads [PermissionGrants]; every line is a binder call, so never on the main thread. */
	fun grants(context: Context): PermissionGrants =
		PermissionGrants(
			overlay = Settings.canDrawOverlays(context),
			notifListener = NotifListenerService.isEnabled(context),
			calendar = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED,
			postNotifs = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
			bluetooth = bluetoothGranted(context),
			usage = UsageAccess.granted(context),
			lockOverlay = lockOverlayEnabled(context),
			batteryExempt = KeepAliveAccess.isExempt(context)
		)

	/** Below Android 12 the legacy install-time permission covers the device list. */
	private fun bluetoothGranted(context: Context): Boolean =
		Build.VERSION.SDK_INT < 31 ||
			context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

	/** True when [LockScreenOverlayService] is in the enabled accessibility services list. */
	fun lockOverlayEnabled(context: Context): Boolean {
		val component = ComponentName(context, LockScreenOverlayService::class.java).flattenToString()
		val enabled =
			Settings.Secure
				.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
				.orEmpty()
		return enabled.split(':').any { it == component }
	}

	/** Special-access screens take a package URI; the rest are plain settings pages. */
	fun openSpecialAccess(
		context: Context,
		action: String
	) {
		try {
			context.startActivity(Intent(action, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
		} catch (e: Exception) {
			logFailure(context, "special#$action", action, e)
		}
	}

	fun openSettings(
		context: Context,
		action: String
	) {
		try {
			context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
		} catch (e: Exception) {
			logFailure(context, "settings#$action", action, e)
		}
	}

	private fun logFailure(
		context: Context,
		key: String,
		action: String,
		e: Exception
	) {
		Log.w(TAG, key, e)
		LogStore.appendOnce(context, "$TAG#$key", context.getString(R.string.log_error, "$action: ${e.message}"))
	}
}
