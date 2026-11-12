package com.pks.downloader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yausername.youtubedl_android.YoutubeDL

data class HistoryItem(val title: String, val quality: String, val ok: Boolean)

/** Shared download state: written by DownloadService, read by the UI. */
object Downloader {
    const val PROCESS_ID = "pk-download"

    var downloading by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var message by mutableStateOf<String?>(null)
    var history by mutableStateOf<List<HistoryItem>>(emptyList())

    fun cancel() {
        YoutubeDL.getInstance().destroyProcessById(PROCESS_ID)
    }
}
