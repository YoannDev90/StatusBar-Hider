package dev.yoanndev90.statusbarhider.core.backup

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.core.os.bundleOf

/**
 * Serves this install's settings blob to the sister build (debug <-> release).
 *
 * exported=true cannot be avoided: the sibling is a separate package signed
 * with a different key, so `exported=false` and a signature permission would
 * both lock it out. The gate lives in [call]: only the sister package -
 * resolved by the system from the caller's UID, not forgeable - gets an
 * answer. The blob itself is the file export, which never carries the
 * control-API token (see [SettingsBackup.export]).
 */
class SettingsProvider : ContentProvider() {
	override fun onCreate(): Boolean = true

	override fun call(
		method: String,
		arg: String?,
		extras: Bundle?
	): Bundle {
		val app = requireNotNull(context) { "provider called before attach" }
		if (!SisterBuild.isSisterCall(app.packageName, callingPackage)) {
			throw SecurityException("Only ${SisterBuild.sisterPackageOf(app.packageName)} may read the settings backup")
		}
		require(method == METHOD_EXPORT) { "Unknown method '$method'" }
		return bundleOf(KEY_BLOB to SettingsBackup.export(app))
	}

	// Read-only by design: only call(METHOD_EXPORT) has an answer.
	override fun query(
		uri: Uri,
		projection: Array<out String>?,
		selection: String?,
		selectionArgs: Array<out String>?,
		sortOrder: String?
	): Cursor? = null

	override fun getType(uri: Uri): String? = null

	override fun insert(
		uri: Uri,
		values: ContentValues?
	): Uri? = null

	override fun delete(
		uri: Uri,
		selection: String?,
		selectionArgs: Array<out String>?
	): Int = 0

	override fun update(
		uri: Uri,
		values: ContentValues?,
		selection: String?,
		selectionArgs: Array<out String>?
	): Int = 0

	companion object {
		/** The one method this provider answers. */
		const val METHOD_EXPORT = "export"

		/** Bundle key carrying the settings blob. */
		const val KEY_BLOB = "blob"

		/**
		 * Reads the sister build's settings blob through its provider. Throws
		 * [SettingsBackupException] when the sibling is unreachable or refuses
		 * (not installed, older install without this provider, oversize blob).
		 */
		fun fetchSisterBlob(context: Context): String {
			val sister = SisterBuild.sisterPackageOf(context.packageName)
			val uri = "content://${SisterBuild.authorityOf(sister)}".toUri()
			val bundle =
				try {
					context.contentResolver.call(uri, METHOD_EXPORT, null, null)
				} catch (e: Exception) {
					throw SettingsBackupException("Cannot reach $sister: ${e.message}", e)
				}
			return bundle?.getString(KEY_BLOB) ?: throw SettingsBackupException("Cannot reach $sister")
		}
	}
}
