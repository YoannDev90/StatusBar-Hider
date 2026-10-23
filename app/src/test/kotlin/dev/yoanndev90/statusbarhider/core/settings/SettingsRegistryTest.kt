package dev.yoanndev90.statusbarhider.core.settings

import dev.yoanndev90.statusbarhider.core.settings.sections.ConnectivitySection
import dev.yoanndev90.statusbarhider.core.settings.specs.OverlaySpecs
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.WidgetId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.descriptors.elementNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard that keeps the registry honest: a field added to [OverlayPrefs]
 * without a spec, a key renamed on one side only, or a default edited in one
 * of the two places all turn this file red.
 */
class SettingsRegistryTest {
	private val specs = OverlaySpecs.all
	private val defaults = OverlayPrefs()

	@Test
	fun `every model field is described exactly once`() {
		val described = specs.map { it.key }
		assertEquals("duplicate spec keys", described.size, described.distinct().size)
		val modelKeys =
			OverlayPrefs
				.serializer()
				.descriptor
				.elementNames
				.toSet()
		assertEquals("model fields and spec keys diverged", modelKeys, described.toSet())
	}

	@Test
	fun `every spec default matches the model default`() {
		for (spec in specs) {
			assertEquals("default drift on '${spec.key}'", spec.default, spec.get(defaults))
		}
	}

	@Test
	fun `every int range holds its own default`() {
		for (spec in specs.filterIsInstance<IntSpec<*>>()) {
			assertTrue("'${spec.key}': min ${spec.min} above default", spec.min <= spec.default)
			assertTrue("'${spec.key}': default above max ${spec.max}", spec.default <= spec.max)
		}
	}

	@Test
	fun `every enum default belongs to its entries`() {
		for (spec in specs.filterIsInstance<EnumSpec<*, *>>()) {
			assertTrue("'${spec.key}': default not in entries", spec.default in spec.values)
		}
	}

	@Test
	fun `normalize leaves defaults untouched`() {
		assertEquals(defaults, OverlaySpecs.normalize(defaults))
	}

	@Test
	fun `normalize reproduces every clamp the hand-written version had`() {
		val cases =
			listOf(
				Triple(
					"update interval above max",
					defaults.copy(updateIntervalSec = 999),
					defaults.copy(updateIntervalSec = 60)
				),
				Triple(
					"update interval below min",
					defaults.copy(updateIntervalSec = 1),
					defaults.copy(updateIntervalSec = 5)
				),
				Triple(
					"paddings clamped",
					defaults.copy(padStartDp = 99, padTopDp = -5, padEndDp = 99, padBottomDp = -5),
					defaults.copy(padStartDp = 32, padTopDp = 0, padEndDp = 32, padBottomDp = 0)
				),
				Triple("max notifs below min", defaults.copy(maxNotifs = 0), defaults.copy(maxNotifs = 1)),
				Triple("burn-in above max", defaults.copy(burnInMin = 99), defaults.copy(burnInMin = 30)),
				Triple("burn-in below zero", defaults.copy(burnInMin = -1), defaults.copy(burnInMin = 0)),
				Triple("font size above max", defaults.copy(fontSizeSp = 999), defaults.copy(fontSizeSp = 20)),
				Triple("unknown font weight", defaults.copy(fontWeightName = "NOPE"), defaults.copy(fontWeightName = "NORMAL")),
				Triple(
					"widget spacing above max",
					defaults.copy(widgetSpacingDp = 99),
					defaults.copy(widgetSpacingDp = 12)
				),
				Triple("camera gap above max", defaults.copy(cameraGapDp = 99), defaults.copy(cameraGapDp = 16)),
				Triple("camera nudge x clamped", defaults.copy(cameraNudgeXDp = 999), defaults.copy(cameraNudgeXDp = 48)),
				Triple(
					"camera nudge x below min",
					defaults.copy(cameraNudgeXDp = -999),
					defaults.copy(cameraNudgeXDp = -48)
				),
				Triple(
					"camera offset x above max",
					defaults.copy(cameraOffsetXPct = 200),
					defaults.copy(cameraOffsetXPct = 100)
				),
				Triple(
					"camera offset y above max",
					defaults.copy(cameraOffsetYDp = 99),
					defaults.copy(cameraOffsetYDp = 60)
				),
				Triple("camera size above max", defaults.copy(cameraSizeDp = 99), defaults.copy(cameraSizeDp = 40)),
				Triple("ring stroke below min", defaults.copy(cameraRingStrokeDp = 0), defaults.copy(cameraRingStrokeDp = 1)),
				Triple("ring stroke above max", defaults.copy(cameraRingStrokeDp = 9), defaults.copy(cameraRingStrokeDp = 8)),
				Triple(
					"blank date format",
					defaults.copy(dateFormat = ""),
					defaults.copy(dateFormat = OverlayPrefs.DEFAULT_DATE_FORMAT)
				),
				Triple(
					"valid ring color kept",
					defaults.copy(cameraRingColor = "#aabbcc"),
					defaults.copy(cameraRingColor = "#aabbcc")
				),
				Triple("invalid ring color dropped", defaults.copy(cameraRingColor = "#GGGGGG"), defaults.copy(cameraRingColor = "")),
				Triple(
					"partial widget order repaired",
					defaults.copy(widgetOrder = listOf(WidgetId.CLOCK)),
					defaults.copy(widgetOrder = WidgetId.DEFAULT_ORDER)
				)
			)
		for ((name, input, expected) in cases) {
			assertEquals(name, expected, OverlaySpecs.normalize(input))
		}
	}

