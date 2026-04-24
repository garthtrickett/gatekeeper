package com.aegisgatekeeper.app.media

import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.GatekeeperAction
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

actual object MediaDownloader {
    private val client = HttpClient(CIO)
    private val scope = CoroutineScope(Dispatchers.IO)

    actual fun enqueueDownload(id: String, url: String) {
        scope.launch {
            try {
                val osName = System.getProperty("os.name").lowercase()
                val userHome = System.getProperty("user.home")
                val cacheDirPath = when {
                    osName.contains("win") -> System.getenv("APPDATA") + File.separator + "gatekeeper" + File.separator + "downloads"
                    osName.contains("mac") -> userHome + File.separator + "Library" + File.separator + "Application Support" + File.separator + "gatekeeper" + File.separator + "downloads"
                    else -> userHome + File.separator + ".local" + File.separator + "share" + File.separator + "gatekeeper" + File.separator + "downloads"
                }
                val cacheDir = File(cacheDirPath).apply { mkdirs() }
                val targetFile = File(cacheDir, "$id.mp3")

                val response = client.get(url)
                val channel = response.bodyAsChannel()
                val contentLength = response.headers["Content-Length"]?.toLong() ?: 1L
                var totalBytesRead = 0L

                targetFile.outputStream().use { out ->
                    while (!channel.isClosedForRead) {
                        val packet = channel.readRemaining(1024 * 64)
                        while (packet.isNotEmpty) {
                            val bytes = packet.readBytes()
                            out.write(bytes)
                            totalBytesRead += bytes.size
                            GatekeeperStateManager.dispatch(GatekeeperAction.DownloadProgressUpdated(id, (totalBytesRead.toFloat() / contentLength) * 100f))
                        }
                    }
                }
                GatekeeperStateManager.dispatch(GatekeeperAction.DownloadCompleted(id, targetFile.absolutePath))
            } catch (e: Exception) {
                GatekeeperStateManager.dispatch(GatekeeperAction.DownloadFailed(id))
            }
        }
    }

    actual fun removeDownload(id: String) {
        val osName = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")
        val cacheDirPath = when {
            osName.contains("win") -> System.getenv("APPDATA") + File.separator + "gatekeeper" + File.separator + "downloads"
            osName.contains("mac") -> userHome + File.separator + "Library" + File.separator + "Application Support" + File.separator + "gatekeeper" + File.separator + "downloads"
            else -> userHome + File.separator + ".local" + File.separator + "share" + File.separator + "gatekeeper" + File.separator + "downloads"
        }
        val targetFile = File(cacheDirPath, "$id.mp3")
        if (targetFile.exists()) {
            targetFile.delete()
        }
    }
}
