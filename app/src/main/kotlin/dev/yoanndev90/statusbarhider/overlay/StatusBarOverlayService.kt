package dev.yoanndev90.statusbarhider.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import dev.yoanndev90.statusbarhider.R

/**
 * System-wide custom status bar drawn over all apps.
 *
 * Battery strategy: TextClock ticks in the system (no manual 1s loop).
 * Polling (bandwidth / alarm / connectivity fallback) runs only when the
 * screen is ON and stops on SCREEN_OFF to preserve deep sleep.
 */
class StatusBarOverlayService : Service() {
	companion object {
		private const val TAG = "CustomBar"
		private const val NOTIF_ID = 1001
		private const val CHANNEL_ID = "overlay"
		const val ACTION_STOP = "dev.yoanndev90.statusbarhider.overlay.STOP"

		fun start(context: Context) {
			val i = Intent(context, StatusBarOverlayService::class.java)
			try {
				if (Build.VERSION.SDK_INT >= 26) {
					context.startForegroundService(i)
				} else {
					context.startService(i)
				}
			} catch (e: Exception) {
				Log.w(TAG, "start failed", e)
			}
		}

		fun stop(context: Context) {
			try {
				context.stopService(Intent(context, StatusBarOverlayService::class.java))
			} catch (_: Exception) {
			}
		}
	}

	private var overlayView: View? = null
	private var overlayParams: WindowManager.LayoutParams? = null
	private var clockView: TextClock? = null
	private var dateView: TextView? = null
	private var notifRow: LinearLayout? = null
	private var batteryView: TextView? = null
	private var batteryIcon: BatteryView? = null
	private var batteryRow: LinearLayout? = null
	private var bandwidthRow: LinearLayout? = null
	private var bandwidthIconDown: ImageView? = null
	private var bandwidthIconUp: ImageView? = null
	private var bandwidthIconMerged: ImageView? = null
	private var bandwidthView: TextView? = null
	private var alarmRow: LinearLayout? = null
	private var alarmIcon: ImageView? = null
	private var alarmView: TextView? = null
	private var connectivityRow: LinearLayout? = null
	private var iconAirplane: ImageView? = null
	private var iconWifi: ImageView? = null
	private var iconMobile: ImageView? = null
	private var mobileTypeView: TextView? = null
	private var iconBluetooth: ImageView? = null
	private var iconVpn: ImageView? = null
	private var iconHotspot: ImageView? = null
	private var iconUsb: ImageView? = null

	private val handler = Handler(Looper.getMainLooper())
	private var screenOn = true
	private var prefs = OverlayPrefs()
	private var batteryPct = -1
	private var batteryCharging = false
	private var usbConnected = false
	private var mobileType = ""
	private var lastRx = -1L
	private var lastTx = -1L
	private var burnInStep = false

	private val notifListener: () -> Unit = {
		handler.post { if (screenOn) renderNotifs() }
	}

	private val batteryReceiver =
		object : BroadcastReceiver() {
			override fun onReceive(
				context: Context,
				intent: Intent
			) {
				val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
				val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
				val pct = OverlayPrefs.batteryPct(level, scale)
				val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
				batteryCharging =
					status == BatteryManager.BATTERY_STATUS_CHARGING ||
					status == BatteryManager.BATTERY_STATUS_FULL
				if (pct >= 0) {
					batteryPct = pct
					renderBattery()
				}
			}
		}

	private val screenReceiver =
		object : BroadcastReceiver() {
			override fun onReceive(
				context: Context,
				intent: Intent
			) {
				when (intent.action) {
					Intent.ACTION_SCREEN_OFF -> {
						screenOn = false
						handler.removeCallbacks(pollRunnable)
						handler.removeCallbacks(burnInRunnable)
					}
					Intent.ACTION_SCREEN_ON -> {
						screenOn = true
						refreshAll()
						schedulePoll()
						scheduleBurnIn()
					}
				}
			}
		}

	private val usbReceiver =
		object : BroadcastReceiver() {
			override fun onReceive(
				context: Context,
				intent: Intent
			) {
				if (intent.action == "android.hardware.usb.action.USB_STATE") {
					usbConnected = intent.extras?.getBoolean("connected") == true
					runOnOverlay { renderConnectivity() }
				}
			}
		}

