package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.model.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrack: AudioTrack? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentIndex: Int = -1,
    val totalTracks: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val loopPointA: Long? = null,
    val loopPointB: Long? = null,
    val errorMessage: String? = null
)

class AudioPlayerService : Service(), MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private val binder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var tickerJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentPlaylist: List<AudioTrack> = emptyList()
    private var currentTrackIndex: Int = -1

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LeoMP3:PlaybackWakeLock")

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_TOGGLE -> togglePlayPause()
            ACTION_REWIND_20 -> seekRelative(-20000L)
            ACTION_REWIND_2 -> seekRelative(-2000L)
            ACTION_FORWARD_5 -> seekRelative(5000L)
            ACTION_FORWARD_30 -> seekRelative(30000L)
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun setPlaylistAndPlay(tracks: List<AudioTrack>, startIndex: Int) {
        if (tracks.isEmpty()) return
        currentPlaylist = tracks
        currentTrackIndex = startIndex.coerceIn(0, tracks.lastIndex)
        playTrack(currentPlaylist[currentTrackIndex])
    }

    fun playTrack(track: AudioTrack) {
        releasePlayer()

        try {
            requestAudioFocus()
            wakeLock?.acquire(3 * 60 * 60 * 1000L) // 3 hours max safeguard

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(applicationContext, track.uri)
                setOnPreparedListener(this@AudioPlayerService)
                setOnCompletionListener(this@AudioPlayerService)
                setOnErrorListener(this@AudioPlayerService)
                prepareAsync()
            }
            mediaPlayer = player

            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                currentTrack = track,
                currentPositionMs = 0L,
                durationMs = track.durationMs,
                currentIndex = currentTrackIndex,
                totalTracks = currentPlaylist.size,
                loopPointA = null,
                loopPointB = null,
                errorMessage = null
            )

            startForegroundWithNotification(track, isPlaying = false)
        } catch (e: Exception) {
            _playbackState.value = _playbackState.value.copy(
                errorMessage = "Error al abrir audio: ${e.localizedMessage}"
            )
        }
    }

    override fun onPrepared(mp: MediaPlayer?) {
        val duration = mp?.duration?.toLong() ?: 0L
        val activeTrack = _playbackState.value.currentTrack?.let {
            if (it.durationMs <= 0L && duration > 0L) it.copy(durationMs = duration) else it
        }

        applyPlaybackSpeed(_playbackState.value.playbackSpeed)
        mp?.start()

        _playbackState.value = _playbackState.value.copy(
            isPlaying = true,
            currentTrack = activeTrack,
            durationMs = if (duration > 0) duration else _playbackState.value.durationMs,
            errorMessage = null
        )

        activeTrack?.let { startForegroundWithNotification(it, isPlaying = true) }
        startTicker()
    }

    override fun onCompletion(mp: MediaPlayer?) {
        stopTicker()
        // Advance to next track if in playlist
        if (currentTrackIndex + 1 < currentPlaylist.size) {
            currentTrackIndex++
            playTrack(currentPlaylist[currentTrackIndex])
        } else {
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                currentPositionMs = _playbackState.value.durationMs
            )
            _playbackState.value.currentTrack?.let {
                startForegroundWithNotification(it, isPlaying = false)
            }
        }
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        stopTicker()
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            errorMessage = "Error de reproducción ($what, $extra)"
        )
        return true
    }

    fun togglePlayPause() {
        if (_playbackState.value.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
        stopTicker()
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
        _playbackState.value.currentTrack?.let {
            startForegroundWithNotification(it, isPlaying = false)
        }
    }

    fun resume() {
        requestAudioFocus()
        mediaPlayer?.let {
            it.start()
            _playbackState.value = _playbackState.value.copy(isPlaying = true)
            _playbackState.value.currentTrack?.let { track ->
                startForegroundWithNotification(track, isPlaying = true)
            }
            startTicker()
        } ?: run {
            _playbackState.value.currentTrack?.let { playTrack(it) }
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, _playbackState.value.durationMs.coerceAtLeast(0L))
        mediaPlayer?.seekTo(target.toInt())
        _playbackState.value = _playbackState.value.copy(currentPositionMs = target)
    }

    fun seekRelative(deltaMs: Long) {
        val current = mediaPlayer?.currentPosition?.toLong() ?: _playbackState.value.currentPositionMs
        val target = (current + deltaMs).coerceIn(0L, _playbackState.value.durationMs.coerceAtLeast(0L))
        seekTo(target)
    }

    fun playNext() {
        if (currentPlaylist.isNotEmpty() && currentTrackIndex + 1 < currentPlaylist.size) {
            currentTrackIndex++
            playTrack(currentPlaylist[currentTrackIndex])
        }
    }

    fun playPrevious() {
        if (currentPlaylist.isNotEmpty() && currentTrackIndex - 1 >= 0) {
            currentTrackIndex--
            playTrack(currentPlaylist[currentTrackIndex])
        } else {
            seekTo(0L)
        }
    }

    fun stopPlayback() {
        releasePlayer()
        stopTicker()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        abandonAudioFocus()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.25f, 3.0f)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = safeSpeed)
        applyPlaybackSpeed(safeSpeed)
    }

    private fun applyPlaybackSpeed(speed: Float) {
        try {
            mediaPlayer?.let { player ->
                val params = player.playbackParams
                params.speed = speed
                player.playbackParams = params
            }
        } catch (_: Exception) {}
    }

    fun setLoopPointA() {
        val current = _playbackState.value.currentPositionMs
        val b = _playbackState.value.loopPointB
        val validB = if (b != null && b > current) b else null
        _playbackState.value = _playbackState.value.copy(
            loopPointA = current,
            loopPointB = validB
        )
    }

    fun setLoopPointB() {
        val current = _playbackState.value.currentPositionMs
        val a = _playbackState.value.loopPointA ?: 0L
        val validB = if (current > a) current else (a + 1000L).coerceAtMost(_playbackState.value.durationMs)
        _playbackState.value = _playbackState.value.copy(
            loopPointA = a,
            loopPointB = validB
        )
        // If current position is beyond B, immediately jump to A
        if (current >= validB) {
            seekTo(a)
        }
    }

    fun clearLoop() {
        _playbackState.value = _playbackState.value.copy(
            loopPointA = null,
            loopPointB = null
        )
    }

    fun toggleLoopAB() {
        val a = _playbackState.value.loopPointA
        val b = _playbackState.value.loopPointB
        when {
            a == null -> setLoopPointA()
            b == null -> setLoopPointB()
            else -> clearLoop()
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition.toLong()
                        val dur = player.duration.toLong().coerceAtLeast(0L)

                        val loopA = _playbackState.value.loopPointA
                        val loopB = _playbackState.value.loopPointB
                        if (loopA != null && loopB != null && loopB > loopA) {
                            if (pos >= loopB || pos < loopA - 500L) {
                                player.seekTo(loopA.toInt())
                                _playbackState.value = _playbackState.value.copy(
                                    currentPositionMs = loopA,
                                    durationMs = if (dur > 0) dur else _playbackState.value.durationMs
                                )
                                delay(100L)
                                return@let
                            }
                        }

                        _playbackState.value = _playbackState.value.copy(
                            currentPositionMs = pos,
                            durationMs = if (dur > 0) dur else _playbackState.value.durationMs
                        )
                    }
                }
                delay(100L) // smooth 100ms updates for oscilloscope needle
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun startForegroundWithNotification(track: AudioTrack, isPlaying: Boolean) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleIntent = Intent(this, AudioPlayerService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePending = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val rewindIntent = Intent(this, AudioPlayerService::class.java).apply {
            action = ACTION_REWIND_20
        }
        val rewindPending = PendingIntent.getService(
            this,
            2,
            rewindIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val forwardIntent = Intent(this, AudioPlayerService::class.java).apply {
            action = ACTION_FORWARD_30
        }
        val forwardPending = PendingIntent.getService(
            this,
            3,
            forwardIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = Intent(this, AudioPlayerService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPending = PendingIntent.getService(
            this,
            4,
            nextIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseText = if (isPlaying) "Pausa" else "Reproducir"
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.title)
            .setContentText("Leo MP3 Player")
            .setContentIntent(openPending)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_rew, "-20s", rewindPending)
            .addAction(playPauseIcon, playPauseText, togglePending)
            .addAction(android.R.drawable.ic_media_ff, "+30s", forwardPending)
            .addAction(android.R.drawable.ic_media_next, "Sig", nextPending)
            .build()

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        } catch (_: Exception) {
            // Android 14+ fallback
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> pause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                        AudioManager.AUDIOFOCUS_GAIN -> resume()
                    }
                }
                .build()
            audioFocusRequest = req
            audioManager?.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                { focus ->
                    when (focus) {
                        AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                        AudioManager.AUDIOFOCUS_GAIN -> resume()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }

    companion object {
        const val CHANNEL_ID = "leo_mp3_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PAUSE = "com.example.leo.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.leo.ACTION_RESUME"
        const val ACTION_TOGGLE = "com.example.leo.ACTION_TOGGLE"
        const val ACTION_REWIND_20 = "com.example.leo.ACTION_REWIND_20"
        const val ACTION_REWIND_2 = "com.example.leo.ACTION_REWIND_2"
        const val ACTION_FORWARD_5 = "com.example.leo.ACTION_FORWARD_5"
        const val ACTION_FORWARD_30 = "com.example.leo.ACTION_FORWARD_30"
        const val ACTION_NEXT = "com.example.leo.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.leo.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.example.leo.ACTION_STOP"
    }
}
