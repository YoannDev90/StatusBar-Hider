package dev.yoanndev90.statusbarhider.core.settings.sections

import androidx.compose.ui.res.stringResource
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.SectionGroup
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.Subtitle
import dev.yoanndev90.statusbarhider.core.settings.settingRows
import dev.yoanndev90.statusbarhider.core.settings.specs.GeneralSpecs

/** Polling, touch handling, boot behavior and burn-in protection. */
object GeneralSection :
	SettingSection<OverlayPrefs>(
		id = "general",
		icon = R.drawable.ic_settings,
		title = R.string.cat_general,
		subtitle =
			Subtitle.Live { prefs ->
				stringResource(R.string.summary_refresh, prefs.updateIntervalSec)
			},
		group = SectionGroup.CUSTOM_BAR,
		keywords = listOf("refresh", "boot", "touch", "polling", "burn-in", "interval", "auto-hide"),
		rows =
			settingRows(
				GeneralSpecs.interactive,
				GeneralSpecs.autoHideBoot,
				GeneralSpecs.autoStartBar,
				GeneralSpecs.updateIntervalSec,
				GeneralSpecs.burnInMin
			)
	)
