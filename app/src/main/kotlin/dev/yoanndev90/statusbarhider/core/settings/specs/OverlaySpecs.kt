package dev.yoanndev90.statusbarhider.core.settings.specs

import dev.yoanndev90.statusbarhider.core.settings.SettingSpec
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs

/**
 * The overlay settings file as one registry: every section's specs plus the
 * pure normalizer shared by the codec, the profiles store and backup imports.
 *
 * Describing a setting here is what gives it a clamp, a default check and
 * (once the renderer lands) a settings row; [dev.yoanndev90.statusbarhider
 * .core.settings.SettingsRegistryTest] enforces that no model field escapes
 * the registry.
 */
object OverlaySpecs {
	/** Every overlay setting, grouped by section file. */
	val all: List<SettingSpec<*, OverlayPrefs>> =
		GeneralSpecs.specs +
			AppearanceSpecs.specs +
			ClockSpecs.specs +
			BatterySpecs.specs +
			ConnectivitySpecs.specs +
			NotificationSpecs.specs +
			CutoutSpecs.specs +
			LockSpecs.specs +
			BarSpecs.specs

	/** Clamps every field through its own spec; no Context, no I/O. */
	fun normalize(prefs: OverlayPrefs): OverlayPrefs =
		all.fold(prefs) { p, spec -> spec.set(p, spec.coerce(spec.get(p))) }
}
