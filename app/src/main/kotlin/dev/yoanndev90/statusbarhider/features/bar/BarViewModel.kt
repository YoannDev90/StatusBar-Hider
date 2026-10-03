package dev.yoanndev90.statusbarhider.features.bar

import android.app.Application
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel

/**
 * Custom bar tab. Most actions are plain pref writes handled by
 * [PrefsViewModel]; this class owns the two that carry extra meaning:
 * the date pattern (logged so the user can confirm it took) and the calendar
 * toggle (whose runtime permission is requested by the screen first).
 */
class BarViewModel(
	application: Application
) : PrefsViewModel(application) {
	fun setDateFormat(pattern: String) {
		updatePrefs { copy(dateFormat = pattern) }
		log(R.string.log_date_format, pattern)
	}

	/** Saves the calendar widget state; call only once the permission is granted. */
	fun setShowCalendar(enabled: Boolean) {
		updatePrefs { copy(showCalendar = enabled) }
	}
}
