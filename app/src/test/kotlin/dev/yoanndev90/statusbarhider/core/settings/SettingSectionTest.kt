package dev.yoanndev90.statusbarhider.core.settings

import dev.yoanndev90.statusbarhider.core.settings.sections.BatterySection
import dev.yoanndev90.statusbarhider.core.settings.sections.ClockSection
import dev.yoanndev90.statusbarhider.core.settings.sections.ConnectivitySection
import dev.yoanndev90.statusbarhider.core.settings.sections.GeneralSection
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The section-side guard: display order of the pilot, the shownWhen filter
 * the renderer draws from, and the metadata a generic row needs to exist at
 * all (icon, label, and a spec type the renderer knows).
 */
class SettingSectionTest {
	private val sections = listOf(ConnectivitySection, GeneralSection, BatterySection, ClockSection)

	@Test
	fun `connectivity lists its toggles in screen order`() {
		val flat = ConnectivitySection.rows.filterIsInstance<SettingRow.OfSpec<OverlayPrefs>>()
		assertEquals(
			listOf(
				"show_wifi",
				"show_mobile",
				"show_bluetooth",
				"show_airplane",
				"show_usb",
				"show_nfc",
				"show_gps",
				"show_dnd",
				"show_data_saver",
				"show_rotate",
				"show_torch",
				"show_vpn",
				"show_hotspot",
				"show_bandwidth",
				"bandwidth_merged"
			),
			flat.map { it.spec.key }
		)
		assertTrue(flat.all { it.spec is BoolSpec<*> })
	}

	@Test
	fun `connectivity puts the bluetooth device block in its own group`() {
		val groups = ConnectivitySection.rows.filterIsInstance<SettingRow.Group<OverlayPrefs>>()
		assertEquals(1, groups.size)
		val inner = groups[0].rows.filterIsInstance<SettingRow.OfSpec<OverlayPrefs>>()
		assertEquals(listOf("show_bt_icons"), inner.map { it.spec.key })
		assertTrue(groups[0].rows.any { it is SettingRow.Custom<*> })
	}

	@Test
	fun `shownWhen hides the merged-speed row while bandwidth is off`() {
		val hidden = ConnectivitySection.visibleRows(OverlayPrefs()).keys()
		assertTrue("bandwidth_merged shown while show_bandwidth is off", "bandwidth_merged" !in hidden)
		val shown = ConnectivitySection.visibleRows(OverlayPrefs().copy(showBandwidth = true)).keys()
		assertTrue("bandwidth_merged missing although show_bandwidth is on", "bandwidth_merged" in shown)
	}

	@Test
	fun `general and battery show every row unconditionally`() {
		assertEquals(5, GeneralSection.visibleRows(OverlayPrefs()).size)
		assertEquals(3, BatterySection.visibleRows(OverlayPrefs()).size)
	}

	@Test
	fun `clock interleaves its custom rows in screen order`() {
		val shape =
			ClockSection.rows.map { row ->
				when (row) {
					is SettingRow.OfSpec -> row.spec.key
					is SettingRow.Group -> "group"
					is SettingRow.Custom -> "custom"
				}
			}
		assertEquals(
			listOf("show_seconds", "use_24h", "show_date", "custom", "show_alarm", "custom"),
			shape
		)
	}

	@Test
	fun `every rendered row is iconified, labelled and known to the renderer`() {
		sections.forEach { section ->
			section.rows.ofSpecs().forEach { row ->
				val spec = row.spec
				assertNotNull("'${spec.key}' rendered without an icon", spec.icon)
				assertNotNull("'${spec.key}' rendered without a label", spec.label)
				assertTrue(
					"'${spec.key}' has no generic renderer",
					spec is BoolSpec<*> || spec is IntSpec<*> || spec is EnumSpec<*, *> || spec is TextSpec<*>
				)
			}
		}
	}

	/** Spec keys of every OfSpec row, groups flattened. */
	private fun List<SettingRow<OverlayPrefs>>.keys(): List<String> =
		flatMap { row ->
			when (row) {
				is SettingRow.OfSpec -> listOf(row.spec.key)
				is SettingRow.Group -> row.rows.keys()
				is SettingRow.Custom -> emptyList()
			}
		}

	/** Every OfSpec row, groups flattened. */
	private fun List<SettingRow<OverlayPrefs>>.ofSpecs(): List<SettingRow.OfSpec<OverlayPrefs>> =
		flatMap { row ->
			when (row) {
				is SettingRow.OfSpec -> listOf(row)
				is SettingRow.Group -> row.rows.ofSpecs()
				is SettingRow.Custom -> emptyList()
			}
		}
}
