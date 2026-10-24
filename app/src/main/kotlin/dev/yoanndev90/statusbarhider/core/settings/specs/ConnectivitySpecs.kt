package dev.yoanndev90.statusbarhider.core.settings.specs

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.BoolSpec
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.SettingSpec
import dev.yoanndev90.statusbarhider.core.settings.ValueSpec

/** Connectivity glyphs: radios, transports and the toggles over them. */
object ConnectivitySpecs {
	val showWifi =
		BoolSpec<OverlayPrefs>(
			key = "show_wifi",
			get = OverlayPrefs::showWifi,
			set = { p, v -> p.copy(showWifi = v) },
			default = true,
			icon = R.drawable.ic_wifi,
			label = R.string.switch_wifi
		)

	val showMobileData =
		BoolSpec<OverlayPrefs>(
			key = "show_mobile",
			get = OverlayPrefs::showMobileData,
			set = { p, v -> p.copy(showMobileData = v) },
			default = true,
			icon = R.drawable.ic_signal,
			label = R.string.switch_mobile_data
		)

	val showBluetooth =
		BoolSpec<OverlayPrefs>(
			key = "show_bluetooth",
			get = OverlayPrefs::showBluetooth,
			set = { p, v -> p.copy(showBluetooth = v) },
			default = true,
			icon = R.drawable.ic_bt,
			label = R.string.switch_bluetooth
		)

	val showBtIcons =
		BoolSpec<OverlayPrefs>(
			key = "show_bt_icons",
			get = OverlayPrefs::showBtIcons,
			set = { p, v -> p.copy(showBtIcons = v) },
			default = true,
			icon = R.drawable.ic_bt,
			label = R.string.switch_bt_icons,
			subtitle = R.string.switch_bt_icons_sub
		)

	/** Uppercased MAC to forced kind name; wins over every automatic signal. */
	val btIconOverrides =
		ValueSpec<Map<String, String>, OverlayPrefs>(
			key = "bt_icon_overrides",
			get = OverlayPrefs::btIconOverrides,
			set = { p, v -> p.copy(btIconOverrides = v) },
			default = emptyMap(),
			icon = R.drawable.ic_bt,
			label = R.string.bt_devices_title
		)

	val showAirplane =
		BoolSpec<OverlayPrefs>(
			key = "show_airplane",
			get = OverlayPrefs::showAirplane,
			set = { p, v -> p.copy(showAirplane = v) },
			default = true,
			icon = R.drawable.ic_plane,
			label = R.string.switch_airplane
		)

	val showVpn =
		BoolSpec<OverlayPrefs>(
			key = "show_vpn",
			get = OverlayPrefs::showVpn,
			set = { p, v -> p.copy(showVpn = v) },
			default = false,
			icon = R.drawable.ic_vpn,
			label = R.string.switch_vpn
		)

	val showHotspot =
		BoolSpec<OverlayPrefs>(
			key = "show_hotspot",
			get = OverlayPrefs::showHotspot,
			set = { p, v -> p.copy(showHotspot = v) },
			default = false,
			icon = R.drawable.ic_hotspot,
			label = R.string.switch_hotspot
		)

	val showNfc =
		BoolSpec<OverlayPrefs>(
			key = "show_nfc",
			get = OverlayPrefs::showNfc,
			set = { p, v -> p.copy(showNfc = v) },
			default = false,
			icon = R.drawable.ic_nfc,
			label = R.string.switch_nfc
		)

	val showGps =
		BoolSpec<OverlayPrefs>(
			key = "show_gps",
			get = OverlayPrefs::showGps,
			set = { p, v -> p.copy(showGps = v) },
			default = false,
			icon = R.drawable.ic_gps,
			label = R.string.switch_gps
		)

	val showDnd =
		BoolSpec<OverlayPrefs>(
			key = "show_dnd",
			get = OverlayPrefs::showDnd,
			set = { p, v -> p.copy(showDnd = v) },
			default = false,
			icon = R.drawable.ic_dnd,
			label = R.string.switch_dnd
		)

	val showDataSaver =
		BoolSpec<OverlayPrefs>(
			key = "show_data_saver",
			get = OverlayPrefs::showDataSaver,
			set = { p, v -> p.copy(showDataSaver = v) },
			default = false,
			icon = R.drawable.ic_data_saver,
			label = R.string.switch_data_saver
		)

	val showRotate =
		BoolSpec<OverlayPrefs>(
			key = "show_rotate",
			get = OverlayPrefs::showRotate,
			set = { p, v -> p.copy(showRotate = v) },
			default = false,
			icon = R.drawable.ic_rotation,
			label = R.string.switch_auto_rotate
		)

	val showTorch =
		BoolSpec<OverlayPrefs>(
			key = "show_torch",
			get = OverlayPrefs::showTorch,
			set = { p, v -> p.copy(showTorch = v) },
			default = false,
			icon = R.drawable.ic_torch,
			label = R.string.switch_flashlight,
			subtitle = R.string.switch_flashlight_sub
		)

	val showUsb =
		BoolSpec<OverlayPrefs>(
			key = "show_usb",
			get = OverlayPrefs::showUsb,
			set = { p, v -> p.copy(showUsb = v) },
			default = true,
			icon = R.drawable.ic_usb,
			label = R.string.switch_usb
		)

	val showBandwidth =
		BoolSpec<OverlayPrefs>(
			key = "show_bandwidth",
			get = OverlayPrefs::showBandwidth,
			set = { p, v -> p.copy(showBandwidth = v) },
			default = false,
			icon = R.drawable.ic_data_usage,
			label = R.string.switch_bandwidth,
			subtitle = R.string.switch_bandwidth_sub
		)

	/** Merge rx + tx into one figure; only meaningful above the bandwidth widget. */
	val bandwidthMerged =
		BoolSpec<OverlayPrefs>(
			key = "bandwidth_merged",
			get = OverlayPrefs::bandwidthMerged,
			set = { p, v -> p.copy(bandwidthMerged = v) },
			default = false,
			icon = R.drawable.ic_swap,
			label = R.string.switch_merged_speed,
			shownWhen = { it.showBandwidth }
		)

	val specs: List<SettingSpec<*, OverlayPrefs>> =
		listOf(
			showWifi,
			showMobileData,
			showBluetooth,
			showBtIcons,
			btIconOverrides,
			showAirplane,
			showVpn,
			showHotspot,
			showNfc,
			showGps,
			showDnd,
			showDataSaver,
			showRotate,
			showTorch,
			showUsb,
			showBandwidth,
			bandwidthMerged
		)
}
