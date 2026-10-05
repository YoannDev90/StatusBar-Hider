package dev.yoanndev90.statusbarhider.overlay

import android.Manifest
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.provider.CalendarContract
import android.provider.Settings
import android.telephony.TelephonyManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only queries against the platform for the overlay indicators.
 *
 * Every call hits the system service fresh (no caching): the service re-runs
 * them on each refresh cycle, and a stale cache would show a state the bar
 * never sees. Failures fall back to "off" / null — a missing permission or a
 * hidden API only costs one icon.
 */
internal class SystemIndicators(
	private val context: Context
) {
	/** True while the keyguard is up (no screen lock at all = never locked). */
	fun isDeviceLocked(): Boolean =
		try {
			context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true
		} catch (_: Exception) {
			false
		}

	/** Next calendar event in the next 24h as "HH:mm Title". */
	fun queryCalendar(enabled: Boolean): String? {
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
			var text: String? = null
			context.contentResolver
				.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
				?.use { c ->
					while (c.moveToNext()) {
						val begin = c.getLong(0)
						val end = c.getLong(1)
						val title = c.getString(2)?.trim().orEmpty()
						val allDay = c.getInt(3) == 1
						if (title.isEmpty()) continue
						// Skip events already finished (Instances range includes ongoing ones).
						if (!allDay && end <= now) continue
						text =
							if (allDay) {
								title
							} else {
								val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
								"${fmt.format(Date(begin))} $title"
							}
						break
					}
				}
			text
		} catch (_: Exception) {
			null
		}
	}

	/** Next alarm clock as "HH:mm", or null when none / feature off. */
	fun queryAlarm(enabled: Boolean): String? {
		if (!enabled) return null
		return try {
			val am = context.getSystemService(AlarmManager::class.java)
			val trigger = am?.nextAlarmClock?.triggerTime ?: return null
			val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
			fmt.format(Date(trigger))
		} catch (_: Exception) {
			null
		}
	}

	/** Package of the last resumed activity in the trailing window, or null when unknown. */
	fun foregroundPackage(): String? =
		try {
			val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
			val now = System.currentTimeMillis()
			val events = usm.queryEvents(now - 10_000L, now)
			val event = UsageEvents.Event()
			var pkg: String? = null
			while (events.hasNextEvent()) {
				events.getNextEvent(event)
				if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
					pkg = event.packageName
				}
			}
			pkg
		} catch (_: Exception) {
			null
		}

	fun isAirplaneOn(): Boolean =
		try {
			Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
		} catch (_: Exception) {
			false
		}

	private fun activeCaps(): NetworkCapabilities? =
		try {
			val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
			cm.getNetworkCapabilities(cm.activeNetwork)
		} catch (_: Exception) {
			null
		}

	fun isWifi(): Boolean = activeCaps()?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

	fun isMobile(): Boolean = activeCaps()?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

	fun isVpn(): Boolean =
		try {
			val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
			cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
		} catch (_: Exception) {
			activeCaps()?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
		}

	/** Network generation label like Dragon's getMobileDataStatus (LTE/5G/3G/2G). */
	fun mobileTypeLabel(): String {
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
		} catch (_: Exception) {
			"4G"
		}
	}

	fun isBluetoothOn(): Boolean =
		try {
			val bm = context.getSystemService(BluetoothManager::class.java)
			bm?.adapter?.isEnabled == true
		} catch (_: Exception) {
			false
		}

	fun isNfcOn(): Boolean =
		try {
			NfcAdapter
				.getDefaultAdapter(context)
				?.isEnabled == true
		} catch (_: Exception) {
			false
		}

	fun isGpsOn(): Boolean =
		try {
			val lm = context.getSystemService(LocationManager::class.java)
			lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
		} catch (_: Exception) {
			false
		}

	/** Do Not Disturb: zen_mode is a plain global setting, readable without policy access. */
	fun isDndOn(): Boolean =
		try {
			Settings.Global.getInt(context.contentResolver, "zen_mode", 0) != 0
		} catch (_: Exception) {
			false
		}

	/** Data saver is on when this app is restricted (or exempted) by it. */
	fun isDataSaverOn(): Boolean =
		try {
			val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
			val status = cm.restrictBackgroundStatus
			status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED ||
				status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED
		} catch (_: Exception) {
			false
		}

	fun isAutoRotateOn(): Boolean =
		try {
			Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) != 0
		} catch (_: Exception) {
			false
		}

	fun isHotspotOn(): Boolean {
		val wm = context.getSystemService(WifiManager::class.java)
		if (wm != null) {
			try {
				val m = wm.javaClass.getDeclaredMethod("isWifiApEnabled")
				m.isAccessible = true
				if (m.invoke(wm) == true) return true
			} catch (_: Exception) {
			}
		}
		return try {
			Settings.Global.getInt(context.contentResolver, "wifi_ap_state", 0) == 13
		} catch (_: Exception) {
			false
		}
	}
}
