package dev.yoanndev90.statusbarhider.overlay

import android.Manifest
import android.app.AlarmManager
import android.app.KeyguardManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.provider.CalendarContract
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.log.LogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only queries against the platform for the overlay indicators.
 *
 * Every call hits the system service fresh (no caching): the service re-runs
 * them on each refresh cycle, and a stale cache would show a state the bar
 * never sees. Failures fall back to "off" / null — a missing permission or a
 * hidden API only costs one icon, and each failure reaches logcat plus the
 * in-app log (once per query, see [warn]) instead of vanishing.
 */
internal class SystemIndicators(
	private val context: Context
) {
	/** True while the keyguard is up - the screen the notifications would show over. */
	fun isKeyguardUp(): Boolean =
		try {
			// isDeviceLocked() answers "is it *securely* locked", so it is false on
			// Swipe/None lock screens where the keyguard still paints and the
			// notification contents are readable; isKeyguardLocked() reports the
			// visibility, which is what the privacy filter needs.
			context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
		} catch (e: Exception) {
			warn("isKeyguardUp", e)
			false
		}

	/** Next calendar event in the next 24h as "HH:mm Title" (or "h:mm a" in 12 h). */
	fun queryCalendar(
		enabled: Boolean,
		use24h: Boolean
	): String? {
		if (!enabled || context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
			return null
		}
		return try {
			val now = System.currentTimeMillis()
			val until = now + 24L * 60 * 60 * 1000
			val uri = Uri.withAppendedPath(CalendarContract.Instances.CONTENT_URI, "$now/$until")
			val projection =
				arrayOf(
					CalendarContract.Instances.BEGIN,
					CalendarContract.Instances.END,
					CalendarContract.Instances.TITLE,
					CalendarContract.Instances.ALL_DAY
				)
			context.contentResolver
				.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
				?.use { c -> firstUpcoming(c, now, use24h) }
		} catch (e: Exception) {
			warn("queryCalendar", e)
			null
		}
	}

	/** The first instance worth showing, or null when the window holds none. */
	private fun firstUpcoming(
		c: Cursor,
		now: Long,
		use24h: Boolean
	): String? {
		// Built once for the whole scan. The clock follows the pref: a hardcoded
		// 24 h pattern next to a 12 h clock reads as a bug.
		val fmt = SimpleDateFormat(if (use24h) "HH:mm" else "h:mm a", Locale.getDefault())
		while (c.moveToNext()) {
			val title = c.getString(2)?.trim().orEmpty()
			val allDay = c.getInt(3) == 1
			// Empty titles are noise; the Instances range also contains events
			// that already finished, but an all-day instance covers its day.
			val stillAhead = allDay || c.getLong(1) > now
			if (title.isNotEmpty() && stillAhead) {
				val clock = if (allDay) "" else "${fmt.format(Date(c.getLong(0)))} "
				return "$clock$title"
			}
		}
		return null
	}

	/** Next alarm clock, or null when none / feature off. */
	fun queryAlarm(
		enabled: Boolean,
		use24h: Boolean
	): String? {
		if (!enabled) return null
		return try {
			val am = context.getSystemService(AlarmManager::class.java)
			val trigger = am?.nextAlarmClock?.triggerTime ?: return null
			val fmt = SimpleDateFormat(if (use24h) "HH:mm" else "h:mm a", Locale.getDefault())
			fmt.format(Date(trigger))
		} catch (e: Exception) {
			warn("queryAlarm", e)
			null
		}
	}

	fun isAirplaneOn(): Boolean =
		try {
			Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
		} catch (e: Exception) {
			warn("isAirplaneOn", e)
			false
		}

	private fun activeCaps(): NetworkCapabilities? =
		try {
			val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
			cm.getNetworkCapabilities(cm.activeNetwork)
		} catch (e: Exception) {
			warn("activeCaps", e)
			null
		}

	/** True when the active network offers [transport]; one caps query answers every caller. */
	fun hasTransport(transport: Int): Boolean =
		activeCaps()?.hasTransport(transport) == true

	fun isVpn(): Boolean =
		try {
			val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
			cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
		} catch (e: Exception) {
			warn("isVpn", e)
			activeCaps()?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
		}

	/** Network generation label like Dragon's getMobileDataStatus (LTE/5G/3G/2G). */
	fun mobileTypeLabel(): String {
		// READ_BASIC_PHONE_STATE is install-time (API 29+); older devices keep
		// the dangerous READ_PHONE_STATE unrequested and fall back to "4G".
		val allowed =
			context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ||
				context.checkSelfPermission(Manifest.permission.READ_BASIC_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
		if (!allowed) return "4G"
		return try {
			val tm = context.getSystemService(TelephonyManager::class.java) ?: return "4G"
			when (tm.dataNetworkType) {
				TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
				TelephonyManager.NETWORK_TYPE_NR -> "5G"
				TelephonyManager.NETWORK_TYPE_HSPAP,
				TelephonyManager.NETWORK_TYPE_HSDPA,
				TelephonyManager.NETWORK_TYPE_HSUPA,
				TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
				TelephonyManager.NETWORK_TYPE_UNKNOWN -> "4G"
				else -> "4G"
			}
		} catch (e: Exception) {
			warn("mobileTypeLabel", e)
			"4G"
		}
	}

	fun isBluetoothOn(): Boolean =
		try {
			val bm = context.getSystemService(BluetoothManager::class.java)
			bm?.adapter?.isEnabled == true
		} catch (e: Exception) {
			warn("isBluetoothOn", e)
			false
		}

	fun isNfcOn(): Boolean =
		try {
			NfcAdapter
				.getDefaultAdapter(context)
				?.isEnabled == true
		} catch (e: Exception) {
			warn("isNfcOn", e)
			false
		}

	fun isGpsOn(): Boolean =
		try {
			val lm = context.getSystemService(LocationManager::class.java)
			lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
		} catch (e: Exception) {
			warn("isGpsOn", e)
			false
		}

	/** Do Not Disturb: zen_mode is a plain global setting, readable without policy access. */
	fun isDndOn(): Boolean =
		try {
			Settings.Global.getInt(context.contentResolver, "zen_mode", 0) != 0
		} catch (e: Exception) {
			warn("isDndOn", e)
			false
		}

	/** Data saver is on when this app is restricted (or exempted) by it. */
	fun isDataSaverOn(): Boolean =
		try {
			val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
			val status = cm.restrictBackgroundStatus
			status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED ||
				status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED
		} catch (e: Exception) {
			warn("isDataSaverOn", e)
			false
		}

	fun isAutoRotateOn(): Boolean =
		try {
			Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) != 0
		} catch (e: Exception) {
			warn("isAutoRotateOn", e)
			false
		}

	fun isHotspotOn(): Boolean {
		val wm = context.getSystemService(WifiManager::class.java)
		if (wm != null) {
			try {
				val m = wm.javaClass.getDeclaredMethod("isWifiApEnabled")
				m.isAccessible = true
				if (m.invoke(wm) == true) return true
			} catch (e: Exception) {
				// Hidden API: expected to fail on some OEMs, the global fallback below covers it.
				warn("isHotspotOn.hiddenApi", e)
			}
		}
		return try {
			Settings.Global.getInt(context.contentResolver, "wifi_ap_state", 0) == 13
		} catch (e: Exception) {
			warn("isHotspotOn", e)
			false
		}
	}

	/**
	 * Reports [e] from query [where]: logcat gets every occurrence, the
	 * in-app log only the first (the poll would otherwise rewrite its whole
	 * file once per refresh cycle).
	 */
	private fun warn(where: String, e: Exception) {
		Log.w(TAG, where, e)
		LogStore.appendOnce(
			context,
			"$TAG#$where",
			context.getString(R.string.log_error, "$where: ${e.message}")
		)
	}

	companion object {
		private const val TAG = "SystemIndicators"
	}
}

