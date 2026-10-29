package dev.yoanndev90.statusbarhider.overlay.system

import android.net.TrafficStats
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.model.IndicatorFormats
import dev.yoanndev90.statusbarhider.overlay.model.OverlayBarState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the device's status into [OverlayBarState]: battery, the connectivity
 * row, the alarm / calendar text, the cellular label and the bandwidth delta.
 *
 * [usbConnected] and [torchIds] are read through lambdas because they must be
 * picked up *after* readConnectivity() resumes - a value captured before the
 * probe would go stale. The battery has no suspension around it, so its two
 * values arrive as plain arguments instead. The bandwidth sampler's baseline
 * lives here too: it belongs to the sampling, not to the service around it.
 */
internal class SystemStatusReader(
	private val indicators: SystemIndicators,
	private val prefs: () -> OverlayPrefs,
	private val updateBarState: ((OverlayBarState) -> OverlayBarState) -> Unit,
	private val usbConnected: () -> Boolean,
	private val torchIds: () -> Set<String>
) {
	companion object {
		/**
		 * Largest gap still taken as "the previous sample": the sampler stops
		 * with the screen, so a restart after a long sleep must re-baseline
		 * instead of reporting the whole gap as traffic.
		 */
		private const val BANDWIDTH_MAX_GAP_MS = 5_000L
	}

	/** Sampler baseline; reset implicitly by the gap check after a screen-off. */
	private var lastRx = -1L
	private var lastTx = -1L
	private var lastSampleAt = 0L

	/** Non-suspending: the caller has just written both values, so they are read as given. */
	fun updateBattery(batteryPct: Int, batteryCharging: Boolean) {
		updateBarState { it.copy(batteryPct = batteryPct, batteryCharging = batteryCharging) }
	}

	/** Refreshes only the cellular indicator, on the faster loop's own cadence. */
	suspend fun updateMobile() {
		val m = indicators.readMobile(prefs())
		updateBarState { it.copy(mobile = m.active, sims = m.sims) }
	}

	suspend fun updateCalendar() {
		val p = prefs()
		val text = withContext(Dispatchers.IO) { indicators.queryCalendar(p.showCalendar, p.use24h) }
		updateBarState { it.copy(calendarText = text) }
	}

	suspend fun updateAlarm() {
		val p = prefs()
		val text = withContext(Dispatchers.IO) { indicators.queryAlarm(p.showAlarm, p.use24h) }
		updateBarState { it.copy(alarmText = text) }
	}

	suspend fun updateConnectivity() {
		val p = prefs()
		val c = indicators.readConnectivity(p)
		updateBarState {
			it.copy(
				usbConnected = usbConnected(),
				airplane = c.airplane,
				wifi = c.wifi,
				mobile = c.mobile,
				sims = c.sims,
				bluetooth = c.bluetooth,
				btDevices = c.btDevices,
				vpn = c.vpn,
				hotspot = c.hotspot,
				nfc = c.nfc,
				gps = c.gps,
				dnd = c.dnd,
				dataSaver = c.dataSaver,
				autoRotate = c.autoRotate,
				torch = p.showTorch && torchIds().isNotEmpty()
			)
		}
	}

	fun updateBandwidth() {
		val rx = TrafficStats.getTotalRxBytes()
		val tx = TrafficStats.getTotalTxBytes()
		val now = System.currentTimeMillis()
		// The loop dies with the screen: a delta against the last pre-sleep
		// sample would report the whole sleep as ".../s".
		val comparable = lastRx >= 0 && rx >= 0 && now - lastSampleAt <= BANDWIDTH_MAX_GAP_MS
		if (comparable) {
			val dRx = rx - lastRx
			val dTx = if (lastTx >= 0 && tx >= 0) tx - lastTx else 0L
			if (prefs().bandwidthMerged) {
				updateBarState {
					it.copy(
						bandwidthRx = IndicatorFormats.speedParts(dRx + dTx),
						bandwidthTx = null
					)
				}
			} else {
				updateBarState {
					it.copy(
						bandwidthRx = IndicatorFormats.speedParts(dRx),
						bandwidthTx = IndicatorFormats.speedParts(dTx)
					)
				}
			}
		}
		lastRx = rx
		lastTx = tx
		lastSampleAt = now
	}
}
