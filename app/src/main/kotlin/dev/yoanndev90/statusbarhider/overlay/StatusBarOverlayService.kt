package dev.yoanndev90.statusbarhider.overlay
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.location.LocationManager
import android.net.ConnectivityManager
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
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.usage.UsageAccess
import dev.yoanndev90.statusbarhider.core.watcher.SystemUiWatcher
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.hide.HideInteractor
import dev.yoanndev90.statusbarhider.overlay.model.IndicatorFormats
import dev.yoanndev90.statusbarhider.overlay.model.OverlayNotifIcon
import dev.yoanndev90.statusbarhider.overlay.model.OverlayProgress
import dev.yoanndev90.statusbarhider.overlay.poll.OverlayPoller
import dev.yoanndev90.statusbarhider.overlay.poll.PollHost
import dev.yoanndev90.statusbarhider.overlay.system.MediaSessions
import dev.yoanndev90.statusbarhider.overlay.system.NotifIcons
import dev.yoanndev90.statusbarhider.overlay.system.SystemIndicators
import dev.yoanndev90.statusbarhider.overlay.system.SystemStatusReader
import dev.yoanndev90.statusbarhider.overlay.system.networkFlow
import dev.yoanndev90.statusbarhider.overlay.system.receiverFlow
import dev.yoanndev90.statusbarhider.overlay.system.runSafely
import dev.yoanndev90.statusbarhider.overlay.system.systemSettingsFlow
import dev.yoanndev90.statusbarhider.overlay.system.torchFlow
import dev.yoanndev90.statusbarhider.overlay.widgets.OverlayBar
import dev.yoanndev90.statusbarhider.overlay.window.OverlayWindow
import dev.yoanndev90.statusbarhider.overlay.window.SharedBar
import dev.yoanndev90.statusbarhider.overlay.window.startOverlayForeground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
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
class StatusBarOverlayService :
	Service(),
	PollHost {
	companion object {
		private const val TAG = "CustomBar"
		private const val REFRESH_DEBOUNCE_MS = 100L

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

	/** Identity of the notification-listener service, for the media-session listener. */
	private fun notifListenerComponent() = ComponentName(this, NotifListenerService::class.java)

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

	/** Camera ids with the torch currently on (filled by the torch flow). */
	private val torchIds = ConcurrentHashMap.newKeySet<String>()

	/** True while the overlay window is detached because a blacklisted app is up front. */
	private var suppressed by SharedBar.suppressed

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

	/** Debounce for [refreshAll]; the poll loops themselves live in [poller]. */
	private var refreshJob: Job? = null

	/** Owns every poll loop. The service is the [PollHost] those loops call into. */
	private val poller = OverlayPoller(serviceScope, this)

	/**
	 * Turns the device's status into [OverlayBarState] fields. Each lambda
	 * re-reads a live field at the very point the original code did, so a
	 * value changed while a probe suspends is seen exactly as before.
	 */
	private val reader =
		SystemStatusReader(
			indicators,
			{ prefs },
			{ barState = it(barState) },
			{ usbConnected },
			{ torchIds }
		)

	// --- PollHost: the live state the loops read, and their tick actions ----

	override val currentPrefs: OverlayPrefs
		get() = prefs

	override val isScreenOn: Boolean
		get() = barState.screenOn

	override val usageAccessGranted: Boolean
		get() = UsageAccess.granted(this)

	override suspend fun sampleBandwidth() = reader.updateBandwidth()

	override suspend fun refreshMobile() = reader.updateMobile()

	override suspend fun refreshSuppressed() = updateSuppressed()

	override suspend fun shiftForBurnIn() = window.shiftForBurnIn()

	override fun onUsageAccessMissing() {
		Log.w(TAG, "Usage access not granted - app blacklist inactive")
		LogStore.append(this, getString(R.string.log_usage_access_missing))
	}

	override fun ensureNotSuppressed() {
		if (suppressed) setSuppressedState(false)
	}

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
		poller.schedulePoll()
		poller.scheduleBandwidth()
		poller.scheduleForegroundPoll()
		poller.scheduleMobile()
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
		if (next.showMedia) mediaSessions.ensure(notifListenerComponent())
		poller.scheduleBandwidth()
		poller.scheduleBurnIn()
		poller.scheduleForegroundPoll()
		poller.scheduleMobile()
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
		indicators.release()
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

	/**
	 * Registers every framework listener as a collected flow. The collections
	 * live in [serviceScope], so destroy cancels them and [awaitClose] unregisters.
	 */
	private fun observeSystem() {
		// Sticky read first: the battery flow's first emission races refreshAll().
		val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
		if (sticky != null) {
			batteryPct =
				IndicatorFormats.batteryPct(
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
				if (barState.screenOn) reader.updateConnectivity()
			}
		}
		serviceScope.launch {
			networkFlow().collect {
				if (barState.screenOn) reader.updateConnectivity()
			}
		}
		serviceScope.launch {
			// DND / auto-rotate changes land here so the icons react immediately.
			systemSettingsFlow(handler).collect {
				if (barState.screenOn) reader.updateConnectivity()
			}
		}
		serviceScope.launch {
			torchFlow(handler).collect { (cameraId, enabled) ->
				if (enabled) {
					torchIds.add(cameraId)
				} else {
					torchIds.remove(cameraId)
				}
				if (barState.screenOn) reader.updateConnectivity()
			}
		}
	}

	private fun onBattery(intent: Intent) {
		val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
		val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
		val pct = IndicatorFormats.batteryPct(level, scale)
		val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
		batteryCharging =
			status == BatteryManager.BATTERY_STATUS_CHARGING ||
			status == BatteryManager.BATTERY_STATUS_FULL
		if (pct >= 0) {
			batteryPct = pct
			reader.updateBattery(batteryPct, batteryCharging)
		}
	}

	private suspend fun onScreen(intent: Intent) {
		when (intent.action) {
			Intent.ACTION_SCREEN_OFF -> {
				// The screen going off always puts the keyguard back up.
				barState = barState.copy(screenOn = false, locked = true)
				poller.cancelAll()
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
				poller.schedulePoll()
				poller.scheduleBandwidth()
				poller.scheduleBurnIn()
				poller.scheduleForegroundPoll()
				poller.scheduleMobile()
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
		if (barState.screenOn) reader.updateConnectivity()
	}

	private suspend fun refreshAll() {
		window.updateCamera()
		reader.updateBattery(batteryPct, batteryCharging)
		updateNotifs()
		reader.updateCalendar()
		updateMedia()
		reader.updateConnectivity()
		reader.updateAlarm()
	}

	override suspend fun refreshPolled() {
		reader.updateAlarm()
		reader.updateCalendar()
		reader.updateConnectivity()
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
		mediaSessions.ensure(notifListenerComponent())
		barState = barState.copy(mediaText = mediaSessions.nowPlaying.value)
	}
}
