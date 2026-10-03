package dev.yoanndev90.statusbarhider.features.style

import android.app.Application
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel

/**
 * Style tab: widget order plus the look of the custom bar. Everything else is
 * a pref write handled by [PrefsViewModel]; the reorder is the only operation
 * that is more than a field assignment.
 */
class StyleViewModel(
	application: Application
) : PrefsViewModel(application) {
	/** Moves the widget at [fromIndex] to [toIndex] in the persisted bar order. */
	fun moveWidget(
		fromIndex: Int,
		toIndex: Int
	) {
		updatePrefs {
			copy(widgetOrder = widgetOrder.toMutableList().apply { add(toIndex, removeAt(fromIndex)) })
		}
	}
}
