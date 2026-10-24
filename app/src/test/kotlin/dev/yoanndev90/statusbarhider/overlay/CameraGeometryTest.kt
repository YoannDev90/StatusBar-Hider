package dev.yoanndev90.statusbarhider.overlay

import dev.yoanndev90.statusbarhider.overlay.model.CameraAnchor
import dev.yoanndev90.statusbarhider.overlay.model.CameraGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraGeometryTest {
	@Test
	fun `slot spans the radius on both sides of the center`() {
		val geometry =
			CameraGeometry(
				centerX = 100f,
				centerY = 40f,
				radius = 25f,
				screenWidth = 1080,
				screenHeight = 2400,
				anchor = CameraAnchor.Center
			)
		assertEquals(75f, geometry.slotLeft, 0f)
		assertEquals(125f, geometry.slotRight, 0f)
	}

	@Test
	fun `zero radius collapses the slot onto the center`() {
		val geometry =
			CameraGeometry(
				centerX = 50f,
				centerY = 0f,
				radius = 0f,
				screenWidth = 100,
				screenHeight = 100,
				anchor = CameraAnchor.Start
			)
		assertEquals(50f, geometry.slotLeft, 0f)
		assertEquals(50f, geometry.slotRight, 0f)
	}
}
