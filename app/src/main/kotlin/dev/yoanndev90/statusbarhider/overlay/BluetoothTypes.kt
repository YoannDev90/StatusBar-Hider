package dev.yoanndev90.statusbarhider.overlay

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import kotlinx.coroutines.CancellationException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** One device kind the bar can draw, whichever signal named it. */
internal enum class BtDeviceKind {
	EARBUDS,
	HEADSET,
	SPEAKER,
	KEYBOARD,
	MOUSE,
	WATCH,
	GAMEPAD,
	PHONE,
	CAR,
	TABLET,
	COMPUTER,
	UNKNOWN;

	/** Drawable plus content description for the overlay bar. */
	fun icon(): BtDeviceIcon =
		when (this) {
			EARBUDS -> BtDeviceIcon(R.drawable.ic_bt_earbuds, R.string.cd_bluetooth_earbuds)
			HEADSET -> BtDeviceIcon(R.drawable.ic_bt_headset, R.string.cd_bluetooth_headset)
			SPEAKER -> BtDeviceIcon(R.drawable.ic_bt_speaker, R.string.cd_bluetooth_speaker)
			KEYBOARD -> BtDeviceIcon(R.drawable.ic_bt_keyboard, R.string.cd_bluetooth_keyboard)
			MOUSE -> BtDeviceIcon(R.drawable.ic_bt_mouse, R.string.cd_bluetooth_mouse)
			WATCH -> BtDeviceIcon(R.drawable.ic_bt_watch, R.string.cd_bluetooth_watch)
			GAMEPAD -> BtDeviceIcon(R.drawable.ic_bt_gamepad, R.string.cd_bluetooth_gamepad)
			PHONE -> BtDeviceIcon(R.drawable.ic_bt_phone, R.string.cd_bluetooth_phone)
			CAR -> BtDeviceIcon(R.drawable.ic_bt_car, R.string.cd_bluetooth_car)
			TABLET -> BtDeviceIcon(R.drawable.ic_bt_tablet, R.string.cd_bluetooth_tablet)
			COMPUTER -> BtDeviceIcon(R.drawable.ic_bt_computer, R.string.cd_bluetooth_computer)
			UNKNOWN -> BtDeviceIcon(R.drawable.ic_bt, R.string.cd_bluetooth)
		}

	/**
	 * Label for the override picker: short, no "Bluetooth" prefix, the menu
	 * lives inside the Bluetooth settings and the prefix reads as noise there.
	 */
	fun menuLabel(): Int =
		when (this) {
			EARBUDS -> R.string.bt_kind_earbuds
			HEADSET -> R.string.bt_kind_headset
			SPEAKER -> R.string.bt_kind_speaker
			KEYBOARD -> R.string.bt_kind_keyboard
			MOUSE -> R.string.bt_kind_mouse
			WATCH -> R.string.bt_kind_watch
			GAMEPAD -> R.string.bt_kind_gamepad
			PHONE -> R.string.bt_kind_phone
			CAR -> R.string.bt_kind_car
			TABLET -> R.string.bt_kind_tablet
			COMPUTER -> R.string.bt_kind_computer
			UNKNOWN -> R.string.bt_override_generic
		}
}

/**
 * Class-of-device field width: format type (2 bits) + minor (6) + major (5).
 * The stack may hand back the raw 24-bit CoD (0x7C010C for a laptop) with
 * the service-class bits above, which no SDK constant carries, so mask first.
 */
private const val DEVICE_CLASS_MASK = 0x1FFF

/** What the device declares through its Bluetooth class (self-declared, often uncategorized). */
internal fun kindFromClass(deviceClass: Int?): BtDeviceKind =
	when (deviceClass?.and(DEVICE_CLASS_MASK)) {
		BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
		BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> BtDeviceKind.EARBUDS
		BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
		BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE -> BtDeviceKind.HEADSET
		BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
		BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
		BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> BtDeviceKind.SPEAKER
		BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> BtDeviceKind.CAR
		BluetoothClass.Device.PERIPHERAL_KEYBOARD,
		BluetoothClass.Device.PERIPHERAL_KEYBOARD_POINTING -> BtDeviceKind.KEYBOARD
		BluetoothClass.Device.PERIPHERAL_POINTING -> BtDeviceKind.MOUSE
		BluetoothClass.Device.WEARABLE_WRIST_WATCH -> BtDeviceKind.WATCH
		BluetoothClass.Device.TOY_GAME,
		BluetoothClass.Device.TOY_CONTROLLER -> BtDeviceKind.GAMEPAD
		BluetoothClass.Device.PHONE_SMART,
		BluetoothClass.Device.PHONE_CELLULAR,
		BluetoothClass.Device.PHONE_CORDLESS -> BtDeviceKind.PHONE
		BluetoothClass.Device.COMPUTER_UNCATEGORIZED,
		BluetoothClass.Device.COMPUTER_DESKTOP,
		BluetoothClass.Device.COMPUTER_SERVER,
		BluetoothClass.Device.COMPUTER_LAPTOP,
		BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA,
		BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA,
		BluetoothClass.Device.COMPUTER_WEARABLE -> BtDeviceKind.COMPUTER
		else -> BtDeviceKind.UNKNOWN
	}

