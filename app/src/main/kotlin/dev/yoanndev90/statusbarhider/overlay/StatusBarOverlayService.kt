package dev.yoanndev90.statusbarhider.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.usage.UsageAccess
import dev.yoanndev90.statusbarhider.core.watcher.SystemUiWatcher
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.hide.HideInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
		private const val REFRESH_DEBOUNCE_MS = 100L
		private const val FOREGROUND_POLL_MS = 2_000L
		private const val REAPPLY_AFTER_UNLOCK_MS = 0L
		private const val REAPPLY_WATCH_MS = 350L
		private const val REAPPLY_POLL_MS = 30L

		const val ACTION_STOP = "dev.yoanndev90.statusbarhider.overlay.STOP"

		fun start(context: Context) {
			try {
				context.startForegroundService(Intent(context, StatusBarOverlayService::class.java))
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
	private val indicators = SystemIndicators(this)
	private val mediaSessions = MediaSessions(this, ::runOnOverlay, ::updateMedia)

	private var prefs by mutableStateOf(OverlayPrefs())
	private var barState by mutableStateOf(OverlayBarState())

	private val window =
		OverlayWindow(
			context = this,
			prefsProvider = { prefs },
			content = {
				OverlayBar(
					prefs = prefs,
					state = barState,
					onClockClick = ::onClockClick,
					onDateClick = ::onDateClick
				)
			},
			onCameraGeometry = { geometry ->
				if (geometry != barState.camera) barState = barState.copy(camera = geometry)
			}
		)

	private val handler = Handler(Looper.getMainLooper())
	private var batteryPct = -1
	private var batteryCharging = false
	private var usbConnected = false
	private var lastRx = -1L
	private var lastTx = -1L

	/** Camera ids with the torch currently on (filled by [torchCallback]). */
	private val torchIds = ConcurrentHashMap.newKeySet<String>()
	private var cameraManager: CameraManager? = null
	private var torchRegistered = false

	/** True while the overlay window is detached because a blacklisted app is up front. */
	private var suppressed = false

	/** The missing-usage-access hint is logged once per service run, not per poll. */
	private var usageHintLogged = false

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
						// The screen going off always puts the keyguard back up.
						barState = barState.copy(screenOn = false, locked = true)
						pollJob?.cancel()
						burnInJob?.cancel()
						bandwidthJob?.cancel()
						foregroundJob?.cancel()
					}
					Intent.ACTION_SCREEN_ON -> {
						barState = barState.copy(screenOn = true, locked = indicators.isDeviceLocked())
						refreshAll()
						schedulePoll()
						scheduleBandwidth()
						scheduleBurnIn()
						scheduleForegroundPoll()
					}
					Intent.ACTION_USER_PRESENT -> {
						barState = barState.copy(locked = false)
						updateNotifs()
						reapplyHideAfterUnlock()
					}
				}
			}
		}

	/**
	 * The platform zeroes the shared disable record when the keyguard goes
	 * away, so the system status bar comes back on every unlock. Apply right
	 * after unlock, then watch for the late wipe and re-apply; silent unless
	 * an apply command itself fails.
	 */
	private fun reapplyHideAfterUnlock() {
		val app = application
		if (!HideInteractor.isHidden(app)) return
		ShellRunner.run(app, null) {
			delay(REAPPLY_AFTER_UNLOCK_MS)
			var last = HideInteractor.applyHide(app)
			var watched = 0L
			while (last.ok && watched < REAPPLY_WATCH_MS) {
				delay(REAPPLY_POLL_MS)
				watched += REAPPLY_POLL_MS
				if (!HideInteractor.isApplied(app)) last = HideInteractor.applyHide(app)
			}
			if (!last.ok) {
				LogStore.append(app, app.getString(R.string.log_reapply_hide_unlock))
				last.lines.forEach { LogStore.append(app, it) }
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

	/** Restartable poll loops; [serviceScope] (structured) cancels them on destroy. */
	private var pollJob: Job? = null
	private var burnInJob: Job? = null
	private var bandwidthJob: Job? = null
	private var foregroundJob: Job? = null
	private var refreshJob: Job? = null

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		// window.attach() needs the stored prefs (lock screen / touchability flags);
		// the flow below keeps them up to date afterwards.
		prefs = prefsRepo.state.value
		// The lock state decides the layout from the very first frame.
		barState = barState.copy(locked = indicators.isDeviceLocked())
		startFg()
		window.attach()
		registerReceivers()
		NotifIcons.addListener(notifListener)
		serviceScope.launch {
			prefsRepo.state.collect { onPrefsChanged(it) }
		}
		refreshAll()
		schedulePoll()
		scheduleBandwidth()
		scheduleForegroundPoll()
		// The overlay is up for hours: it is the natural owner of the SystemUI watcher.
		SystemUiWatcher.start(this, this)
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

	/** Rotation / fold changes resize the display: re-resolve the cutout against the new screen. */
	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)
		handler.post { window.updateCamera() }
	}

	private fun onPrefsChanged(next: OverlayPrefs) {
		prefs = next
		window.ensureFlags()
		if (next.showMedia) mediaSessions.ensure()
		scheduleBandwidth()
		scheduleBurnIn()
		scheduleForegroundPoll()
		// Trailing-edge debounce: a slider drag calls this per frame, and
		// refreshAll() itself runs six widget updates.
		refreshJob?.cancel()
		refreshJob =
			serviceScope.launch {
				delay(REFRESH_DEBOUNCE_MS)
				refreshAll()
			}
	}

	override fun onDestroy() {
		serviceScope.cancel()
		handler.removeCallbacksAndMessages(null)
		NotifIcons.removeListener(notifListener)
		listOf(batteryReceiver, screenReceiver, usbReceiver, radioReceiver).forEach { receiver ->
			runSafely { unregisterReceiver(receiver) }
		}
		mediaSessions.dispose()
		runSafely {
			val cm = getSystemService(ConnectivityManager::class.java)
			cm?.unregisterNetworkCallback(networkCallback)
		}
		runSafely { contentResolver.unregisterContentObserver(systemObserver) }
		if (torchRegistered) runSafely { cameraManager?.unregisterTorchCallback(torchCallback) }
		SystemUiWatcher.stop(this)
		window.detach()
		// The overlay is gone: let SystemUI re-read the "Custom bar" tile state.
		HideInteractor.notifyTiles(this)
		super.onDestroy()
	}

	private fun startFg() {
		val nm = getSystemService(NotificationManager::class.java) ?: return
		if (nm.getNotificationChannel(CHANNEL_ID) == null) {
			nm.createNotificationChannel(
				NotificationChannel(
					CHANNEL_ID,
					getString(R.string.notif_channel_name),
					NotificationManager.IMPORTANCE_MIN
				)
			)
		}
		val notif =
			Notification
				.Builder(this, CHANNEL_ID)
				.setContentTitle(getString(R.string.notif_content_title))
				.setContentText(getString(R.string.notif_content_text))
				.setSmallIcon(R.drawable.ic_tile_bar)
				.build()
		try {
			if (Build.VERSION.SDK_INT >= 34) {
				startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
			} else {
				startForeground(NOTIF_ID, notif)
			}
		} catch (e: Exception) {
			Log.w(TAG, "startForeground failed", e)
		}
	}

	private fun onClockClick() {
		if (!prefs.interactive) return
		runSafely {
			startActivity(
				Intent(AlarmClock.ACTION_SHOW_ALARMS)
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			)
		}
	}

	private fun onDateClick() {
		if (!prefs.interactive) return
		try {
			val uri = Uri.parse("content://com.android.calendar/time")
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

	private fun scheduleBurnIn() {
		burnInJob?.cancel()
		burnInJob = null
		if (prefs.burnInMin > 0 && barState.screenOn) {
			burnInJob = serviceScope.launch { burnInLoop() }
		}
	}

	private fun registerReceivers() {
		runSafely {
			registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
			val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
			if (sticky != null) {
				batteryPct =
					OverlayPrefs.batteryPct(
						sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
						sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
					)
			}
		}
		runSafely {
			val f = IntentFilter()
			f.addAction(Intent.ACTION_SCREEN_ON)
			f.addAction(Intent.ACTION_SCREEN_OFF)
			f.addAction(Intent.ACTION_USER_PRESENT)
			registerReceiver(screenReceiver, f)
		}
		runSafely {
			registerReceiver(usbReceiver, IntentFilter("android.hardware.usb.action.USB_STATE"))
		}
		runSafely {
			val f = IntentFilter()
			f.addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
			f.addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
			f.addAction("android.location.GPS_ENABLED_CHANGE")
			f.addAction(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
			registerReceiver(radioReceiver, f)
		}
		runSafely {
			val cm = getSystemService(ConnectivityManager::class.java)
			cm?.registerDefaultNetworkCallback(networkCallback)
		}
		runSafely {
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
		window.updateCamera()
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
		pollJob?.cancel()
		pollJob = serviceScope.launch { pollLoop() }
	}

	/**
	 * (Re)arms the 1s bandwidth sampler. Every path that can turn the screen or
	 * the toggle back on must call it, otherwise the loop dies on the first
	 * screen-off / screen-on cycle.
	 */
	private fun scheduleBandwidth() {
		bandwidthJob?.cancel()
		bandwidthJob = null
		if (prefs.showBandwidth && barState.screenOn) {
			bandwidthJob = serviceScope.launch { bandwidthLoop() }
		}
	}

	/**
	 * (Re)arms the 2 s foreground-app poll. Turning the feature off, emptying
	 * the list or switching the screen off must go through here, otherwise the
	 * loop either dies or keeps a suppressed bar suppressed forever.
	 */
	private fun scheduleForegroundPoll() {
		foregroundJob?.cancel()
		foregroundJob = null
		val active = prefs.hideBarInApps && prefs.hiddenApps.isNotEmpty() && barState.screenOn
		if (active && !UsageAccess.granted(this)) {
			if (!usageHintLogged) {
				usageHintLogged = true
				Log.w(TAG, "Usage access not granted - app blacklist inactive")
				LogStore.append(this, getString(R.string.log_usage_access_missing))
			}
			if (suppressed) setSuppressed(false)
			return
		}
		if (active) {
			foregroundJob = serviceScope.launch { foregroundLoop() }
		} else if (suppressed) {
			setSuppressed(false)
		}
	}

	/** Alarm / calendar / connectivity poll; the screen-off cancel ends it. */
	private suspend fun CoroutineScope.pollLoop() {
		while (isActive && barState.screenOn) {
			refreshPolled()
			delay((prefs.updateIntervalSec.coerceIn(5, 60) * 1000).toLong())
		}
	}

	/** 1 s bandwidth sampler; the loop dies with the screen or the toggle. */
	private suspend fun CoroutineScope.bandwidthLoop() {
		while (isActive && barState.screenOn && prefs.showBandwidth) {
			updateBandwidth()
			delay(1000L)
		}
	}

	/** 2 s foreground-app poll; the loop dies with the feature or the screen. */
	private suspend fun CoroutineScope.foregroundLoop() {
		while (isActive && barState.screenOn && prefs.hideBarInApps && prefs.hiddenApps.isNotEmpty()) {
			updateSuppressed()
			delay(FOREGROUND_POLL_MS)
		}
	}

	/** Periodic burn-in shift; re-reads [OverlayPrefs.burnInMin] every cycle. */
	private suspend fun CoroutineScope.burnInLoop() {
		while (isActive && barState.screenOn && prefs.burnInMin > 0) {
			delay((prefs.burnInMin.coerceIn(1, 30) * 60 * 1000).toLong())
			if (!isActive) return
			window.shiftForBurnIn()
		}
	}

	/** Recomputes the suppression state from the app currently in front. */
	private fun updateSuppressed() {
		val pkg = indicators.foregroundPackage() ?: return
		setSuppressed(pkg in prefs.hiddenApps)
	}

	/**
	 * Attaches / detaches the whole overlay window: a half-empty bar would
	 * still take touch space and paint its background over the app.
	 */
	private fun setSuppressed(value: Boolean) {
		if (suppressed == value) return
		suppressed = value
		if (value) {
			window.detach()
		} else {
			window.attach()
		}
	}

	private fun updateBattery() {
		barState =
			barState.copy(
				batteryPct = batteryPct,
				batteryCharging = batteryCharging
			)
	}

	private fun updateNotifs() {
		val listenerOn = NotifListenerService.isEnabled(this)
		// Privacy filter: while the keyguard is up the icons (and the progress
		// ring they feed) would leak what the notifications are about.
		val privateFilter = barState.locked && prefs.hideNotifsOnLock
		val enabled = prefs.showNotifs && listenerOn && !privateFilter
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
		val progress =
			NotifIcons
				.progress()
				?.let { OverlayProgress(it.fraction, it.indeterminate, it.pkg) }
				?.takeIf { listenerOn && !privateFilter }
		barState = barState.copy(notifsEnabled = enabled, notifs = icons, progress = progress)
	}

	/** Next calendar event in the next 24h as "HH:mm Title". */
	private fun updateCalendar() {
		barState = barState.copy(calendarText = indicators.queryCalendar(prefs.showCalendar))
	}

	/** Now playing from media sessions; hidden when notification access is off. */
	private fun updateMedia() {
		barState = barState.copy(mediaText = queryMedia())
	}

	private fun queryMedia(): String? {
		if (!prefs.showMedia || !NotifListenerService.isEnabled(this)) return null
		mediaSessions.ensure()
		return mediaSessions.nowPlaying()
	}

	private fun updateConnectivity() {
		val airplane = indicators.isAirplaneOn()
		val showRest = !airplane
		val mobile = showRest && prefs.showMobileData && indicators.isMobile()
		barState =
			barState.copy(
				usbConnected = usbConnected,
				airplane = airplane,
				wifi = showRest && prefs.showWifi && indicators.isWifi(),
				mobile = mobile,
				mobileType = if (mobile) indicators.mobileTypeLabel().ifEmpty { "4G" } else "",
				bluetooth = showRest && prefs.showBluetooth && indicators.isBluetoothOn(),
				vpn = showRest && prefs.showVpn && indicators.isVpn(),
				hotspot = showRest && prefs.showHotspot && indicators.isHotspotOn(),
				nfc = showRest && prefs.showNfc && indicators.isNfcOn(),
				gps = prefs.showGps && indicators.isGpsOn(),
				dnd = prefs.showDnd && indicators.isDndOn(),
				dataSaver = prefs.showDataSaver && indicators.isDataSaverOn(),
				autoRotate = prefs.showRotate && indicators.isAutoRotateOn(),
				torch = prefs.showTorch && torchIds.isNotEmpty()
			)
	}

	private fun updateAlarm() {
		barState = barState.copy(alarmText = indicators.queryAlarm(prefs.showAlarm))
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
}
