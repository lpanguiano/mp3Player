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
        if (waveformPoints.isNotEmpty()) {
            val sb = StringBuilder()
            for (i in waveformPoints.indices) {
                if (i > 0) sb.append(",")
                sb.append(String.format(java.util.Locale.US, "%.3f", waveformPoints[i]))
            }
            json.put("wf1", sb.toString())
        }
        if (waveformPointsCh2.isNotEmpty()) {
            val sb = StringBuilder()
            for (i in waveformPointsCh2.indices) {
                if (i > 0) sb.append(",")
                sb.append(String.format(java.util.Locale.US, "%.3f", waveformPointsCh2[i]))
            }
            json.put("wf2", sb.toString())
        }
        return json
    }

    companion object {
        fun fromJsonObject(json: JSONObject): AudioTrack {
            val id = json.optString("id", UUID.randomUUID().toString())
            val uri = json.optString("uri", "")
            val title = json.optString("title", "Audio Desconocido")
            val durationMs = json.optLong("durationMs", 0L)
            val wf1Str = json.optString("wf1", "")
            val wf1 = if (wf1Str.isNotBlank()) {
                wf1Str.split(",").mapNotNull { it.toFloatOrNull() }
            } else emptyList()
            val wf2Str = json.optString("wf2", "")
            val wf2 = if (wf2Str.isNotBlank()) {
                wf2Str.split(",").mapNotNull { it.toFloatOrNull() }
            } else emptyList()

            return AudioTrack(
                id = id,
                uriString = uri,
                title = title,
                durationMs = durationMs,
                waveformPoints = wf1,
                waveformPointsCh2 = wf2
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

data class LoadingState(
    val isLoading: Boolean = false,
    val isAnalyzingSpectrum: Boolean = false,
    val fileName: String = "",
    val progress: Float = 0f,
    val currentItem: Int = 1,
    val totalItems: Int = 1
)
