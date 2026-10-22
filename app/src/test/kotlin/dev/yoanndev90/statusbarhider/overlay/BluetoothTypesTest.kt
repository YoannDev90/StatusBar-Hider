package dev.yoanndev90.statusbarhider.overlay

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
	fun deviceNamesMapToKindsAsLastResort() {
		assertEquals(BtDeviceKind.WATCH, kindFromName("Xiaomi Watch S3"))
		assertEquals(BtDeviceKind.EARBUDS, kindFromName("JBL Endurance Peak 4"))
		// Speaker words win over the JBL brand, which covers both families.
		assertEquals(BtDeviceKind.SPEAKER, kindFromName("JBL Charge 5"))
		assertEquals(BtDeviceKind.EARBUDS, kindFromName("Sony WH-1000XM5"))
		assertNull(kindFromName("Oglo# Loops 4"))
		assertNull(kindFromName("victus"))
	}

	@Test
	fun everyKindCarriesAnIconAndALabel() {
		BtDeviceKind.entries.forEach { kind ->
			assertNotEquals(0, kind.icon().icon)
			assertNotEquals(0, kind.icon().label)
		}
	}
}
