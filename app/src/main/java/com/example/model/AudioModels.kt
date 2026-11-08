package com.example.model

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AudioTrack(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val title: String,
    val durationMs: Long = 0L,
    val waveformPoints: List<Float> = emptyList(),
    val waveformPointsCh2: List<Float> = emptyList()
) {
    val uri: Uri
        get() = Uri.parse(uriString)

    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("uri", uriString)
        json.put("title", title)
        json.put("durationMs", durationMs)
        return json
    }

    companion object {
        fun fromJsonObject(json: JSONObject): AudioTrack {
            val id = json.optString("id", UUID.randomUUID().toString())
            val uri = json.optString("uri", "")
            val title = json.optString("title", "Audio Desconocido")
            val durationMs = json.optLong("durationMs", 0L)
            return AudioTrack(
                id = id,
                uriString = uri,
                title = title,
                durationMs = durationMs
            )
        }
    }
}

data class LeoPlaylist(
    val name: String = "Nueva Lista",
    val createdAt: Long = System.currentTimeMillis(),
    val tracks: List<AudioTrack> = emptyList()
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("format", "leo-playlist")
        root.put("version", 1)
        root.put("name", name)
        root.put("createdAt", createdAt)

        val array = JSONArray()
        tracks.forEach { track ->
            array.put(track.toJsonObject())
        }
        root.put("tracks", array)
        return root.toString(2)
    }

    companion object {
        fun fromJsonString(jsonStr: String): LeoPlaylist {
            val root = JSONObject(jsonStr)
            val name = root.optString("name", "Lista .leo")
            val createdAt = root.optLong("createdAt", System.currentTimeMillis())
            val tracksList = mutableListOf<AudioTrack>()
            val array = root.optJSONArray("tracks")
            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i)
                    if (item != null) {
                        tracksList.add(AudioTrack.fromJsonObject(item))
                    }
                }
            }
            return LeoPlaylist(name = name, createdAt = createdAt, tracks = tracksList)
        }
    }
}
