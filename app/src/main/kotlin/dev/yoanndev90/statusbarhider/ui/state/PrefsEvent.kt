package dev.yoanndev90.statusbarhider.ui.state

/** One-shot UI instructions from a ViewModel; the screen renders them. */
sealed interface PrefsEvent {
	/** The overlay permission is missing: toast the grant hint and open the settings page. */
	data object GrantOverlayPermission : PrefsEvent
}
