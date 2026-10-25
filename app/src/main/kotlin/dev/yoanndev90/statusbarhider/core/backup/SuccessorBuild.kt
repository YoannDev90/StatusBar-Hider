package dev.yoanndev90.statusbarhider.core.backup

/**
 * The app this build turns into: the launcher label became "Corniche", and
 * the package name follows it. The successor is therefore a *different*
 * package, which [SisterBuild.isSisterCall] can never match - so this last
 * release under the old name opens a one-way door for it.
 *
 * The successor pulls our blob exactly once, to migrate the user, and only
 * ever reads: [SettingsProvider] answers a single METHOD_EXPORT, the blob is
 * the file export which carries no control-API token (see
 * [SettingsBackup.export]), and `query()` stays null. The caller package is
 * resolved by the system from the caller's UID, so it cannot be forged.
 *
 * Drop this object together with the rename: from then on the successor *is*
 * this package and the sister gate alone covers both flavours.
 */
object SuccessorBuild {
	/** Both flavours the successor installs as (`applicationIdSuffix = ".debug"`). */
	private val PACKAGES = setOf(
		"dev.yoanndev90.corniche",
		"dev.yoanndev90.corniche.debug"
	)

	/** True when [caller] is the renamed successor, allowed to read our blob. */
	fun isSuccessorCall(caller: String?): Boolean = caller != null && caller in PACKAGES

	/** Human wording for the refusal message of [SettingsProvider]. */
	fun readableBy(): String = "the successor build (${PACKAGES.first()})"
}
