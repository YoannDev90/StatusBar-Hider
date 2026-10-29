package dev.yoanndev90.statusbarhider.overlay.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.model.OverlayBarState
import dev.yoanndev90.statusbarhider.overlay.model.SimStatus

/**
 * The one generation label the cellular block draws: the data SIM's, falling
 * back to the first row when the platform could not say which subscription
 * carries the traffic. A single-SIM device always lands on that first row.
 */
internal fun cellularTypeLabel(sims: List<SimStatus>): String =
	(sims.firstOrNull { it.isData } ?: sims.firstOrNull())?.type.orEmpty()

/**
 * One connectivity icon: visible while its toggle is on, the underlying state
 * applies, and airplane mode has not silenced its radio.
 */
private data class Indicator(
	val icon: Int,
	val label: Int,
	val shown: Boolean,
	val active: Boolean,
	val radio: Boolean = false,
	val typeLabel: Boolean = false
) {
	fun isVisible(airplaneOff: Boolean): Boolean = shown && active && (!radio || airplaneOff)
}

/** Every icon the connectivity widget can draw, in bar order. */
private fun connectivityIndicators(prefs: OverlayPrefs, state: OverlayBarState): List<Indicator> {
	// Known connected devices replace the generic glyph, one icon each.
	val bluetooth =
		if (state.bluetooth && state.btDevices.isNotEmpty()) {
			state.btDevices.map { Indicator(it.icon, it.label, prefs.showBluetooth, true, radio = true) }
		} else {
			listOf(Indicator(R.drawable.ic_bt, R.string.cd_bluetooth, prefs.showBluetooth, state.bluetooth, radio = true))
		}
	return listOf(
		Indicator(R.drawable.ic_plane, R.string.cd_airplane_mode, prefs.showAirplane, state.airplane),
		Indicator(R.drawable.ic_wifi, R.string.cd_wifi, prefs.showWifi, state.wifi, radio = true),
		Indicator(R.drawable.ic_signal, R.string.cd_mobile_data, prefs.showMobileData, state.mobile, radio = true, typeLabel = true)
	) + bluetooth + listOf(
		Indicator(R.drawable.ic_vpn, R.string.cd_vpn, prefs.showVpn, state.vpn, radio = true),
		Indicator(R.drawable.ic_hotspot, R.string.cd_hotspot, prefs.showHotspot, state.hotspot, radio = true),
		Indicator(R.drawable.ic_usb, R.string.cd_usb, prefs.showUsb, state.usbConnected),
		Indicator(R.drawable.ic_nfc, R.string.cd_nfc, prefs.showNfc, state.nfc, radio = true),
		Indicator(R.drawable.ic_gps, R.string.cd_gps, prefs.showGps, state.gps),
		// Unaffected by airplane mode: DND, data saver, auto-rotate, torch and USB are
		// device-wide states that stay meaningful while offline.
		Indicator(R.drawable.ic_dnd, R.string.cd_do_not_disturb, prefs.showDnd, state.dnd),
		Indicator(R.drawable.ic_data_saver, R.string.cd_data_saver, prefs.showDataSaver, state.dataSaver),
		Indicator(R.drawable.ic_rotation, R.string.cd_auto_rotate, prefs.showRotate, state.autoRotate),
		Indicator(R.drawable.ic_torch, R.string.cd_flashlight, prefs.showTorch, state.torch)
	)
}

@Composable
internal fun ConnectivityWidget(
	prefs: OverlayPrefs,
	state: OverlayBarState,
	fg: Color
) {
	val visible = connectivityIndicators(prefs, state).filter { it.isVisible(airplaneOff = !state.airplane) }
	if (visible.isEmpty()) return
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(2.dp)
	) {
		visible.forEach { indicator ->
			if (indicator.typeLabel) {
				// Cellular: one glyph per SIM, then a single label naming the
				// data SIM's real network type - the split AOSP uses, so a dual
				// SIM bar stays as narrow as it can. A level the platform never
				// reported (-1) keeps the static drawable so the icon never
				// turns into a blank.
				state.sims.forEach { sim ->
					if (sim.level >= 0) {
						SignalIcon(sim.level, fg, stringResource(R.string.cd_signal_level_sim, sim.slot, sim.level))
					} else {
						IconImage(indicator.icon, stringResource(indicator.label), fg)
					}
				}
				Text(
					text = cellularTypeLabel(state.sims).ifEmpty { "4G" },
					fontSize = prefs.textSp(-3),
					color = fg,
					modifier = Modifier.padding(start = 1.dp, end = 2.dp)
				)
			} else {
				IconImage(indicator.icon, stringResource(indicator.label), fg)
			}
		}
	}
}
