package com.pks.downloader

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yausername.youtubedl_android.YoutubeDL
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(
    val id: Long,
    val title: String,
    val quality: String,
    val ok: Boolean,
    val uri: String? = null,
    val mime: String? = null
)

/** Shared download state: written by DownloadService, read by the UI. */
object Downloader {
    const val PROCESS_ID = "pk-download"
    private const val PREFS = "pk_history"
    private const val KEY = "items"

    private var appCtx: Context? = null

    var downloading by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var message by mutableStateOf<String?>(null)
    var history by mutableStateOf<List<HistoryItem>>(emptyList())

    /** Call once from Application.onCreate: loads the saved download list. */
    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        history = load(ctx.applicationContext)
    }

    fun cancel() {
        YoutubeDL.getInstance().destroyProcessById(PROCESS_ID)
    }

    fun addHistory(item: HistoryItem) {
        history = listOf(item) + history
        save()
    }

    /** Removes items from the list; optionally deletes their files from the phone too. */
    fun remove(ids: Set<Long>, deleteFiles: Boolean) {
        val ctx = appCtx ?: return
        if (deleteFiles) {
            history.filter { it.id in ids }.forEach { h ->
                h.uri?.let { Storage.delete(ctx, Uri.parse(it)) }
            }
        }
        history = history.filterNot { it.id in ids }
        save()
    }

    private fun save() {
        val ctx = appCtx ?: return
        val arr = JSONArray()
        history.forEach { h ->
            arr.put(JSONObject().apply {
                put("id", h.id)
                put("title", h.title)
                put("quality", h.quality)
                put("ok", h.ok)
                put("uri", h.uri ?: JSONObject.NULL)
                put("mime", h.mime ?: JSONObject.NULL)
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(ctx: Context): List<HistoryItem> {
        return try {
            val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?: return emptyList()
            val arr = JSONArray(s)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HistoryItem(
                    id = o.getLong("id"),
                    title = o.getString("title"),
                    quality = o.getString("quality"),
                    ok = o.getBoolean("ok"),
                    uri = if (o.isNull("uri")) null else o.getString("uri"),
                    mime = if (o.isNull("mime")) null else o.getString("mime")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