/** One shot of every connectivity indicator, already filtered by the prefs. */
internal data class Connectivity(
	val airplane: Boolean,
	val wifi: Boolean,
	val mobile: Boolean,
	val mobileType: String,
	val bluetooth: Boolean,
	val vpn: Boolean,
	val hotspot: Boolean,
	val nfc: Boolean,
	val gps: Boolean,
	val dnd: Boolean,
	val dataSaver: Boolean,
	val autoRotate: Boolean
)

/**
 * Reads every connectivity indicator in one shot. The whole block runs off
 * the main thread; the caller applies the result on its own context.
 *
 * [radio] skips the read when airplane mode has already turned the radio
 * off for real, [plain] covers the flags that survive it.
 */
internal suspend fun SystemIndicators.readConnectivity(p: OverlayPrefs): Connectivity =
	withContext(Dispatchers.IO) {
		val airplane = isAirplaneOn()

		fun radio(show: Boolean, read: () -> Boolean) = !airplane && show && read()

		fun plain(show: Boolean, read: () -> Boolean) = show && read()

		val mobile = radio(p.showMobileData) { hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) }
		Connectivity(
			airplane = airplane,
			wifi = radio(p.showWifi) { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) },
			mobile = mobile,
			mobileType = if (mobile) mobileTypeLabel().ifEmpty { "4G" } else "",
			bluetooth = radio(p.showBluetooth) { isBluetoothOn() },
			vpn = radio(p.showVpn) { isVpn() },
			hotspot = radio(p.showHotspot) { isHotspotOn() },
			nfc = radio(p.showNfc) { isNfcOn() },
			gps = plain(p.showGps) { isGpsOn() },
			dnd = plain(p.showDnd) { isDndOn() },
			dataSaver = plain(p.showDataSaver) { isDataSaverOn() },
			autoRotate = plain(p.showRotate) { isAutoRotateOn() }
		)
	}