	private val networkCallback =
		object : ConnectivityManager.NetworkCallback() {
			override fun onAvailable(network: Network) {
				runOnOverlay { renderConnectivity() }
			}

			override fun onLost(network: Network) {
				runOnOverlay { renderConnectivity() }
			}

			override fun onCapabilitiesChanged(
				network: Network,
				caps: NetworkCapabilities
			) {
				runOnOverlay { renderConnectivity() }
			}
		}

	private val pollRunnable =
		object : Runnable {
			override fun run() {
				if (!screenOn) return
				refreshPolled()
				handler.postDelayed(this, (prefs.updateIntervalSec.coerceIn(5, 60) * 1000).toLong())
			}
		}

	private val burnInRunnable =
		object : Runnable {
			override fun run() {
				if (screenOn && prefs.burnInMin > 0) {
					shiftForBurnIn()
					handler.postDelayed(this, (prefs.burnInMin.coerceIn(1, 30) * 60 * 1000).toLong())
				}
			}
		}

	private val bandwidthRunnable =
		object : Runnable {
			override fun run() {
				if (!screenOn || !prefs.showBandwidth) return
				updateBandwidth()
				handler.postDelayed(this, 1000L)
			}
		}

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		prefs = OverlayPrefs.load(this)
		startFg()
		attachOverlay()
		registerReceivers()
		NotifIcons.addListener(notifListener)
		refreshAll()
		schedulePoll()
		scheduleBurnIn()
		if (prefs.showBandwidth) handler.post(bandwidthRunnable)
	}

	override fun onStartCommand(
		intent: Intent?,
		flags: Int,
		startId: Int
	): Int {
		if (intent?.action == ACTION_STOP) {
			stopSelf()
			return START_NOT_STICKY
		}
		prefs = OverlayPrefs.load(this)
		ensureTouchableFlags()
		applyPrefsToViews()
		refreshAll()
		return START_STICKY
	}

	/** Re-attaches the overlay when the interactive toggle changed window flags. */
	private fun ensureTouchableFlags() {
		val p = overlayParams ?: return
		val wantTouchable = prefs.interactive
		val isTouchable = (p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0
		if (wantTouchable != isTouchable) {
			detachOverlay()
			attachOverlay()
		}
	}

	override fun onDestroy() {
		handler.removeCallbacksAndMessages(null)
		NotifIcons.removeListener(notifListener)
		try {
			unregisterReceiver(batteryReceiver)
		} catch (_: Exception) {
		}
		try {
			unregisterReceiver(screenReceiver)
		} catch (_: Exception) {
		}
		try {
			unregisterReceiver(usbReceiver)
		} catch (_: Exception) {
		}
		try {
			val cm = getSystemService(ConnectivityManager::class.java)
			cm?.unregisterNetworkCallback(networkCallback)
		} catch (_: Exception) {
		}
		detachOverlay()
		super.onDestroy()
	}

	private fun startFg() {
		val nm = getSystemService(NotificationManager::class.java) ?: return
		if (Build.VERSION.SDK_INT >= 26) {
			if (nm.getNotificationChannel(CHANNEL_ID) == null) {
				nm.createNotificationChannel(
					NotificationChannel(CHANNEL_ID, "Custom status bar", NotificationManager.IMPORTANCE_MIN)
				)
			}
		}
		val notif =
			if (Build.VERSION.SDK_INT >= 26) {
				Notification
					.Builder(this, CHANNEL_ID)
					.setContentTitle("Custom status bar active")
					.setContentText("Tap Restore in the app to remove it")
					.setSmallIcon(android.R.drawable.stat_notify_more)
					.build()
			} else {
				@Suppress("DEPRECATION")
				Notification
					.Builder(this)
					.setContentTitle("Custom status bar active")
					.setSmallIcon(android.R.drawable.stat_notify_more)
					.build()
			}
		try {
			if (Build.VERSION.SDK_INT >= 34) {
				startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
			} else if (Build.VERSION.SDK_INT >= 29) {
				startForeground(NOTIF_ID, notif)
			} else {
				startForeground(NOTIF_ID, notif)
			}
		} catch (e: Exception) {
			Log.w(TAG, "startForeground failed", e)
		}
	}

	private fun attachOverlay() {
		if (overlayView != null) return
		val wm = getSystemService(WindowManager::class.java) ?: return
		val view = LayoutInflater.from(this).inflate(R.layout.view_custom_status_bar, null)
		clockView = view.findViewById(R.id.clockView)
		dateView = view.findViewById(R.id.dateView)
		notifRow = view.findViewById(R.id.notifRow)
		batteryRow = view.findViewById(R.id.batteryRow)
		batteryIcon = view.findViewById(R.id.batteryIcon)
		batteryView = view.findViewById(R.id.batteryView)
		bandwidthRow = view.findViewById(R.id.bandwidthRow)
		bandwidthIconDown = view.findViewById(R.id.bandwidthIconDown)
		bandwidthIconUp = view.findViewById(R.id.bandwidthIconUp)
		bandwidthIconMerged = view.findViewById(R.id.bandwidthIconMerged)
		bandwidthView = view.findViewById(R.id.bandwidthView)
		alarmRow = view.findViewById(R.id.alarmRow)
		alarmIcon = view.findViewById(R.id.alarmIcon)
		alarmView = view.findViewById(R.id.alarmView)
		connectivityRow = view.findViewById(R.id.connectivityRow)
		iconAirplane = view.findViewById(R.id.iconAirplane)
		iconWifi = view.findViewById(R.id.iconWifi)
		iconMobile = view.findViewById(R.id.iconMobile)
		mobileTypeView = view.findViewById(R.id.mobileTypeView)
		iconBluetooth = view.findViewById(R.id.iconBluetooth)
		iconVpn = view.findViewById(R.id.iconVpn)
		iconHotspot = view.findViewById(R.id.iconHotspot)
		iconUsb = view.findViewById(R.id.iconUsb)
		val touchable = prefs.interactive
		val flags =
			WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
				if (touchable) {
					WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
						WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
						WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
				} else {
					WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
						WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
				}
		val params =
			WindowManager.LayoutParams(
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.WRAP_CONTENT,
				if (Build.VERSION.SDK_INT >= 26) {
					WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
				} else {
					@Suppress("DEPRECATION")
					WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
				},
				flags,
				PixelFormat.TRANSLUCENT
			)
		params.gravity = Gravity.TOP
		if (Build.VERSION.SDK_INT >= 28) {
			params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
		}
		try {
			wm.addView(view, params)
			overlayView = view
			overlayParams = params
		} catch (e: Exception) {
			Log.w(TAG, "addView failed (overlay permission?)", e)
		}
		view.setOnApplyWindowInsetsListener { v, insets ->
			applyEdgePadding(v)
			insets
		}
		view.post { applyEdgePadding(view) }
		applyWidgetOrder()
		applyClicks()
		applyPrefsToViews()
	}

	/** Reorders bar children from prefs, keeping the spacer flexible. */
	private fun applyWidgetOrder() {
		val root = overlayView?.findViewById<android.widget.LinearLayout>(R.id.overlayRoot) ?: return
		val byId: Map<String, View?> =
			mapOf(
				WidgetId.CLOCK to clockView,
				WidgetId.DATE to dateView,
				WidgetId.NOTIFS to notifRow,
				WidgetId.SPACER to root.findViewById(R.id.spacerView),
				WidgetId.CONNECTIVITY to connectivityRow,
				WidgetId.BATTERY to batteryRow,
				WidgetId.ALARM to alarmRow,
				WidgetId.BANDWIDTH to bandwidthRow
			)
		root.removeAllViews()
		for (id in prefs.widgetOrder) {
			val child = byId[id] ?: continue
			(child.parent as? android.view.ViewGroup)?.removeView(child)
			val lp =
				if (id == WidgetId.SPACER) {
					android.widget.LinearLayout.LayoutParams(0, 1, 1f)
				} else {
					android.widget.LinearLayout.LayoutParams(
						android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
						android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
					)
				}
			root.addView(child, lp)
		}
	}

	/** Tap actions, only wired in interactive mode (overlay is untouchable otherwise). */
	private fun applyClicks() {
		if (!prefs.interactive) {
			clockView?.setOnClickListener(null)
			clockView?.isClickable = false
			dateView?.setOnClickListener(null)
			dateView?.isClickable = false
			return
		}
		clockView?.apply {
			isClickable = true
			setOnClickListener {
				try {
					startActivity(
						android.content
							.Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
							.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					)
				} catch (_: Exception) {
				}
			}
		}
		dateView?.apply {
			isClickable = true
			setOnClickListener {
				try {
					val uri = android.net.Uri.parse("content://com.android.calendar/time")
					startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
				} catch (_: Exception) {
					try {
						startActivity(
							packageManager
								.getLaunchIntentForPackage("com.google.android.calendar")
								?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
								?: return@setOnClickListener
						)
					} catch (_: Exception) {
					}
				}
			}
		}
	}

	/** OLED burn-in protection: nudges the whole bar by 1px on a timer. */
	private fun shiftForBurnIn() {
		val wm = getSystemService(WindowManager::class.java) ?: return
		val v = overlayView ?: return
		val p = overlayParams ?: return
		try {
			burnInStep = !burnInStep
			p.y = if (burnInStep) 1 else 0
			wm.updateViewLayout(v, p)
		} catch (_: Exception) {
		}
	}

	private fun scheduleBurnIn() {
		handler.removeCallbacks(burnInRunnable)
		if (prefs.burnInMin > 0 && screenOn) {
			handler.postDelayed(burnInRunnable, (prefs.burnInMin.coerceIn(1, 30) * 60 * 1000).toLong())
		}
	}

	/**
	 * Side padding comes from user prefs; a side-hugging cutout only ever
	 * adds to it. Top/bottom are fully manual (no vertical shift).
	 */
	private fun applyEdgePadding(view: View) {
		val root = view.findViewById<View>(R.id.overlayRoot) ?: view
		val density = resources.displayMetrics.density
		val screenWidth = resources.displayMetrics.widthPixels
		val sideCutout = InsetsUtils.sideCutoutWidthPx(view, screenWidth)
		val start = maxOf((prefs.padStartDp * density).toInt(), sideCutout).coerceAtLeast(0)
		val end = maxOf((prefs.padEndDp * density).toInt(), sideCutout).coerceAtLeast(0)
		val top = (prefs.padTopDp * density).toInt().coerceAtLeast(0)
		val bottom = (prefs.padBottomDp * density).toInt().coerceAtLeast(0)
		root.setPadding(start, top, end, bottom)
	}

	private fun detachOverlay() {
		val wm = getSystemService(WindowManager::class.java)
		val v = overlayView
		if (wm != null && v != null) {
			try {
				wm.removeView(v)
			} catch (_: Exception) {
			}
		}
		overlayView = null
		overlayParams = null
	}

	private fun registerReceivers() {
		try {
			registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
			val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
			if (sticky != null) {
				batteryPct =
					OverlayPrefs.batteryPct(
						sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
						sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
					)
			}
		} catch (_: Exception) {
		}
		try {
			val f = IntentFilter()
			f.addAction(Intent.ACTION_SCREEN_ON)
			f.addAction(Intent.ACTION_SCREEN_OFF)
			registerReceiver(screenReceiver, f)
		} catch (_: Exception) {
		}
		try {
			registerReceiver(usbReceiver, IntentFilter("android.hardware.usb.action.USB_STATE"))
		} catch (_: Exception) {
		}
		try {
			val cm = getSystemService(ConnectivityManager::class.java)
			cm?.registerDefaultNetworkCallback(networkCallback)
		} catch (_: Exception) {
		}
	}

	private fun applyPrefsToViews() {
		val fmt = prefs.effectiveTimeFormat()
		clockView?.format24Hour = fmt
		clockView?.format12Hour = fmt
		bandwidthRow?.visibility = if (prefs.showBandwidth) View.VISIBLE else View.GONE
		alarmRow?.visibility = if (prefs.showAlarm) View.VISIBLE else View.GONE
		overlayView?.setBackgroundColor(prefs.backgroundColor())
		val fg = prefs.textColor()
		clockView?.setTextColor(fg)
		dateView?.setTextColor(fg)
		batteryView?.setTextColor(fg)
		batteryIcon?.fgColor = fg
		mobileTypeView?.setTextColor(fg)
		alarmView?.setTextColor(fg)
		bandwidthView?.setTextColor(fg)
		tintIcons(fg)
		applyWidgetOrder()
		applyClicks()
		overlayView?.let { applyEdgePadding(it) }
		handler.removeCallbacks(bandwidthRunnable)
		if (prefs.showBandwidth && screenOn) handler.post(bandwidthRunnable)
		scheduleBurnIn()
		refreshAll()
	}

	private fun tintIcons(color: Int) {
		listOf(
			bandwidthIconDown,
			bandwidthIconUp,
			bandwidthIconMerged,
			alarmIcon,
			iconAirplane,
			iconWifi,
			iconMobile,
			iconBluetooth,
			iconVpn,
			iconHotspot,
			iconUsb
		).forEach { it?.setColorFilter(color) }
	}

	private fun runOnOverlay(block: () -> Unit) {
		handler.post { if (screenOn) block() }
	}

	private fun refreshAll() {
		renderBattery()
		renderDate()
		renderNotifs()
		renderConnectivity()
		refreshPolled()
	}

	private fun refreshPolled() {
		renderAlarm()
		renderDate()
		renderConnectivity()
	}

	private fun schedulePoll() {
		handler.removeCallbacks(pollRunnable)
		handler.post(pollRunnable)
	}

	private fun renderBattery() {
		val row = batteryRow ?: return
		if (!prefs.showBattery && !prefs.showBatteryIcon) {
			row.visibility = View.GONE
			return
		}
		row.visibility = View.VISIBLE
		batteryIcon?.apply {
			visibility = if (prefs.showBatteryIcon) View.VISIBLE else View.GONE
			if (batteryPct >= 0) level = batteryPct
			charging = batteryCharging
		}
		batteryView?.apply {
			visibility = if (prefs.showBattery && prefs.showBatteryPct && batteryPct >= 0) View.VISIBLE else View.GONE
			text = "$batteryPct%"
		}
	}

	private fun renderDate() {
		val v = dateView ?: return
		if (!prefs.showDate) {
			v.visibility = View.GONE
			return
		}
		v.visibility = View.VISIBLE
		v.text =
			try {
				val fmt = java.text.SimpleDateFormat(prefs.dateFormat, java.util.Locale.getDefault())
				fmt.format(java.util.Date())
			} catch (_: Exception) {
				""
			}
	}

	private fun renderNotifs() {
		val row = notifRow ?: return
		if (!prefs.showNotifs || !NotifListenerService.isEnabled(this)) {
			row.visibility = View.GONE
			return
		}
		val entries = NotifIcons.snapshot().take(prefs.maxNotifs.coerceIn(1, 8))
		row.removeAllViews()
		if (entries.isEmpty()) {
			row.visibility = View.GONE
			return
		}
		row.visibility = View.VISIBLE
		val d = resources.displayMetrics.density
		val size = (16 * d).toInt()
		val fg = prefs.textColor()
		for (e in entries) {
			val iv = ImageView(this)
			try {
				val icon = e.icon
				if (icon != null) {
					iv.setImageDrawable(icon)
				} else {
					iv.setImageResource(R.drawable.ic_signal)
					iv.setColorFilter(fg)
				}
			} catch (_: Exception) {
				continue
			}
			iv.contentDescription = e.pkg
			row.addView(
				iv,
				android.widget.LinearLayout
					.LayoutParams(size, size)
					.apply { marginEnd = (3 * d).toInt() }
			)
		}
	}

	private fun renderConnectivity() {
		val airplane = isAirplaneOn()
		iconAirplane?.visibility = if (airplane && prefs.showAirplane) View.VISIBLE else View.GONE
		val showRest = !airplane
		val wifi = showRest && prefs.showWifi && isWifi()
		iconWifi?.visibility = if (wifi) View.VISIBLE else View.GONE
		val mobile = showRest && prefs.showMobileData && isMobile()
		iconMobile?.visibility = if (mobile) View.VISIBLE else View.GONE
		mobileTypeView?.apply {
			if (mobile) {
				text = mobileType.ifEmpty { "4G" }
				visibility = View.VISIBLE
			} else {
				visibility = View.GONE
			}
		}
		iconBluetooth?.visibility =
			if (showRest && prefs.showBluetooth && isBluetoothOn()) View.VISIBLE else View.GONE
		iconVpn?.visibility = if (showRest && prefs.showVpn && isVpn()) View.VISIBLE else View.GONE
		iconHotspot?.visibility =
			if (showRest && prefs.showHotspot && isHotspotOn()) View.VISIBLE else View.GONE
		iconUsb?.visibility = if (prefs.showUsb && usbConnected) View.VISIBLE else View.GONE
		val anyVisible =
			listOf(iconAirplane, iconWifi, iconMobile, iconBluetooth, iconVpn, iconHotspot, iconUsb)
				.any { it?.visibility == View.VISIBLE }
		connectivityRow?.visibility = if (anyVisible) View.VISIBLE else View.GONE
	}

	private fun renderAlarm() {
		val row = alarmRow ?: return
		val v = alarmView ?: return
		if (!prefs.showAlarm) {
			row.visibility = View.GONE
			return
		}
		try {
			val am = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
			val trigger = am?.nextAlarmClock?.triggerTime
			if (trigger == null) {
				row.visibility = View.GONE
			} else {
				val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
				v.text = fmt.format(java.util.Date(trigger))
				row.visibility = View.VISIBLE
			}
		} catch (_: Exception) {
			row.visibility = View.GONE
		}
	}

	private fun updateBandwidth() {
		val v = bandwidthView ?: return
		val rx = TrafficStats.getTotalRxBytes()
		val tx = TrafficStats.getTotalTxBytes()
		if (lastRx >= 0 && rx >= 0) {
			val dRx = rx - lastRx
			val dTx = if (lastTx >= 0 && tx >= 0) tx - lastTx else 0L
			if (prefs.bandwidthMerged) {
				bandwidthIconMerged?.visibility = View.VISIBLE
				bandwidthIconDown?.visibility = View.GONE
				bandwidthIconUp?.visibility = View.GONE
				v.text = "${OverlayPrefs.formatSpeed(dRx + dTx)}/s"
			} else {
				bandwidthIconMerged?.visibility = View.GONE
				bandwidthIconDown?.visibility = View.VISIBLE
				bandwidthIconUp?.visibility = View.VISIBLE
				v.text = "${OverlayPrefs.formatSpeed(dRx)}/s ${OverlayPrefs.formatSpeed(dTx)}/s"
			}
		}
		lastRx = rx
		lastTx = tx
	}

	private fun isAirplaneOn(): Boolean =
		try {
			Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
		} catch (_: Exception) {
			false
		}

	private fun activeCaps(): NetworkCapabilities? =
		try {
			val cm = getSystemService(ConnectivityManager::class.java) ?: return null
			cm.getNetworkCapabilities(cm.activeNetwork)
		} catch (_: Exception) {
			null
		}

	private fun isWifi(): Boolean = activeCaps()?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

	private fun isMobile(): Boolean {
		val mobile = activeCaps()?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
		if (mobile) mobileType = mobileTypeLabel()
		return mobile
	}

	private fun isVpn(): Boolean =
		try {
			val cm = getSystemService(ConnectivityManager::class.java) ?: return false
			cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
		} catch (_: Exception) {
			activeCaps()?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
		}

	/** Network generation label like Dragon's getMobileDataStatus (LTE/5G/3G/2G). */
	private fun mobileTypeLabel(): String {
		return try {
			val tm = getSystemService(TelephonyManager::class.java) ?: return "4G"
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
		} catch (_: SecurityException) {
			"4G"
		} catch (_: Exception) {
			"4G"
		}
	}

	private fun isBluetoothOn(): Boolean =
		try {
			val bm = getSystemService(BluetoothManager::class.java)
			bm?.adapter?.isEnabled == true
		} catch (_: Exception) {
			false
		}

	private fun isHotspotOn(): Boolean {
		try {
			val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
			if (wm != null) {
				try {
					val m = wm.javaClass.getDeclaredMethod("isWifiApEnabled")
					m.isAccessible = true
					if (m.invoke(wm) == true) return true
				} catch (_: Exception) {
				}
			}
		} catch (_: Exception) {
		}
		return try {
			Settings.Global.getInt(contentResolver, "wifi_ap_state", 0) == 13
		} catch (_: Exception) {
			false
		}
	}
}
