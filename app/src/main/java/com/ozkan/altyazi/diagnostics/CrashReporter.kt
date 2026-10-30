package com.ozkan.altyazi.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Explains why the app closed last time, so a crash on the tablet can be reported without a
 * computer: Java crashes are written to a file as they happen; native crashes and ANRs are read
 * back from Android's exit records (with the readable part of the tombstone) on the next start.
 * The recent Remote Play log lines are added, since most native code belongs to it.
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"
    private const val PREFS = "diagnostics"
    private const val KEY_SEEN = "seen_exit_ts"

    fun install(context: Context) {
        val file = File(context.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file.writeText(buildString {
                    append("Java crash on thread ").append(thread.name).append(" at ").append(now()).append('\n')
                    append(appVersion(context)).append('\n')
                    append(error.stackTraceToString())
                })
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The report for the last unexpected exit, once; null when the app ended normally. */
    fun takeReport(context: Context): String? {
        val parts = mutableListOf<String>()
        val file = File(context.filesDir, FILE)
        if (file.exists()) {
            parts += runCatching { file.readText() }.getOrDefault("")
            file.delete()
        }
        exitRecord(context)?.let { parts += it }
        if (parts.isEmpty()) return null
        remotePlayLog()?.let { parts += "Remote Play log:\n$it" }
        return parts.joinToString("\n\n").take(MAX_REPORT)
    }

    private fun exitRecord(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val am = context.getSystemService(ActivityManager::class.java) ?: return null
        val info = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 1) }.getOrNull()?.firstOrNull() ?: return null
        if (info.timestamp <= prefs.getLong(KEY_SEEN, 0L)) return null
        prefs.edit().putLong(KEY_SEEN, info.timestamp).apply()

        val reason = when (info.reason) {
            ApplicationExitInfo.REASON_CRASH -> "Java crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
            ApplicationExitInfo.REASON_ANR -> "Not responding (ANR)"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "Closed by the system: low memory"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Closed by the system: excessive resource use"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Initialization failure"
            else -> return null // normal exits, user swipes, updates
        }
        return buildString {
            append(reason).append(" at ").append(format(info.timestamp)).append('\n')
            append(appVersion(context)).append('\n')
            info.description?.let { append("Description: ").append(it).append('\n') }
            append("Memory at exit: ").append(info.pss / 1024).append(" MB PSS\n")
            val trace = runCatching { info.traceInputStream?.use { readable(it, info.reason) } }.getOrNull()
            if (!trace.isNullOrBlank()) append('\n').append(trace)
        }
    }

    /**
     * ANR traces are text; native tombstones are protobuf, so only their readable strings are kept
     * (signal, abort message, frame function names and libraries).
     */
    private fun readable(stream: InputStream, reason: Int): String {
        val bytes = stream.readBytes()
        if (reason == ApplicationExitInfo.REASON_ANR) return String(bytes).lineSequence().take(120).joinToString("\n")
        val strings = mutableListOf<String>()
        val current = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) current.append(c.toChar())
            else {
                if (current.length >= 4) strings += current.toString()
                current.setLength(0)
            }
        }
        if (current.length >= 4) strings += current.toString()
        return strings.filter { s ->
            s.contains(".so") || s.contains("SIG") || s.contains("abort", ignoreCase = true) ||
                s.contains("chiaki") || s.contains("altyazi") || s.contains("Java_") || s.startsWith("_Z") || s.contains("fault")
        }.distinct().take(80).joinToString("\n")
    }

    /** This app's own recent Remote Play log lines (apps may read their own log). */
    private fun remotePlayLog(): String? = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-t", "60", "-s", "RemotePlay:*").redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }.trim().takeIf { it.lines().size > 1 }
    }.getOrNull()

    private fun appVersion(context: Context): String = runCatching {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        "App ${p.versionName} · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
    }.getOrDefault("")

    private fun now() = format(System.currentTimeMillis())
    private fun format(ts: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ts))

    private const val MAX_REPORT = 12_000
}
