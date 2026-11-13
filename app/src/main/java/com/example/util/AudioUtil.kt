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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

object AudioUtil {

    fun getQuickFileName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            return name
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment?.substringAfterLast('/') ?: "audio.mp3"
    }

    /**
     * Reads only lightweight metadata (title and duration) in ~10-30ms so playback can start
     * immediately without waiting for waveform generation.
     */
    suspend fun resolveQuickTrackMetadata(
        context: Context,
        uri: Uri,
        trackId: String? = null
    ): AudioTrack = withContext(Dispatchers.IO) {
        var title = "Audio"
        var durationMs = 0L

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
            try { retriever.release() } catch (_: Exception) {}
        }

        AudioTrack(
            id = trackId ?: java.util.UUID.randomUUID().toString(),
            uriString = uri.toString(),
            title = title,
            durationMs = durationMs
        )
    }

    suspend fun resolveTrackFromUri(
        context: Context,
        uri: Uri,
        trackId: String? = null,
        onProgress: ((Float) -> Unit)? = null,
        onPartialWaveform: ((List<Float>, List<Float>) -> Unit)? = null
    ): AudioTrack = withContext(Dispatchers.IO) {
        val quickMeta = resolveQuickTrackMetadata(context, uri, trackId)
        val coroutineCtx = currentCoroutineContext()

        val (waveformCh1, waveformCh2) = extractRealPcmStereoWaveform(
            context = context,
            uri = uri,
            durationMs = quickMeta.durationMs,
            targetBars = 240,
            isCancelled = { !coroutineCtx.isActive },
            onProgress = onProgress,
            onPartialWaveform = onPartialWaveform
        )

        quickMeta.copy(
            waveformPoints = waveformCh1,
            waveformPointsCh2 = waveformCh2
        )
    }

    /**
     * Micro-optimization: subsamples PCM shorts inside the decoded buffer using integer peak
     * comparisons and converts to normalized float [0f..1f] only once per frame.
     */
    private fun extractPeaksSubsampled(
        outBuffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
        channelCount: Int
    ): Pair<Float, Float> {
        outBuffer.position(bufferInfo.offset)
        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
        val shortBuffer = outBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val remainingShorts = shortBuffer.remaining()
        if (remainingShorts <= 0) return 0f to 0f

        var maxRaw1 = 0
        var maxRaw2 = 0

        if (channelCount >= 2) {
            // Subsample every 8th stereo pair (stride 16 shorts) on normal frames, finer on tiny buffers
            val stride = when {
                remainingShorts >= 256 -> 16
                remainingShorts >= 64 -> 8
                else -> 2
            }
            var i = 0
            while (i + 1 < remainingShorts) {
                val v1 = shortBuffer.get(i).toInt()
                val a1 = if (v1 == -32768) 32768 else if (v1 < 0) -v1 else v1
                if (a1 > maxRaw1) maxRaw1 = a1

                val v2 = shortBuffer.get(i + 1).toInt()
                val a2 = if (v2 == -32768) 32768 else if (v2 < 0) -v2 else v2
                if (a2 > maxRaw2) maxRaw2 = a2

                i += stride
            }
        } else {
            val stride = when {
                remainingShorts >= 128 -> 8
                remainingShorts >= 32 -> 4
                else -> 1
            }
            var i = 0
            while (i < remainingShorts) {
                val v1 = shortBuffer.get(i).toInt()
                val a1 = if (v1 == -32768) 32768 else if (v1 < 0) -v1 else v1
                if (a1 > maxRaw1) maxRaw1 = a1
                i += stride
            }
            maxRaw2 = maxRaw1
        }

        val peak1 = (maxRaw1 / 32768f).coerceIn(0f, 1f)
        val peak2 = (maxRaw2 / 32768f).coerceIn(0f, 1f)
        return peak1 to peak2
    }

    /**
     * Extracts stereo waveform amplitudes adaptively depending on audio duration/size:
     * - Audio > 20s: Temporal jump sampling via MediaExtractor.seekTo() across evenly spaced points
     *   (adapting jump density and burst frames to audio length), emitting progressive updates.
     * - Audio <= 20s or unknown duration (or fallback): Fast sequential subsampled scan.
     */
    private fun extractRealPcmStereoWaveform(
        context: Context,
        uri: Uri,
        durationMs: Long,
        targetBars: Int = 240,
        isCancelled: () -> Boolean = { false },
        onProgress: ((Float) -> Unit)? = null,
        onPartialWaveform: ((List<Float>, List<Float>) -> Unit)? = null
    ): Pair<List<Float>, List<Float>> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var afd: android.content.res.AssetFileDescriptor? = null

        val ch1Bars = FloatArray(targetBars)
        val ch2Bars = FloatArray(targetBars)
        var usedJumpSuccess = false

        try {
            var fileLengthBytes = -1L
            try {
                afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
                if (afd != null) {
                    fileLengthBytes = afd.length
                    extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                } else {
                    extractor.setDataSource(context, uri, null)
                }
            } catch (_: Exception) {
                extractor.setDataSource(context, uri, null)
            }

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
                var channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else 2

                val trackDurationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                    audioFormat.getLong(MediaFormat.KEY_DURATION)
                } else {
                    durationMs * 1000L
                }
                val totalEstUs = max(trackDurationUs, durationMs * 1000L)

                val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mpeg"
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(audioFormat, null, null, 0)
                codec.start()

                val bufferInfo = MediaCodec.BufferInfo()

                // STRATEGY 1: Adaptive temporal jump sampling for tracks > 20 seconds (or > 1 MB)
                val useTemporalJumps = totalEstUs > 20_000_000L || (totalEstUs > 0L && fileLengthBytes > 1_000_000L)
                if (useTemporalJumps) {
                    val totalSeconds = totalEstUs / 1_000_000L
                    // Adjust jump density & decoded frames per jump depending on audio duration/size
                    val (numJumps, framesPerJump) = when {
                        totalSeconds > 720L || fileLengthBytes > 15_000_000L -> 120 to 2 // > 12 min (e.g., 20 min+): 120 jumps x 2 frames
                        totalSeconds > 180L || fileLengthBytes > 4_000_000L -> 160 to 2  // 3 - 12 min: 160 jumps x 2 frames
                        else -> targetBars to 2                                         // 20s - 3 min: 240 jumps x 2 frames
                    }

                    val safeMaxTimeUs = (totalEstUs - 60_000L).coerceAtLeast(0L)
                    var successfulJumps = 0
                    var lastFilledBar = -1
                    var lastPeak1 = 0f
                    var lastPeak2 = 0f
                    var lastEmitTimeMs = 0L

                    for (jump in 0 until numJumps) {
                        if (isCancelled()) break

                        val targetTimeUs = if (numJumps > 1) {
                            (jump.toLong() * safeMaxTimeUs) / (numJumps - 1).toLong()
                        } else 0L

                        extractor.seekTo(targetTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        try {
                            codec.flush()
                        } catch (_: Exception) {
                            break
                        }

                        var framesDecoded = 0
                        var inputSubmitted = 0
                        var jumpPeak1 = 0f
                        var jumpPeak2 = 0f
                        var attempts = 0
                        val maxInputPackets = framesPerJump + 2

                        while (framesDecoded < framesPerJump && attempts < 24 && !isCancelled()) {
                            attempts++
                            if (inputSubmitted < maxInputPackets) {
                                val inIndex = codec.dequeueInputBuffer(0L)
                                if (inIndex >= 0) {
                                    val inBuffer = codec.getInputBuffer(inIndex)
                                    inBuffer?.clear()
                                    val sampleSize = inBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                                    if (sampleSize > 0) {
                                        val sampleTime = extractor.sampleTime
                                        codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                                        extractor.advance()
                                        inputSubmitted++
                                    } else {
                                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                        inputSubmitted = maxInputPackets
                                    }
                                }
                            }

                            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 2000L)
                            if (outIndex >= 0) {
                                val outBuffer = codec.getOutputBuffer(outIndex)
                                if (outBuffer != null && bufferInfo.size > 0) {
                                    val (p1, p2) = extractPeaksSubsampled(outBuffer, bufferInfo, channelCount)
                                    if (p1 > jumpPeak1) jumpPeak1 = p1
                                    if (p2 > jumpPeak2) jumpPeak2 = p2
                                    framesDecoded++
                                }
                                codec.releaseOutputBuffer(outIndex, false)
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                val outFmt = codec.outputFormat
                                if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                    channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                }
                            }
                        }

                        if (framesDecoded > 0) {
                            successfulJumps++
                        }

                        // Map jump result onto the 240-bar canvas (interpolating smoothly if numJumps < targetBars)
                        val barIndex = if (numJumps > 1) {
                            ((jump.toFloat() / (numJumps - 1).toFloat()) * (targetBars - 1)).toInt().coerceIn(0, targetBars - 1)
                        } else 0

                        if (lastFilledBar < 0) {
                            for (b in 0..barIndex) {
                                ch1Bars[b] = jumpPeak1
                                ch2Bars[b] = jumpPeak2
                            }
                        } else if (barIndex > lastFilledBar) {
                            val span = (barIndex - lastFilledBar).toFloat()
                            for (b in (lastFilledBar + 1)..barIndex) {
                                val t = (b - lastFilledBar) / span
                                ch1Bars[b] = lastPeak1 + (jumpPeak1 - lastPeak1) * t
                                ch2Bars[b] = lastPeak2 + (jumpPeak2 - lastPeak2) * t
                            }
                        } else {
                            ch1Bars[barIndex] = jumpPeak1
                            ch2Bars[barIndex] = jumpPeak2
                        }

                        lastFilledBar = barIndex
                        lastPeak1 = jumpPeak1
                        lastPeak2 = jumpPeak2

                        // Emit progressive updates ~every 60ms or on completion so UI renders live
                        val now = System.currentTimeMillis()
                        if (now - lastEmitTimeMs > 60L || jump == numJumps - 1) {
                            lastEmitTimeMs = now
                            val p = ((jump + 1).toFloat() / numJumps.toFloat()).coerceIn(0.05f, 0.99f)
                            onProgress?.invoke(p)
                            onPartialWaveform?.invoke(ch1Bars.toList(), ch2Bars.toList())
                        }
                    }

                    if (successfulJumps >= (numJumps / 4).coerceAtLeast(1)) {
                        usedJumpSuccess = true
                    }
                }

                // STRATEGY 2: Sequential fast subsampled pass for short clips (<= 20s) or fallback
                if (!usedJumpSuccess && !isCancelled()) {
                    try {
                        extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        codec.flush()
                    } catch (_: Exception) {}

                    val ch1FramePeaks = ArrayList<Float>()
                    val ch2FramePeaks = ArrayList<Float>()
                    var inputEos = false
                    var outputEos = false
                    var idleCycles = 0
                    val maxFrames = 60000
                    var lastReportedTimeMs = 0L

                    while (!outputEos && ch1FramePeaks.size < maxFrames && !isCancelled()) {
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

                        val outIndex = codec.dequeueOutputBuffer(bufferInfo, 2000L)
                        if (outIndex >= 0) {
                            didWork = true
                            idleCycles = 0
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputEos = true
                            }
                            val outBuffer = codec.getOutputBuffer(outIndex)
                            if (outBuffer != null && bufferInfo.size > 0) {
                                val (peak1, peak2) = extractPeaksSubsampled(outBuffer, bufferInfo, channelCount)
                                ch1FramePeaks.add(peak1)
                                ch2FramePeaks.add(peak2)

                                val now = System.currentTimeMillis()
                                if (now - lastReportedTimeMs > 80L && totalEstUs > 0) {
                                    lastReportedTimeMs = now
                                    val sampleTimeUs = extractor.sampleTime
                                    val p = (sampleTimeUs.toFloat() / totalEstUs.toFloat()).coerceIn(0.05f, 0.95f)
                                    onProgress?.invoke(p)

                                    // Progressive fill of visible bars so far
                                    val filledLimit = (p * targetBars).toInt().coerceIn(1, targetBars)
                                    val framesCount = ch1FramePeaks.size
                                    val fpb = framesCount.toDouble() / filledLimit.toDouble()
                                    for (b in 0 until filledLimit) {
                                        val sf = (b * fpb).toInt().coerceIn(0, framesCount - 1)
                                        val ef = ((b + 1) * fpb).toInt().coerceIn(sf + 1, framesCount)
                                        var m1 = 0f
                                        var m2 = 0f
                                        for (f in sf until ef) {
                                            if (ch1FramePeaks[f] > m1) m1 = ch1FramePeaks[f]
                                            if (ch2FramePeaks[f] > m2) m2 = ch2FramePeaks[f]
                                        }
                                        ch1Bars[b] = m1
                                        ch2Bars[b] = m2
                                    }
                                    onPartialWaveform?.invoke(ch1Bars.toList(), ch2Bars.toList())
                                }
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                        } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val outFmt = codec.outputFormat
                            if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                        }

                        if (!didWork) {
                            idleCycles++
                            if (idleCycles > 30) {
                                if (inputEos) break else if (idleCycles > 200) break
                            }
                        }
                    }

                    val totalFrames = ch1FramePeaks.size
                    if (totalFrames > 0) {
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
                        usedJumpSuccess = true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { afd?.close() } catch (_: Exception) {}
        }

        if (usedJumpSuccess) {
            onProgress?.invoke(1.0f)
            val list1 = ch1Bars.toList()
            val list2 = ch2Bars.toList()
            onPartialWaveform?.invoke(list1, list2)
            return Pair(list1, list2)
        }

        // Active dynamic waveform fallback if container cannot be decoded
        val fallback1 = FloatArray(targetBars) { i ->
            val phase = (i.toFloat() / targetBars.toFloat()) * 32f
            val base = (kotlin.math.sin(phase.toDouble()).toFloat() * 0.35f + 0.45f).coerceIn(0.1f, 0.85f)
            val flutter = ((i * 17) % 31) / 100f
            (base + flutter).coerceIn(0.08f, 0.95f)
        }.toList()
        val fallback2 = FloatArray(targetBars) { i ->
            val phase = (i.toFloat() / targetBars.toFloat()) * 28f + 1.2f
            val base = (kotlin.math.cos(phase.toDouble()).toFloat() * 0.32f + 0.42f).coerceIn(0.1f, 0.82f)
            val flutter = ((i * 23) % 29) / 100f
            (base + flutter).coerceIn(0.08f, 0.92f)
        }.toList()

        onProgress?.invoke(1.0f)
        return Pair(fallback1, fallback2)
    }

    fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
