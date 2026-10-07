package dev.yoanndev90.statusbarhider.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * The overlay window itself: owns the [ComposeView], its [WindowManager]
 * params and the synthetic lifecycle owners a window-hosted view needs
 * (without them `setContent` crashes — a window is not an Activity).
 *
 * The service drives it (attach / detach / burn-in timer) and reads the
 * resolved cutout geometry back through [cameraGeometry] whenever it changes.
 *
 * @param prefsFlow current prefs (flags depend on them)
 * @param content bar composition, written against the service state
 */
internal class OverlayWindow(
	private val context: Context,
	private val prefsFlow: StateFlow<OverlayPrefs>,
	/** Window type: APPLICATION_OVERLAY, or ACCESSIBILITY_OVERLAY from the lock-screen service. */
	private val windowType: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
	private val content: @Composable () -> Unit
) {
	companion object {
		private const val TAG = "CustomBar"

		/** Largest random burn-in offset: keeps the bar fully on screen. */
		private const val BURN_IN_MAX_DP = 8
	}

	var view: ComposeView? = null
		private set
	var params: WindowManager.LayoutParams? = null
		private set

	private val _cameraGeometry = MutableStateFlow<CameraGeometry?>(null)

	/** Resolved cutout geometry (null in auto mode); emits from [updateCamera]. */
	val cameraGeometry: StateFlow<CameraGeometry?> = _cameraGeometry.asStateFlow()

	private var lifecycleOwner: ServiceLifecycleOwner? = null
	private var viewModelStore: ViewModelStore? = null

	/** Last burn-in offset applied to [WindowManager.LayoutParams.y] (px). */
	private var burnInOffsetPx = 0

	/** True while the Compose owners are paused because the screen is off. */
	private var renderingPaused = false

	/**
	 * Window flags the overlay currently needs. [WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED]
	 * is what keeps the bar drawn above the keyguard (the Activity-only
	 * `setShowWhenLocked` does not exist for WindowManager-added views).
	 */
	@Suppress("DEPRECATION")
	private fun desiredFlags(): Int {
		val prefs = prefsFlow.value
		val lockFlag =
			if (prefs.showOnLockScreen) {
				WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
			} else {
				0
			}
		val touchFlag =
			if (prefs.interactive) {
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
					WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
					WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
			} else {
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
					WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
			}
		return WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
			WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
			lockFlag or
			touchFlag
	}

	fun attach() {
		if (view != null) return
		val wm = context.getSystemService(WindowManager::class.java) ?: return
		val params =
			WindowManager.LayoutParams(
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.WRAP_CONTENT,
				windowType,
				desiredFlags(),
				PixelFormat.TRANSLUCENT
			)
		params.gravity = Gravity.TOP
		if (Build.VERSION.SDK_INT >= 28) {
			params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
		}
		val view =
			ComposeView(context).apply {
				setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
				setContent(content)
			}
		attachComposeOwners(view)
		// An attach can happen while the screen is off (boot / restart):
		// don't leave the fresh owners resumed in that case.
		if (renderingPaused) lifecycleOwner?.pause()
		try {
			wm.addView(view, params)
			this.view = view
			this.params = params
		} catch (e: Exception) {
			Log.w(TAG, "addView failed (overlay permission?)", e)
			destroyComposeOwners()
			return
		}
		view.setOnApplyWindowInsetsListener { _, insets ->
			updateCamera()
			insets
		}
		view.post { updateCamera() }
	}

	/**
	 * Single attachment point, driven by the service: attaches (or re-applies
	 * the window flags when a toggle changed them) when [shouldAttach] is true,
	 * removes the whole window otherwise. Tearing the surface down — instead of
	 * leaving it behind the keyguard — means nothing is composed or painted
	 * while the bar has no reason to exist (app blacklist, keyguard up with
	 * the lock-screen toggle off).
	 */
	fun sync(shouldAttach: Boolean) {
		if (!shouldAttach) {
			detach()
			return
		}
		val p = params
		if (p == null) {
			attach()
		} else if (desiredFlags() != p.flags) {
			detach()
			attach()
		}
	}

	/**
	 * Pauses / resumes the synthetic lifecycle the ComposeView runs on: with
	 * the screen off the composition would otherwise keep living (and could
	 * still invalidate) for no visual result.
	 */
	fun setRenderingActive(active: Boolean) {
		if (renderingPaused == !active) return
		renderingPaused = !active
		val owner = lifecycleOwner ?: return
		if (active) {
			owner.resume()
		} else {
			owner.pause()
		}
	}

	fun detach() {
		val wm = context.getSystemService(WindowManager::class.java)
		val v = view
		if (wm != null && v != null) runSafely(context) { wm.removeView(v) }
		view = null
		params = null
		destroyComposeOwners()
	}

	/**
	 * Resolves the cutout geometry (auto-detect + user correction, or manual
	 * offsets) and publishes it on [cameraGeometry]; slot and progress ring
	 * both read it through the service.
	 */
	fun updateCamera() {
		val v = view ?: return
		val wm = context.getSystemService(WindowManager::class.java) ?: return
		val (width, height) = InsetsUtils.screenSize(wm, context.resources)
		_cameraGeometry.value = InsetsUtils.resolveCamera(v, width, height, prefsFlow.value)
	}

	/** OLED burn-in protection: moves the whole bar to a fresh random offset (0..8dp). */
	fun shiftForBurnIn() {
		val wm = context.getSystemService(WindowManager::class.java) ?: return
		val v = view ?: return
		val p = params ?: return
		runSafely(context) {
			val maxPx = (BURN_IN_MAX_DP * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
			var next: Int
			do {
				next = Random.nextInt(maxPx + 1)
			} while (next == burnInOffsetPx)
			burnInOffsetPx = next
			p.y = next
			wm.updateViewLayout(v, p)
		}
	}

	/** A WindowManager-hosted view has no Activity owners, so Compose is given synthetic ones. */
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
		lifecycleOwner = owner
		viewModelStore = store
	}

	private fun destroyComposeOwners() {
		lifecycleOwner?.let {
			it.pause()
			it.stop()
			it.destroy()
		}
		viewModelStore?.clear()
		lifecycleOwner = null
		viewModelStore = null
	}
}