/** Fast Pair `device_type` value to kind ("Untethered Headset", "Smart Watch"...). */
internal fun kindFromTypeName(type: String): BtDeviceKind? {
	val t = type.lowercase(Locale.ROOT)
	return when {
		"watch" in t -> BtDeviceKind.WATCH
		"speaker" in t -> BtDeviceKind.SPEAKER
		"controller" in t || "gamepad" in t -> BtDeviceKind.GAMEPAD
		"keyboard" in t -> BtDeviceKind.KEYBOARD
		"mouse" in t || "pointing" in t -> BtDeviceKind.MOUSE
		"headset" in t || "headphone" in t || "earbud" in t || "earphone" in t || "untethered" in t -> BtDeviceKind.EARBUDS
		"phone" in t -> BtDeviceKind.PHONE
		else -> null
	}
}

/** Which signal named a device kind. */
internal enum class BtSource {
	OVERRIDE,
	FAST_PAIR,
	CLASS
}

/** One resolved device: the kind to draw plus the signal that picked it. */
internal data class BtDeviceType(
	val kind: BtDeviceKind,
	val source: BtSource
)

/** One connected device as the settings preview shows it. */
internal data class BtDevicePreview(
	val mac: String,
	val name: String,
	val kind: BtDeviceKind,
	val source: BtSource
)

/** Stored override ("SPEAKER"...) to kind; unknown or blank names mean no override. */
internal fun kindFromOverrideName(name: String?): BtDeviceKind? = BtDeviceKind.entries.firstOrNull { it.name == name }

/**
 * The full resolution chain for one device: the user override wins, then
 * whatever the stack knew (Fast Pair metadata), then the declared class.
 */
internal fun resolveKind(
	mac: String?,
	overrides: Map<String, String>,
	precise: BtDeviceType?,
	deviceClass: Int?
): BtDeviceType {
	val override = mac?.uppercase(Locale.ROOT)?.let { kindFromOverrideName(overrides[it]) }
	if (override != null) return BtDeviceType(override, BtSource.OVERRIDE)
	precise?.let { return it }
	return BtDeviceType(kindFromClass(deviceClass), BtSource.CLASS)
}

/** `AA:BB:..[ DUAL ][ 0xXXXXXX ] Some Name` lines of the bonded list (section-indented, OEMs may append the last octets to the name). */
private val BONDED_LINE =
	Regex("""^\s*([0-9A-Fa-f:]{17}) \[ \w+ \]\[ 0x[0-9A-Fa-f]+ \] (.+)$""")

private val METADATA_BLOCK =
	Regex("""custom metadata\(([^)]*)\)""")

private val METADATA_FIELD =
	Regex("""(\w+)=([^|]*)""")

/** MAC (uppercased) to name, from every bonded-list line of the dump. */
internal fun parseBondedNames(dump: String): Map<String, String> =
	buildMap {
		for (line in dump.lineSequence()) {
			val match = BONDED_LINE.find(line) ?: continue
			val mac = match.groupValues[1].uppercase(Locale.ROOT)
			var name = match.groupValues[2].trim()
			// "Xiaomi Watch S3 DCE9" on a C4:...:DC:E9 device: drop the tail copy.
			val suffix = mac.takeLast(5).take(2) + mac.takeLast(2)
			if (name.length > 5 && name.endsWith(" $suffix", ignoreCase = true)) {
				name = name.dropLast(5)
			}
			put(mac, name)
		}
	}

