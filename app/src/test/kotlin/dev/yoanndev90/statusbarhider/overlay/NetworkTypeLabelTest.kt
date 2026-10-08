package dev.yoanndev90.statusbarhider.overlay

import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("DEPRECATION")
class NetworkTypeLabelTest {
	@Test
	fun `NR is 5G`() {
		assertEquals("5G", networkTypeLabel(TelephonyManager.NETWORK_TYPE_NR))
	}

	@Test
	fun `LTE is LTE`() {
		assertEquals("LTE", networkTypeLabel(TelephonyManager.NETWORK_TYPE_LTE))
	}

	@Test
	fun `every UMTS and EVDO generation is 3G`() {
		val threeG =
			listOf(
				TelephonyManager.NETWORK_TYPE_UMTS,
				TelephonyManager.NETWORK_TYPE_HSDPA,
				TelephonyManager.NETWORK_TYPE_HSUPA,
				TelephonyManager.NETWORK_TYPE_HSPA,
				TelephonyManager.NETWORK_TYPE_HSPAP,
				TelephonyManager.NETWORK_TYPE_TD_SCDMA,
				TelephonyManager.NETWORK_TYPE_EHRPD,
				TelephonyManager.NETWORK_TYPE_EVDO_0,
				TelephonyManager.NETWORK_TYPE_EVDO_A,
				TelephonyManager.NETWORK_TYPE_EVDO_B
			)
		threeG.forEach { assertEquals("3G", networkTypeLabel(it)) }
	}

	@Test
	fun `every GSM and CDMA generation is 2G`() {
		val twoG =
			listOf(
				TelephonyManager.NETWORK_TYPE_GSM,
				TelephonyManager.NETWORK_TYPE_GPRS,
				TelephonyManager.NETWORK_TYPE_EDGE,
				TelephonyManager.NETWORK_TYPE_CDMA,
				TelephonyManager.NETWORK_TYPE_1xRTT,
				TelephonyManager.NETWORK_TYPE_IDEN
			)
		twoG.forEach { assertEquals("2G", networkTypeLabel(it)) }
	}

	@Test
	fun `unknown or unusable types return empty so the caller can fall back`() {
		assertEquals("", networkTypeLabel(TelephonyManager.NETWORK_TYPE_UNKNOWN))
		assertEquals("", networkTypeLabel(TelephonyManager.NETWORK_TYPE_IWLAN))
		assertEquals("", networkTypeLabel(0))
	}
}
