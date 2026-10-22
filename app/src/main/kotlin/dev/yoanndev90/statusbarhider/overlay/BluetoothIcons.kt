package dev.yoanndev90.statusbarhider.overlay

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One connected Bluetooth device, ready to draw: icon plus content description. */
data class BtDeviceIcon(
	val icon: Int,
	val label: Int
)

/** The declared class mapped through [kindFromClass]; kept as the thin fallback entry point. */
internal fun btDeviceIcon(deviceClass: Int?): BtDeviceIcon = kindFromClass(deviceClass).icon()

/**
 * Profiles carrying the device types worth an icon: audio, HID (keyboards,
 * mice), hearing aids and LE Audio. One device often answers several
 * profiles, so the read dedupes by address.
 */
private val ICON_PROFILES =
	intArrayOf(
		BluetoothProfile.HEADSET,
		BluetoothProfile.A2DP,
		// HID_HOST (4) is @SystemApi, not in the public SDK, but the value is
		// stable across releases and keyboards / mice only answer that profile.
		4,
		BluetoothProfile.HEARING_AID,
		BluetoothProfile.LE_AUDIO
	)

/**
 * Connected devices, read through long-lived per-profile service proxies.
 *
 * `BluetoothManager.getConnectedDevices()` refuses every profile with
 * "Profile not supported" on some OEM builds (seen on HyperOS 2 / Android 15),
 * so the same list is pulled from the profile services directly: no profile
 * allow-list on that binder path, and the data is identical.
 *
 * The proxies bind asynchronously on the first read and stay bound until
 * [release]. A poll racing a dead proxy (BT toggled off) drops it and the
 * next cycle rebinds.
 */
internal class BluetoothProfiles(
	private val context: Context,
	private val warn: (String, Exception) -> Unit
) : BluetoothProfile.ServiceListener {
	// Lazy: service field initializers run before ContextWrapper.attach()
	// sets the base context, so getSystemService must not fire in <init>.
	private val adapter by lazy { context.getSystemService(BluetoothManager::class.java)?.adapter }
	private val proxies = ConcurrentHashMap<Int, BluetoothProfile>()
	private val requested = ConcurrentHashMap.newKeySet<Int>()

	/** Released once: late bind results are closed instead of kept. */
	@Volatile
	private var released = false

	/** Unblocks the first read once any proxy answered the bind. */
	private val firstBind = CountDownLatch(1)
	private val firstRead = AtomicBoolean(true)

	/** Connected devices across [ICON_PROFILES], deduped by address. */
	fun connectedDevices(): List<BluetoothDevice> {
		if (released) return emptyList()
		requestMissing()
		// The very first read waits out the binder bind so the icons show on
		// the first poll instead of one cycle late. Off-main thread only.
		if (firstRead.compareAndSet(true, false)) {
			try {
				firstBind.await(1, TimeUnit.SECONDS)
			} catch (e: InterruptedException) {
				Thread.currentThread().interrupt()
			}
		}
		val devices = LinkedHashSet<BluetoothDevice>()
		for ((profile, proxy) in proxies) {
			try {
				devices.addAll(proxy.connectedDevices)
			} catch (e: Exception) {
				// Service died with the stack (BT toggled): drop, next poll rebinds.
				drop(profile, proxy)
				warn("btProfiles#$profile", e)
			}
		}
		return devices.toList()
	}

	/** Unbinds every proxy. The overlay service calls this on shutdown. */
	fun release() {
		released = true
		for ((profile, proxy) in proxies) drop(profile, proxy)
		requested.clear()
	}

	private fun requestMissing() {
		val adapter = adapter ?: return
		for (profile in ICON_PROFILES) {
			if (!requested.add(profile)) continue
			try {
				if (!adapter.getProfileProxy(context, this, profile)) {
					// Profile unknown to this stack: retry next poll is pointless,
					// but the flag must not stick either in case BT was mid-toggle.
					requested.remove(profile)
				}
			} catch (e: Exception) {
				requested.remove(profile)
				warn("btProfileBind#$profile", e)
			}
		}
	}

	private fun drop(
		profile: Int,
		proxy: BluetoothProfile
	) {
		proxies.remove(profile, proxy)
		requested.remove(profile)
		try {
			adapter?.closeProfileProxy(profile, proxy)
		} catch (e: Exception) {
			warn("btProfileClose#$profile", e)
		}
	}

	override fun onServiceConnected(
		profile: Int,
		proxy: BluetoothProfile
	) {
		if (released) {
			// release() raced the bind: close the orphan proxy right away.
			try {
				adapter?.closeProfileProxy(profile, proxy)
			} catch (e: Exception) {
				warn("btProfileClose#$profile", e)
			}
			return
		}
		proxies[profile] = proxy
		firstBind.countDown()
	}

	override fun onServiceDisconnected(profile: Int) {
		proxies.remove(profile)?.let { drop(profile, it) }
		requested.remove(profile)
	}
}

/**
 * One icon per connected device, read fresh on every poll through
 * [SystemIndicators.btTypes]. The precise kind (Fast Pair metadata, then
 * the device name) wins over the declared class, which most TWS leave
 * uncategorized. Empty without BLUETOOTH_CONNECT (Android 12+, denied at
 * runtime): the bar then keeps the plain Bluetooth glyph, same as before
 * this feature existed.
 */
internal suspend fun SystemIndicators.connectedBluetoothIcons(): List<BtDeviceIcon> {
	if (Build.VERSION.SDK_INT >= 31 &&
		context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
	) {
		return emptyList()
	}
	return try {
		val devices = btProfiles.connectedDevices()
		val precise = btTypes.kindFor(devices)
		// LinkedHashSet: stable order across polls, one entry per device class.
		val icons = LinkedHashSet<BtDeviceIcon>()
		for (device in devices) {
			val mac = device.address?.uppercase(Locale.ROOT)
			val kind = precise[mac] ?: kindFromClass(device.bluetoothClass?.deviceClass)
			icons.add(kind.icon())
		}
		icons.toList()
	} catch (e: Exception) {
		warn("connectedBluetooth", e)
		emptyList()
	}
}
