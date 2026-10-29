package dev.yoanndev90.statusbarhider.overlay.model

/**
 * One SIM's cellular read, ready for the bar: which slot it sits in (1-based,
 * so it matches what the user sees in Settings), its generation label and its
 * signal level.
 *
 * The bar draws one [SignalIcon][dev.yoanndev90.statusbarhider.overlay.widgets.SignalIcon]
 * per entry and a single network-type label, on the entry carrying
 * [isData] — the same split AOSP uses, where the label names the subscription
 * the traffic actually goes through.
 */
data class SimStatus(
	/** Logical slot, 1-based; the platform reports 0-based indices. */
	val slot: Int,
	/** Generation label like "5G" / "LTE"; empty means fall back to "4G". */
	val type: String,
	/** Signal level 0..4, or -1 when it could not be read. */
	val level: Int,
	/** The subscription carrying mobile data: the one the label describes. */
	val isData: Boolean = false
)
