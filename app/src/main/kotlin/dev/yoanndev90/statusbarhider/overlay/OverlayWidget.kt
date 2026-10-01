package dev.yoanndev90.statusbarhider.overlay

/**
 * Widget model for the custom status bar overlay.
 *
 * MIT-safe re-implementation inspired by Dragon-Launcher's StatusBar sealed class
 * (GPL-3.0, not copied). Only ideas are reused: widget list, formatters, toggles.
 */
sealed interface OverlayWidget {
	data class Time(
		val format: String = DEFAULT_FORMAT_WITH_SECONDS
	) : OverlayWidget {
		companion object {
			const val DEFAULT_FORMAT_WITH_SECONDS = "HH:mm:ss"
			const val DEFAULT_FORMAT_NO_SECONDS = "HH:mm"
		}
	}

	data class Date(
		val format: String = "MMM dd"
	) : OverlayWidget

	data class Battery(
		val showPercentage: Boolean = true
	) : OverlayWidget

	data class Connectivity(
		val showWifi: Boolean = true,
		val showMobileData: Boolean = true,
		val showBluetooth: Boolean = true,
		val showAirplane: Boolean = true,
		val showVpn: Boolean = false,
		val showHotspot: Boolean = false
	) : OverlayWidget

	data class NextAlarm(
		val format: String = "HH:mm"
	) : OverlayWidget

	data class Bandwidth(
		val merged: Boolean = false
	) : OverlayWidget

	data object SpacerCutout : OverlayWidget
}

/** Default widget order for a fresh install. */
fun defaultWidgets(): List<OverlayWidget> =
	listOf(
		OverlayWidget.Time(),
		OverlayWidget.SpacerCutout,
		OverlayWidget.Connectivity(),
		OverlayWidget.Battery(),
		OverlayWidget.NextAlarm()
	)
