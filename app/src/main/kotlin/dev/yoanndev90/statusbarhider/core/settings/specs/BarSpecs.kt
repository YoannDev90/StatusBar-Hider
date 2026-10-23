package dev.yoanndev90.statusbarhider.core.settings.specs

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.BoolSpec
import dev.yoanndev90.statusbarhider.core.settings.ListSpec
import dev.yoanndev90.statusbarhider.core.settings.SettingSpec
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.WidgetId

/**
 * The bar as a whole: its on/off state, the app blacklist and the widget
 * order. The list settings keep custom rows (HiddenApps screen, order
 * editor), so they carry no row metadata yet.
 */
object BarSpecs {
	/** Master switch, driven from the home screen rather than a settings row. */
	val enabled =
		BoolSpec<OverlayPrefs>(
			key = "enabled",
			get = OverlayPrefs::enabled,
			set = { p, v -> p.copy(enabled = v) },
			default = false
		)

	val hideBarInApps =
		BoolSpec<OverlayPrefs>(
			key = "hide_bar_in_apps",
			get = OverlayPrefs::hideBarInApps,
			set = { p, v -> p.copy(hideBarInApps = v) },
			default = false,
			icon = R.drawable.ic_app_blocking,
			label = R.string.switch_hide_bar_in_apps
		)

	val hiddenApps =
		ListSpec<OverlayPrefs>(
			key = "hidden_apps",
			get = OverlayPrefs::hiddenApps,
			set = { p, v -> p.copy(hiddenApps = v) },
			default = emptyList()
		)

	/** Stored as the legacy CSV form; a load always yields the full set, once. */
	val widgetOrder =
		ListSpec<OverlayPrefs>(
			key = "widget_order",
			get = OverlayPrefs::widgetOrder,
			set = { p, v -> p.copy(widgetOrder = v) },
			default = WidgetId.DEFAULT_ORDER,
			repair = { order -> WidgetId.parseOrder(order.joinToString(",")) }
		)

	val specs: List<SettingSpec<*, OverlayPrefs>> = listOf(enabled, hideBarInApps, hiddenApps, widgetOrder)
}
