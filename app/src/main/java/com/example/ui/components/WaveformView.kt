package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.PlayheadRed
import com.example.ui.theme.PureBlack
import com.example.ui.theme.ScopeCenterLine
import com.example.ui.theme.ScopeGridColor
import kotlin.math.abs
import kotlin.math.sin

import androidx.compose.ui.geometry.Size

@Composable
fun OscilloscopeWaveformView(
    waveformCh1: List<Float>,
    waveformCh2: List<Float> = emptyList(),
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    loopPointA: Long? = null,
    loopPointB: Long? = null
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val actualProgress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val displayProgress = if (isDragging) dragProgress else actualProgress

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PureBlack)
            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
            .testTag("waveform_display")
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (size.width > 0 && durationMs > 0) {
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek((newProgress * durationMs).toLong())
                    }
                }
            }
            .pointerInput(durationMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        if (size.width > 0) {
                            dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        }
                    },
                    onDragEnd = {
                        if (durationMs > 0) {
                            onSeek((dragProgress * durationMs).toLong())
                        }
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (size.width > 0) {
                            dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(2.dp)) {
            val width = size.width
            val height = size.height
            if (width <= 0 || height <= 0) return@Canvas

            // 1. Draw Oscilloscope Grid Lines (horizontal & vertical)
            val numVerticalLines = 14
            val numHorizontalLines = 8

            // Vertical grid lines
            val colStep = width / numVerticalLines
            for (i in 1 until numVerticalLines) {
                val x = i * colStep
                drawLine(
                    color = ScopeGridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
            }

            // Horizontal grid lines
            val rowStep = height / numHorizontalLines
            for (i in 1 until numHorizontalLines) {
                val y = i * rowStep
                val isDivider = i == numHorizontalLines / 2
                drawLine(
                    color = if (isDivider) ScopeCenterLine else ScopeGridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = if (isDivider) 1.5f else 1f
                )
            }

            // 2. Dual Channels: Canal 1 (Superior) and Canal 2 (Inferior)
            val halfHeight = height / 2f
            val ch1Center = halfHeight / 2f
            val ch2Center = halfHeight + (halfHeight / 2f)
            val channelMaxAmp = (halfHeight / 2f) * 0.88f

            val ptsCh1 = if (waveformCh1.isNotEmpty()) waveformCh1 else List(240) { 0f }
            val ptsCh2 = if (waveformCh2.isNotEmpty()) waveformCh2 else ptsCh1
            val numBars = ptsCh1.size
            val barSpacing = width / numBars

            for (i in 0 until numBars) {
                val x = i * barSpacing + (barSpacing / 2f)

                // --- CANAL 1 (SUPERIOR - IZQUIERDO) ---
                val amp1 = ptsCh1[i].coerceIn(0f, 1f) * channelMaxAmp
                if (amp1 > 1.2f) {
                    drawLine(
                        color = NeonGreen,
                        start = Offset(x, ch1Center - amp1),
                        end = Offset(x, ch1Center + amp1),
                        strokeWidth = 1.8f,
                        cap = StrokeCap.Round
                    )
                } else {
                    drawLine(
                        color = NeonGreen.copy(alpha = 0.35f),
                        start = Offset(x - (barSpacing * 0.4f), ch1Center),
                        end = Offset(x + (barSpacing * 0.4f), ch1Center),
                        strokeWidth = 1f
                    )
                }

                // --- CANAL 2 (INFERIOR - DERECHO) ---
                val amp2 = ptsCh2[i].coerceIn(0f, 1f) * channelMaxAmp
                if (amp2 > 1.2f) {
                    drawLine(
                        color = NeonGreen,
                        start = Offset(x, ch2Center - amp2),
                        end = Offset(x, ch2Center + amp2),
                        strokeWidth = 1.8f,
                        cap = StrokeCap.Round
                    )
                } else {
                    drawLine(
                        color = NeonGreen.copy(alpha = 0.35f),
                        start = Offset(x - (barSpacing * 0.4f), ch2Center),
                        end = Offset(x + (barSpacing * 0.4f), ch2Center),
                        strokeWidth = 1f
                    )
                }
            }

            // 3. Draw A-B Loop overlay and markers if set
            if (durationMs > 0) {
                val xA = loopPointA?.let { (it.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) * width }
                val xB = loopPointB?.let { (it.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) * width }

                // Shaded active loop region
                if (xA != null && xB != null && xB > xA) {
                    drawRect(
                        color = Color(0x3300E676),
                        topLeft = Offset(xA, 0f),
                        size = Size(xB - xA, height)
                    )
                }

                // Marker A (Bright Gold/Amber)
                if (xA != null) {
                    val loopColor = Color(0xFFFFD600)
                    drawLine(
                        color = loopColor,
                        start = Offset(xA, 0f),
                        end = Offset(xA, height),
                        strokeWidth = 2.5f
                    )
                    // Indicator tag A
                    drawLine(color = loopColor, start = Offset(xA, 0f), end = Offset(xA + 10f, 0f), strokeWidth = 3f)
                    drawLine(color = loopColor, start = Offset(xA + 10f, 0f), end = Offset(xA, 10f), strokeWidth = 3f)
                }

                // Marker B (Bright Gold/Amber)
                if (xB != null) {
                    val loopColor = Color(0xFFFFD600)
                    drawLine(
                        color = loopColor,
                        start = Offset(xB, 0f),
                        end = Offset(xB, height),
                        strokeWidth = 2.5f
                    )
                    // Indicator tag B
                    drawLine(color = loopColor, start = Offset(xB - 10f, 0f), end = Offset(xB, 0f), strokeWidth = 3f)
                    drawLine(color = loopColor, start = Offset(xB - 10f, 0f), end = Offset(xB, 10f), strokeWidth = 3f)
                }
            }

            // 4. Draw Red Vertical Playhead Needle (as seen in user image!)
            val playheadX = (displayProgress * width).coerceIn(0f, width)

            // Red vertical cursor line
            drawLine(
                color = PlayheadRed,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, height),
                strokeWidth = if (isDragging) 3f else 2f
            )

            // Small red triangle pointer at the top
            val indicatorSize = 8f
            drawLine(
                color = PlayheadRed,
                start = Offset(playheadX - indicatorSize, 0f),
                end = Offset(playheadX + indicatorSize, 0f),
                strokeWidth = 3f
            )
            drawLine(
                color = PlayheadRed,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, indicatorSize),
                strokeWidth = 3f
            )
        }
    }
}
