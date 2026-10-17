package com.bazyak.payfacade.common

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "PayFacade"
private const val LOG_NAME = "pay-facade.log"
private const val MAX_BYTES = 256 * 1024

/**
 * Appends diagnostics to a file inside the app's own external directory, so a session can be
 * reviewed afterwards instead of requiring logcat to be attached at the time - which is not
 * practical while actually paying at a terminal.
 *
 * The file is at Android/data/com.bazyak.payfacade/files/pay-facade.log and needs no permission
 * to pull over adb. It is capped: once past the limit the current file becomes .old and a fresh
 * one starts, so two runs' worth is always available and nothing grows without bound.
 */
object FileLog {

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private var logFile: File? = null

    fun init(context: Context) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        logFile = File(dir, LOG_NAME)
        write("--- session started, version ${appVersion(context)} ---")
    }

    @Synchronized
    fun write(message: String) {
        Log.d(TAG, message)
        val file = logFile ?: return
        try {
            if (file.length() > MAX_BYTES) {
                file.copyTo(File(file.parentFile, "$LOG_NAME.old"), overwrite = true)
                file.writeText("")
            }
            file.appendText("${timeFormat.format(Date())}  $message\n")
        } catch (e: Exception) {
            Log.w(TAG, "cannot write log file", e)
        }
    }

    private fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
}
