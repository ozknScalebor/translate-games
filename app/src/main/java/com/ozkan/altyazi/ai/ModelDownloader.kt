package com.ozkan.altyazi.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Streams a large file to disk with resume support: an interrupted download continues from the
 * partial file instead of starting over. The final file only appears once complete.
 */
object ModelDownloader {

    private const val BUFFER = 256 * 1024

    suspend fun download(url: String, target: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val partial = File(target.path + ".part")
        val already = if (partial.exists()) partial.length() else 0L

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            if (already > 0) setRequestProperty("Range", "bytes=$already-")
        }
        try {
            val code = connection.responseCode
            val resumed = code == HttpURLConnection.HTTP_PARTIAL
            if (code != HttpURLConnection.HTTP_OK && !resumed) throw IOException("HTTP $code")

            val offset = if (resumed) already else 0L
            val total = connection.contentLengthLong.let { if (it > 0) it + offset else -1L }

            RandomAccessFile(partial, "rw").use { out ->
                out.setLength(offset)
                out.seek(offset)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER)
                    var done = offset
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            if (!partial.renameTo(target)) throw IOException("Could not finalize ${target.name}")
        } finally {
            connection.disconnect()
        }
    }
}
