package io.horizontalsystems.walletkit.core

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import android.util.Log
import io.horizontalsystems.walletkit.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug-only startup trace. Writes Downloads/tariffia-startup-log.txt on the device so the trace
 * can be read without a computer. Does nothing in release builds.
 */
object StartupTrace {
    private const val TAG = "StartupTrace"
    private const val FILE_NAME = "tariffia-startup-log.txt"
    private const val RELATIVE_PATH = "Download/"

    private val buffer = StringBuilder()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var context: Context? = null

    fun init(base: Context) {
        if (!BuildConfig.DEBUG) return
        context = base
        log("process start pid=${Process.myPid()} sdk=${Build.VERSION.SDK_INT} model=${Build.MODEL}")
    }

    @Synchronized
    fun log(message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        val line = buildString {
            append(timeFormat.format(Date())).append(' ').append(message).append('\n')
            if (throwable != null) append(throwable.stackTraceToString())
        }
        Log.w(TAG, line)
        buffer.append(line)
        context?.let { write(it, buffer.toString()) }
    }

    // Never throws: a failing trace must not change the behaviour being traced.
    private fun write(context: Context, text: String) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            resolver.delete(
                collection,
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(FILE_NAME, RELATIVE_PATH)
            )
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: return
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
        } catch (e: Throwable) {
            Log.e(TAG, "startup trace write failed", e)
        }
    }
}
