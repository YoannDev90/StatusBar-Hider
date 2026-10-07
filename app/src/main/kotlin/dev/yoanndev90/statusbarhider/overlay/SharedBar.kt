package dev.yoanndev90.statusbarhider.overlay

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * State shared inside the app process between [StatusBarOverlayService]
 * (which maintains it) and [LockScreenOverlayService] (which mirrors the bar
 * while the keyguard is up, where a TYPE_APPLICATION_OVERLAY is hidden by the
 * window policy).
 */
internal object SharedBar {
	val barState: MutableState<OverlayBarState> = mutableStateOf(OverlayBarState())

	/** True while the bar is detached because a blacklisted app is up front. */
	val suppressed: MutableState<Boolean> = mutableStateOf(false)
}
