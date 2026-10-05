package dev.yoanndev90.statusbarhider.data

import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/** Shizuku connection + authorization state. */
enum class ShizukuState {
	NOT_RUNNING,
	NOT_GRANTED,
	READY
}

/**
 * Single source of truth for the Shizuku state.
 *
 * Permission results are delivered through [Shizuku.addRequestPermissionResultListener],
 * which is the only dispatch channel in Shizuku API 13.x: the result never reaches
 * `Activity.onRequestPermissionsResult`, so the previous override was dead code.
 *
 * The three Shizuku listeners are registered once at construction: the repo is a
 * process singleton, so there is no owner to unregister from, and the sticky
 * binder listener covers an already-connected Shizuku.
 */
class ShizukuRepository private constructor() {
	private val _state = MutableStateFlow(currentState())
	val state: StateFlow<ShizukuState> = _state.asStateFlow()

	private val binderListener = Shizuku.OnBinderReceivedListener { refresh() }
	private val deadListener = Shizuku.OnBinderDeadListener { _state.value = ShizukuState.NOT_RUNNING }
	private val permissionListener =
		Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

	init {
		Shizuku.addBinderReceivedListenerSticky(binderListener)
		Shizuku.addBinderDeadListener(deadListener)
		Shizuku.addRequestPermissionResultListener(permissionListener)
		refresh()
	}

	/** Recomputes the state from the live binder. Returns the new state. */
	fun refresh(): ShizukuState {
		val next = currentState()
		_state.value = next
		return next
	}

	/**
	 * Shows the Shizuku authorization dialog. The result is delivered through
	 * [permissionListener] above, so the request code is an opaque identifier
	 * owned by this class.
	 */
	fun requestPermission() {
		Shizuku.requestPermission(REQUEST_CODE)
	}

	companion object {
		private const val REQUEST_CODE = 1001

		private fun currentState(): ShizukuState =
			when {
				!Shizuku.pingBinder() -> ShizukuState.NOT_RUNNING
				!ShellRunner.granted() -> ShizukuState.NOT_GRANTED
				else -> ShizukuState.READY
			}

		@Volatile
		private var instance: ShizukuRepository? = null

		fun getInstance(): ShizukuRepository =
			instance ?: synchronized(this) {
				instance ?: ShizukuRepository().also { instance = it }
			}
	}
}
