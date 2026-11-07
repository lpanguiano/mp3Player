package com.example

import com.example.util.AudioUtil
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testFormatTime() {
        assertEquals("00:00", AudioUtil.formatTime(0L))
        assertEquals("00:05", AudioUtil.formatTime(5000L))
        assertEquals("01:05", AudioUtil.formatTime(65000L))
        assertEquals("10:30", AudioUtil.formatTime(630000L))
        assertEquals("00:00", AudioUtil.formatTime(-5000L))
    }

    @Test
    fun testJumpSeekCalculations() {
        val duration = 120000L // 2 minutes
        var current = 45000L // 45 seconds

        // -20s jump
        current = (current - 20000L).coerceIn(0L, duration)
        assertEquals(25000L, current)

        // -2s jump
        current = (current - 2000L).coerceIn(0L, duration)
        assertEquals(23000L, current)

        // +5s jump
        current = (current + 5000L).coerceIn(0L, duration)
        assertEquals(28000L, current)

        // +30s jump
        current = (current + 30000L).coerceIn(0L, duration)
        assertEquals(58000L, current)
    }

    @Test
    fun testLoopABBoundaries() {
        val loopA = 15000L // 15 seconds
        val loopB = 45000L // 45 seconds

        // While within loop [15s, 45s]
        var currentPos = 30000L
        val shouldLoop = currentPos >= loopB || currentPos < loopA
        assertEquals(false, shouldLoop)

        // When reaching end of loop
        currentPos = 45000L
        val shouldLoopAtEnd = currentPos >= loopB || currentPos < loopA
        assertEquals(true, shouldLoopAtEnd)

        // Reset position to A
        if (shouldLoopAtEnd) {
            currentPos = loopA
        }
        assertEquals(15000L, currentPos)
    }

    @Test
    fun testPlaybackSpeeds() {
        val validSpeeds = listOf(0.75f, 1.0f, 1.5f, 2.0f)
        for (speed in validSpeeds) {
            val clamped = speed.coerceIn(0.25f, 3.0f)
            assertEquals(speed, clamped, 0.001f)
        }
    }
}
