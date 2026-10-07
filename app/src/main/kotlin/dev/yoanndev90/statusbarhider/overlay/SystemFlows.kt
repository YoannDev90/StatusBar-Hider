package dev.yoanndev90.statusbarhider.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/*
 * The system signals the overlay listens to, as cold flows.
 *
 * Each one registers on collection and unregisters when the collection is
 * cancelled, so cancelling the collector is the whole teardown: a screen-off
 * pass leaves no receiver, observer or callback behind.
 */

/** Registers a receiver for [filter]; the collection's cancel unregisters it. */
internal fun Context.receiverFlow(filter: IntentFilter): Flow<Intent> =
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
		// RECEIVER_EXPORTED, not NOT_EXPORTED: these actions all come from
		// system_server, and Android documents that NOT_EXPORTED drops
		// broadcasts from highly privileged apps that do not run under the
		// system UID. Without a flag the framework already forces EXPORTED
		// for protected broadcasts, so this is the same behaviour made
		// explicit — which is what Android 14+ enforcement and lint want.
		runSafely(this@receiverFlow) {
			ContextCompat.registerReceiver(
				this@receiverFlow,
				receiver,
				filter,
				ContextCompat.RECEIVER_EXPORTED
			)
		}
		awaitClose { runSafely(this@receiverFlow) { unregisterReceiver(receiver) } }
	}

/** zen_mode + auto-rotate changes; the collection's cancel unregisters it. */
internal fun Context.systemSettingsFlow(handler: Handler): Flow<Unit> =
	callbackFlow {
		val observer =
			object : ContentObserver(handler) {
				override fun onChange(selfChange: Boolean) {
					trySend(Unit)
				}
			}
		runSafely(this@systemSettingsFlow) {
			contentResolver.registerContentObserver(Settings.Global.getUriFor("zen_mode"), false, observer)
			contentResolver.registerContentObserver(
				Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
				false,
				observer
			)
		}
		awaitClose { runSafely(this@systemSettingsFlow) { contentResolver.unregisterContentObserver(observer) } }
	}

/** Torch on/off per camera id; the collection's cancel unregisters it. */
internal fun Context.torchFlow(handler: Handler): Flow<Pair<String, Boolean>> =
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
		awaitClose { runSafely(this@torchFlow) { cm.unregisterTorchCallback(cb) } }
	}

/** Default network changes; the collection's cancel unregisters it. */
internal fun Context.networkFlow(): Flow<Unit> =
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
		runSafely(this@networkFlow) { cm?.registerDefaultNetworkCallback(cb) }
		awaitClose { runSafely(this@networkFlow) { cm?.unregisterNetworkCallback(cb) } }
	}
