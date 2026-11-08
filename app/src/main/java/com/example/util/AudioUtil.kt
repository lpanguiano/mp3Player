package com.example.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

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
            if (title == "Audio" && uri.lastPathSegment != null) {
                title = uri.lastPathSegment?.substringAfterLast('/') ?: "audio.mp3"
            }
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        // Extract actual PCM stereo waveform directly from decoded audio
        val (waveformCh1, waveformCh2) = extractRealPcmStereoWaveform(context, uri, durationMs, targetBars = 240)

        AudioTrack(
            uriString = uri.toString(),
            title = title,
            durationMs = durationMs,
            waveformPoints = waveformCh1,
            waveformPointsCh2 = waveformCh2
        )
    }

    /**
     * Extracts true audio stereo waveform amplitudes across the ENTIRE audio track by decoding
     * actual 16-bit PCM audio samples (Left and Right channels) via MediaExtractor and MediaCodec.
     * Maps all frames from second 0 to the exact end of the audio track with zero early cutoff.
     */
    private fun extractRealPcmStereoWaveform(
        context: Context,
        uri: Uri,
        durationMs: Long,
        targetBars: Int = 240
    ): Pair<List<Float>, List<Float>> {
        val ch1FramePeaks = ArrayList<Float>()
        val ch2FramePeaks = ArrayList<Float>()
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex != -1 && audioFormat != null) {
                extractor.selectTrack(audioTrackIndex)
                val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else 2

                val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mpeg"
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(audioFormat, null, null, 0)
                codec.start()

                val bufferInfo = MediaCodec.BufferInfo()
                var inputEos = false
                var outputEos = false
                var idleCycles = 0
                val maxFrames = 60000 // Covers over 25 minutes of full-fidelity frames

                while (!outputEos && ch1FramePeaks.size < maxFrames) {
                    var didWork = false

                    if (!inputEos) {
                        val inIndex = codec.dequeueInputBuffer(0L)
                        if (inIndex >= 0) {
                            val inBuffer = codec.getInputBuffer(inIndex)
                            inBuffer?.clear()
                            val sampleSize = inBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                            if (sampleSize > 0) {
                                val sampleTime = extractor.sampleTime
                                codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                                extractor.advance()
                                didWork = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                                didWork = true
                            }
                        }
                    }

                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
                    if (outIndex >= 0) {
                        didWork = true
                        idleCycles = 0
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputEos = true
                        }
                        val outBuffer = codec.getOutputBuffer(outIndex)
                        if (outBuffer != null && bufferInfo.size > 0) {
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuffer = outBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

                            var peak1 = 0f
                            var peak2 = 0f
                            while (shortBuffer.hasRemaining()) {
                                val s1 = abs(shortBuffer.get().toInt()) / 32768f
                                val s2 = if (channelCount >= 2 && shortBuffer.hasRemaining()) {
                                    abs(shortBuffer.get().toInt()) / 32768f
                                } else s1
                                if (s1 > peak1) peak1 = s1
                                if (s2 > peak2) peak2 = s2
                            }
                            ch1FramePeaks.add(peak1)
                            ch2FramePeaks.add(peak2)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                    }

                    if (!didWork) {
                        idleCycles++
                        if (idleCycles > 50) {
                            if (inputEos) {
                                break
                            } else {
                                try {
                                    Thread.sleep(1)
                                } catch (_: Exception) {}
                                if (idleCycles > 200) break
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }

        // Resample collected frames across the ENTIRE audio track into targetBars (0% to 100% width)
        val totalFrames = ch1FramePeaks.size
        if (totalFrames > 0) {
            val ch1Bars = FloatArray(targetBars)
            val ch2Bars = FloatArray(targetBars)
            val framesPerBar = totalFrames.toDouble() / targetBars.toDouble()

            for (bar in 0 until targetBars) {
                val startFrame = (bar * framesPerBar).toInt().coerceIn(0, totalFrames - 1)
                val endFrame = ((bar + 1) * framesPerBar).toInt().coerceIn(startFrame + 1, totalFrames)

                var max1 = 0f
                var max2 = 0f
                for (f in startFrame until endFrame) {
                    if (ch1FramePeaks[f] > max1) max1 = ch1FramePeaks[f]
                    if (ch2FramePeaks[f] > max2) max2 = ch2FramePeaks[f]
                }
                ch1Bars[bar] = max1
                ch2Bars[bar] = max2
            }

            return Pair(ch1Bars.toList(), ch2Bars.toList())
        }

        // Fallback: flat zero baseline if audio cannot be decoded
        return Pair(List(targetBars) { 0f }, List(targetBars) { 0f })
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
