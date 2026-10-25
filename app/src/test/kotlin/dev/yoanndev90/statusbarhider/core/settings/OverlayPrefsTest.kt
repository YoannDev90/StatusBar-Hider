package dev.yoanndev90.statusbarhider.overlay

import dev.yoanndev90.statusbarhider.core.settings.OverlayBackground
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Model-level behaviour only: the JSON shape and the clamping of free-form
 * values are covered by
 * [dev.yoanndev90.statusbarhider.core.settings.OverlayPrefsCodecTest].
 */
class OverlayPrefsTest {
	@Test
	fun `effective time format honors both toggles`() {
		assertEquals(
			OverlayPrefs.DEFAULT_FORMAT_NO_SECONDS,
			OverlayPrefs().copy(use24h = true, showSeconds = false).effectiveTimeFormat()
		)
		assertEquals(
			OverlayPrefs.DEFAULT_FORMAT_WITH_SECONDS,
			OverlayPrefs().copy(use24h = true, showSeconds = true).effectiveTimeFormat()
		)
		assertEquals(
			OverlayPrefs.FORMAT_12H,
			OverlayPrefs().copy(use24h = false, showSeconds = false).effectiveTimeFormat()
		)
		assertEquals(
			OverlayPrefs.FORMAT_12H_WITH_SECONDS,
			OverlayPrefs().copy(use24h = false, showSeconds = true).effectiveTimeFormat()
		)
	}

	@Test
	fun `background color follows the enum`() {
		assertEquals(android.graphics.Color.TRANSPARENT, OverlayPrefs().copy(background = OverlayBackground.TRANSPARENT).backgroundColor())
		assertEquals(0xCC000000.toInt(), OverlayPrefs().copy(background = OverlayBackground.SEMI).backgroundColor())
		assertEquals(android.graphics.Color.BLACK, OverlayPrefs().copy(background = OverlayBackground.BLACK).backgroundColor())
	}

	@Test
	fun `text color flips with the dark-text toggle`() {
		assertEquals(android.graphics.Color.WHITE, OverlayPrefs().copy(darkText = false).textColor())
		assertEquals(android.graphics.Color.BLACK, OverlayPrefs().copy(darkText = true).textColor())
	}
}
