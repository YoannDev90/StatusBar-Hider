package dev.yoanndev90.statusbarhider.core.settings.sections

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.SectionGroup
import dev.yoanndev90.statusbarhider.core.settings.SettingRow
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.Subtitle
import dev.yoanndev90.statusbarhider.core.settings.specs.LockSpecs
import dev.yoanndev90.statusbarhider.ui.components.SettingAction

/**
 * Keyguard visibility, the accessibility service behind it and notification
 * privacy. A sub-screen of the lock hub — searchable, not listed at root.
 */
object LockBehaviorSection :
	SettingSection<OverlayPrefs>(
		id = "lock/behavior",
		icon = R.drawable.ic_settings,
		title = R.string.lock_sub_behavior,
		subtitle = Subtitle.Res(R.string.lock_sub_behavior_sub),
		group = SectionGroup.CUSTOM_BAR,
		keywords = listOf("lock", "keyguard", "visibility", "overlay", "accessibility", "hide", "notifications"),
		listed = false,
		rows =
			listOf(
				SettingRow.OfSpec(LockSpecs.showOnLockScreen),
				SettingRow.Custom { prefs, vm ->
					if (prefs.showOnLockScreen) {
						SettingAction(R.drawable.ic_key, R.string.action_enable_lock_overlay) {
							vm.enableLockScreenOverlay()
						}
					}
				},
				SettingRow.Custom { _, vm ->
					// Reachable even with the toggle off: the service can still be on from
					// an earlier session, and the app must not be why it stays that way.
					SettingAction(R.drawable.ic_close, R.string.action_disable_lock_overlay, danger = true) {
						vm.disableLockScreenOverlay()
					}
				},
				SettingRow.OfSpec(LockSpecs.hideNotifsOnLock)
			)
	)
