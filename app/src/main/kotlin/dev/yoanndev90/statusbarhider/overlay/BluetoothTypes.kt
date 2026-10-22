package dev.yoanndev90.statusbarhider.overlay

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Context
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
			UNKNOWN -> BtDeviceIcon(R.drawable.ic_bt, R.string.cd_bluetooth)
		}
}

/** What the device declares through its Bluetooth class (self-declared, often uncategorized). */
internal fun kindFromClass(deviceClass: Int?): BtDeviceKind =
	when (deviceClass) {
		BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
		BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> BtDeviceKind.EARBUDS
		BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
		BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE -> BtDeviceKind.HEADSET
		BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
		BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
		BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> BtDeviceKind.SPEAKER
		BluetoothClass.Device.PERIPHERAL_KEYBOARD,
		BluetoothClass.Device.PERIPHERAL_KEYBOARD_POINTING -> BtDeviceKind.KEYBOARD
		BluetoothClass.Device.PERIPHERAL_POINTING -> BtDeviceKind.MOUSE
		BluetoothClass.Device.WEARABLE_WRIST_WATCH -> BtDeviceKind.WATCH
		BluetoothClass.Device.TOY_GAME,
		BluetoothClass.Device.TOY_CONTROLLER -> BtDeviceKind.GAMEPAD
		BluetoothClass.Device.PHONE_SMART -> BtDeviceKind.PHONE
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

/**
 * Conservative keyword table for devices no provider described: speaker
 * words come first so a "JBL Charge" stays a speaker while a
 * "JBL Endurance" resolves as earbuds.
 */
private val NAME_KINDS =
	listOf(
		"watch" to BtDeviceKind.WATCH,
		"montre" to BtDeviceKind.WATCH,
		"speaker" to BtDeviceKind.SPEAKER,
		"enceinte" to BtDeviceKind.SPEAKER,
		"boom" to BtDeviceKind.SPEAKER,
		"flip" to BtDeviceKind.SPEAKER,
		"xtreme" to BtDeviceKind.SPEAKER,
		"partybox" to BtDeviceKind.SPEAKER,
		"charge" to BtDeviceKind.SPEAKER,
		"buds" to BtDeviceKind.EARBUDS,
		"earbud" to BtDeviceKind.EARBUDS,
		"headphone" to BtDeviceKind.EARBUDS,
		"headset" to BtDeviceKind.EARBUDS,
		"airpods" to BtDeviceKind.EARBUDS,
		"freebuds" to BtDeviceKind.EARBUDS,
		"enco" to BtDeviceKind.EARBUDS,
		"jbl" to BtDeviceKind.EARBUDS,
		"soundcore" to BtDeviceKind.EARBUDS,
		"wh-" to BtDeviceKind.EARBUDS,
		"qc" to BtDeviceKind.EARBUDS,
		"keyboard" to BtDeviceKind.KEYBOARD,
		"clavier" to BtDeviceKind.KEYBOARD,
		"mouse" to BtDeviceKind.MOUSE,
		"souris" to BtDeviceKind.MOUSE,
		"controller" to BtDeviceKind.GAMEPAD,
		"manette" to BtDeviceKind.GAMEPAD,
		"gamepad" to BtDeviceKind.GAMEPAD
	)

/** Device name to kind, last resort before the declared class. */
internal fun kindFromName(name: String): BtDeviceKind? {
	val n = name.lowercase(Locale.ROOT)
	return NAME_KINDS.firstNotNullOfOrNull { (keyword, kind) -> if (n.contains(keyword)) kind else null }
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

	private val kinds = ConcurrentHashMap<String, BtDeviceKind>()

	/** MACs one dump has already covered, match or no match. */
	private val settled = ConcurrentHashMap.newKeySet<String>()

	@Volatile
	private var lastReadAt = 0L

	/** Precise kinds for [devices], keyed by uppercased MAC; missing entries mean "use the declared class". */
	suspend fun kindFor(devices: List<BluetoothDevice>): Map<String, BtDeviceKind> {
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
				kinds[mac]?.let { put(mac, it) }
			}
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
		val types = parseFastPairDeviceTypes(res.out)
		for ((mac, name) in parseBondedNames(res.out)) {
			val kind = types[name.lowercase(Locale.ROOT)]?.let { kindFromTypeName(it) } ?: kindFromName(name)
			if (kind != null) kinds[mac] = kind
			settled.add(mac)
		}
	}
}
