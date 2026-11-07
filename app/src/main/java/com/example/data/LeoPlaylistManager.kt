package com.example.data

import android.content.Context
import android.net.Uri
import com.example.model.LeoPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object LeoPlaylistManager {

    private const val PLAYLISTS_DIR = "leo_playlists"

    suspend fun saveToUri(context: Context, uri: Uri, playlist: LeoPlaylist): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(playlist.toJsonString())
                    writer.flush()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun loadFromUri(context: Context, uri: Uri): LeoPlaylist? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    val content = reader.readText()
                    LeoPlaylist.fromJsonString(content)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveLocally(context: Context, playlist: LeoPlaylist): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, PLAYLISTS_DIR).apply { if (!exists()) mkdirs() }
            val sanitized = playlist.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(dir, "$sanitized.leo")
            file.writeText(playlist.toJsonString(), Charsets.UTF_8)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun listLocalPlaylists(context: Context): List<LeoPlaylist> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, PLAYLISTS_DIR)
        if (!dir.exists()) return@withContext emptyList()
        val list = mutableListOf<LeoPlaylist>()
        dir.listFiles { _, name -> name.endsWith(".leo") }?.forEach { file ->
            try {
                val content = file.readText(Charsets.UTF_8)
                list.add(LeoPlaylist.fromJsonString(content))
            } catch (_: Exception) {}
        }
        list.sortedByDescending { it.createdAt }
    }

    suspend fun deleteLocal(context: Context, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, PLAYLISTS_DIR)
            val sanitized = name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(dir, "$sanitized.leo")
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }
}
