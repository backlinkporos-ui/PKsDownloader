package com.pks.downloader

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class PkApp : Application() {
    @Volatile var engineReady = false
        private set

    override fun onCreate() {
        super.onCreate()
        // First launch unpacks python + ffmpeg, so do it off the main thread.
        Thread {
            try {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
            } catch (e: Exception) {
                Log.e("PkApp", "Engine init failed", e)
                return@Thread
            }
            // YouTube changes often, so pull the newest yt-dlp on every launch.
            try {
                YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel.NIGHTLY)
            } catch (e: Exception) {
                Log.e("PkApp", "yt-dlp update failed", e)
            }
            engineReady = true
        }.start()
    }
}
