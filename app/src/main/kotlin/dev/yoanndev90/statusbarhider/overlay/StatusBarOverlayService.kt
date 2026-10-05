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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
	private val mediaSessions = MediaSessions(this, serviceScope)

	private var prefs by mutableStateOf(OverlayPrefs())
	private var barState by mutableStateOf(OverlayBarState())

	private val window =
		OverlayWindow(
			context = this,
			prefsFlow = prefsRepo.state,
			content = {
				OverlayBar(
					prefs = prefs,
					state = barState,
					onClockClick = ::onClockClick,
					onDateClick = ::onDateClick
				)
			}
		)

	private val handler = Handler(Looper.getMainLooper())
	private var batteryPct = -1
	private var batteryCharging = false
	private var usbConnected = false
	private var lastRx = -1L
	private var lastTx = -1L

	/** Camera ids with the torch currently on (filled by the torch flow). */
	private val torchIds = ConcurrentHashMap.newKeySet<String>()

	/** Indicator reads collected off the main thread (see [updateConnectivity]). */
	private data class Connectivity(
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

	/** True while the overlay window is detached because a blacklisted app is up front. */
	private var suppressed = false

	/** The missing-usage-access hint is logged once per service run, not per poll. */
	private var usageHintLogged = false

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
		observeSystem()
		serviceScope.launch {
			refreshAll()
		}
		serviceScope.launch {
			prefsRepo.state.collect { onPrefsChanged(it) }
		}
		serviceScope.launch {
			NotifIcons.snapshots.collect {
				if (barState.screenOn) updateNotifs()
			}
		}
		serviceScope.launch {
			mediaSessions.nowPlaying.collect {
				if (barState.screenOn) updateMedia()
			}
		}
		serviceScope.launch {
			window.cameraGeometry.collect { geometry ->
				if (geometry != barState.camera) barState = barState.copy(camera = geometry)
			}
		}
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
		// Cancelling the scope unregisters every collected framework listener.
		serviceScope.cancel()
		handler.removeCallbacksAndMessages(null)
		mediaSessions.dispose()
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
		runSafely(this@StatusBarOverlayService) {
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

	/**
	 * Registers every framework listener as a collected flow. The collections
	 * live in [serviceScope], so destroy cancels them and [awaitClose] unregisters.
	 */
	private fun observeSystem() {
		// Sticky read first: the battery flow's first emission races refreshAll().
		val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
		if (sticky != null) {
			batteryPct =
				OverlayPrefs.batteryPct(
					sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
					sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
				)
		}
		serviceScope.launch {
			receiverFlow(IntentFilter(Intent.ACTION_BATTERY_CHANGED)).collect { onBattery(it) }
		}
		serviceScope.launch {
			val filter = IntentFilter()
			filter.addAction(Intent.ACTION_SCREEN_ON)
			filter.addAction(Intent.ACTION_SCREEN_OFF)
			filter.addAction(Intent.ACTION_USER_PRESENT)
			receiverFlow(filter).collect { onScreen(it) }
		}
		serviceScope.launch {
			receiverFlow(IntentFilter("android.hardware.usb.action.USB_STATE")).collect { onUsb(it) }
		}
		serviceScope.launch {
			// NFC adapter toggles and location provider changes refresh the indicators.
			val filter = IntentFilter()
			filter.addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
			filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
			filter.addAction("android.location.GPS_ENABLED_CHANGE")
			filter.addAction(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
			receiverFlow(filter).collect {
				if (barState.screenOn) updateConnectivity()
			}
		}
		serviceScope.launch {
			networkFlow().collect {
				if (barState.screenOn) updateConnectivity()
			}
		}
		serviceScope.launch {
			// DND / auto-rotate changes land here so the icons react immediately.
			systemSettingsFlow().collect {
				if (barState.screenOn) updateConnectivity()
			}
		}
		serviceScope.launch {
			torchFlow().collect { (cameraId, enabled) ->
				if (enabled) {
					torchIds.add(cameraId)
				} else {
					torchIds.remove(cameraId)
				}
				if (barState.screenOn) updateConnectivity()
			}
		}
	}

	/** Registers a receiver for [filter]; the collection's cancel unregisters it. */
	private fun receiverFlow(filter: IntentFilter): Flow<Intent> =
		callbackFlow {
			val receiver =
				object : BroadcastReceiver() {
					override fun onReceive(
						context: Context,
						intent: Intent
					) {
						trySend(intent)
					}
				}
			runSafely(this@StatusBarOverlayService) { registerReceiver(receiver, filter) }
			awaitClose { runSafely(this@StatusBarOverlayService) { unregisterReceiver(receiver) } }
		}

	/** zen_mode + auto-rotate changes; the collection's cancel unregisters it. */
	private fun systemSettingsFlow(): Flow<Unit> =
		callbackFlow {
			val observer =
				object : ContentObserver(handler) {
					override fun onChange(selfChange: Boolean) {
						trySend(Unit)
					}
				}
			runSafely(this@StatusBarOverlayService) {
				contentResolver.registerContentObserver(Settings.Global.getUriFor("zen_mode"), false, observer)
				contentResolver.registerContentObserver(
					Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
					false,
					observer
				)
			}
			awaitClose { runSafely(this@StatusBarOverlayService) { contentResolver.unregisterContentObserver(observer) } }
		}

	/** Torch on/off per camera id; the collection's cancel unregisters it. */
	private fun torchFlow(): Flow<Pair<String, Boolean>> =
		callbackFlow {
			val cm = getSystemService(CameraManager::class.java) ?: return@callbackFlow
			val cb =
				object : CameraManager.TorchCallback() {
					override fun onTorchModeChanged(
						cameraId: String,
						enabled: Boolean
					) {
						trySend(cameraId to enabled)
					}
				}
			try {
				cm.registerTorchCallback(cb, handler)
			} catch (_: Exception) {
				return@callbackFlow
			}
			awaitClose { runSafely(this@StatusBarOverlayService) { cm.unregisterTorchCallback(cb) } }
		}

	/** Default network changes; the collection's cancel unregisters it. */
	private fun networkFlow(): Flow<Unit> =
		callbackFlow {
			val cm = getSystemService(ConnectivityManager::class.java)
			val cb =
				object : ConnectivityManager.NetworkCallback() {
					override fun onAvailable(network: Network) {
						trySend(Unit)
					}

					override fun onLost(network: Network) {
						trySend(Unit)
					}

					override fun onCapabilitiesChanged(
						network: Network,
						caps: NetworkCapabilities
					) {
						trySend(Unit)
					}
				}
			runSafely(this@StatusBarOverlayService) { cm?.registerDefaultNetworkCallback(cb) }
			awaitClose { runSafely(this@StatusBarOverlayService) { cm?.unregisterNetworkCallback(cb) } }
		}

	private fun onBattery(intent: Intent) {
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

	private suspend fun onScreen(intent: Intent) {
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

	private suspend fun onUsb(intent: Intent) {
		usbConnected = intent.extras?.getBoolean("connected") == true
		if (barState.screenOn) updateConnectivity()
	}

	private suspend fun refreshAll() {
		window.updateCamera()
		updateBattery()
		updateNotifs()
		updateCalendar()
		updateMedia()
		updateConnectivity()
		updateAlarm()
	}

	private suspend fun refreshPolled() {
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
	private suspend fun updateSuppressed() {
		// UsageStats queries are binder IPC: off the 2s poll's main thread.
		val pkg = withContext(Dispatchers.IO) { indicators.foregroundPackage() } ?: return
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

	private suspend fun updateNotifs() {
		val p = prefs
		val locked = barState.locked
		// Privacy filter: while the keyguard is up the icons (and the progress
		// ring they feed) would leak what the notifications are about.
		val privateFilter = locked && p.hideNotifsOnLock
		// Settings read + icon rasterization are IPC/CPU: off the main thread.
		val (enabled, icons, progress) =
			withContext(Dispatchers.IO) {
				val listenerOn = NotifListenerService.isEnabled(this@StatusBarOverlayService)
				val on = p.showNotifs && listenerOn && !privateFilter
				val list: List<OverlayNotifIcon> =
					if (!on) {
						emptyList()
					} else {
						NotifIcons.snapshot().take(p.maxNotifs.coerceIn(1, 8)).mapNotNull { e ->
							try {
								val bitmap = e.icon?.toBitmap()?.asImageBitmap()
								OverlayNotifIcon(bitmap, e.pkg)
							} catch (_: Exception) {
								null
							}
						}
					}
				val ring =
					NotifIcons
						.progress()
						?.let { OverlayProgress(it.fraction, it.indeterminate, it.pkg) }
						?.takeIf { listenerOn && !privateFilter }
				Triple(on, list, ring)
			}
		barState = barState.copy(notifsEnabled = enabled, notifs = icons, progress = progress)
	}

	/** Next calendar event in the next 24h as "HH:mm Title". */
	private suspend fun updateCalendar() {
		val show = prefs.showCalendar
		val text = withContext(Dispatchers.IO) { indicators.queryCalendar(show) }
		barState = barState.copy(calendarText = text)
	}

	/** Now playing from media sessions; hidden when notification access is off. */
	private fun updateMedia() {
		barState = barState.copy(mediaText = queryMedia())
	}

	private fun queryMedia(): String? {
		if (!prefs.showMedia || !NotifListenerService.isEnabled(this)) return null
		mediaSessions.ensure()
		return mediaSessions.nowPlaying.value
	}

	private suspend fun updateConnectivity() {
		// Every indicator is a binder/Settings read: collect them off the main
		// thread, then apply one fresh copy on the caller's (main) context.
		val p = prefs
		val c =
			withContext(Dispatchers.IO) {
				val airplane = indicators.isAirplaneOn()
				val showRest = !airplane
				val mobile = showRest && p.showMobileData && indicators.isMobile()
				Connectivity(
					airplane = airplane,
					wifi = showRest && p.showWifi && indicators.isWifi(),
					mobile = mobile,
					mobileType = if (mobile) indicators.mobileTypeLabel().ifEmpty { "4G" } else "",
					bluetooth = showRest && p.showBluetooth && indicators.isBluetoothOn(),
					vpn = showRest && p.showVpn && indicators.isVpn(),
					hotspot = showRest && p.showHotspot && indicators.isHotspotOn(),
					nfc = showRest && p.showNfc && indicators.isNfcOn(),
					gps = p.showGps && indicators.isGpsOn(),
					dnd = p.showDnd && indicators.isDndOn(),
					dataSaver = p.showDataSaver && indicators.isDataSaverOn(),
					autoRotate = p.showRotate && indicators.isAutoRotateOn()
				)
			}
		barState =
			barState.copy(
				usbConnected = usbConnected,
				airplane = c.airplane,
				wifi = c.wifi,
				mobile = c.mobile,
				mobileType = c.mobileType,
				bluetooth = c.bluetooth,
				vpn = c.vpn,
				hotspot = c.hotspot,
				nfc = c.nfc,
				gps = c.gps,
				dnd = c.dnd,
				dataSaver = c.dataSaver,
				autoRotate = c.autoRotate,
				torch = p.showTorch && torchIds.isNotEmpty()
			)
	}

	private suspend fun updateAlarm() {
		val show = prefs.showAlarm
		val text = withContext(Dispatchers.IO) { indicators.queryAlarm(show) }
		barState = barState.copy(alarmText = text)
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
