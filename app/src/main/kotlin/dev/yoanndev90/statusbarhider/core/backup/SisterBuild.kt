package dev.yoanndev90.statusbarhider.core.backup

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException

/**
 * The other install of this app: debug and release are two packages
 * (`applicationIdSuffix = ".debug"`) with separate storage, but identical
 * code, so each build can find the sibling by package name alone and offer
 * to pull its settings.
 */
object SisterBuild {
	private const val DEBUG_SUFFIX = ".debug"

	/** The sister's package for [self]: strips or adds the debug suffix. */
	fun sisterPackageOf(self: String): String =
		if (self.endsWith(DEBUG_SUFFIX)) self.removeSuffix(DEBUG_SUFFIX) else self + DEBUG_SUFFIX

	/**
	 * True when [caller] may read our settings blob. The system derives the
	 * calling package from the caller's UID, so only the real sibling -
	 * the fixed sister package installed alongside us - passes this gate.
	 */
	fun isSisterCall(
		self: String,
		caller: String?
	): Boolean = caller != null && caller == sisterPackageOf(self)

	/** Authority of the settings provider inside [packageName]. */
	fun authorityOf(packageName: String): String = "$packageName.settingsprovider"

	/** Installed name of the sister build, or null when only one build is installed. */
	fun installedLabel(context: Context): String? {
		val pm = context.packageManager
		return try {
			pm.getApplicationLabel(pm.getApplicationInfo(sisterPackageOf(context.packageName), 0)).toString()
		} catch (ignore: NameNotFoundException) {
			null
		}
	}
}
