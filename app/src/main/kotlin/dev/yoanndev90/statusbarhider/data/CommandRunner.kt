package dev.yoanndev90.statusbarhider.data

import dev.yoanndev90.statusbarhider.ShizukuCmd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs Shizuku shell commands off the main thread. */
object CommandRunner {
	suspend fun run(
		cmd: String,
		timeoutSec: Long = 15
	): ShizukuCmd.Result = withContext(Dispatchers.IO) { ShizukuCmd.run(cmd, timeoutSec) }
}
