package dev.yoanndev90.statusbarhider.ui.setup

import android.app.Application
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.data.ShizukuRepository
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Setup checklist: the Shizuku state is the only row that is a flow (it can
 * change while the screen is open, e.g. after the permission dialog); the
 * other rows are plain permission checks re-read on every resume.
 */
class SetupViewModel(
	application: Application
) : PrefsViewModel(application) {
	private val shizukuRepo = ShizukuRepository.getInstance()

	val shizuku: StateFlow<ShizukuState> = shizukuRepo.state

	fun requestShizukuPermission() {
		when (shizukuRepo.state.value) {
			ShizukuState.NOT_RUNNING -> log(R.string.log_shizuku_not_running)
			ShizukuState.READY -> log(R.string.log_already_authorized)
			ShizukuState.NOT_GRANTED -> shizukuRepo.requestPermission()
		}
	}
}
