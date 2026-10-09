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
import android.os.Build
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
			// getAllNetworks() is deprecated because polling races and never
			// notifies, but a callback is asynchronous while this poll must
			// answer synchronously (and may cover a non-default VPN network,
			// which getActiveNetwork() would miss).
			@Suppress("DEPRECATION")
			cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
		} catch (e: Exception) {
			warn("isVpn", e)
			activeCaps()?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
		}

	/**
	 * Label plus signal level for the cellular indicator, both neutral while it
	 * is not the transport in use. One entry point for the full connectivity
	 * read and for the service's faster mobile loop, so the two can never show
	 * different values.
	 */
	fun mobileStatus(active: Boolean): MobileStatus =
		if (active) {
			MobileStatus(active = true, type = mobileTypeLabel().ifEmpty { "4G" }, level = signalLevel())
		} else {
			MobileStatus(active = false, type = "", level = -1)
		}

	/** Network generation label like Dragon's getMobileDataStatus (LTE/5G/3G/2G). */
	private fun mobileTypeLabel(): String {
		// READ_BASIC_PHONE_STATE is install-time (API 29+); older devices keep
		// the dangerous READ_PHONE_STATE unrequested and fall back to "4G".
		val allowed =
			context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ||
				context.checkSelfPermission(Manifest.permission.READ_BASIC_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
		if (!allowed) return "4G"
		return try {
			val tm = context.getSystemService(TelephonyManager::class.java) ?: return "4G"
			// The data radio is the one the label is about; the voice radio only
			// fills in when the modem reports no usable data type (IWLAN, lag).
			networkTypeLabel(tm.dataNetworkType)
				.ifEmpty { networkTypeLabel(tm.voiceNetworkType) }
				.ifEmpty { "4G" }
		} catch (e: Exception) {
			warn("mobileTypeLabel", e)
			"4G"
		}
	}

	/**
	 * Signal level 0..4 on the same 5-step scale the system bars use, or -1 when
	 * it cannot be read (no telephony radio, API < 28, modem not reporting).
	 */
	private fun signalLevel(): Int {
		// getSignalStrength() arrived in API 28: below that the call is a
		// NoSuchMethodError, which catch(Exception) below would never see.
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return -1
		return try {
			val tm = context.getSystemService(TelephonyManager::class.java) ?: return -1
			tm.signalStrength?.level?.coerceIn(0, 4) ?: -1
		} catch (e: Exception) {
			warn("signalLevel", e)
			-1
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

/**
 * Generation label for one [TelephonyManager] NETWORK_TYPE_* value, or "" when
 * the platform reports no usable type so the caller can fall back (voice radio
 * first, "4G" last).
 *
 * The CDMA / iDEN constants are deprecated as the networks retire, but a modem
 * still reports them and the bar must not mislabel a 2G cell as "4G".
 */
@Suppress("DEPRECATION")
internal fun networkTypeLabel(type: Int): String =
	when (type) {
		TelephonyManager.NETWORK_TYPE_NR -> "5G"
		TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
		TelephonyManager.NETWORK_TYPE_HSPAP,
		TelephonyManager.NETWORK_TYPE_HSDPA,
		TelephonyManager.NETWORK_TYPE_HSUPA,
		TelephonyManager.NETWORK_TYPE_HSPA,
		TelephonyManager.NETWORK_TYPE_UMTS,
		TelephonyManager.NETWORK_TYPE_TD_SCDMA,
		TelephonyManager.NETWORK_TYPE_EHRPD,
		TelephonyManager.NETWORK_TYPE_EVDO_0,
		TelephonyManager.NETWORK_TYPE_EVDO_A,
		TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G"
		TelephonyManager.NETWORK_TYPE_GSM,
		TelephonyManager.NETWORK_TYPE_GPRS,
		TelephonyManager.NETWORK_TYPE_EDGE,
		TelephonyManager.NETWORK_TYPE_CDMA,
		TelephonyManager.NETWORK_TYPE_1xRTT,
		TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
		else -> ""
	}

/** Cellular indicator snapshot: transport in use, generation label, signal level. */
internal data class MobileStatus(
	val active: Boolean,
	val type: String,
	/** Signal level 0..4, or -1 when it could not be read. */
	val level: Int
)

/**
 * Cellular indicator alone, without the rest of the connectivity block: the
 * overlay service refreshes the label and the bars on their own faster loop,
 * through the same [SystemIndicators.mobileStatus] [readConnectivity] uses so
 * both paths always agree on what the bar shows.
 */
internal suspend fun SystemIndicators.readMobile(p: OverlayPrefs): MobileStatus =
	withContext(Dispatchers.IO) {
		val active = !isAirplaneOn() && p.showMobileData && hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
		mobileStatus(active)
	}

/** One shot of every connectivity indicator, already filtered by the prefs. */
internal data class Connectivity(
	val airplane: Boolean,
	val wifi: Boolean,
	val mobile: Boolean,
	val mobileType: String,
	val signalLevel: Int,
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
		val status = mobileStatus(mobile)
		Connectivity(
			airplane = airplane,
			wifi = radio(p.showWifi) { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) },
			mobile = status.active,
			mobileType = status.type,
			signalLevel = status.level,
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
