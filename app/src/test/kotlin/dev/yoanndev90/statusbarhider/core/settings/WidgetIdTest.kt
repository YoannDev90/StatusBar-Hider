package dev.yoanndev90.statusbarhider.core.settings

import dev.yoanndev90.statusbarhider.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetIdTest {
	@Test
	fun `null order falls back to the default layout`() {
		assertEquals(WidgetId.DEFAULT_ORDER, WidgetId.parseOrder(null))
	}

	@Test
	fun `blank and unknown-only orders fall back to the default layout`() {
		assertEquals(WidgetId.DEFAULT_ORDER, WidgetId.parseOrder(""))
		assertEquals(WidgetId.DEFAULT_ORDER, WidgetId.parseOrder("   "))
		assertEquals(WidgetId.DEFAULT_ORDER, WidgetId.parseOrder("bogus,also_bogus"))
	}

	@Test
	fun `stored order wins and unknown ids are dropped`() {
		assertEquals(
			listOf(
				WidgetId.NOTIFS,
				WidgetId.CLOCK,
				WidgetId.DATE,
				WidgetId.CALENDAR,
				WidgetId.MEDIA,
				WidgetId.SPACER,
				WidgetId.CONNECTIVITY,
				WidgetId.BATTERY,
				WidgetId.ALARM,
				WidgetId.BANDWIDTH
			),
			WidgetId.parseOrder("notifs,clock,bogus")
		)
	}

	@Test
	fun `missing ids are appended after the stored ones`() {
		val parsed = WidgetId.parseOrder("battery")
		assertEquals(WidgetId.BATTERY, parsed.first())
		assertEquals(WidgetId.ALL.toSet(), parsed.toSet())
		assertEquals(WidgetId.ALL.size, parsed.size)
	}

	@Test
	fun `duplicates collapse to the first occurrence`() {
		val parsed = WidgetId.parseOrder("clock,clock,date,clock")
		assertEquals(listOf(WidgetId.CLOCK, WidgetId.DATE), parsed.take(2))
		assertEquals(WidgetId.ALL.size, parsed.size)
	}

	@Test
	fun `whitespace around ids is tolerated`() {
		assertEquals(
			listOf(WidgetId.CLOCK, WidgetId.DATE),
			WidgetId.parseOrder(" clock , date ").take(2)
		)
	}

	@Test
	fun `the default order is exactly the known set`() {
		assertEquals(WidgetId.ALL, WidgetId.DEFAULT_ORDER)
		assertEquals(WidgetId.ALL.size, WidgetId.ALL.distinct().size)
	}

	@Test
	fun `every known id has its own label and unknown ids fall back`() {
		val labels = WidgetId.ALL.map { WidgetId.labelRes(it) }
		assertEquals(labels.size, labels.distinct().size)
		labels.forEach { assertTrue("label resource must be a real id", it != 0) }
		assertEquals(R.string.widget_unknown, WidgetId.labelRes("nope"))
	}
}
