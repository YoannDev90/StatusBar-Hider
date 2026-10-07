package dev.yoanndev90.statusbarhider.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Second window for the custom status bar, hosted as a
 * [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] (window layer 31):
 * the window policy hides TYPE_APPLICATION_OVERLAY windows behind the keyguard
 * (mPolicyVisibility=false), but layer 31 sits above the NotificationShade,
 * which is what sameerasw/essentials does for its lock-screen bar.
 *
 * Renders only while the keyguard is up; the regular overlay keeps handling
 * the unlocked case (and everything else the bar does).
 */
class LockScreenOverlayService : AccessibilityService() {
	/** Lazy: the base Context only exists after Service.attach(), unlike during construction. */
	private val prefsRepo by lazy { OverlayPrefsRepository.getInstance(this) }
	private val prefsState = mutableStateOf(OverlayPrefs())
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private var window: OverlayWindow? = null

	override fun onServiceConnected() {
		super.onServiceConnected()
		val w =
			OverlayWindow(
				context = this,
				prefsFlow = prefsRepo.state,
				windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
				content = {
					OverlayBar(
						prefs = prefsState.value,
						state = SharedBar.barState.value,
						onClockClick = ::onClockClick,
						onDateClick = ::onDateClick
					)
				}
			)
		window = w
		scope.launch { prefsRepo.state.collect { prefsState.value = it } }
		scope.launch {
			snapshotFlow { shouldAttach() }
				.distinctUntilChanged()
				.collect { w.sync(it) }
		}
		scope.launch {
			snapshotFlow { SharedBar.barState.value.screenOn }
				.distinctUntilChanged()
				.collect { w.setRenderingActive(it) }
		}
	}

	private fun shouldAttach(): Boolean {
		val s = SharedBar.barState.value
		return prefsState.value.enabled &&
			prefsState.value.showOnLockScreen &&
			s.locked &&
			s.screenOn &&
			!SharedBar.suppressed.value
	}

	private fun onClockClick() {
		if (!prefsState.value.interactive) return
		runSafely(this) {
			startActivity(
				Intent(AlarmClock.ACTION_SHOW_ALARMS)
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			)
		}
	}

	private fun onDateClick() {
		if (!prefsState.value.interactive) return
		runSafely(this) {
			try {
				startActivity(
					Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android/calendar/time"))
						.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				)
			} catch (_: Exception) {
				packageManager
					.getLaunchIntentForPackage("com.google.android.calendar")
					?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
			}
		}
	}

	override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

	override fun onInterrupt() = Unit

	override fun onDestroy() {
		scope.cancel()
		window?.detach()
		window = null
		super.onDestroy()
	}
}
