package com.example.statusbarhider

import android.os.ParcelFileDescriptor
import rikka.shizuku.Shizuku
import moe.shizuku.server.IShizukuService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object ShizukuCmd {

    data class Result(val exit: Int, val out: String)

    fun granted(): Boolean =
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun run(cmd: String, timeoutSec: Long = 15): Result {
        val binder = Shizuku.getBinder() ?: return Result(-1, "")
        val service = IShizukuService.Stub.asInterface(binder)
        val remote = try {
            service.newProcess(arrayOf("sh", "-c", cmd), null, null)
        } catch (_: Exception) {
            return Result(-1, "")
        }

        val pool = Executors.newSingleThreadExecutor()
        try {
            val future = pool.submit<String> {
                ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream)
                    .bufferedReader().readText().trim()
            }
            val out = try {
                future.get(timeoutSec, TimeUnit.SECONDS)
            } catch (_: Exception) {
                ""
            }
            return Result(0, out)
        } finally {
            pool.shutdownNow()
            try { remote.destroy() } catch (_: Exception) {}
        }
    }
}
