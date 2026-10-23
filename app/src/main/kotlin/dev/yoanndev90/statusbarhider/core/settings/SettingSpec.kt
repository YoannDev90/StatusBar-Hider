@file:Suppress("LongParameterList") // Declarative registry constructors: storage args first, every rendering arg defaulted.

package dev.yoanndev90.statusbarhider.core.settings

/**
 * Single source of truth for one setting: storage key, accessors, default,
 * accepted shape and the metadata a settings row renders from.
 *
 * The registry is plain data: adding a field to the model without describing
 * it here fails [SettingsRegistryTest], and a range declared once feeds both
 * the slider and the clamp, so the two can no longer drift apart.
 *
 * Parameter order is fixed: storage first (key, get, set, default), then
 * rendering (icon, label, subtitle), then policy (shownWhen, exportable).
 *
 * @param T value type on the model.
 * @param S settings model this spec reads and writes.
 */
sealed class SettingSpec<T, S : Any>(
	val key: String,
	val get: (S) -> T,
	val set: (S, T) -> S,
	val default: T,
	val icon: Int? = null,
	val label: Int? = null,
	val subtitle: Int? = null,
	val shownWhen: (S) -> Boolean = { true },
	val exportable: Boolean = true
) {
	/** Puts [value] back inside its accepted shape; runs on every load and import. */
	open fun coerce(value: T): T = value
}

/** On / off toggle. */
class BoolSpec<S : Any>(
	key: String,
	get: (S) -> Boolean,
	set: (S, Boolean) -> S,
	default: Boolean,
	icon: Int? = null,
	label: Int? = null,
	subtitle: Int? = null,
	shownWhen: (S) -> Boolean = { true },
	exportable: Boolean = true
) : SettingSpec<Boolean, S>(key, get, set, default, icon, label, subtitle, shownWhen, exportable)

/**
 * Bounded integer: [min] and [max] feed the slider and the clamp alike, so
 * a stored value out of range is repaired by the same declaration that draws
 * the handle stops.
 */
class IntSpec<S : Any>(
	key: String,
	get: (S) -> Int,
	set: (S, Int) -> S,
	default: Int,
	val min: Int,
	val max: Int,
	icon: Int? = null,
	label: Int? = null,
	val suffix: Int? = null,
	subtitle: Int? = null,
	shownWhen: (S) -> Boolean = { true },
	exportable: Boolean = true
) : SettingSpec<Int, S>(key, get, set, default, icon, label, subtitle, shownWhen, exportable) {
	init {
		require(min <= max) { "empty range for '$key': [$min, $max]" }
	}

	override fun coerce(value: Int): Int = value.coerceIn(min, max)
}

/**
 * Closed set of values, each paired with the label that names it: an index
 * mapping written by hand is unrepresentable. [T] is any value type — an enum
 * today, a plain id string while the font weight is still one.
 */
class EnumSpec<T : Any, S : Any>(
	key: String,
	get: (S) -> T,
	set: (S, T) -> S,
	default: T,
	val entries: List<Pair<T, Int>>,
	icon: Int? = null,
	label: Int? = null,
	subtitle: Int? = null,
	shownWhen: (S) -> Boolean = { true },
	exportable: Boolean = true
) : SettingSpec<T, S>(key, get, set, default, icon, label, subtitle, shownWhen, exportable) {
	/** Accepted values, kept aligned with [entries] by construction. */
	val values: List<T> get() = entries.map { it.first }

	/** Anything outside [entries] is replaced by [default] instead of surviving. */
	override fun coerce(value: T): T = value.takeIf { it in values } ?: default
}

/**
 * Free text with a validity rule: an invalid value falls back to [default]
 * rather than being dropped. The live validation feedback (toasts, button
 * state) stays in the screen that owns the text field.
 */
class TextSpec<S : Any>(
	key: String,
	get: (S) -> String,
	set: (S, String) -> S,
	default: String,
	val validate: (String) -> Boolean,
	icon: Int? = null,
	label: Int? = null,
	subtitle: Int? = null,
	shownWhen: (S) -> Boolean = { true },
	exportable: Boolean = true
) : SettingSpec<String, S>(key, get, set, default, icon, label, subtitle, shownWhen, exportable) {
	override fun coerce(value: String): String = value.takeIf(validate) ?: default
}

/**
 * List setting; [repair] is the invariant re-enforced on every load (the
 * widget order must always name every widget exactly once, stored ids first).
 */
class ListSpec<S : Any>(
	key: String,
	get: (S) -> List<String>,
	set: (S, List<String>) -> S,
	default: List<String>,
	val repair: (List<String>) -> List<String> = { it },
	icon: Int? = null,
	label: Int? = null,
	subtitle: Int? = null,
	shownWhen: (S) -> Boolean = { true },
	exportable: Boolean = true
) : SettingSpec<List<String>, S>(key, get, set, default, icon, label, subtitle, shownWhen, exportable) {
	override fun coerce(value: List<String>): List<String> = repair(value)
}

/**
 * Catch-all for any shape the typed specs do not cover (maps, and whatever
 * the next field brings): same registry slot, same guard tests, with the
 * clamp itself passed in. [T] stays explicit at the call site so the model
 * type cannot drift.
 */
class ValueSpec<T : Any, S : Any>(
	key: String,
	get: (S) -> T,
	set: (S, T) -> S,
	default: T,
	val coerceValue: (T) -> T = { it },
	icon: Int? = null,
	label: Int? = null,
	subtitle: Int? = null,
	shownWhen: (S) -> Boolean = { true },
	exportable: Boolean = true
) : SettingSpec<T, S>(key, get, set, default, icon, label, subtitle, shownWhen, exportable) {
	override fun coerce(value: T): T = coerceValue(value)
}
