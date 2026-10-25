package dev.yoanndev90.statusbarhider.overlay

import android.bluetooth.BluetoothClass
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.overlay.system.btDeviceIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BluetoothIconsTest {
	@Test
	fun earbudsMapToEarbudsIcon() {
		assertEquals(R.drawable.ic_bt_earbuds, btDeviceIcon(BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET).icon)
		assertEquals(R.drawable.ic_bt_earbuds, btDeviceIcon(BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES).icon)
	}

	@Test
	fun headsetsMapToHeadsetIcon() {
		assertEquals(R.drawable.ic_bt_headset, btDeviceIcon(BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE).icon)
		assertEquals(R.drawable.ic_bt_headset, btDeviceIcon(BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE).icon)
	}

	@Test
	fun speakersMapToSpeakerIcon() {
		assertEquals(R.drawable.ic_bt_speaker, btDeviceIcon(BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER).icon)
		assertEquals(R.drawable.ic_bt_speaker, btDeviceIcon(BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO).icon)
		assertEquals(R.drawable.ic_bt_speaker, btDeviceIcon(BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO).icon)
	}

	@Test
	fun peripheralsMapToTheirOwnIcons() {
		assertEquals(R.drawable.ic_bt_keyboard, btDeviceIcon(BluetoothClass.Device.PERIPHERAL_KEYBOARD).icon)
		assertEquals(R.drawable.ic_bt_mouse, btDeviceIcon(BluetoothClass.Device.PERIPHERAL_POINTING).icon)
		assertEquals(R.drawable.ic_bt_watch, btDeviceIcon(BluetoothClass.Device.WEARABLE_WRIST_WATCH).icon)
		assertEquals(R.drawable.ic_bt_gamepad, btDeviceIcon(BluetoothClass.Device.TOY_GAME).icon)
		assertEquals(R.drawable.ic_bt_phone, btDeviceIcon(BluetoothClass.Device.PHONE_SMART).icon)
	}

	@Test
	fun carsPhonesAndComputersMapToTheirOwnIcons() {
		assertEquals(R.drawable.ic_bt_car, btDeviceIcon(BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO).icon)
		assertEquals(R.drawable.ic_bt_phone, btDeviceIcon(BluetoothClass.Device.PHONE_CELLULAR).icon)
		assertEquals(R.drawable.ic_bt_computer, btDeviceIcon(BluetoothClass.Device.COMPUTER_LAPTOP).icon)
		assertEquals(R.drawable.ic_bt_computer, btDeviceIcon(BluetoothClass.Device.COMPUTER_DESKTOP).icon)
		// The service-class bits of a raw CoD must not push a laptop back to the generic glyph.
		assertEquals(R.drawable.ic_bt_computer, btDeviceIcon(0x7C010C).icon)
	}

	@Test
	fun unknownFallsBackToGenericGlyph() {
		// null = no BluetoothClass at all, 0 = uncategorized: both must stay generic.
		assertEquals(R.drawable.ic_bt, btDeviceIcon(null).icon)
		assertEquals(R.drawable.ic_bt, btDeviceIcon(0).icon)
		assertEquals(R.drawable.ic_bt, btDeviceIcon(android.bluetooth.BluetoothClass.Device.Major.UNCATEGORIZED).icon)
	}

	@Test
	fun everyIconCarriesALabel() {
		val classes =
			listOf(
				BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
				BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
				BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
				BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE,
				BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
				BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
				BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
				BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
				BluetoothClass.Device.PERIPHERAL_KEYBOARD,
				BluetoothClass.Device.PERIPHERAL_POINTING,
				BluetoothClass.Device.WEARABLE_WRIST_WATCH,
				BluetoothClass.Device.TOY_GAME,
				BluetoothClass.Device.TOY_CONTROLLER,
				BluetoothClass.Device.PHONE_SMART,
				BluetoothClass.Device.COMPUTER_LAPTOP,
				null
			)
		classes.forEach { assertNotEquals(0, btDeviceIcon(it).label) }
	}
}
