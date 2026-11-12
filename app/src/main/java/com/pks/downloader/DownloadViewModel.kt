package com.pks.downloader

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Quality(val label: String) {
    BEST("Best"), P720("720p"), P480("480p"), AUDIO("MP3")
}

data class VideoMeta(
    val title: String,
    val thumbnail: String?,
    val uploader: String?,
    val durationSec: Int
)

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    var url by mutableStateOf("")
    var meta by mutableStateOf<VideoMeta?>(null)
    var quality by mutableStateOf(Quality.BEST)
    var fetching by mutableStateOf(false)

    // Download state lives in Downloader so it survives the Activity.
    val downloading: Boolean get() = Downloader.downloading
    val progress: Float get() = Downloader.progress
    val history: List<HistoryItem> get() = Downloader.history
    var message: String?
        get() = Downloader.message
        set(v) { Downloader.message = v }

    fun fetchInfo() {
        val link = url.trim()
        if (link.isEmpty() || fetching || downloading) return
        fetching = true
        meta = null
        message = null
        viewModelScope.launch {
            try {
                val pk = getApplication<PkApp>()
                var waited = 0
                while (!pk.engineReady && waited < 120) { delay(500); waited++ }
                if (!pk.engineReady) { message = "Engine is not ready yet. Try again in a moment."; return@launch }

                val info = withContext(Dispatchers.IO) {
                    val req = YoutubeDLRequest(link)
                    req.addOption("--no-playlist")
                    YoutubeDL.getInstance().getInfo(req)
                }
                meta = VideoMeta(
                    title = info.title ?: "Untitled",
                    thumbnail = info.thumbnail,
                    uploader = info.uploader,
                    durationSec = info.duration
                )
            } catch (e: Exception) {
                message = "Could not read this link: ${e.message?.take(160)}"
            } finally {
                fetching = false
            }
        }
    }

    fun startDownload() {
        val m = meta ?: return
        if (Downloader.downloading) return
        Downloader.downloading = true
        Downloader.progress = 0f
        Downloader.message = null
        val ctx = getApplication<PkApp>()
        val i = Intent(ctx, DownloadService::class.java)
            .putExtra(DownloadService.EXTRA_URL, url.trim())
            .putExtra(DownloadService.EXTRA_TITLE, m.title)
            .putExtra(DownloadService.EXTRA_QUALITY, quality.name)
        ContextCompat.startForegroundService(ctx, i)
    }

    fun cancel() = Downloader.cancel()
}
