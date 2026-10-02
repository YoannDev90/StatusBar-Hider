package dev.yoanndev90.statusbarhider

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object ShizukuCmd {
	private const val TAG = "ShizukuCmd"

	data class Result(
		val exit: Int,
		val out: String
	)

	private val executor = Executors.newCachedThreadPool()

	fun granted(): Boolean =
		Shizuku.pingBinder() &&
			Shizuku.checkSelfPermission() ==
			android.content.pm.PackageManager.PERMISSION_GRANTED

	fun run(
		context: Context,
		cmd: String,
		timeoutSec: Long = 15
	): Result {
		val binder = Shizuku.getBinder() ?: return Result(-1, context.getString(R.string.err_shizuku_binder_unavailable))
		val service = IShizukuService.Stub.asInterface(binder)
		val remote =
			try {
				service.newProcess(arrayOf("sh", "-c", cmd), null, null)
			} catch (e: Exception) {
				Log.e(TAG, "Failed to start process for: $cmd", e)
				return Result(-1, context.getString(R.string.err_start_process, e.message))
			}

		try {
			val future =
				executor.submit<String> {
					ParcelFileDescriptor
						.AutoCloseInputStream(remote.inputStream)
						.bufferedReader()
						.readText()
						.trim()
				}
			val out =
				try {
					future.get(timeoutSec, TimeUnit.SECONDS)
				} catch (e: TimeoutException) {
					future.cancel(true)
					Log.w(TAG, "Command timed out after ${timeoutSec}s: $cmd")
					return Result(-1, context.getString(R.string.err_command_timeout, timeoutSec))
				}
			return Result(0, out)
		} finally {
			try {
				remote.destroy()
			} catch (e: Exception) {
				Log.w(TAG, "Failed to destroy remote process", e)
			}
		}
	}
}
