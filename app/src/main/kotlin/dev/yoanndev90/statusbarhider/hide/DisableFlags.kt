package dev.yoanndev90.statusbarhider.hide

/**
 * The two figures [HideInteractor] reads out of a `dumpsys statusbar` dump,
 * taken apart because they answer different questions:
 *
 * - [aggregate] is the `mDisabled1=` line: every disable record OR-ed
 *   together, so it moves by itself whenever SystemUI adds or clears its own
 *   flags (lock screen, setup wizard).
 * - [shared] is the `what1` of the one record every
 *   `cmd statusbar send-disable-flag` caller writes into - AOSP registers all
 *   of them under a single static token, ours among them, so this is the
 *   shell's current answer for our flags and for nobody else's.
 *
 * A dump may print either, both or neither.
 */
data class DisableFlags(
	val aggregate: Long?,
	val shared: Long?
) {
	/** Our flags as precisely as the dump allows: the shared record, else the sum. */
	val ours: Long?
		get() = shared ?: aggregate
}

/** Marks the record shared by every `cmd statusbar` caller, third parties included. */
private const val SHARED_TOKEN = "StatusBarShellCommandToken"

/** [DisableFlags] out of a `dumpsys statusbar` dump, or null when it carries neither. */
fun parseDisableFlags(out: String): DisableFlags? {
	var aggregate: Long? = null
	var shared: Long? = null
	for (line in out.lineSequence()) {
		val trimmed = line.trimStart()
		if (aggregate == null && trimmed.startsWith("mDisabled1=")) {
			aggregate = hexField(trimmed, "mDisabled1=")
		}
		if (shared == null && line.contains(SHARED_TOKEN)) {
			shared = hexField(line, "what1=")
		}
	}
	return if (aggregate == null && shared == null) null else DisableFlags(aggregate, shared)
}

/**
 * Whether [flags] still hold [mask]: null when the dump answered nothing.
 *
 * With no mask recorded yet - a first run since install, or a device whose
 * dump never prints the shared record - the answer falls back to "some record
 * is active", which is all this check could say before masks existed.
 */
fun holdsMask(flags: DisableFlags?, mask: Long?): Boolean? {
	if (flags == null) return null
	if (mask == null) return (flags.aggregate ?: flags.shared)?.let { it != 0L }
	return flags.ours?.let { (it and mask) == mask }
}

/** Value of `key=0x...` in [line]; null when the key or a hex value is missing. */
private fun hexField(line: String, key: String): Long? {
	val start = line.indexOf(key).takeIf { it >= 0 } ?: return null
	val digits = line
		.substring(start + key.length)
		.trim()
		.removePrefix("0x")
		.takeWhile { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
	return digits.takeIf { it.isNotEmpty() }?.toLongOrNull(16)
}
