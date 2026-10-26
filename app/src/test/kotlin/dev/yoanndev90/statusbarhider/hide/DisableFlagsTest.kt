package dev.yoanndev90.statusbarhider.hide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reading the whole "is the hide still applied" answer rests on: the
 * shared record must be told apart from the aggregate, and a record that lost
 * one of our bits must read as not applied - that gap is what a third-party
 * `send-disable-flag` leaves behind (docs/limitations.md).
 */
class DisableFlagsTest {
	/** The device's real dump after our three flags were applied. */
	private val applied =
		"""
		mDisabled1=0x920000
		  mDisableRecords.size=2
		    [0] userId=0 what1=0x00000000 what2=0x00000000 pkg=com.android.systemui token=android.os.BinderProxy@f4e33cd
		    [1] userId=0 what1=0x00920000 what2=0x00000000 pkg=android token=com.android.server.statusbar.StatusBarShellCommand${'$'}StatusBarShellCommandToken@c9b88aa
		""".trimIndent()

	/** The same dump after another caller replaced our record's bits. */
	private val overwritten =
		"""
		mDisabled1=0x820000
		  mDisableRecords.size=2
		    [0] userId=0 what1=0x00000000 what2=0x00000000 pkg=com.android.systemui token=android.os.BinderProxy@f4e33cd
		    [1] userId=0 what1=0x00820000 what2=0x00000000 pkg=android token=com.android.server.statusbar.StatusBarShellCommand${'$'}StatusBarShellCommandToken@c9b88aa
		""".trimIndent()

	/** Our record zeroed by a restore while SystemUI still holds its own bits. */
	private val restored =
		"""
		mDisabled1=0x1600000
		  mDisableRecords.size=2
		    [0] userId=0 what1=0x01600000 what2=0x00000000 pkg=com.android.systemui token=android.os.BinderProxy@f4e33cd
		    [1] userId=0 what1=0x00000000 what2=0x00000000 pkg=android token=com.android.server.statusbar.StatusBarShellCommand${'$'}StatusBarShellCommandToken@c9b88aa
		""".trimIndent()

	private val mask = 0x920000L

	@Test
	fun `both figures of the dump are read, the shared record preferred as ours`() {
		val flags = parseDisableFlags(applied)
		assertEquals(0x920000L, flags?.aggregate)
		assertEquals(0x920000L, flags?.shared)
		assertEquals(0x920000L, flags?.ours)
	}

	@Test
	fun `a record that lost one of our bits is no longer applied`() {
		assertEquals(false, holdsMask(parseDisableFlags(overwritten), mask))
	}

	@Test
	fun `our bits still whole read as applied`() {
		assertEquals(true, holdsMask(parseDisableFlags(applied), mask))
	}

	@Test
	fun `SystemUI's own lock flags cannot stand in for ours`() {
		assertEquals(false, holdsMask(parseDisableFlags(restored), mask))
	}

	@Test
	fun `without a recorded mask the old any-record rule stands`() {
		val cleared =
			"""
			mDisabled1=0x0
			  mDisableRecords.size=1
			    [1] userId=0 what1=0x00000000 what2=0x00000000 pkg=android token=StatusBarShellCommandToken@c9b88aa
			""".trimIndent()
		assertEquals(true, holdsMask(parseDisableFlags(overwritten), null))
		assertEquals(false, holdsMask(parseDisableFlags(cleared), null))
		assertNull(holdsMask(null, null))
	}

	@Test
	fun `a dump without the shared record still answers through the aggregate`() {
		val aggregateOnly =
			"""
			mDisabled1=0x920000
			  mDisableRecords.size=1
			    [0] userId=0 what1=0x00920000 what2=0x00000000 pkg=com.android.systemui token=android.os.BinderProxy@f4e33cd
			""".trimIndent()
		val flags = parseDisableFlags(aggregateOnly)
		assertNull(flags?.shared)
		assertEquals(0x920000L, flags?.ours)
		assertEquals(true, holdsMask(flags, mask))
	}

	@Test
	fun `a dump that answers nothing leaves every caller on its fallback`() {
		assertNull(parseDisableFlags("no such line in this output"))
		assertNull(holdsMask(null, mask))
	}

	@Test
	fun `a zeroed shared record is read as zero, not as absent`() {
		assertEquals(false, holdsMask(parseDisableFlags(applied).let { it?.copy(shared = 0L) }, mask))
		assertTrue(parseDisableFlags(applied)!!.shared != 0L)
	}
}