	@Test
	fun `resetting a section restores only its own settings`() {
		val customized =
			OverlayPrefs()
				.copy(
					enabled = true,
					showSeconds = true,
					showBattery = false,
					showWifi = false,
					fontSizeSp = 16,
					updateIntervalSec = 15
				)
		val reset = ConnectivitySection.resetIn(customized)
		// Every connectivity setting is back to its default...
		ConnectivitySection.rows.specs().forEach { spec ->
			assertFalse("${spec.key} should be at its default", spec.isModifiedIn(reset))
		}
		// ...and nothing outside the section moved.
		assertTrue(reset.enabled)
		assertTrue(reset.showSeconds)
		assertFalse(reset.showBattery)
		assertEquals(16, reset.fontSizeSp)
		assertEquals(15, reset.updateIntervalSec)
	}

	@Test
	fun `modifiedIn lists only the settings away from their default`() {
		val store = FakeOverlayStore()
		// Self-relative values: the assertion does not depend on any default.
		val customized = OverlayPrefs().copy(updateIntervalSec = OverlayPrefs().updateIntervalSec + 7, showBattery = false)
		store.replace(customized)
		assertEquals(
			listOf("show_battery", "update_interval_sec"),
			store.modifiedIn(store.state.value).map { it.key }.sorted()
		)
	}

	@Test
	fun `resetStore puts every setting back to its default`() {
		val store = FakeOverlayStore()
		store.replace(OverlayPrefs().copy(updateIntervalSec = OverlayPrefs().updateIntervalSec + 7))
		SettingsRegistry.resetStore(store)
		assertEquals(OverlayPrefs(), store.state.value)
	}
}

/** In-memory [SettingStore] over the overlay file, for the registry's generic operations. */
private class FakeOverlayStore(
	override val specs: List<SettingSpec<*, OverlayPrefs>> = OverlaySpecs.all
) : SettingStore<OverlayPrefs>() {
	override val state = MutableStateFlow(OverlayPrefs())
	override val default = OverlayPrefs()

	override fun update(transform: OverlayPrefs.() -> OverlayPrefs) {
		state.value = state.value.transform()
	}

	override fun replace(value: OverlayPrefs) {
		state.value = value
	}
}
