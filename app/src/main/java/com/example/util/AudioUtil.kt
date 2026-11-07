package com.example.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

object AudioUtil {

    suspend fun resolveTrackFromUri(context: Context, uri: Uri): AudioTrack = withContext(Dispatchers.IO) {
        var title = "Audio"
        var durationMs = 0L

        // Try getting display name from ContentResolver
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            title = name
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Extract metadata using MediaMetadataRetriever
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            if (!metaTitle.isNullOrBlank()) {
                title = if (!artist.isNullOrBlank()) "$artist - $metaTitle" else metaTitle
            }

            durationMs = durationStr?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            // Fallback to Uri last path segment if title is still default
            if (title == "Audio" && uri.lastPathSegment != null) {
                title = uri.lastPathSegment?.substringAfterLast('/') ?: "audio.mp3"
            }
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        // Generate dense waveform points matching oscilloscope visual
        val waveform = generateWaveformProfile(context, uri, title)

        AudioTrack(
            uriString = uri.toString(),
            title = title,
            durationMs = durationMs,
            waveformPoints = waveform
        )
    }

    private fun generateWaveformProfile(context: Context, uri: Uri, seedSource: String): List<Float> {
        val count = 180
        val points = mutableListOf<Float>()

        // Try reading header/initial bytes for seed
        var seed = seedSource.hashCode().toLong()
        try {
            val input: InputStream? = context.contentResolver.openInputStream(uri)
            val buffer = ByteArray(512)
            val read = input?.read(buffer) ?: 0
            if (read > 0) {
                var sum: Long = 0
                for (i in 0 until read) {
                    sum += abs(buffer[i].toLong()) * (i + 1)
                }
                seed = seed xor sum
            }
            input?.close()
        } catch (_: Exception) {}

        val rng = Random(seed)
        for (i in 0 until count) {
            val progress = i.toFloat() / count.toFloat()
            // Envelope: quieter at start and end, energetic in middle
            val envelope = sin(progress * Math.PI).toFloat().coerceIn(0.15f, 1.0f)
            val noise = rng.nextFloat()
            val burst = if (rng.nextFloat() > 0.82f) 0.35f else 0f
            val amplitude = ((noise * 0.7f + burst) * envelope).coerceIn(0.08f, 0.98f)
            points.add(amplitude)
        }

        return points
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
