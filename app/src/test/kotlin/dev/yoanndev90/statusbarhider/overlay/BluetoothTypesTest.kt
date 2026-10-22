package dev.yoanndev90.statusbarhider.overlay

import android.bluetooth.BluetoothClass
import dev.yoanndev90.statusbarhider.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothTypesTest {
	@Test
	fun bondedListYieldsMacToNameAndStripsTheOemSuffix() {
		val dump =
			"""
			Bonded devices:
			AA:BB:CC:04:DC:E9 [ DUAL ][ 0x001F00 ] Xiaomi Watch S3 DCE9
			11:22:33:04:DA:C4 [ DUAL ][ 0x001F00 ] JBL Endurance Peak 4
			44:55:66:77:88:99 [ DUAL ][ 0x240404 ] Some Speaker
			""".trimIndent()
		val names = parseBondedNames(dump)
		assertEquals("Xiaomi Watch S3", names["AA:BB:CC:04:DC:E9"])
		assertEquals("JBL Endurance Peak 4", names["11:22:33:04:DA:C4"])
		assertEquals("Some Speaker", names["44:55:66:77:88:99"])
	}

	@Test
	fun fastPairMetadataYieldsModelToDeviceType() {
		val dump =
			"XX:XX:XX:XX:DA:C4 last_active_time=132 {profile connection policy(A2DP=100), " +
				"optional codec(support=1), isActiveHfpDevice (false), custom metadata(" +
				"manufacturer_name=Harman International Industries, Inc.|model_name=JBL Endurance Peak 4|" +
				"software_version=0.26.13.10|device_type=Untethered Headset|main_icon=null)}"
		assertEquals("Untethered Headset", parseFastPairDeviceTypes(dump)["jbl endurance peak 4"])
	}

	@Test
	fun absentOrNullDeviceTypeIsIgnored() {
		assertEquals(emptyMap<String, String>(), parseFastPairDeviceTypes("custom metadata(model_name=victus|device_type=null)"))
		assertEquals(emptyMap<String, String>(), parseFastPairDeviceTypes("custom metadata(device_type=Untethered Headset)"))
	}

	@Test
	fun deviceTypeNamesMapToKinds() {
		assertEquals(BtDeviceKind.EARBUDS, kindFromTypeName("Untethered Headset"))
		assertEquals(BtDeviceKind.EARBUDS, kindFromTypeName("Headphone"))
		assertEquals(BtDeviceKind.WATCH, kindFromTypeName("Smart Watch"))
		assertEquals(BtDeviceKind.SPEAKER, kindFromTypeName("Speaker"))
		assertEquals(BtDeviceKind.GAMEPAD, kindFromTypeName("Game Controller"))
		assertNull(kindFromTypeName("Uncategorized"))
	}

	@Test
	fun declaredClassesForCarsPhonesAndComputersMapToKinds() {
		assertEquals(BtDeviceKind.CAR, kindFromClass(BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO))
		assertEquals(BtDeviceKind.PHONE, kindFromClass(BluetoothClass.Device.PHONE_CELLULAR))
		assertEquals(BtDeviceKind.COMPUTER, kindFromClass(BluetoothClass.Device.COMPUTER_LAPTOP))
		assertEquals(BtDeviceKind.COMPUTER, kindFromClass(BluetoothClass.Device.COMPUTER_DESKTOP))
		// A tablet declares no class of its own; keep the uncategorized computer generic.
		assertEquals(BtDeviceKind.UNKNOWN, kindFromClass(BluetoothClass.Device.TOY_UNCATEGORIZED))
	}

	@Test
	fun serviceBitsInTheCoDDoNotHideTheDeclaredClass() {
		// 0x7C010C = service-class bits + COMPUTER_LAPTOP, exactly what the victus declares.
		assertEquals(BtDeviceKind.COMPUTER, kindFromClass(0x7C010C))
	}

	@Test
	fun menuLabelsDropTheBluetoothPrefix() {
		BtDeviceKind.entries.filter { it != BtDeviceKind.UNKNOWN }.forEach { kind ->
			assertNotEquals(0, kind.menuLabel())
		}
		assertEquals(R.string.bt_override_generic, BtDeviceKind.UNKNOWN.menuLabel())
	}

	@Test
	fun everyKindCarriesAnIconAndALabel() {
		BtDeviceKind.entries.forEach { kind ->
			assertNotEquals(0, kind.icon().icon)
			assertNotEquals(0, kind.icon().label)
		}
	}

	@Test
	fun userOverrideWinsOverEveryAutomaticSignal() {
		val overrides = mapOf("AA:BB:CC:DD:EE:FF" to "SPEAKER")
		val precise = BtDeviceType(BtDeviceKind.WATCH, BtSource.FAST_PAIR)
		val resolved = resolveKind("aa:bb:cc:dd:ee:ff", overrides, precise, BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES)
		assertEquals(BtDeviceKind.SPEAKER, resolved.kind)
		assertEquals(BtSource.OVERRIDE, resolved.source)
	}

	@Test
	fun automaticChainFallsBackFromPreciseToDeclaredClass() {
		val precise = BtDeviceType(BtDeviceKind.EARBUDS, BtSource.FAST_PAIR)
		assertEquals(BtSource.FAST_PAIR, resolveKind(null, emptyMap(), precise, null).source)
		val fromClass = resolveKind(null, emptyMap(), null, BluetoothClass.Device.WEARABLE_WRIST_WATCH)
		assertEquals(BtDeviceKind.WATCH, fromClass.kind)
		assertEquals(BtSource.CLASS, fromClass.source)
	}

	@Test
	fun unknownOverrideNamesAreIgnored() {
		assertNull(kindFromOverrideName(null))
		assertNull(kindFromOverrideName(""))
		assertNull(kindFromOverrideName("SPKR"))
		assertEquals(BtDeviceKind.UNKNOWN, kindFromOverrideName("UNKNOWN"))
	}
}
