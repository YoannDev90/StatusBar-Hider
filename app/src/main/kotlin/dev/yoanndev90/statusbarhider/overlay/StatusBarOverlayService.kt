package dev.yoanndev90.statusbarhider.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.AlarmClock
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
		private const val REFRESH_DEBOUNCE_MS = 100L
		private const val FOREGROUND_POLL_MS = 2_000L

		/**
		 * The label and the signal bars are what the eye tracks; the general
		 * poll (5..60 s) is far too slow for a level that moves on its own.
		 */
		private const val MOBILE_POLL_MS = 2_000L

		/**
		 * Largest gap still taken as "the previous sample": the sampler stops
		 * with the screen, so a restart after a long sleep must re-baseline
		 * instead of reporting the whole gap as traffic.
		 */
		private const val BANDWIDTH_MAX_GAP_MS = 5_000L

		private const val USB_STATE_ACTION = "android.hardware.usb.action.USB_STATE"
		private const val REAPPLY_AFTER_UNLOCK_MS = 0L
		private const val REAPPLY_WATCH_MS = 350L
		private const val REAPPLY_POLL_MS = 30L

		const val ACTION_STOP = "dev.yoanndev90.statusbarhider.overlay.STOP"

		/**
		 * Starts the foreground service. Returns false when the system refused
		 * it (background-start restrictions, OEM kill), so the persisted flag
		 * can be rolled back instead of claiming a bar that never appears.
		 */
		fun start(context: Context): Boolean {
			val app = context.applicationContext
			return try {
				app.startForegroundService(Intent(context, StatusBarOverlayService::class.java))
				true
			} catch (e: Exception) {
				// Without this line the switch stays "on" while no window ever
				// appears and the failure is invisible from the app.
				Log.w(TAG, "start failed", e)
				LogStore.append(app, app.getString(R.string.log_start_service_failed, e.message ?: e.toString()))
				false
			}
		}

		fun stop(context: Context) {
			val app = context.applicationContext
			try {
				app.stopService(Intent(context, StatusBarOverlayService::class.java))
			} catch (e: Exception) {
				Log.w(TAG, "stop failed", e)
				LogStore.append(app, app.getString(R.string.log_stop_service_failed, e.message ?: e.toString()))
			}
		}
	}

	private val prefsRepo = OverlayPrefsRepository.getInstance(this)
	private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private val indicators = SystemIndicators(this)
	private val mediaSessions = MediaSessions(this, serviceScope)

	private var prefs by mutableStateOf(OverlayPrefs())
	private var barState by SharedBar.barState

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
	private var lastSampleAt = 0L

	/** Camera ids with the torch currently on (filled by the torch flow). */
	private val torchIds = ConcurrentHashMap.newKeySet<String>()

	/** True while the overlay window is detached because a blacklisted app is up front. */
	private var suppressed by SharedBar.suppressed

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
	private var mobileJob: Job? = null
	private var refreshJob: Job? = null

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		// window.sync() needs the stored prefs (lock screen / touchability flags)
		// and the initial lock state; the flow below keeps them up to date afterwards.
		prefs = prefsRepo.state.value
		// Seed both flags from the device: SCREEN_ON/OFF are not sticky, so a
		// start while the screen is off (boot / restart) would otherwise claim
		// a lit screen until the next broadcast.
		barState =
			barState.copy(
				locked = indicators.isKeyguardUp(),
				screenOn = getSystemService(PowerManager::class.java)?.isInteractive ?: true
			)
		startOverlayForeground()
		syncWindowAttachment()
		if (!barState.screenOn) window.setRenderingActive(false)
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
		scheduleMobile()
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
		// Re-evaluates flags *and* the lock-screen teardown below.
		syncWindowAttachment()
		if (next.showMedia) mediaSessions.ensure()
		scheduleBandwidth()
		scheduleBurnIn()
		scheduleForegroundPoll()
		scheduleMobile()
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
			// Sticky first: the flow only carries broadcasts, so without it the
			// connection made before the service started stays unknown until the
			// next plug/unplug.
			registerReceiver(null, IntentFilter(USB_STATE_ACTION))?.let { onUsb(it) }
			receiverFlow(IntentFilter(USB_STATE_ACTION)).collect { onUsb(it) }
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
			systemSettingsFlow(handler).collect {
				if (barState.screenOn) updateConnectivity()
			}
		}
		serviceScope.launch {
			torchFlow(handler).collect { (cameraId, enabled) ->
				if (enabled) {
					torchIds.add(cameraId)
				} else {
					torchIds.remove(cameraId)
				}
				if (barState.screenOn) updateConnectivity()
			}
		}
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
				mobileJob?.cancel()
				// Including the debounced refresh: it would otherwise run six
				// widget updates, IPC included, while the screen is off.
				refreshJob?.cancel()
				// No pixels to draw: pause the composition, and drop the window
				// entirely when the bar must not show on the lock screen.
				window.setRenderingActive(false)
				syncWindowAttachment()
			}
			Intent.ACTION_SCREEN_ON -> {
				barState = barState.copy(screenOn = true, locked = indicators.isKeyguardUp())
				window.setRenderingActive(true)
				syncWindowAttachment()
				refreshAll()
				schedulePoll()
				scheduleBandwidth()
				scheduleBurnIn()
				scheduleForegroundPoll()
				scheduleMobile()
			}
			Intent.ACTION_USER_PRESENT -> {
				barState = barState.copy(locked = false)
				syncWindowAttachment()
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
			if (suppressed) setSuppressedState(false)
			return
		}
		if (active) {
			foregroundJob = serviceScope.launch { foregroundLoop() }
		} else if (suppressed) {
			setSuppressedState(false)
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

	/**
	 * (Re)arms the 2 s cellular refresh. The loop dies with the screen or the
	 * "Mobile data" toggle; turning the toggle off must go through here so the
	 * bar does not keep polling a radio it no longer draws.
	 */
	private fun scheduleMobile() {
		mobileJob?.cancel()
		mobileJob = null
		if (prefs.showMobileData && barState.screenOn) {
			mobileJob = serviceScope.launch { mobileLoop() }
		}
	}

	/** 2 s cellular label / signal-level refresh; see [scheduleMobile]. */
	private suspend fun CoroutineScope.mobileLoop() {
		while (isActive && barState.screenOn && prefs.showMobileData) {
			updateMobile()
			delay(MOBILE_POLL_MS)
		}
	}

	/** Refreshes only the cellular indicator, on the faster loop's own cadence. */
	private suspend fun updateMobile() {
		val m = indicators.readMobile(prefs)
		barState = barState.copy(mobile = m.active, mobileType = m.type, signalLevel = m.level)
	}

	/** The2s poll only earns its keep while something could actually hide. */
	private fun CoroutineScope.foregroundPollWanted(): Boolean {
		if (!isActive || !barState.screenOn) return false
		return prefs.hideBarInApps && prefs.hiddenApps.isNotEmpty()
	}

	/** 2 s foreground-app poll; the loop dies with the feature or the screen. */
	private suspend fun CoroutineScope.foregroundLoop() {
		while (foregroundPollWanted()) {
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
		val pkg = withContext(Dispatchers.IO) { UsageAccess.foregroundPackage(applicationContext) } ?: return
		setSuppressedState(pkg in prefs.hiddenApps)
	}

	/**
	 * Single source of truth for the window's existence. It goes away when a
	 * blacklisted app is in front, or while the keyguard is up and the lock
	 * screen toggle is off — the latter tears the surface down instead of
	 * composing a bar the keyguard would cover anyway.
	 */
	private fun syncWindowAttachment() {
		window.sync(!suppressed && !(barState.locked && !prefs.showOnLockScreen))
	}

	/**
	 * Attaches / detaches the whole overlay window: a half-empty bar would
	 * still take touch space and paint its background over the app.
	 */
	private fun setSuppressedState(value: Boolean) {
		if (suppressed == value) return
		suppressed = value
		syncWindowAttachment()
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

	private suspend fun updateCalendar() {
		val show = prefs.showCalendar
		val use24h = prefs.use24h
		val text = withContext(Dispatchers.IO) { indicators.queryCalendar(show, use24h) }
		barState = barState.copy(calendarText = text)
	}

	/** Now playing from media sessions; hidden when notification access is off. */
	private suspend fun updateMedia() {
		// The notification-access check is a Settings read: off the main thread.
		// ensure() stays here - it registers a framework listener, which is a
		// one-off and must land on the thread the manager expects.
		val listenerOn = withContext(Dispatchers.IO) { NotifListenerService.isEnabled(this@StatusBarOverlayService) }
		if (!prefs.showMedia || !listenerOn) {
			barState = barState.copy(mediaText = null)
			return
		}
		mediaSessions.ensure()
		barState = barState.copy(mediaText = mediaSessions.nowPlaying.value)
	}

	private suspend fun updateConnectivity() {
		val p = prefs
		val c = indicators.readConnectivity(p)
		barState =
			barState.copy(
				usbConnected = usbConnected,
				airplane = c.airplane,
				wifi = c.wifi,
				mobile = c.mobile,
				mobileType = c.mobileType,
				signalLevel = c.signalLevel,
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
		val use24h = prefs.use24h
		val text = withContext(Dispatchers.IO) { indicators.queryAlarm(show, use24h) }
		barState = barState.copy(alarmText = text)
	}

	private fun updateBandwidth() {
		val rx = TrafficStats.getTotalRxBytes()
		val tx = TrafficStats.getTotalTxBytes()
		val now = System.currentTimeMillis()
		// The loop dies with the screen: a delta against the last pre-sleep
		// sample would report the whole sleep as ".../s".
		val comparable = lastRx >= 0 && rx >= 0 && now - lastSampleAt <= BANDWIDTH_MAX_GAP_MS
		if (comparable) {
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
		lastSampleAt = now
	}
}
