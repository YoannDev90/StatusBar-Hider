package dev.yoanndev90.statusbarhider.core.settings

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.sections.AppearanceSection
import dev.yoanndev90.statusbarhider.core.settings.sections.BatterySection
import dev.yoanndev90.statusbarhider.core.settings.sections.ClockSection
import dev.yoanndev90.statusbarhider.core.settings.sections.ConnectivitySection
import dev.yoanndev90.statusbarhider.core.settings.sections.CutoutSection
import dev.yoanndev90.statusbarhider.core.settings.sections.GeneralSection
import dev.yoanndev90.statusbarhider.core.settings.sections.HiddenAppsSection
import dev.yoanndev90.statusbarhider.core.settings.sections.LockAppearanceSection
import dev.yoanndev90.statusbarhider.core.settings.sections.LockBehaviorSection
import dev.yoanndev90.statusbarhider.core.settings.sections.NotificationSection
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.ui.components.cardsOf
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
	private val sections =
		listOf(
			ConnectivitySection,
			GeneralSection,
			BatterySection,
			ClockSection,
			AppearanceSection,
			NotificationSection,
			CutoutSection,
			LockAppearanceSection,
			LockBehaviorSection,
			HiddenAppsSection
		)

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
	fun `appearance keeps the widget order as its own titled group`() {
		val shape =
			AppearanceSection.rows.map { row ->
				when (row) {
					is SettingRow.OfSpec -> row.spec.key
					is SettingRow.Group -> "group"
					is SettingRow.Custom -> "custom"
				}
			}
		assertEquals(
			listOf(
				"dark_text",
				"background",
				"pad_start_dp",
				"pad_top_dp",
				"pad_end_dp",
				"pad_bottom_dp",
				"font_size_sp",
				"widget_spacing_dp",
				"font_weight",
				"group"
			),
			shape
		)
		val group = AppearanceSection.rows.filterIsInstance<SettingRow.Group<OverlayPrefs>>().single()
		assertEquals(R.string.section_widget_order, group.title)
		assertTrue(group.rows.single() is SettingRow.Custom<*>)
	}

	@Test
	fun `notifications keeps the test action alone under its titled card`() {
		val shape =
			NotificationSection.rows.map { row ->
				when (row) {
					is SettingRow.OfSpec -> row.spec.key
					is SettingRow.Group -> "group"
					is SettingRow.Custom -> "custom"
				}
			}
		assertEquals(listOf("show_notifs", "custom", "max_notifs", "show_media", "group"), shape)
	}

	@Test
	fun `cutout swaps the nudge pair for the offset pair through shownWhen`() {
		val auto = CutoutSection.visibleRows(OverlayPrefs()).keys()
		assertTrue("nudge sliders missing in auto mode", "camera_nudge_x_dp" in auto && "camera_nudge_y_dp" in auto)
		assertTrue("offset sliders shown in auto mode", "camera_offset_x_pct" !in auto && "camera_offset_y_dp" !in auto)
		val manual = CutoutSection.visibleRows(OverlayPrefs().copy(cameraAutoDetect = false)).keys()
		assertTrue("offset sliders missing in manual mode", "camera_offset_x_pct" in manual && "camera_offset_y_dp" in manual)
		assertTrue("nudge sliders shown in manual mode", "camera_nudge_x_dp" !in manual && "camera_nudge_y_dp" !in manual)
	}

	@Test
	fun `lock behavior keeps both overlay actions between the switches`() {
		val shape =
			LockBehaviorSection.rows.map { row ->
				when (row) {
					is SettingRow.OfSpec -> row.spec.key
					is SettingRow.Group -> "group"
					is SettingRow.Custom -> "custom"
				}
			}
		assertEquals(
			listOf("show_on_lock_screen", "custom", "custom", "hide_notifs_on_lock"),
			shape
		)
	}

	@Test
	fun `cards are built eagerly so no loose run can compose empty`() {
		// Regression: the item lambda used to capture the mutable runner, so
		// every loose run before a group composed with its emptied final value
		// — connectivity lost its 15 toggles on device. Defaults hide the
		// merged-speed row (show_bandwidth off), hence 14 of the 15 specs.
		val cards = cardsOf(ConnectivitySection.visibleRows(OverlayPrefs()))
		assertEquals(2, cards.size)
		assertEquals(0, cards[0].title)
		assertEquals(14, cards[0].rows.size)
		assertEquals(2, cards[1].rows.size)
	}

	@Test
	fun `flattening every section's cards yields its visible rows back`() {
		sections.forEach { section ->
			val visible = section.visibleRows(OverlayPrefs())
			val opened = visible.flatMap { row -> if (row is SettingRow.Group) row.rows else listOf(row) }
			assertEquals(opened, cardsOf(visible).flatMap { it.rows })
		}
	}

	@Test
	fun `every rendered row is iconified, labelled and known to the renderer`() {
		sections.forEach { section ->
			section.rows.ofSpecs().forEach { row ->
				val spec = row.spec
				assertNotNull("'${spec.key}' rendered without an icon", spec.icon)
				// Radio options carry their own labels: only the row-shaped
				// renderers require a label of their own.
				if (spec !is EnumSpec<*, *>) {
					assertNotNull("'${spec.key}' rendered without a label", spec.label)
				}
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