/** Lowercased model name to Fast Pair `device_type`, from the per-device metadata blocks. */
internal fun parseFastPairDeviceTypes(dump: String): Map<String, String> =
	buildMap {
		for (block in METADATA_BLOCK.findAll(dump)) {
			var model: String? = null
			var type: String? = null
			for (field in METADATA_FIELD.findAll(block.groupValues[1])) {
				when (field.groupValues[1]) {
					"model_name" -> model = field.groupValues[2]
					"device_type" -> type = field.groupValues[2]
				}
			}
			val t = type?.takeIf { it.isNotBlank() && it != "null" }
			if (model != null && model != "null" && t != null) {
				put(model.lowercase(Locale.ROOT), t)
			}
		}
	}

/**
 * Precise device kinds behind the self-declared Bluetooth class.
 *
 * Fast Pair metadata (device type, model, battery) is stored by the Bluetooth
 * stack itself, whichever provider filled it - Google GMS, microG's Nearby
 * module, an OEM implementation. None of them expose a public lookup API, so
 * the store is read through `dumpsys bluetooth_manager` (Shizuku): the data
 * is the providers', the reading path is provider-agnostic by construction.
 *
 * Reads are cached: a resolved MAC stays resolved for the session, a dump is
 * re-run at most once when something new is connected, plus a slow full pass
 * for late metadata writes. Without a Shizuku grant the whole path stays
 * silent and the bar falls back to the declared class.
 */
internal class BluetoothTypes(
	private val context: Context,
	private val warn: (String, Exception) -> Unit
) {
	companion object {
		private const val DUMP_CMD = "dumpsys bluetooth_manager"
		private const val TIMEOUT_SEC = 15L

		/** A device no dump has settled yet is retried on this cycle... */
		private const val REFRESH_NEW_MS = 2 * 60_000L

		/** ...and the full pass catches metadata written long after pairing. */
		private const val REFRESH_ALL_MS = 10 * 60_000L
	}

	private val types = ConcurrentHashMap<String, BtDeviceType>()

	/** MACs one dump has already covered, match or no match. */
	private val settled = ConcurrentHashMap.newKeySet<String>()

	@Volatile
	private var lastReadAt = 0L

	/** Precise types for [devices], keyed by uppercased MAC; missing entries mean "use the declared class". */
	suspend fun typesFor(devices: List<BluetoothDevice>): Map<String, BtDeviceType> {
		if (devices.isEmpty() || !ShellRunner.granted()) return emptyMap()
		val unseen =
			devices.any { device ->
				device.address?.uppercase(Locale.ROOT)?.let { it !in settled } ?: true
			}
		val now = System.currentTimeMillis()
		if (now - lastReadAt >= (if (unseen) REFRESH_NEW_MS else REFRESH_ALL_MS)) {
			read()
		}
		return buildMap {
			for (device in devices) {
				val mac = device.address?.uppercase(Locale.ROOT) ?: continue
				types[mac]?.let { put(mac, it) }
			}
		}
	}

	/**
	 * Full preview of [devices]: name, kind and source through the whole
	 * override chain. Empty without BLUETOOTH_CONNECT, like the bar read.
	 */
	suspend fun resolve(
		devices: List<BluetoothDevice>,
		overrides: Map<String, String>
	): List<BtDevicePreview> {
		if (Build.VERSION.SDK_INT >= 31 &&
			context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
		) {
			return emptyList()
		}
		val precise = typesFor(devices)
		return devices.map { device ->
			val mac = device.address?.uppercase(Locale.ROOT)
			val type = resolveKind(mac, overrides, precise[mac], device.bluetoothClass?.deviceClass)
			BtDevicePreview(mac = mac.orEmpty(), name = device.name ?: mac.orEmpty(), kind = type.kind, source = type.source)
		}
	}

	/** One dumpsys pass through the serialized shell. */
	private suspend fun read() {
		lastReadAt = System.currentTimeMillis()
		val res =
			try {
				ShellRunner.run(context, DUMP_CMD, TIMEOUT_SEC)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				warn("btTypes", e)
				return
			}
		if (res.exit != 0) return
		val fastPair = parseFastPairDeviceTypes(res.out)
		for ((mac, name) in parseBondedNames(res.out)) {
			val kind = fastPair[name.lowercase(Locale.ROOT)]?.let { kindFromTypeName(it) }
			if (kind != null) {
				types[mac] = BtDeviceType(kind, BtSource.FAST_PAIR)
			}
			settled.add(mac)
		}
	}
}
