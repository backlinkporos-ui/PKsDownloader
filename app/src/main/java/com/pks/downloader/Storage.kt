package com.pks.downloader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object Storage {
    const val FOLDER = "PK Downloader"

    fun mimeFor(file: File): String = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        else -> "video/mp4"
    }

    /** Copies a finished file into the public Downloads/PK Downloader folder. Returns its Uri, or null on failure. */
    fun saveToDownloads(ctx: Context, file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeFor(file))
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /** Deletes a saved file from the phone. Returns false if it was already gone or could not be removed. */
    fun delete(ctx: Context, uri: Uri): Boolean = try {
        ctx.contentResolver.delete(uri, null, null) > 0
    } catch (e: Exception) {
        false
    }

    fun exists(ctx: Context, uri: Uri): Boolean = try {
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (e: Exception) {
        false
    }
}
