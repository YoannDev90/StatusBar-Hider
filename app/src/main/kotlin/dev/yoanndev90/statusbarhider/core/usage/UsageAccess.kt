package dev.yoanndev90.statusbarhider.core.usage

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Self-check for the "Usage access" special permission, the one behind the
 * foreground-app blacklist. The grant lives in Settings >
 * Special app access > Usage access, so this only reports it.
 */
object UsageAccess {
	fun granted(context: Context): Boolean =
		try {
			val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
			val mode =
				if (Build.VERSION.SDK_INT >= 29) {
					appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
				} else {
					@Suppress("DEPRECATION")
					appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
				}
			mode == AppOpsManager.MODE_ALLOWED
		} catch (_: Exception) {
			false
		}
}
