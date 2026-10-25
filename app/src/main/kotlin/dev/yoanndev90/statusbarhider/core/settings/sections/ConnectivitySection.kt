package dev.yoanndev90.statusbarhider.core.settings.sections

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.SectionGroup
import dev.yoanndev90.statusbarhider.core.settings.SettingRow
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.Subtitle
import dev.yoanndev90.statusbarhider.core.settings.settingRows
import dev.yoanndev90.statusbarhider.core.settings.specs.ConnectivitySpecs
import dev.yoanndev90.statusbarhider.ui.screens.settings.BtDevicesSection

/**
 * Connectivity indicators: radios, radios-adjacent toggles, system states
 * and bandwidth — the toggles flat in declaration order, then the Bluetooth
 * per-device icon block as its own group.
 */
object ConnectivitySection :
	SettingSection<OverlayPrefs>(
		id = "connectivity",
		icon = R.drawable.ic_wifi,
		title = R.string.cat_connectivity,
		subtitle = Subtitle.Res(R.string.cat_connectivity_sub),
		group = SectionGroup.CUSTOM_BAR,
		keywords = listOf("wifi", "bluetooth", "airplane", "vpn", "hotspot", "bandwidth", "nfc", "gps", "dnd"),
		rows =
			settingRows(
				ConnectivitySpecs.showWifi,
				ConnectivitySpecs.showMobileData,
				ConnectivitySpecs.showBluetooth,
				ConnectivitySpecs.showAirplane,
				ConnectivitySpecs.showUsb,
				ConnectivitySpecs.showNfc,
				ConnectivitySpecs.showGps,
				ConnectivitySpecs.showDnd,
				ConnectivitySpecs.showDataSaver,
				ConnectivitySpecs.showRotate,
				ConnectivitySpecs.showTorch,
				ConnectivitySpecs.showVpn,
				ConnectivitySpecs.showHotspot,
				ConnectivitySpecs.showBandwidth,
				ConnectivitySpecs.bandwidthMerged
			) +
				listOf(
					SettingRow.Group(
						rows =
							listOf(
								SettingRow.OfSpec(ConnectivitySpecs.showBtIcons),
								SettingRow.Custom { prefs, vm ->
									BtDevicesSection(overrides = prefs.btIconOverrides) { mac, kindName ->
										vm.updatePrefs {
											copy(btIconOverrides = if (kindName == null) btIconOverrides - mac else btIconOverrides + (mac to kindName))
										}
									}
								}
							)
					)
				)
	)
