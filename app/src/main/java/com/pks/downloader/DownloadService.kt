package com.pks.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class DownloadService : Service() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_QUALITY = "quality"
        const val ACTION_CANCEL = "com.pks.downloader.CANCEL"
        private const val CH_PROGRESS = "pk_progress"
        private const val CH_DONE = "pk_done"
        private const val ID_PROGRESS = 101
        private const val ID_DONE = 102
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            Downloader.cancel()
            return START_NOT_STICKY
        }
        val url = intent?.getStringExtra(EXTRA_URL)
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Video"
        val quality = runCatching { Quality.valueOf(intent?.getStringExtra(EXTRA_QUALITY) ?: "") }
            .getOrDefault(Quality.BEST)
        createChannels()

        // Must be called quickly after startForegroundService().
        ServiceCompat.startForeground(
            this, ID_PROGRESS, progressNotification(title, 0, true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        if (url == null) {
            Downloader.downloading = false
            stopSelf()
            return START_NOT_STICKY
        }
        runDownload(url, title, quality)
        return START_NOT_STICKY
    }

    private fun runDownload(url: String, title: String, q: Quality) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PKsDownloader:dl").apply {
            acquire(2 * 60 * 60 * 1000L)
        }
        Downloader.downloading = true
        Downloader.progress = 0f
        Downloader.message = null

        scope.launch {
            val tmp = File(cacheDir, "dl")
            var ok = false
            var error: String? = null
            try {
                val app = application as PkApp
                var waited = 0
                while (!app.engineReady && waited < 120) { delay(500); waited++ }
                if (!app.engineReady) throw IllegalStateException("Engine is not ready")

                tmp.deleteRecursively(); tmp.mkdirs()
                val req = YoutubeDLRequest(url)
                req.addOption("--no-playlist")
                req.addOption("--no-mtime")
                req.addOption("-o", tmp.absolutePath + "/%(title).80s.%(ext)s")
                when (q) {
                    Quality.BEST -> {
                        req.addOption("-f", "bv*[height<=1080]+ba/b[height<=1080]/b")
                        req.addOption("--merge-output-format", "mp4")
                    }
                    Quality.P720 -> {
                        req.addOption("-f", "bv*[height<=720]+ba/b[height<=720]/b")
                        req.addOption("--merge-output-format", "mp4")
                    }
                    Quality.P480 -> {
                        req.addOption("-f", "bv*[height<=480]+ba/b[height<=480]/b")
                        req.addOption("--merge-output-format", "mp4")
                    }
                    Quality.AUDIO -> {
                        req.addOption("-f", "bestaudio/best")
                        req.addOption("-x")
                        req.addOption("--audio-format", "mp3")
                    }
                }

                var lastPct = -1
                YoutubeDL.getInstance().execute(req, Downloader.PROCESS_ID) { p, _, _ ->
                    val pct = p.toInt().coerceIn(0, 100)
                    Downloader.progress = pct / 100f
                    if (pct != lastPct) {
                        lastPct = pct
                        notify(ID_PROGRESS, progressNotification(title, pct, false))
                    }
                }

                val files = tmp.listFiles()
                    ?.filter { it.isFile && it.extension != "part" && it.extension != "ytdl" }
                    .orEmpty()
                ok = files.isNotEmpty() && files.all { Storage.saveToDownloads(this@DownloadService, it) }
                if (!ok) error = "Download finished but saving failed."
            } catch (e: YoutubeDL.CanceledException) {
                error = "Cancelled"
            } catch (e: Exception) {
                error = "Download failed: ${e.message?.take(160)}"
            } finally {
                tmp.deleteRecursively()
                Downloader.history = listOf(HistoryItem(title, q.label, ok)) + Downloader.history
                Downloader.message = if (ok) "Saved to Downloads/${Storage.FOLDER}" else error
                if (ok) Downloader.progress = 1f
                Downloader.downloading = false
                wakeLock?.let { if (it.isHeld) it.release() }

                ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                notify(ID_DONE, doneNotification(title, ok, error))
                stopSelf()
            }
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun progressNotification(title: String, pct: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (indeterminate) "Starting…" else "$pct%")
            .setProgress(100, pct, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .addAction(
                0, "Cancel",
                PendingIntent.getService(
                    this, 1,
                    Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    private fun doneNotification(title: String, ok: Boolean, error: String?) =
        NotificationCompat.Builder(this, CH_DONE)
            .setSmallIcon(
                if (ok) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setContentTitle(if (ok) "Download complete" else "Download stopped")
            .setContentText(if (ok) title else (error ?: title))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun notify(id: Int, n: android.app.Notification) {
        try {
            NotificationManagerCompat.from(this).notify(id, n)
        } catch (_: SecurityException) {
            // Notification permission denied: the download still runs.
        }
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "Downloads in progress", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
