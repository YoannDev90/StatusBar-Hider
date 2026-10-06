package dev.yoanndev90.statusbarhider.features.logs

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.yoanndev90.statusbarhider.core.log.LogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Logs tab: exposes the live log lines and builds the share intent. The file
 * side (load / FileProvider) runs on IO instead of the screen's main thread.
 */
class LogsViewModel(
	application: Application
) : AndroidViewModel(application) {
	init {
		LogStore.ensureLoadedAsync(application)
	}

	/** Live log lines (newest last), already bounded by LogStore. */
	val lines: StateFlow<List<String>> = LogStore.lines

	/** [Intent] sharing the log file, or null when it could not be built. */
	suspend fun shareIntent(): Intent? = withContext(Dispatchers.IO) { LogStore.shareIntent(getApplication()) }

	/**
	 * Builds [shareIntent] in [viewModelScope]: the export writes a file first,
	 * and a scope tied to the composition would cancel it the moment the user
	 * leaves the tab - leaving an empty file behind with no chooser to pick it.
	 */
	fun shareLogs(onDone: (Intent?) -> Unit) {
		viewModelScope.launch { onDone(shareIntent()) }
	}
}
