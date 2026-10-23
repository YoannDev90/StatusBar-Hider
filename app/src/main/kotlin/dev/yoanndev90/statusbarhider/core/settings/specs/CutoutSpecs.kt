package dev.yoanndev90.statusbarhider.core.settings.specs

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.BoolSpec
import dev.yoanndev90.statusbarhider.core.settings.IntSpec
import dev.yoanndev90.statusbarhider.core.settings.SettingSpec
import dev.yoanndev90.statusbarhider.core.settings.TextSpec
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs

/**
 * Camera cutout placement: automatic detection, or the manual nudge / offset
 * pair, plus the ring drawn around the cutout.
 */
object CutoutSpecs {
	val cameraAutoDetect =
		BoolSpec<OverlayPrefs>(
			key = "camera_auto_detect",
			get = OverlayPrefs::cameraAutoDetect,
			set = { p, v -> p.copy(cameraAutoDetect = v) },
			default = true,
			icon = R.drawable.ic_crop_free,
			label = R.string.switch_camera_auto
		)

	val cameraNudgeXDp =
		IntSpec<OverlayPrefs>(
			key = "camera_nudge_x_dp",
			get = OverlayPrefs::cameraNudgeXDp,
			set = { p, v -> p.copy(cameraNudgeXDp = v) },
			default = 0,
			min = -48,
			max = 48,
			icon = R.drawable.ic_drag_indicator,
			label = R.string.slider_camera_nudge_x,
			suffix = R.string.suffix_dp,
			shownWhen = { it.cameraAutoDetect }
		)

	val cameraNudgeYDp =
		IntSpec<OverlayPrefs>(
			key = "camera_nudge_y_dp",
			get = OverlayPrefs::cameraNudgeYDp,
			set = { p, v -> p.copy(cameraNudgeYDp = v) },
			default = 0,
			min = -48,
			max = 48,
			icon = R.drawable.ic_drag_indicator,
			label = R.string.slider_camera_nudge_y,
			suffix = R.string.suffix_dp,
			shownWhen = { it.cameraAutoDetect }
		)

	val cameraOffsetXPct =
		IntSpec<OverlayPrefs>(
			key = "camera_offset_x_pct",
			get = OverlayPrefs::cameraOffsetXPct,
			set = { p, v -> p.copy(cameraOffsetXPct = v) },
			default = 50,
			min = 0,
			max = 100,
			icon = R.drawable.ic_open_with,
			label = R.string.slider_camera_offset_x,
			suffix = R.string.suffix_percent,
			shownWhen = { !it.cameraAutoDetect }
		)

	val cameraOffsetYDp =
		IntSpec<OverlayPrefs>(
			key = "camera_offset_y_dp",
			get = OverlayPrefs::cameraOffsetYDp,
			set = { p, v -> p.copy(cameraOffsetYDp = v) },
			default = 12,
			min = 0,
			max = 60,
			icon = R.drawable.ic_open_with,
			label = R.string.slider_camera_offset_y,
			suffix = R.string.suffix_dp,
			shownWhen = { !it.cameraAutoDetect }
		)

	val cameraSizeDp =
		IntSpec<OverlayPrefs>(
			key = "camera_size_dp",
			get = OverlayPrefs::cameraSizeDp,
			set = { p, v -> p.copy(cameraSizeDp = v) },
			default = 0,
			min = 0,
			max = 40,
			icon = R.drawable.ic_select_all,
			label = R.string.slider_camera_size,
			suffix = R.string.suffix_dp
		)

	val cameraGapDp =
		IntSpec<OverlayPrefs>(
			key = "camera_gap_dp",
			get = OverlayPrefs::cameraGapDp,
			set = { p, v -> p.copy(cameraGapDp = v) },
			default = 4,
			min = 0,
			max = 16,
			icon = R.drawable.ic_straighten,
			label = R.string.slider_camera_gap,
			suffix = R.string.suffix_dp
		)

	val cameraRing =
		BoolSpec<OverlayPrefs>(
			key = "camera_ring",
			get = OverlayPrefs::cameraRing,
			set = { p, v -> p.copy(cameraRing = v) },
			default = true,
			icon = R.drawable.ic_donut_small,
			label = R.string.switch_camera_ring
		)

	val cameraRingStrokeDp =
		IntSpec<OverlayPrefs>(
			key = "camera_ring_stroke_dp",
			get = OverlayPrefs::cameraRingStrokeDp,
			set = { p, v -> p.copy(cameraRingStrokeDp = v) },
			default = 3,
			min = 1,
			max = 8,
			icon = R.drawable.ic_line_weight,
			label = R.string.slider_camera_ring_stroke,
			suffix = R.string.suffix_dp,
			shownWhen = { it.cameraRing }
		)

	/** Plain `#RRGGBB`; anything else falls back to the text color (empty). */
	val cameraRingColor =
		TextSpec<OverlayPrefs>(
			key = "camera_ring_color",
			get = OverlayPrefs::cameraRingColor,
			set = { p, v -> p.copy(cameraRingColor = v) },
			default = "",
			validate = { it.isEmpty() || RING_COLOR.matches(it) },
			icon = R.drawable.ic_color_lens,
			label = R.string.label_ring_color,
			shownWhen = { it.cameraRing }
		)

	/** Stored ring colors are plain `#RRGGBB`; anything else falls back. */
	private val RING_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

	val specs: List<SettingSpec<*, OverlayPrefs>> =
		listOf(
			cameraAutoDetect,
			cameraNudgeXDp,
			cameraNudgeYDp,
			cameraOffsetXPct,
			cameraOffsetYDp,
			cameraSizeDp,
			cameraGapDp,
			cameraRing,
			cameraRingStrokeDp,
			cameraRingColor
		)
}
