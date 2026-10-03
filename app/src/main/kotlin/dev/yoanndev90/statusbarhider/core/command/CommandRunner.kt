package dev.yoanndev90.statusbarhider.core.command

import android.content.Context
import dev.yoanndev90.statusbarhider.core.shizuku.ShizukuCmd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs Shizuku shell commands off the main thread. */
object CommandRunner {
	suspend fun run(
		context: Context,
		cmd: String,
		timeoutSec: Long = 15
	): ShizukuCmd.Result = withContext(Dispatchers.IO) { ShizukuCmd.run(context, cmd, timeoutSec) }
}
