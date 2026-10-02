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
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
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
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.hide.HideController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * System-wide custom status bar drawn over all apps, rendered with Compose.
 *
 * Battery strategy: the clock and date tick locally inside their widgets
 * (1s only when seconds are shown, 60s otherwise), frozen while the screen
 * is off. Polling (alarm / calendar / connectivity fallback) and bandwidth
 * sampling run only when the screen is ON to preserve deep sleep.
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

	private val prefsRepo = OverlayPrefsRepository.getInstance(this)
	private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	private var overlayView: ComposeView? = null
	private var overlayParams: WindowManager.LayoutParams? = null
	private var overlayLifecycle: ServiceLifecycleOwner? = null
	private var overlayViewModelStore: ViewModelStore? = null

	private var prefs by mutableStateOf(OverlayPrefs())
	private var barState by mutableStateOf(OverlayBarState())

	private val handler = Handler(Looper.getMainLooper())
	private var batteryPct = -1
	private var batteryCharging = false
	private var usbConnected = false
	private var mobileType = ""
	private var lastRx = -1L
	private var lastTx = -1L
	private var burnInStep = false
	private var mediaRegistered = false
	private var mediaSessionManager: android.media.session.MediaSessionManager? = null
	private var mediaControllers: List<android.media.session.MediaController> = emptyList()

	/** Camera ids with the torch currently on (filled by [torchCallback]). */
	private val torchIds = ConcurrentHashMap.newKeySet<String>()
	private var cameraManager: CameraManager? = null
	private var torchRegistered = false

	/** DND / auto-rotate changes land here so the icons react immediately. */
	private val systemObserver =
		object : ContentObserver(handler) {
			override fun onChange(
				selfChange: Boolean
			) {
				runOnOverlay { updateConnectivity() }
			}
		}

	private val torchCallback =
		object : CameraManager.TorchCallback() {
			override fun onTorchModeChanged(
				cameraId: String,
				enabled: Boolean
			) {
				if (enabled) {
					torchIds.add(cameraId)
				} else {
					torchIds.remove(cameraId)
				}
				runOnOverlay { updateConnectivity() }
			}
		}

	private val mediaCallback =
		object : android.media.session.MediaController.Callback() {
			override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = runOnOverlay { updateMedia() }

			override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = runOnOverlay { updateMedia() }

			override fun onSessionDestroyed() = runOnOverlay { updateMedia() }
		}

	private val mediaSessionsListener =
		android.media.session.MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
			runOnOverlay { syncMediaControllers(sessions) }
		}

	private fun syncMediaControllers(sessions: List<android.media.session.MediaController>?) {
		mediaControllers.forEach { c ->
			try {
				c.unregisterCallback(mediaCallback)
			} catch (_: Exception) {
			}
		}
		mediaControllers = sessions.orEmpty()
		mediaControllers.forEach { c ->
			try {
				c.registerCallback(mediaCallback)
			} catch (_: Exception) {
			}
		}
		updateMedia()
	}

	/** Registers the media session listener; retries until notification access is granted. */
	private fun ensureMediaSessions() {
		if (mediaRegistered) return
		try {
			val msm = getSystemService(android.media.session.MediaSessionManager::class.java) ?: return
			val cn = android.content.ComponentName(this, NotifListenerService::class.java)
			msm.addOnActiveSessionsChangedListener(mediaSessionsListener, cn)
			mediaRegistered = true
			mediaSessionManager = msm
			syncMediaControllers(msm.getActiveSessions(cn))
		} catch (_: SecurityException) {
		} catch (_: Exception) {
		}
	}

	private val notifListener: () -> Unit = {
		handler.post { if (barState.screenOn) updateNotifs() }
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
					updateBattery()
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
						barState = barState.copy(screenOn = false)
						handler.removeCallbacks(pollRunnable)
						handler.removeCallbacks(burnInRunnable)
					}
					Intent.ACTION_SCREEN_ON -> {
						barState = barState.copy(screenOn = true)
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
					runOnOverlay { updateConnectivity() }
				}
			}
		}

	/** NFC adapter toggles and location provider changes refresh the indicators. */
	private val radioReceiver =
		object : BroadcastReceiver() {
			override fun onReceive(
				context: Context,
				intent: Intent
			) {
				runOnOverlay { updateConnectivity() }
			}
		}

	private val networkCallback =
		object : ConnectivityManager.NetworkCallback() {
			override fun onAvailable(network: Network) {
				runOnOverlay { updateConnectivity() }
			}

			override fun onLost(network: Network) {
				runOnOverlay { updateConnectivity() }
			}

			override fun onCapabilitiesChanged(
				network: Network,
				caps: NetworkCapabilities
			) {
				runOnOverlay { updateConnectivity() }
			}
		}

	private val pollRunnable =
		object : Runnable {
			override fun run() {
				if (!barState.screenOn) return
				refreshPolled()
				handler.postDelayed(this, (prefs.updateIntervalSec.coerceIn(5, 60) * 1000).toLong())
			}
		}

	private val burnInRunnable =
		object : Runnable {
			override fun run() {
				if (barState.screenOn && prefs.burnInMin > 0) {
					shiftForBurnIn()
					scheduleBurnIn()
				}
			}
		}

	private val bandwidthRunnable =
		object : Runnable {
			override fun run() {
				if (!barState.screenOn || !prefs.showBandwidth) return
				updateBandwidth()
				handler.postDelayed(this, 1000L)
			}
		}

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		startFg()
		attachOverlay()
		registerReceivers()
		NotifIcons.addListener(notifListener)
		serviceScope.launch {
			prefsRepo.state.collect { onPrefsChanged(it) }
		}
		refreshAll()
		schedulePoll()
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
		// Prefs (and their side effects) arrive through the repository flow.
		return START_STICKY
	}

	private fun onPrefsChanged(next: OverlayPrefs) {
		prefs = next
		ensureTouchableFlags()
		if (next.showMedia) ensureMediaSessions()
		handler.removeCallbacks(bandwidthRunnable)
		if (next.showBandwidth && barState.screenOn) handler.post(bandwidthRunnable)
		scheduleBurnIn()
		refreshAll()
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
		serviceScope.cancel()
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
			unregisterReceiver(radioReceiver)
		} catch (_: Exception) {
		}
		try {
			mediaSessionManager?.removeOnActiveSessionsChangedListener(mediaSessionsListener)
		} catch (_: Exception) {
		}
		mediaControllers.forEach { c ->
			try {
				c.unregisterCallback(mediaCallback)
			} catch (_: Exception) {
			}
		}
		mediaControllers = emptyList()
		try {
			val cm = getSystemService(ConnectivityManager::class.java)
			cm?.unregisterNetworkCallback(networkCallback)
		} catch (_: Exception) {
		}
		try {
			contentResolver.unregisterContentObserver(systemObserver)
		} catch (_: Exception) {
		}
		if (torchRegistered) {
			try {
				cameraManager?.unregisterTorchCallback(torchCallback)
			} catch (_: Exception) {
			}
		}
		detachOverlay()
		// The overlay is gone: let SystemUI re-read the "Custom bar" tile state.
		HideController.notifyTiles(this)
		super.onDestroy()
	}

	private fun startFg() {
		val nm = getSystemService(NotificationManager::class.java) ?: return
		if (Build.VERSION.SDK_INT >= 26) {
			if (nm.getNotificationChannel(CHANNEL_ID) == null) {
				nm.createNotificationChannel(
					NotificationChannel(
						CHANNEL_ID,
						getString(R.string.notif_channel_name),
						NotificationManager.IMPORTANCE_MIN
					)
				)
			}
		}
		val notif =
			if (Build.VERSION.SDK_INT >= 26) {
				Notification
					.Builder(this, CHANNEL_ID)
					.setContentTitle(getString(R.string.notif_content_title))
					.setContentText(getString(R.string.notif_content_text))
					.setSmallIcon(android.R.drawable.stat_notify_more)
					.build()
			} else {
				@Suppress("DEPRECATION")
				Notification
					.Builder(this)
					.setContentTitle(getString(R.string.notif_content_title))
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
		val view =
			ComposeView(this).apply {
				setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
				setContent {
					OverlayBar(
						prefs = prefs,
						state = barState,
						onClockClick = ::onClockClick,
						onDateClick = ::onDateClick
					)
				}
			}
		attachComposeOwners(view)
		try {
			wm.addView(view, params)
			overlayView = view
			overlayParams = params
		} catch (e: Exception) {
			Log.w(TAG, "addView failed (overlay permission?)", e)
			destroyComposeOwners()
			return
		}
		view.setOnApplyWindowInsetsListener { v, insets ->
			updateCutout(v)
			insets
		}
		view.post { updateCutout(view) }
	}

	/**
	 * A WindowManager-hosted view has no Activity owners, so Compose is given
	 * synthetic ones. Without them setContent crashes.
	 */
	private fun attachComposeOwners(view: View) {
		val owner = ServiceLifecycleOwner()
		val store = ViewModelStore()
		view.setViewTreeLifecycleOwner(owner)
		view.setViewTreeViewModelStoreOwner(
			object : ViewModelStoreOwner {
				override val viewModelStore: ViewModelStore = store
			}
		)
		view.setViewTreeSavedStateRegistryOwner(owner)
		owner.create()
		owner.start()
		owner.resume()
		overlayLifecycle = owner
		overlayViewModelStore = store
	}

	private fun destroyComposeOwners() {
		overlayLifecycle?.let {
			it.pause()
			it.stop()
			it.destroy()
		}
		overlayViewModelStore?.clear()
		overlayLifecycle = null
		overlayViewModelStore = null
	}

	/** Side padding grows to clear a side-hugging cutout; top/bottom stay manual. */
	private fun updateCutout(view: View) {
		val side = InsetsUtils.sideCutoutWidthPx(view, resources.displayMetrics.widthPixels)
		if (side != barState.sideCutoutPx) {
			barState = barState.copy(sideCutoutPx = side)
		}
	}

	private fun onClockClick() {
		if (!prefs.interactive) return
		try {
			startActivity(
				android.content
					.Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			)
		} catch (_: Exception) {
		}
	}

	private fun onDateClick() {
		if (!prefs.interactive) return
		try {
			val uri = android.net.Uri.parse("content://com.android.calendar/time")
			startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
		} catch (_: Exception) {
			try {
				startActivity(
					packageManager
						.getLaunchIntentForPackage("com.google.android.calendar")
						?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
						?: return
				)
			} catch (_: Exception) {
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
		if (prefs.burnInMin > 0 && barState.screenOn) {
			handler.postDelayed(burnInRunnable, (prefs.burnInMin.coerceIn(1, 30) * 60 * 1000).toLong())
		}
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
		destroyComposeOwners()
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
			val f = IntentFilter()
			f.addAction("android.nfc.action.ADAPTER_STATE_CHANGED")
			f.addAction("android.location.PROVIDERS_CHANGED")
			f.addAction("android.location.GPS_ENABLED_CHANGE")
			f.addAction(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
			registerReceiver(radioReceiver, f)
		} catch (_: Exception) {
		}
		try {
			val cm = getSystemService(ConnectivityManager::class.java)
			cm?.registerDefaultNetworkCallback(networkCallback)
		} catch (_: Exception) {
		}
		try {
			contentResolver.registerContentObserver(
				Settings.Global.getUriFor("zen_mode"),
				false,
				systemObserver
			)
			contentResolver.registerContentObserver(
				Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
				false,
				systemObserver
			)
		} catch (_: Exception) {
		}
		try {
			cameraManager = getSystemService(CameraManager::class.java)
			cameraManager?.registerTorchCallback(torchCallback, handler)
			torchRegistered = true
		} catch (_: Exception) {
			torchRegistered = false
		}
	}

	private fun runOnOverlay(block: () -> Unit) {
		handler.post { if (barState.screenOn) block() }
	}

	private fun refreshAll() {
		updateBattery()
		updateNotifs()
		updateCalendar()
		updateMedia()
		updateConnectivity()
		updateAlarm()
	}

	private fun refreshPolled() {
		updateAlarm()
		updateCalendar()
		updateConnectivity()
	}

	private fun schedulePoll() {
		handler.removeCallbacks(pollRunnable)
		handler.post(pollRunnable)
	}

	private fun updateBattery() {
		barState =
			barState.copy(
				batteryPct = batteryPct,
				batteryCharging = batteryCharging
			)
	}

	private fun updateNotifs() {
		val enabled = prefs.showNotifs && NotifListenerService.isEnabled(this)
		val icons =
			if (!enabled) {
				emptyList()
			} else {
				NotifIcons.snapshot().take(prefs.maxNotifs.coerceIn(1, 8)).mapNotNull { e ->
					try {
						val bitmap = e.icon?.toBitmap()?.asImageBitmap()
						OverlayNotifIcon(bitmap, e.pkg)
					} catch (_: Exception) {
						null
					}
				}
			}
		barState = barState.copy(notifsEnabled = enabled, notifs = icons)
	}

	/** Next calendar event in the next 24h as "HH:mm Title". */
	private fun updateCalendar() {
		barState = barState.copy(calendarText = queryCalendar())
	}

	private fun queryCalendar(): String? {
		if (!prefs.showCalendar || checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
			return null
		}
		return try {
			val now = System.currentTimeMillis()
			val until = now + 24L * 60 * 60 * 1000
			val uri = android.net.Uri.withAppendedPath(android.provider.CalendarContract.Instances.CONTENT_URI, "$now/$until")
			val projection =
				arrayOf(
					android.provider.CalendarContract.Instances.BEGIN,
					android.provider.CalendarContract.Instances.END,
					android.provider.CalendarContract.Instances.TITLE,
					android.provider.CalendarContract.Instances.ALL_DAY
				)
			var text: String? = null
			contentResolver
				.query(uri, projection, null, null, "${android.provider.CalendarContract.Instances.BEGIN} ASC")
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
								val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
								"${fmt.format(java.util.Date(begin))} $title"
							}
						break
					}
				}
			text
		} catch (_: Exception) {
			null
		}
	}

	/** Now playing from media sessions; hidden when notification access is off. */
	private fun updateMedia() {
		barState = barState.copy(mediaText = queryMedia())
	}

	private fun queryMedia(): String? {
		if (!prefs.showMedia || !NotifListenerService.isEnabled(this)) return null
		ensureMediaSessions()
		val ctrl =
			mediaControllers.firstOrNull {
				it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
			} ?: mediaControllers.firstOrNull {
				it.playbackState?.state == android.media.session.PlaybackState.STATE_PAUSED
			}
		val md = ctrl?.metadata
		val title = md
			?.getText(android.media.MediaMetadata.METADATA_KEY_TITLE)
			?.toString()
			?.trim()
			.orEmpty()
		val artist = md
			?.getText(android.media.MediaMetadata.METADATA_KEY_ARTIST)
			?.toString()
			?.trim()
			.orEmpty()
		if (title.isEmpty()) return null
		return if (artist.isEmpty()) title else "$title — $artist"
	}

	private fun updateConnectivity() {
		val airplane = isAirplaneOn()
		val showRest = !airplane
		val mobile = showRest && prefs.showMobileData && isMobile()
		barState =
			barState.copy(
				usbConnected = usbConnected,
				airplane = airplane,
				wifi = showRest && prefs.showWifi && isWifi(),
				mobile = mobile,
				mobileType = if (mobile) mobileType.ifEmpty { "4G" } else "",
				bluetooth = showRest && prefs.showBluetooth && isBluetoothOn(),
				vpn = showRest && prefs.showVpn && isVpn(),
				hotspot = showRest && prefs.showHotspot && isHotspotOn(),
				nfc = showRest && prefs.showNfc && isNfcOn(),
				gps = prefs.showGps && isGpsOn(),
				dnd = prefs.showDnd && isDndOn(),
				dataSaver = prefs.showDataSaver && isDataSaverOn(),
				autoRotate = prefs.showRotate && isAutoRotateOn(),
				torch = prefs.showTorch && torchIds.isNotEmpty()
			)
	}

	private fun updateAlarm() {
		barState = barState.copy(alarmText = queryAlarm())
	}

	private fun queryAlarm(): String? {
		if (!prefs.showAlarm) return null
		return try {
			val am = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
			val trigger = am?.nextAlarmClock?.triggerTime ?: return null
			val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
			fmt.format(java.util.Date(trigger))
		} catch (_: Exception) {
			null
		}
	}

	private fun updateBandwidth() {
		val rx = TrafficStats.getTotalRxBytes()
		val tx = TrafficStats.getTotalTxBytes()
		if (lastRx >= 0 && rx >= 0) {
			val dRx = rx - lastRx
			val dTx = if (lastTx >= 0 && tx >= 0) tx - lastTx else 0L
			val text =
				if (prefs.bandwidthMerged) {
					"${OverlayPrefs.formatSpeed(dRx + dTx)}/s"
				} else {
					"${OverlayPrefs.formatSpeed(dRx)}/s ${OverlayPrefs.formatSpeed(dTx)}/s"
				}
			barState = barState.copy(bandwidthText = text)
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

	private fun isNfcOn(): Boolean =
		try {
			android.nfc.NfcAdapter
				.getDefaultAdapter(this)
				?.isEnabled == true
		} catch (_: Exception) {
			false
		}

	private fun isGpsOn(): Boolean =
		try {
			val lm = getSystemService(android.location.LocationManager::class.java)
			lm?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true
		} catch (_: Exception) {
			false
		}

	/** Do Not Disturb: zen_mode is a plain global setting, readable without policy access. */
	private fun isDndOn(): Boolean =
		try {
			Settings.Global.getInt(contentResolver, "zen_mode", 0) != 0
		} catch (_: Exception) {
			false
		}

	/** Data saver is on when this app is restricted (or exempted) by it. */
	private fun isDataSaverOn(): Boolean =
		try {
			val cm = getSystemService(ConnectivityManager::class.java) ?: return false
			val status = cm.restrictBackgroundStatus
			status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED ||
				status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED
		} catch (_: Exception) {
			false
		}

	private fun isAutoRotateOn(): Boolean =
		try {
			Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) != 0
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

	/** Manual LifecycleOwner for the WindowManager-hosted ComposeView. */
	private class ServiceLifecycleOwner :
		LifecycleOwner,
		SavedStateRegistryOwner {
		private val registry = LifecycleRegistry(this)
		private val savedStateController by lazy { SavedStateRegistryController.create(this) }

		override val lifecycle: Lifecycle get() = registry
		override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

		fun create() {
			savedStateController.performAttach()
			savedStateController.performRestore(null)
			registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
		}

		fun start() {
			registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
		}

		fun resume() {
			registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
		}

		fun pause() {
			registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
		}

		fun stop() {
			registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
		}

		fun destroy() {
			registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
		}
	}
}
